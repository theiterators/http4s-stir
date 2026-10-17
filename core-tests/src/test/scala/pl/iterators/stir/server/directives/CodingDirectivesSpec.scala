package pl.iterators.stir.server.directives

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import fs2.{ Pipe, Stream }
import fs2.text.utf8
import fs2.compression.{ Compression, DeflateParams, InflateParams, ZLibParams }
import org.http4s.{ ContentCoding, HttpVersion, MediaType, Method, QValue, Request, Response, Status, TransferCoding }
import org.scalatest.Inside
import org.http4s.headers.{ `Accept-Encoding`, `Content-Encoding`, `Content-Type`, `Transfer-Encoding` }
import pl.iterators.stir.server._

import java.io.File
import java.nio.file.{ Files => NioFiles }
import scala.concurrent.duration._

class CodingDirectivesSpec extends RoutingSpec with Inside {
  override implicit def runtime: IORuntime = IORuntime.global

  val text = "Hello, compression! " * 50
  val textRoute: Route = complete(text)
  val echo: Route = entity(as[String]) { s => complete(s) }

  def gzipped(s: String): Array[Byte] =
    Stream.emits(s.getBytes("UTF-8")).covary[IO]
      .through(Compression[IO].gzip(fileName = None, modificationTime = None, comment = None,
        deflateParams = DeflateParams(header = ZLibParams.Header.GZIP)))
      .compile.to(Array).unsafeRunSync()

  def deflated(s: String): Array[Byte] =
    Stream.emits(s.getBytes("UTF-8")).covary[IO]
      .through(Compression[IO].deflate(DeflateParams(header = ZLibParams.Header.ZLIB)))
      .compile.to(Array).unsafeRunSync()

  def gunzipped: String =
    responseEntity.through(Compression[IO].gunzip()).flatMap(_.content)
      .through(utf8.decode).compile.string.unsafeRunSync()

  def inflated: String =
    responseEntity.through(Compression[IO].inflate(InflateParams(header = ZLibParams.Header.ZLIB)))
      .through(utf8.decode).compile.string.unsafeRunSync()

  val refuseIdentity = `Accept-Encoding`(ContentCoding.identity.withQValue(QValue.Zero))

  "a (decodeRequestWith(gzip) | decodeRequestWith(identity)) compound directive" should {
    val decodeWithGzipOrIdentity = decodeRequestWith(ContentCoding.gzip) | decodeRequestWith(ContentCoding.identity)
    "decode the request content if it has encoding 'gzip'" in {
      Post("/", gzipped("Hello"))       ~> addHeader(`Content-Encoding`(ContentCoding.gzip)) ~>
      decodeWithGzipOrIdentity { echo } ~> check { responseAs[String] shouldEqual "Hello" }
    }
    "decode the request content if it has encoding 'identity'" in {
      Post("/", "yes") ~> addHeader(`Content-Encoding`(ContentCoding.identity)) ~> decodeWithGzipOrIdentity { echo } ~>
      check { responseAs[String] shouldEqual "yes" }
    }
    "decode the request content if no Content-Encoding header is present" in {
      Post("/", "yes") ~> decodeWithGzipOrIdentity { echo } ~> check { responseAs[String] shouldEqual "yes" }
    }
    "reject the request content if it has encoding 'deflate'" in {
      Post("/", "yes") ~> addHeader(`Content-Encoding`(ContentCoding.deflate)) ~> decodeWithGzipOrIdentity { echo } ~>
      check {
        rejections shouldEqual Seq(UnsupportedRequestEncodingRejection(ContentCoding.gzip),
          UnsupportedRequestEncodingRejection(ContentCoding.identity))
      }
    }
  }

  "the encodeResponseWith(identity) directive" should {
    "produce a response if no Accept-Encoding is present in the request" in {
      Get() ~> encodeResponseWith(ContentCoding.identity) { textRoute } ~> check {
        header[`Content-Encoding`] shouldEqual None
        responseAs[String] shouldEqual text
      }
    }
    "produce a not encoded response if the client only accepts non matching encodings" in {
      Get() ~> addHeader(`Accept-Encoding`(ContentCoding.gzip)) ~> encodeResponseWith(ContentCoding.identity) {
        textRoute
      } ~> check {
        header[`Content-Encoding`] shouldEqual None
        responseAs[String] shouldEqual text
      }
    }
    "reject the request if the request has an 'Accept-Encoding: identity; q=0' header" in {
      Get() ~> addHeader(refuseIdentity) ~> encodeResponseWith(ContentCoding.identity) { textRoute } ~> check {
        rejection shouldEqual UnacceptedResponseEncodingRejection(Set(ContentCoding.identity))
      }
    }
  }

  "a (encodeResponseWith(gzip) | encodeResponseWith(identity)) compound directive" should {
    val encodeGzipOrIdentity = encodeResponseWith(ContentCoding.gzip) | encodeResponseWith(ContentCoding.identity)
    "produce a GZIP encoded response if the request has no Accept-Encoding header" in {
      Get() ~> encodeGzipOrIdentity { textRoute } ~> check {
        header[`Content-Encoding`] shouldEqual Some(`Content-Encoding`(ContentCoding.gzip))
        gunzipped shouldEqual text
      }
    }
    "produce a GZIP encoded response if the request has an `Accept-Encoding: deflate;q=0.5, gzip` header" in {
      Get()                  ~> addHeader(`Accept-Encoding`(ContentCoding.deflate.withQValue(QValue.unsafeFromString("0.5")),
        ContentCoding.gzip)) ~> encodeGzipOrIdentity { textRoute } ~> check {
        header[`Content-Encoding`] shouldEqual Some(`Content-Encoding`(ContentCoding.gzip))
        gunzipped shouldEqual text
      }
    }
    "produce a non-encoded response if the request has an `Accept-Encoding: identity` header" in {
      Get() ~> addHeader(`Accept-Encoding`(ContentCoding.identity)) ~> encodeGzipOrIdentity { textRoute } ~> check {
        header[`Content-Encoding`] shouldEqual None
        responseAs[String] shouldEqual text
      }
    }
    "produce a non-encoded response if the request has an `Accept-Encoding: deflate` header" in {
      Get() ~> addHeader(`Accept-Encoding`(ContentCoding.deflate)) ~> encodeGzipOrIdentity { textRoute } ~> check {
        header[`Content-Encoding`] shouldEqual None
        responseAs[String] shouldEqual text
      }
    }
    "reject the request if it refuses identity and does not accept gzip" in {
      Get()                              ~> addHeader(`Accept-Encoding`(ContentCoding.deflate, ContentCoding.identity.withQValue(QValue.Zero))) ~>
      encodeGzipOrIdentity { textRoute } ~> check {
        rejections shouldEqual Seq(UnacceptedResponseEncodingRejection(Set(ContentCoding.gzip)),
          UnacceptedResponseEncodingRejection(Set(ContentCoding.identity)))
      }
    }
  }

  "the encodeResponse directive" should {
    "not encode when the request has no Accept-Encoding" in {
      Get() ~> encodeResponse(textRoute) ~> check {
        header[`Content-Encoding`] shouldEqual None
        responseAs[String] shouldEqual text
      }
    }
    "produce a GZIP encoded response if the request has an `Accept-Encoding: gzip, deflate` header" in {
      Get()                        ~> addHeader(`Accept-Encoding`(ContentCoding.gzip, ContentCoding.deflate)) ~>
      encodeResponse { textRoute } ~>
      check {
        header[`Content-Encoding`] shouldEqual Some(`Content-Encoding`(ContentCoding.gzip))
        gunzipped shouldEqual text
      }
    }
    "gzip the response when the client accepts gzip" in {
      Get() ~> addHeader(`Accept-Encoding`(ContentCoding.gzip)) ~> encodeResponse(textRoute) ~> check {
        header[`Content-Encoding`] shouldEqual Some(`Content-Encoding`(ContentCoding.gzip))
        header("Content-Length") shouldEqual None
        gunzipped shouldEqual text
      }
    }
    "deflate the response when the client accepts only deflate" in {
      Get() ~> addHeader(`Accept-Encoding`(ContentCoding.deflate)) ~> encodeResponse(textRoute) ~> check {
        header[`Content-Encoding`] shouldEqual Some(`Content-Encoding`(ContentCoding.deflate))
        inflated shouldEqual text
      }
    }
    "prefer identity on a tie" in {
      Get() ~> addHeader(`Accept-Encoding`(ContentCoding.gzip, ContentCoding.identity)) ~> encodeResponse(textRoute) ~>
      check { header[`Content-Encoding`] shouldEqual None }
    }
    "reject with UnacceptedResponseEncodingRejection when nothing is acceptable" in {
      Get() ~> addHeader(refuseIdentity) ~> encodeResponse(textRoute) ~> check {
        rejection shouldEqual UnacceptedResponseEncodingRejection(
          Set(ContentCoding.identity, ContentCoding.gzip, ContentCoding.deflate))
      }
    }
    "complete with 406 when sealed and nothing is acceptable" in {
      Get() ~> addHeader(refuseIdentity) ~> Route.seal(encodeResponse(textRoute)) ~> check {
        status shouldEqual Status.NotAcceptable
      }
    }
    "not encode a precompressed svgz file whatever the nesting" in {
      val file = File.createTempFile("stirHttpTest", ".svgz")
      try {
        NioFiles.write(file.toPath, "123".getBytes("UTF-8"))
        Get() ~> addHeader(`Accept-Encoding`(ContentCoding.gzip)) ~> {
          withPrecompressedMediaTypeSupport { encodeResponse { getFromFile(file) } }
        } ~> check {
          header[`Content-Encoding`] shouldEqual Some(`Content-Encoding`(ContentCoding.gzip))
          responseAs[String] shouldEqual "123"
        }
      } finally {
        file.delete
        ()
      }
    }
    "not encode a response without entity" in {
      Get() ~> addHeader(`Accept-Encoding`(ContentCoding.gzip)) ~> encodeResponse(complete(Status.Ok)) ~> check {
        header[`Content-Encoding`] shouldEqual None
        responseAs[String] shouldEqual ""
      }
    }
    "not encode a response that is already encoded" in {
      val preEncoded = mapResponse(_.putHeaders(`Content-Encoding`(ContentCoding.gzip))) { complete("raw") }
      Get() ~> addHeader(`Accept-Encoding`(ContentCoding.gzip)) ~> encodeResponse(preEncoded) ~> check {
        responseAs[String] shouldEqual "raw"
      }
    }
  }

  "the encodeResponse directive on a streamed response" should {
    // Pekko's encoders flush every chunk, so a reader gets each chunk's bytes as it is produced
    def firstBytes(coding: ContentCoding, decode: Pipe[IO, Byte, Byte]): String = {
      val slow = (Stream("hello") ++ Stream.sleep_[IO](10.seconds) ++ Stream("world")).through(utf8.encode)
      Get() ~> addHeader(`Accept-Encoding`(coding)) ~> encodeResponseWith(coding) {
        complete(Response[IO]().withBodyStream(slow).withContentType(`Content-Type`(MediaType.text.plain)))
      } ~> check {
        chunksStream.flatMap(Stream.chunk).through(decode).through(utf8.decode).take(1).compile.string
          .timeout(3.seconds)
          .unsafeRunSync()
      }
    }
    "send a gzipped chunk without waiting for the rest of the stream" in {
      firstBytes(ContentCoding.gzip, Compression[IO].gunzip().andThen(_.flatMap(_.content))) shouldEqual "hello"
    }
    "send all of a large chunk without waiting for the rest of the stream" in {
      // a chunk whose compressed form exceeds the deflater's buffer is still flushed whole
      val random = new scala.util.Random(7)
      val big = Array.fill(60000)(('!' + random.nextInt(90)).toByte)
      val slow = Stream.chunk(fs2.Chunk.array(big)) ++ Stream.sleep_[IO](10.seconds) ++ Stream.emit('!'.toByte)
      Get() ~> addHeader(`Accept-Encoding`(ContentCoding.gzip)) ~> encodeResponseWith(ContentCoding.gzip) {
        complete(Response[IO]().withBodyStream(slow).withContentType(`Content-Type`(MediaType.text.plain)))
      } ~> check {
        chunksStream.flatMap(Stream.chunk).through(Compression[IO].gunzip()).flatMap(_.content).take(60000L)
          .compile.count.timeout(3.seconds).unsafeRunSync() shouldEqual 60000L
      }
    }
    "send a deflated chunk without waiting for the rest of the stream" in {
      firstBytes(ContentCoding.deflate,
        Compression[IO].inflate(InflateParams(header = ZLibParams.Header.ZLIB))) shouldEqual
      "hello"
    }
  }

  "the encodeResponseWith directive" should {
    "use the first encoder when the request has no Accept-Encoding" in {
      Get() ~> encodeResponseWith(ContentCoding.gzip)(textRoute) ~> check {
        header[`Content-Encoding`] shouldEqual Some(`Content-Encoding`(ContentCoding.gzip))
        gunzipped shouldEqual text
      }
    }
    "reject when the client does not accept any of the given encoders" in {
      Get()                                             ~> addHeader(`Accept-Encoding`(ContentCoding.identity)) ~>
      encodeResponseWith(ContentCoding.gzip)(textRoute) ~>
      check { rejection shouldEqual UnacceptedResponseEncodingRejection(Set(ContentCoding.gzip)) }
    }
  }

  "the responseEncodingAccepted directive" should {
    "pass when the encoding is accepted" in {
      Get()                                                    ~> addHeader(`Accept-Encoding`(ContentCoding.gzip)) ~>
      responseEncodingAccepted(ContentCoding.gzip)(completeOk) ~>
      check { status shouldEqual Status.Ok }
    }
    "reject when the encoding is not accepted" in {
      Get()                                                    ~> addHeader(`Accept-Encoding`(ContentCoding.identity)) ~>
      responseEncodingAccepted(ContentCoding.gzip)(completeOk) ~>
      check { rejection shouldEqual UnacceptedResponseEncodingRejection(Set(ContentCoding.gzip)) }
    }
  }

  "the decodeRequest directive" should {
    "gunzip a gzip-encoded request" in {
      Post("/", gzipped(text)) ~> addHeader(`Content-Encoding`(ContentCoding.gzip)) ~> decodeRequest(echo) ~> check {
        responseAs[String] shouldEqual text
      }
    }
    "inflate a deflate-encoded request" in {
      Post("/", deflated(text)) ~> addHeader(`Content-Encoding`(ContentCoding.deflate)) ~> decodeRequest(echo) ~>
      check {
        responseAs[String] shouldEqual text
      }
    }
    "pass an unencoded request through" in {
      Post("/", "plain") ~> decodeRequest(echo) ~> check { responseAs[String] shouldEqual "plain" }
    }
    "pass an empty request through whatever its Content-Encoding" in {
      Post("/", "")             ~> addHeader(`Content-Encoding`(ContentCoding.unsafeFromString("br"))) ~>
      decodeRequest(completeOk) ~>
      check { status shouldEqual Status.Ok }
    }
    "pass a bodiless request without Content-Length through decodeRequestWith(gzip), as Pekko does" in {
      Request[IO](method = Method.POST) ~> decodeRequestWith(ContentCoding.gzip)(completeOk) ~> check {
        status shouldEqual Status.Ok
      }
    }
    "pass a bodiless request without Content-Length through whatever its Content-Encoding" in {
      Request[IO](method = Method.POST).putHeaders(`Content-Encoding`(ContentCoding.deflate)) ~>
      decodeRequestWith(ContentCoding.gzip)(completeOk)                                       ~> check { status shouldEqual Status.Ok }
    }
    "decode a length-less HTTP/2 gzip body instead of assuming it empty" in {
      Request[IO](method = Method.POST, httpVersion = HttpVersion.`HTTP/2`,
        body = Stream.emits(gzipped(text)).covary[IO])
        .putHeaders(`Content-Encoding`(ContentCoding.gzip)) ~> decodeRequest(echo) ~> check {
        responseAs[String] shouldEqual text
      }
    }
    "decode a chunked HTTP/1.1 gzip body without Content-Length" in {
      Request[IO](method = Method.POST, body = Stream.emits(gzipped(text)).covary[IO])
        .putHeaders(`Content-Encoding`(ContentCoding.gzip), `Transfer-Encoding`(TransferCoding.chunked)) ~>
      decodeRequest(echo)                                                                                ~> check { responseAs[String] shouldEqual text }
    }
    "reject with UnsupportedRequestEncodingRejection for an unknown encoding" in {
      Post("/", "body") ~> addHeader(`Content-Encoding`(ContentCoding.unsafeFromString("br"))) ~> decodeRequest(echo) ~>
      check {
        rejections shouldEqual Seq(UnsupportedRequestEncodingRejection(ContentCoding.gzip),
          UnsupportedRequestEncodingRejection(ContentCoding.deflate),
          UnsupportedRequestEncodingRejection(ContentCoding.identity))
      }
    }
    "complete with 400 when sealed and the encoding is unknown" in {
      Post("/", "body")               ~> addHeader(`Content-Encoding`(ContentCoding.unsafeFromString("br"))) ~>
      Route.seal(decodeRequest(echo)) ~>
      check {
        status shouldEqual Status.BadRequest
        responseAs[String] shouldEqual
        "The request's Content-Encoding is not supported. Expected:\ngzip or deflate or identity"
      }
    }
    "complete with 400 when the compressed body is corrupt" in {
      Post("/", "not gzip at all")    ~> addHeader(`Content-Encoding`(ContentCoding.gzip)) ~>
      Route.seal(decodeRequest(echo)) ~>
      check {
        status shouldEqual Status.BadRequest
        responseAs[String] should include("The request's encoding is corrupt")
      }
    }
    "leave a decoded request visible to requestEntityPresent" in {
      Post("/", gzipped(text)) ~> addHeader(`Content-Encoding`(ContentCoding.gzip)) ~> {
        decodeRequest { requestEntityPresent { echo } }
      } ~> check { responseAs[String] shouldEqual text }
    }
    "let a nested withSizeLimit limit the decoded body instead of the compressed one" in {
      Post("/", gzipped(text)) ~> addHeader(`Content-Encoding`(ContentCoding.gzip)) ~> {
        withSizeLimit(100000) { decodeRequest { withSizeLimit(50000) { echo } } }
      } ~> check { responseAs[String] shouldEqual text }
    }
    "bound the decoded body to CodingDirectives.DefaultDecodeMaxBytes unless a nested directive says otherwise" in {
      val nineMiB = Stream.constant(0.toByte).take(9L * 1024 * 1024).covary[IO]
        .through(Compression[IO].gzip(fileName = None, modificationTime = None, comment = None,
          deflateParams = DeflateParams(header = ZLibParams.Header.GZIP)))
        .compile.to(Array).unsafeRunSync()
      val count: Route = extractRequest { request =>
        onSuccess(request.body.chunks.compile.fold(0L)((n, chunk) => n + chunk.size)) { n => complete(n.toString) }
      }
      Post("/", nineMiB) ~> addHeader(`Content-Encoding`(ContentCoding.gzip)) ~> Route.seal(decodeRequest(count)) ~>
      check { status shouldEqual Status.PayloadTooLarge }
      Post("/", nineMiB)                                 ~> addHeader(`Content-Encoding`(ContentCoding.gzip)) ~>
      Route.seal(decodeRequest(withoutSizeLimit(count))) ~>
      check { responseAs[String] shouldEqual (9L * 1024 * 1024).toString }
      Post("/", nineMiB)                                                 ~> addHeader(`Content-Encoding`(ContentCoding.gzip)) ~>
      Route.seal(decodeRequest(withSizeLimit(10L * 1024 * 1024)(count))) ~>
      check { responseAs[String] shouldEqual (9L * 1024 * 1024).toString }
    }
    "reject with MalformedRequestContentRejection when entity reads a corrupt compressed body" in {
      Post("/", "definitely not gzip") ~> addHeader(`Content-Encoding`(ContentCoding.gzip)) ~> decodeRequest(echo) ~>
      check {
        inside(rejection) { case MalformedRequestContentRejection(message, _) =>
          message shouldEqual "The request's encoding is corrupt"
        }
      }
    }
    "let an alternative handle a corrupt compressed body that entity rejected" in {
      Post("/", "definitely not gzip")             ~> addHeader(`Content-Encoding`(ContentCoding.gzip)) ~>
      (decodeRequest(echo) ~ complete("fallback")) ~> check { responseAs[String] shouldEqual "fallback" }
    }
    "fail with 400 when a corrupt compressed body is read outside entity" in {
      val drain: Route = extractRequest { request => onSuccess(request.body.compile.drain)(completeOk) }
      Post("/", "definitely not gzip") ~> addHeader(`Content-Encoding`(ContentCoding.gzip)) ~>
      Route.seal(decodeRequest(drain)) ~> check {
        status shouldEqual Status.BadRequest
        responseAs[String] should include("The request's encoding is corrupt")
      }
    }
    "gunzip over a real server" in {
      Post("/", gzipped(text)) ~> addHeader(`Content-Encoding`(ContentCoding.gzip)) ~!> decodeRequest(echo) ~> check {
        responseAs[String] shouldEqual text
      }
    }
  }

  "the decodeRequestWith directive" should {
    "reject a request encoded differently" in {
      Post("/", deflated(text))                   ~> addHeader(`Content-Encoding`(ContentCoding.deflate)) ~>
      decodeRequestWith(ContentCoding.gzip)(echo) ~>
      check { rejection shouldEqual UnsupportedRequestEncodingRejection(ContentCoding.gzip) }
    }
  }

  "the requestEncodedWith directive" should {
    "reject a request without Content-Encoding when gzip is required" in {
      Post("/", "body") ~> requestEncodedWith(ContentCoding.gzip)(completeOk) ~> check {
        rejection shouldEqual UnsupportedRequestEncodingRejection(ContentCoding.gzip)
      }
    }
  }
}
