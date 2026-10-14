package pl.iterators.stir.server.directives

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.syntax.all._
import fs2.Stream
import org.http4s.{ Headers, MediaType, Request, Response }
import org.http4s.headers.{ `Content-Disposition`, `Content-Type` }
import org.http4s.multipart.{ Multipart, Multiparts, Part }
import org.typelevel.ci._
import pl.iterators.stir.server._

import java.io.File
import java.nio.file.{ Files => NioFiles }

class FileUploadDirectivesSpec extends RoutingSpec {
  override implicit def runtime: IORuntime = IORuntime.global

  def bytes(content: String): Stream[IO, Byte] = Stream.emits(content.getBytes("UTF-8")).covary[IO]

  def textPart(field: String, filename: String, content: String): Part[IO] =
    Part.fileData[IO](field, filename, bytes(content), `Content-Type`(MediaType.text.plain))

  def untypedPart(field: String, filename: String, content: String): Part[IO] =
    Part[IO](Headers(`Content-Disposition`("form-data", Map(ci"name" -> field, ci"filename" -> filename))),
      bytes(content))

  def upload(parts: Part[IO]*): Request[IO] = {
    val multipart: Multipart[IO] = Multiparts.forSync[IO].flatMap(_.multipart(parts.toVector)).unsafeRunSync()
    Post("/", multipart).transformHeaders(_ ++ multipart.headers)
  }

  def tempDest(info: FileInfo): File = {
    val file = File.createTempFile("stir-upload-spec", info.fileName)
    file.deleteOnExit()
    file
  }

  def read(file: File): String = new String(NioFiles.readAllBytes(file.toPath), "UTF-8")

  def mediaType(info: FileInfo): String =
    s"${info.contentType.mediaType.mainType}/${info.contentType.mediaType.subType}"

  "fileUpload" should {
    "provide the declared content type" in {
      upload(textPart("file", "a.txt", "abc")) ~> fileUpload("file") { case (info, _) =>
        complete(mediaType(info))
      } ~> check { responseAs[String] shouldEqual "text/plain" }
    }
    "default the content type to application/octet-stream when the part has none" in {
      upload(untypedPart("file", "a.bin", "abc")) ~> fileUpload("file") { case (info, data) =>
        onSuccess(data.through(fs2.text.utf8.decode).compile.string) { content =>
          complete(mediaType(info) + ":" + content)
        }
      } ~> check { responseAs[String] shouldEqual "application/octet-stream:abc" }
    }
    "reject with MissingFormFieldRejection when no part has the field name" in {
      upload(textPart("other", "a.txt", "abc")) ~> fileUpload("file") { _ => completeOk } ~> check {
        rejection shouldEqual MissingFormFieldRejection("file")
      }
    }
  }

  "fileUploadSpooled" should {
    "stream the first matching part while the inner route runs" in {
      upload(textPart("file", "a.txt", "first"), textPart("file", "b.txt", "second")) ~> fileUploadSpooled("file") {
        case (info, data) =>
          onSuccess(data.through(fs2.text.utf8.decode).compile.string) { content =>
            complete(info.fileName + ":" + content)
          }
      } ~> check { responseAs[String] shouldEqual "a.txt:first" }
    }
    "default the content type to application/octet-stream when the part has none" in {
      upload(untypedPart("file", "a.bin", "abc")) ~> fileUploadSpooled("file") { case (info, _) =>
        complete(mediaType(info))
      } ~> check { responseAs[String] shouldEqual "application/octet-stream" }
    }
    "reject with MissingFormFieldRejection when no part has the field name" in {
      upload(textPart("other", "a.txt", "abc")) ~> fileUploadSpooled("file") { _ => completeOk } ~> check {
        rejection shouldEqual MissingFormFieldRejection("file")
      }
    }
    "keep a spooled part readable while the response body streams it" in {
      upload(textPart("file", "a.txt", "spooled to disk")) ~!> fileUploadSpooled("file", maxSizeBeforeWrite = 1) {
        case (_, data) => complete(Response[IO]().withBodyStream(data))
      } ~> check { responseAs[String] shouldEqual "spooled to disk" }
    }
    "stream a part over a real server" in {
      upload(textPart("file", "a.txt", "streamed")) ~!> fileUploadSpooled("file") { case (_, data) =>
        onSuccess(data.through(fs2.text.utf8.decode).compile.string) { content => complete(content) }
      } ~> check { responseAs[String] shouldEqual "streamed" }
    }
  }

  "storeUploadedFile" should {
    "write the first matching part to the destination" in {
      upload(textPart("file", "a.txt", "first"), textPart("file", "b.txt", "second"))                         ~>
      storeUploadedFile("file", tempDest) { case (info, file) => complete(info.fileName + ":" + read(file)) } ~>
      check { responseAs[String] shouldEqual "a.txt:first" }
    }
  }

  "storeUploadedFiles" should {
    "store every part with the field name, including parts without a content type" in {
      upload(textPart("file", "a.txt", "first"), untypedPart("file", "b.bin", "second"), textPart("x", "c", "no")) ~>
      storeUploadedFiles("file", tempDest) { files =>
        complete(files.map { case (info, file) => info.fileName + "=" + read(file) }.mkString(","))
      } ~> check { responseAs[String] shouldEqual "a.txt=first,b.bin=second" }
    }
    "store the parts over a real server" in {
      upload(textPart("file", "a.txt", "first"), untypedPart("file", "b.bin", "second")) ~!>
      storeUploadedFiles("file", tempDest) { files => complete(files.size.toString) } ~>
      check { responseAs[String] shouldEqual "2" }
    }
  }

  "fileUploadAll" should {
    "stream every stored part" in {
      upload(textPart("file", "a.txt", "first"), untypedPart("file", "b.bin", "second")) ~>
      fileUploadAll("file") { files =>
        onSuccess(files.toList.traverse { case (info, data) =>
          data.through(fs2.text.utf8.decode).compile.string.map(info.fileName + "=" + _)
        }) { rendered => complete(rendered.mkString(",")) }
      } ~> check { responseAs[String] shouldEqual "a.txt=first,b.bin=second" }
    }
  }
}
