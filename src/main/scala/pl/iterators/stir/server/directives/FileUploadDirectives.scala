package pl.iterators.stir.server.directives

import cats.effect.IO
import org.http4s.headers.`Content-Type`
import org.http4s.multipart.Multipart
import pl.iterators.stir.server._
import fs2.Stream
import fs2.io.file.{Files, Path}

import java.io.File

/**
 * @groupname fileupload File upload directives
 * @groupprio fileupload 80
 */
trait FileUploadDirectives {
  import BasicDirectives._
  import IODirectives._
  import MarshallingDirectives._

  /**
   * Streams the bytes of the file submitted using multipart with the given file name into a designated file on disk.
   * If there is an error writing to disk the request will be failed with the thrown exception, if there is no such
   * field the request will be rejected, if there are multiple file parts with the same name, the first one will be
   * used and the subsequent ones ignored.
   *
   * @group fileupload
   */
  def storeUploadedFile(fieldName: String, destFn: FileInfo => File): Directive[(FileInfo, File)] = {
      fileUpload(fieldName).flatMap {
        case (fileInfo, bytes) =>
          val dest = destFn(fileInfo)
          val path = Path.fromNioPath(dest.toPath)
          val uploadedF: IO[(FileInfo, File)] =
            bytes.through(Files[IO].writeAll(path)).compile.drain.map(_ => (fileInfo, dest)).onError(_ => IO.delay(dest.delete()))
          onSuccess(uploadedF)
      }
    }
////
//  /**
//   * Streams the bytes of the file submitted using multipart with the given field name into designated files on disk.
//   * If there is an error writing to disk the request will be failed with the thrown exception, if there is no such
//   * field the request will be rejected. Stored files are cleaned up on exit but not on failure.
//   *
//   * @group fileupload
//   */
//  @ApiMayChange
//  def storeUploadedFiles(fieldName: String, destFn: FileInfo => File): Directive1[immutable.Seq[(FileInfo, File)]] =
//    entity(as[Multipart.FormData]).flatMap { formData =>
//      extractRequestContext.flatMap { ctx =>
//        implicit val mat = ctx.materializer
//        implicit val ec = ctx.executionContext
//
//        val uploaded: Source[(FileInfo, File), Any] = formData.parts
//          .mapConcat { part =>
//            if (part.filename.isDefined && part.name == fieldName) part :: Nil
//            else {
//              part.entity.discardBytes()
//              Nil
//            }
//          }
//          .mapAsync(1) { part =>
//            val fileInfo = FileInfo(part.name, part.filename.get, part.entity.contentType)
//            val dest = destFn(fileInfo)
//
//            part.entity.dataBytes.runWith(FileIO.toPath(dest.toPath))
//              .flatMap(StreamUtils.handleIOResult)
//              .map(_ => (fileInfo, dest))
//          }
//
//        val uploadedF = uploaded.runWith(Sink.seq[(FileInfo, File)])
//
//        onSuccess(uploadedF)
//      }
//    }
//
  /**
   * Collects each body part that is a multipart file as a tuple containing metadata and a `Source`
   * for streaming the file contents somewhere. If there is no such field the request will be rejected,
   * if there are multiple file parts with the same name, the first one will be used and the subsequent
   * ones ignored.
   *
   * @group fileupload
   */
  def fileUpload(fieldName: String): Directive1[(FileInfo, Stream[IO, Byte])] =
    entity(as[Multipart[IO]]).flatMap { formData =>
      Directive[Tuple1[(FileInfo, Stream[IO, Byte])]] { inner => ctx =>
        formData.parts.find(_.name.contains(fieldName)) match {
          case Some(part) if part.filename.isDefined && part.contentType.isDefined =>
            val fileInfo = FileInfo(part.name.getOrElse(fieldName), part.filename.getOrElse(""), part.contentType.getOrElse(throw new IllegalStateException(s"Missing content type for part $fieldName")))
            val data = part.body
            inner(Tuple1((fileInfo, data)))(ctx)
          case _ =>
            IO.pure(RouteResult.Rejected(MissingFormFieldRejection(fieldName) :: Nil))
        }
      }
    }
//
//  /**
//   * Collects each body part that is a multipart file as a tuple containing metadata and a `Source`
//   * for streaming the file contents somewhere. If there is no such field the request will be rejected.
//   * Files are buffered into temporary files on disk so in-memory buffers don't overflow. The temporary
//   * files are cleaned up once materialized, or on exit if the stream is not consumed.
//   *
//   * @group fileupload
//   */
//  @ApiMayChange
//  def fileUploadAll(fieldName: String): Directive1[immutable.Seq[(FileInfo, Source[ByteString, Any])]] =
//    extractRequestContext.flatMap { ctx =>
//      implicit val ec = ctx.executionContext
//
//      def tempDest(fileInfo: FileInfo): File = {
//        val dest = File.createTempFile("akka-http-upload", ".tmp")
//        dest.deleteOnExit()
//        dest
//      }
//
//      storeUploadedFiles(fieldName, tempDest).map { files =>
//        files.map {
//          case (fileInfo, src) =>
//            val byteSource: Source[ByteString, Any] = FileIO.fromPath(src.toPath)
//              .mapMaterializedValue { f =>
//                f.onComplete(_ => src.delete())
//              }
//
//            (fileInfo, byteSource)
//        }
//      }
//    }
}

object FileUploadDirectives extends FileUploadDirectives

/**
 * Additional metadata about the file being uploaded/that was uploaded using the [[FileUploadDirectives]]
 *
 * @param fieldName Name of the form field the file was uploaded in
 * @param fileName User specified name of the uploaded file
 * @param contentType Content type of the file
 */
final case class FileInfo(fieldName: String, fileName: String, contentType: `Content-Type`)