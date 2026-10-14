package pl.iterators.stir.server.directives

import cats.effect.IO
import cats.syntax.all._
import fs2.Stream
import fs2.io.file.{ Files, Path }
import org.http4s.{ EntityDecoder, MediaType }
import org.http4s.headers.`Content-Type`
import org.http4s.multipart.{ Multipart, Part }
import pl.iterators.stir.server._

import java.io.File
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
   * used and the subsequent ones ignored. Parts larger than 50 MiB are spooled to a temporary file while decoding.
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
   * a `Content-Type` are stored as `application/octet-stream`; parts larger than 50 MiB are spooled to a temporary
   * file while decoding.
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
   * 50 MiB to a temporary file, and keeps that decoder open while the inner route runs. The part's stream must
   * therefore be consumed inside the inner route (written to disk, folded, forwarded and awaited); a response
   * whose body still reads from it would find the spooled file gone. If there is no such field the request is
   * rejected; if several parts share the name the first one is used.
   *
   * @group fileupload
   */
  def fileUploadSpooled(fieldName: String): Directive1[(FileInfo, Stream[IO, Byte])] =
    Directive[Tuple1[(FileInfo, Stream[IO, Byte])]] { inner => ctx =>
      EntityDecoder.mixedMultipartResource[IO]().use { decoder =>
        MarshallingDirectives.decodeEntity(ctx.request, decoder).flatMap {
          case Left(rejection) => IO.pure(RouteResult.Rejected(rejection :: Nil))
          case Right(formData) =>
            formData.parts.find(part => part.name.contains(fieldName) && part.filename.isDefined) match {
              case Some(part) => inner(Tuple1((fileInfo(part), part.body)))(ctx)
              case None       => IO.pure(RouteResult.Rejected(MissingFormFieldRejection(fieldName) :: Nil))
            }
        }
      }
    } & cancelRejections(classOf[UnsupportedRequestContentTypeRejection])

  /**
   * Collects each body part that is a multipart file with the given field name as a tuple containing metadata and
   * a stream of the file contents. Files are stored in temporary files on disk so in-memory buffers don't overflow;
   * each temporary file is deleted once its stream is consumed, or on exit if it is not.
   *
   * @group fileupload
   */
  def fileUploadAll(fieldName: String): Directive1[Seq[(FileInfo, Stream[IO, Byte])]] = {
    def tempDest(@unused fileInfo: FileInfo): File = {
      val dest = File.createTempFile("http4s-stir-upload", ".tmp")
      dest.deleteOnExit()
      dest
    }

    storeUploadedFiles(fieldName, tempDest).map { files =>
      files.map {
        case (info, src) =>
          val path = Path(src.getAbsolutePath)
          (info, Files[IO].readAll(path).onFinalize(IO.delay(src.delete()).void))
      }
    }
  }

  private def multipartToDisk(fieldName: String, destFn: FileInfo => File,
      firstOnly: Boolean): Directive1[Seq[(FileInfo, File)]] =
    Directive[Tuple1[Seq[(FileInfo, File)]]] { inner => ctx =>
      EntityDecoder.mixedMultipartResource[IO]().use { decoder =>
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
