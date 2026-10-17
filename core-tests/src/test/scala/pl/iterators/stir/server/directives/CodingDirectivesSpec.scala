package pl.iterators.stir.server.directives

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import fs2.Stream
import fs2.compression.{ Compression, DeflateParams, InflateParams, ZLibParams }
import org.http4s.{ ContentCoding, QValue, Status }
import org.http4s.headers.{ `Accept-Encoding`, `Content-Encoding` }
import pl.iterators.stir.server._

class CodingDirectivesSpec extends RoutingSpec {
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
      .through(fs2.text.utf8.decode).compile.string.unsafeRunSync()

  def inflated: String =
    responseEntity.through(Compression[IO].inflate(InflateParams(header = ZLibParams.Header.ZLIB)))
      .through(fs2.text.utf8.decode).compile.string.unsafeRunSync()

  val refuseIdentity = `Accept-Encoding`(ContentCoding.identity.withQValue(QValue.Zero))

  "the encodeResponse directive" should {
    "not encode when the request has no Accept-Encoding" in {
      Get() ~> encodeResponse(textRoute) ~> check {
        header[`Content-Encoding`] shouldEqual None
        responseAs[String] shouldEqual text
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
      val file = java.io.File.createTempFile("stirHttpTest", ".svgz")
      try {
        java.nio.file.Files.write(file.toPath, "123".getBytes("UTF-8"))
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
