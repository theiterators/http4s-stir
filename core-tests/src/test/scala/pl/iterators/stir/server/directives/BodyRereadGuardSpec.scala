package pl.iterators.stir.server.directives

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import fs2.Stream
import org.http4s.{ MediaType, Request, Status, UrlForm }
import org.http4s.headers.`Content-Type`
import org.http4s.multipart.{ Multiparts, Part }
import pl.iterators.stir.server.{
  Directive0,
  ExceptionHandler,
  IllegalRequestException,
  MalformedRequestContentRejection,
  RejectionHandler,
  Route
}

import java.io.File

class BodyRereadGuardSpec extends RoutingSpec {
  override implicit def runtime: IORuntime = IORuntime.global

  val readPartly: Route = extractRequest { request => onSuccess(request.body.take(1).compile.drain)(reject) }
  val readFully: Route = extractRequest { request => onSuccess(request.body.compile.drain)(reject) }
  val echo: Route = entity(as[String])(s => complete("read:" + s))

  // the failing read surfaces as an exception, or as a rejection where entity rejects every failure (#74)
  val surfaced: Directive0 =
    handleExceptions(ExceptionHandler { case e: IllegalStateException => complete(e.getMessage) }) &
    handleRejections(RejectionHandler.newBuilder().handle {
      case MalformedRequestContentRejection(text, _) => complete(text)
    }.result())

  def multipart: Request[IO] = {
    val parts = Vector(Part.fileData[IO]("file", "big.txt",
      Stream.emits(("y" * (1024 * 1024)).getBytes("UTF-8")).covary[IO], `Content-Type`(MediaType.text.plain)))
    val body = Multiparts.forSync[IO].flatMap(_.multipart(parts)).unsafeRunSync()
    Post("/", body).transformHeaders(_ ++ body.headers)
  }

  "a request body read partly" should {
    "fail a later read at once instead of handing it a body that cannot be read again" in {
      Post("/", "abc") ~> surfaced(readPartly ~ echo) ~> check {
        responseAs[String] shouldEqual MiscDirectives.RereadMessage
      }
    }
  }

  "a request body of known length read fully" should {
    "stay readable in memory, as before" in {
      Post("/", "abc") ~> (readFully ~ echo) ~> check { responseAs[String] shouldEqual "read:abc" }
    }
    "count a reader stopping at its declared length without asking for the end as stopping partway" in {
      val toLength: Route = extractRequest { request => onSuccess(request.body.take(3).compile.drain)(reject) }
      Post("/", "abc") ~> surfaced(toLength ~ echo) ~> check {
        responseAs[String] shouldEqual MiscDirectives.RereadMessage
      }
    }
  }

  "a chunked or length-less request body" should {
    def streamed = Post().withBodyStream(Stream.emits("abc".getBytes("UTF-8")).covary[IO])
    "be readable once" in {
      streamed ~> echo ~> check { responseAs[String] shouldEqual "read:abc" }
    }
    "fail a second read even after the first read it to the end" in {
      streamed ~> surfaced(readFully ~ echo) ~> check { responseAs[String] shouldEqual MiscDirectives.RereadMessage }
    }
    "be answered over a real server when an alternative reads it again after entity" in {
      val chunked = Stream.emits(("w" * (300 * 1024)).getBytes("UTF-8")).covary[IO].chunkN(8192).flatMap(Stream.chunk)
      Post().withBodyStream(chunked) ~!> {
        entity(as[String])(_ => reject) ~ echo
      } ~> check { status.isSuccess shouldBe false }
    }
    "be answered over a real server when an alternative reads it again after fileUpload" in {
      multipart ~!> {
        fileUpload("file") { case (_, bytes) => onSuccess(bytes.compile.drain)(reject) } ~ echo
      } ~> check { status.isSuccess shouldBe false }
    }
  }

  "an alternative after a body that failed mid-stream over a real server" should {
    "be answered instead of waiting for bytes that never come" in {
      // a body transform (decompression, decryption, a checksum) failing after 64 KiB: entity rejects, and the
      // alternative reads the original body, of which the server has already handed out a part
      val failingAfter64KiB = mapRequest(request =>
        request.withBodyStream(request.body.take(64 * 1024L) ++
          Stream.raiseError[IO](IllegalRequestException(Status.BadRequest, "broken"))))
      Post("/", "x" * (1024 * 1024)) ~!> {
        failingAfter64KiB(echo) ~ echo
      } ~> check { status.isSuccess shouldBe false }
    }
  }

  "an alternative after a form or an upload that failed mid-stream over a real server" should {
    // the reviewer's cases: whichever way the failure surfaces (a 500, or a rejection letting the alternative run),
    // the request is answered
    val failingAfter64KiB = mapRequest(request =>
      request.withBodyStream(request.body.take(64 * 1024L) ++
        Stream.raiseError[IO](new IllegalStateException("broken upstream"))))
    "be answered after formFieldSeq" in {
      Post("/", UrlForm("field" -> ("z" * (1024 * 1024)))) ~!> {
        failingAfter64KiB(formFieldSeq(fields => complete(fields.size.toString))) ~ echo
      } ~> check { status.isSuccess shouldBe false }
    }
    "be answered after fileUpload" in {
      multipart ~!> {
        failingAfter64KiB(fileUpload("file") { case (_, bytes) => onSuccess(bytes.compile.drain)(complete("ok")) }) ~
        echo
      } ~> check { status.isSuccess shouldBe false }
    }
    "be answered after storeUploadedFile" in {
      multipart ~!> {
        failingAfter64KiB(storeUploadedFile("file", _ => File.createTempFile("guard", ".tmp")) { case (_, file) =>
          file.delete(); complete("ok")
        }) ~ echo
      } ~> check { status.isSuccess shouldBe false }
    }
  }
}
