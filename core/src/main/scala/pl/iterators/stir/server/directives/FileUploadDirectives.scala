package pl.iterators.stir.server.directives

import cats.effect.{ IO, Outcome }
import cats.syntax.all._
import fs2.Stream
import fs2.io.file.{ Files, Path }
import org.http4s.{ EmptyBody, EntityDecoder, MediaType }
import org.http4s.headers.`Content-Type`
import org.http4s.multipart.{ Multipart, Part }
import pl.iterators.stir.server._

import java.io.File
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.unused

/**
 * @groupname fileupload File upload directives
 * @groupprio fileupload 80
 */
trait FileUploadDirectives {
  import BasicDirectives._
  import FileUploadDirectives._
  import MarshallingDirectives._
  import RouteDirectives._

  /**
   * Streams the bytes of the file submitted using multipart with the given file name into a designated file on disk.
   * If there is an error writing to disk the request will be failed with the thrown exception, if there is no such
   * field the request will be rejected, if there are multiple file parts with the same name, the first one will be
   * used and the subsequent ones ignored. Parts larger than [[FileUploadDirectives.DiskSpoolThreshold]] are spooled to
   * a temporary file while decoding; a body with more than [[FileUploadDirectives.MaxParts]] parts is rejected (400).
   *
   * @group fileupload
   */
  def storeUploadedFile(fieldName: String, destFn: FileInfo => File): Directive[(FileInfo, File)] =
    multipartToDisk(fieldName, destFn, firstOnly = true).flatMap {
      case (fileInfo, file) +: _ => tprovide((fileInfo, file))
      case _                     => reject(MissingFormFieldRejection(fieldName)): Directive[(FileInfo, File)]
    }

  /**
   * Streams the bytes of the files submitted using multipart with the given field name into designated files on
   * disk. If there is an error writing to disk the request will be failed with the thrown exception. Parts without
   * a `Content-Type` are stored as `application/octet-stream`; parts larger than
   * [[FileUploadDirectives.DiskSpoolThreshold]] are spooled to a temporary file while decoding; a body with more than
   * [[FileUploadDirectives.MaxParts]] parts is rejected (400).
   *
   * @group fileupload
   */
  def storeUploadedFiles(fieldName: String, destFn: FileInfo => File): Directive1[Seq[(FileInfo, File)]] =
    multipartToDisk(fieldName, destFn, firstOnly = false)

  /**
   * Collects the first body part that is a multipart file with the given field name as a tuple containing metadata
   * and a stream of the file contents. If there is no such field the request will be rejected. The whole multipart
   * body is buffered in memory, because the part's stream outlives the decoder; use `storeUploadedFile` or
   * `fileUploadAll` for large uploads.
   *
   * @group fileupload
   */
  def fileUpload(fieldName: String): Directive1[(FileInfo, Stream[IO, Byte])] =
    entity(as[Multipart[IO]]).flatMap { formData =>
      formData.parts.find(part => part.name.contains(fieldName) && part.filename.isDefined) match {
        case Some(part) => provide((fileInfo(part), part.body))
        case None       => reject(MissingFormFieldRejection(fieldName)): Directive1[(FileInfo, Stream[IO, Byte])]
      }
    }

  /**
   * Like [[fileUpload]], but decodes the multipart body with http4s' mixed decoder, which spools parts above
   * 50 MiB to a temporary file, and keeps that decoder (and its temporary files) alive until the inner route has
   * completed and the response body has been consumed. The part's stream can therefore be read inside the route
   * or streamed as the response. If there is no such field the request is rejected; if several parts share the
   * name the first one is used.
   *
   * @group fileupload
   */
  def fileUploadSpooled(fieldName: String): Directive1[(FileInfo, Stream[IO, Byte])] =
    fileUploadSpooled(fieldName, DefaultMaxSizeBeforeWrite)

  /**
   * @param maxSizeBeforeWrite the size in bytes above which a part is spooled to a temporary file
   * @group fileupload
   */
  def fileUploadSpooled(fieldName: String, maxSizeBeforeWrite: Int): Directive1[(FileInfo, Stream[IO, Byte])] =
    Directive[Tuple1[(FileInfo, Stream[IO, Byte])]] { inner => ctx =>
      EntityDecoder.mixedMultipartResource[IO](maxSizeBeforeWrite = maxSizeBeforeWrite, maxParts = MaxParts,
        failOnLimit = true).allocated.flatMap {
        case (decoder, releaseDecoder) =>
          IO.ref(false).flatMap { released =>
            // the decoder is released once: on rejection, on error, or when the response body is finalized
            val release = released.getAndSet(true).flatMap(done => if (done) IO.unit else releaseDecoder)
            val run = MarshallingDirectives.decodeEntity(ctx.request, decoder).flatMap {
              case Left(rejection) => IO.pure(RouteResult.Rejected(rejection :: Nil))
              case Right(formData) =>
                formData.parts.find(part => part.name.contains(fieldName) && part.filename.isDefined) match {
                  case Some(part) => inner(Tuple1((fileInfo(part), part.body)))(ctx)
                  case None       => IO.pure(RouteResult.Rejected(MissingFormFieldRejection(fieldName) :: Nil))
                }
            }
            run.attempt.flatMap {
              case Right(RouteResult.Complete(response)) if response.body eq EmptyBody =>
                release.as(RouteResult.Complete(response))
              case Right(RouteResult.Complete(response)) =>
                IO.pure(RouteResult.Complete(response.withBodyStream(response.body.onFinalize(release))))
              case Right(rejected) => release.as(rejected)
              case Left(error)     => release *> IO.raiseError(error)
            }
          }
      }
    } & cancelRejections(classOf[UnsupportedRequestContentTypeRejection])

  /**
   * Collects each body part that is a multipart file with the given field name as a tuple containing metadata and
   * a stream of the file contents. Files are stored in temporary files on disk so in-memory buffers don't overflow.
   * The temporary files are deleted once the response body has been consumed (so a part can be streamed back as the
   * response), or as soon as the inner route rejects, fails or is cancelled; nothing is registered with
   * `deleteOnExit`, which would keep every path in memory for the life of the process. The part streams are therefore
   * only valid while the request is being handled: copy a part elsewhere before completing if it is needed later.
   *
   * @group fileupload
   */
  def fileUploadAll(fieldName: String): Directive1[Seq[(FileInfo, Stream[IO, Byte])]] =
    Directive[Tuple1[Seq[(FileInfo, Stream[IO, Byte])]]] { inner => ctx =>
      // every file is owned from the moment it is created, so a failure or cancellation while the parts are still
      // being copied to disk cannot leak the ones already written
      val created = new AtomicReference(List.empty[File])
      def tempDest(@unused fileInfo: FileInfo): File = {
        val file = File.createTempFile("http4s-stir-upload", ".tmp")
        created.updateAndGet(file :: _)
        file
      }
      val delete = IO.blocking(created.get.foreach(_.delete())).void
      IO.ref(false).flatMap { released =>
        // the files are deleted once: on rejection, error or cancellation, or when the response body is finalized
        val release = released.getAndSet(true).flatMap(done => if (done) IO.unit else delete)
        val run = storeUploadedFiles(fieldName, tempDest).tapply { case Tuple1(files) =>
          innerCtx =>
            val streams = files.map { case (info, src) => (info, Files[IO].readAll(Path(src.getAbsolutePath))) }
            IO.defer(inner(Tuple1(streams))(innerCtx))
        }(ctx)
        IO.uncancelable { poll =>
          poll(run).guaranteeCase {
            case Outcome.Succeeded(_) => IO.unit
            case _                    => release
          }.flatMap {
            case RouteResult.Complete(response) if response.body eq EmptyBody =>
              release.as(RouteResult.Complete(response))
            case RouteResult.Complete(response) =>
              IO.pure(RouteResult.Complete(response.withBodyStream(response.body.onFinalize(release))))
            case rejected => release.as(rejected)
          }
        }
      }
    }

  private def multipartToDisk(fieldName: String, destFn: FileInfo => File,
      firstOnly: Boolean): Directive1[Seq[(FileInfo, File)]] =
    Directive[Tuple1[Seq[(FileInfo, File)]]] { inner => ctx =>
      EntityDecoder.mixedMultipartResource[IO](maxSizeBeforeWrite = DiskSpoolThreshold, maxParts = MaxParts,
        failOnLimit = true).use { decoder =>
        MarshallingDirectives.decodeEntity(ctx.request, decoder).flatMap {
          case Left(rejection) => IO.pure(Left(rejection): Either[Rejection, Seq[(FileInfo, File)]])
          case Right(formData) =>
            val matching = formData.parts.filter(part => part.name.contains(fieldName) && part.filename.isDefined)
            val selected = if (firstOnly) matching.take(1) else matching
            selected.toList.traverse(store(_, destFn)).map(files =>
              Right(files): Either[Rejection, Seq[(FileInfo, File)]])
        }
      }.flatMap {
        case Right(files)    => inner(Tuple1(files))(ctx)
        case Left(rejection) => IO.pure(RouteResult.Rejected(rejection :: Nil))
      }
    } & cancelRejections(classOf[UnsupportedRequestContentTypeRejection])

  private def store(part: Part[IO], destFn: FileInfo => File): IO[(FileInfo, File)] = {
    val info = fileInfo(part)
    val dest = destFn(info)
    val path = Path(dest.getAbsolutePath)
    part.body.through(Files[IO].writeAll(path)).compile.drain.as((info, dest)).onError {
      case _ => IO.delay(dest.delete()).void
    }
  }

  private def fileInfo(part: Part[IO]): FileInfo =
    FileInfo(part.name.getOrElse(""), part.filename.getOrElse(""), part.contentType.getOrElse(DefaultPartContentType))
}

object FileUploadDirectives extends FileUploadDirectives {

  /**
   * The most parts a multipart body may carry for the disk-based directives (`storeUploadedFile(s)`, `fileUploadAll`,
   * `fileUploadSpooled`); a body with more fails with a 400 instead of silently losing parts. Pekko has no limit and
   * neither have stir's in-memory `fileUpload` and `formFields`; the disk-based decoder keeps a temporary file and a
   * fiber per part, so the cap is a cheap guard against a client opening thousands of them.
   */
  val MaxParts: Int = 1000

  /**
   * The size above which `storeUploadedFile(s)` and `fileUploadAll` spool a part to a temporary file while the body
   * is decoded (64 KiB). Every selected part is written to its destination afterwards anyway, and up to this many
   * bytes of each part stay on the heap until then, so the threshold bounds memory to `MaxParts` times this value.
   */
  val DiskSpoolThreshold: Int = 64 * 1024

  /** http4s' default: parts above 50 MiB are spooled to a temporary file. */
  val DefaultMaxSizeBeforeWrite: Int = 52428800

  /** Content type assumed for a multipart file part that declares none. */
  val DefaultPartContentType: `Content-Type` = `Content-Type`(MediaType.application.`octet-stream`)
}

/**
 * Additional metadata about the file being uploaded/that was uploaded using the [[FileUploadDirectives]]
 *
 * @param fieldName Name of the form field the file was uploaded in
 * @param fileName User specified name of the uploaded file
 * @param contentType Content type of the file
 */
final case class FileInfo(fieldName: String, fileName: String, contentType: `Content-Type`)
