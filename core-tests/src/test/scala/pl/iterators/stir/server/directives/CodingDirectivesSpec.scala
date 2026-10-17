package pl.iterators.stir.server.directives

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import fs2.Stream
import fs2.text.utf8
import fs2.compression.{ Compression, DeflateParams, InflateParams, ZLibParams }
import org.http4s.{
  Charset,
  ContentCoding,
  Header,
  HttpVersion,
  MediaType,
  Method,
  QValue,
  Request,
  Response,
  Status,
  TransferCoding
}
import org.scalatest.Inside
import org.http4s.headers.{ `Accept-Encoding`, `Content-Encoding`, `Content-Type`, `Transfer-Encoding` }
import org.typelevel.ci.CIString
import pl.iterators.stir.server._

import java.io.File
import java.nio.file.{ Files => NioFiles }

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

  "Pekko's coding cases" when {
    // Pekko's CodingDirectivesSpec, case by case; compressed bytes differ between compressors, so the bodies are
    // compared after decompression
    val yeah: Route = complete("Yeah!")
    val nope: Route = complete(Status.NotFound, "Nope!")
    val drain: Route = extractRequest { request => onSuccess(request.body.compile.drain)(completeOk) }
    val textPlain = `Content-Type`(MediaType.text.plain, Charset.`UTF-8`)
    def acceptEncoding(value: String) = Header.Raw(CIString("Accept-Encoding"), value)

    "the identity (NoEncoding) decoder" should {
      val decode = decodeRequestWith(ContentCoding.identity)
      "decode the request content if it has a `Content-Encoding: identity` header and the content is not encoded" in {
        Post("/", "yes") ~> addHeader(`Content-Encoding`(ContentCoding.identity)) ~> decode(echo) ~> check {
          responseAs[String] shouldEqual "yes"
        }
      }
      "reject requests with content encoded with 'deflate'" in {
        Post("/", "yes") ~> addHeader(`Content-Encoding`(ContentCoding.deflate)) ~> decode(echo) ~> check {
          rejection shouldEqual UnsupportedRequestEncodingRejection(ContentCoding.identity)
        }
      }
      "decode the request content if no Content-Encoding header is present" in {
        Post("/", "yes") ~> decode(echo) ~> check { responseAs[String] shouldEqual "yes" }
      }
      "leave request without content unchanged" in {
        Post() ~> decode(completeOk) ~> check { status shouldEqual Status.Ok }
      }
      val echoBytes: Route = extractRequest { request =>
        onSuccess(request.body.compile.to(Array)) { bytes =>
          complete(
            request.contentType.fold("")(c => s"${c.mediaType.mainType}/${c.mediaType.subType}:") + bytes.mkString(","))
        }
      }
      "leave a strict request entity unchanged" in {
        val data = Array.fill[Byte](10000)(42.toByte)
        Post("/", data) ~> addHeader(`Content-Type`(MediaType.application.`octet-stream`)) ~> decode(echoBytes) ~>
        check { responseAs[String] shouldEqual "application/octet-stream:" + data.mkString(",") }
      }
      "leave a streamed request entity unchanged" in {
        val chunks = Stream("abc", "def", "ghi").through(utf8.encode).covary[IO]
        Post().withBodyStream(chunks) ~> addHeader(`Content-Type`(MediaType.application.`octet-stream`)) ~>
        decode(echoBytes)             ~> check {
          responseAs[String] shouldEqual "application/octet-stream:" + "abcdefghi".getBytes("UTF-8").mkString(",")
        }
      }
    }

    "the gzip decoder" should {
      val decode = decodeRequestWith(ContentCoding.gzip)
      "decode the request content if it has a `Content-Encoding: gzip` header and the content is gzip encoded" in {
        Post("/", gzipped("Hello")) ~> addHeader(`Content-Encoding`(ContentCoding.gzip)) ~> decode(echo) ~> check {
          responseAs[String] shouldEqual "Hello"
        }
      }
      "reject the request content if it has encoding 'gzip' but is corrupt" in {
        Post("/", Array[Byte](0, 1, 2)) ~> addHeader(`Content-Encoding`(ContentCoding.gzip)) ~>
        Route.seal(decode(drain))       ~> check {
          status shouldEqual Status.BadRequest
          responseAs[String] shouldEqual "The request's encoding is corrupt"
        }
      }
      "reject truncated gzip request content" in {
        Post("/", gzipped("Hello").dropRight(2)) ~> addHeader(`Content-Encoding`(ContentCoding.gzip)) ~>
        Route.seal(decode(drain))                ~> check {
          status shouldEqual Status.BadRequest
          responseAs[String] shouldEqual "The request's encoding is corrupt"
        }
      }
      "reject requests without Content-Encoding header" in {
        Post("/", "yes") ~> decode(echo) ~> check {
          rejection shouldEqual UnsupportedRequestEncodingRejection(ContentCoding.gzip)
        }
      }
      "leave request without content unchanged" in {
        Post() ~> decode(completeOk) ~> check { status shouldEqual Status.Ok }
      }
    }

    "the encoder" should {
      "encode the response content with gzip if the response is not a success and request has no Accept-Encoding header" in {
        Post() ~> encodeResponseWith(ContentCoding.gzip)(nope) ~> check {
          status shouldEqual Status.NotFound
          header[`Content-Encoding`].map(_.contentCoding) shouldEqual Some(ContentCoding.gzip)
          header[`Content-Type`] shouldEqual Some(textPlain)
          gunzipped shouldEqual "Nope!"
        }
      }
      "encode the response content with deflate if the response is not a success and request has no Accept-Encoding header" in {
        Post() ~> encodeResponseWith(ContentCoding.deflate)(nope) ~> check {
          status shouldEqual Status.NotFound
          header[`Content-Encoding`].map(_.contentCoding) shouldEqual Some(ContentCoding.deflate)
          inflated shouldEqual "Nope!"
        }
      }
      "not encode the response content with gzip if the response is of status not allowing entity" in {
        Post() ~> encodeResponseWith(ContentCoding.gzip)(complete(Status.NoContent)) ~> check {
          status shouldEqual Status.NoContent
          header[`Content-Encoding`] shouldEqual None
          responseAs[Array[Byte]] shouldBe empty
        }
      }
      "allow compressed responses with no body for informational messages" in {
        Post() ~> encodeResponseWith(ContentCoding.deflate)(complete(Status.Continue, "Let's continue!")) ~> check {
          status shouldEqual Status.Continue
          header[`Content-Encoding`] shouldEqual None
          responseAs[Array[Byte]] shouldBe empty
        }
      }
    }

    "the gzip encoder" should {
      "encode the response content with gzip if the client accepts it with a dedicated Accept-Encoding header" in {
        Post() ~> addHeader(`Accept-Encoding`(ContentCoding.gzip)) ~> encodeResponseWith(ContentCoding.gzip)(yeah) ~>
        check {
          header[`Content-Encoding`].map(_.contentCoding) shouldEqual Some(ContentCoding.gzip)
          header[`Content-Type`] shouldEqual Some(textPlain)
          gunzipped shouldEqual "Yeah!"
        }
      }
      "leave responses with an already set Content-Encoding header unchanged" in {
        Post() ~> addHeader(`Accept-Encoding`(ContentCoding.gzip)) ~> encodeResponseWith(ContentCoding.gzip) {
          respondWithHeader(Header.Raw(CIString("Content-Encoding"), "identity"))(completeOk)
        } ~> check {
          status shouldEqual Status.Ok
          headers.headers.filter(_.name == CIString("Content-Encoding")).map(_.value) shouldEqual List("identity")
          responseAs[Array[Byte]] shouldBe empty
        }
      }
      "correctly encode the chunk stream produced by a chunked response" in {
        val lengthy = "This is a somewhat lengthy text that is being chunked by the autochunk directive!"
        val chunked = Stream.emits(lengthy.getBytes("UTF-8")).chunkN(8).flatMap(Stream.chunk).covary[IO]
        Post() ~> addHeader(`Accept-Encoding`(ContentCoding.gzip)) ~> encodeResponseWith(ContentCoding.gzip) {
          complete(Response[IO]().withBodyStream(chunked).withContentType(textPlain))
        } ~> check {
          header[`Content-Encoding`].map(_.contentCoding) shouldEqual Some(ContentCoding.gzip)
          gunzipped shouldEqual lengthy
        }
      }
      "correctly encode the chunk stream produced by an empty chunked response" in {
        Post() ~> addHeader(`Accept-Encoding`(ContentCoding.gzip)) ~> encodeResponseWith(ContentCoding.gzip) {
          // a streamed body without bytes: `Stream.empty` itself is http4s' known-empty `EmptyBody`
          complete(Response[IO]().withBodyStream(Stream.exec(IO.unit)).withContentType(textPlain))
        } ~> check {
          header[`Content-Encoding`].map(_.contentCoding) shouldEqual Some(ContentCoding.gzip)
          gunzipped shouldEqual ""
        }
      }
    }

    "the encodeResponseWith(identity) directive" should {
      "produce a non-encoded response if the request has an `Accept-Encoding: gzip, identity` header" in {
        Post()                                           ~> addHeader(`Accept-Encoding`(ContentCoding.gzip, ContentCoding.identity)) ~>
        encodeResponseWith(ContentCoding.identity)(yeah) ~> check {
          header[`Content-Encoding`] shouldEqual None
          responseAs[String] shouldEqual "Yeah!"
        }
      }
      "produce a non-encoded response from (deflate, identity) for a request accepting gzip" in {
        Post()                                                                  ~> addHeader(`Accept-Encoding`(ContentCoding.gzip)) ~>
        encodeResponseWith(ContentCoding.deflate, ContentCoding.identity)(yeah) ~> check {
          header[`Content-Encoding`] shouldEqual None
          responseAs[String] shouldEqual "Yeah!"
        }
      }
    }

    "the encodeResponseWith directive" should {
      "produce a response encoded with the specified encoder if the request has a matching Accept-Encoding header" in {
        Post() ~> addHeader(`Accept-Encoding`(ContentCoding.gzip)) ~> encodeResponseWith(ContentCoding.gzip)(yeah) ~>
        check {
          header[`Content-Encoding`].map(_.contentCoding) shouldEqual Some(ContentCoding.gzip)
          gunzipped shouldEqual "Yeah!"
        }
      }
      "produce a response encoded with one of the specified encoders if the request has a matching Accept-Encoding header" in {
        Post()                                                              ~> addHeader(`Accept-Encoding`(ContentCoding.deflate)) ~>
        encodeResponseWith(ContentCoding.gzip, ContentCoding.deflate)(yeah) ~> check {
          header[`Content-Encoding`].map(_.contentCoding) shouldEqual Some(ContentCoding.deflate)
          inflated shouldEqual "Yeah!"
        }
      }
      "produce a response encoded with the first of the specified encoders if the request has no Accept-Encoding header" in {
        Post() ~> encodeResponseWith(ContentCoding.gzip, ContentCoding.deflate)(yeah) ~> check {
          header[`Content-Encoding`].map(_.contentCoding) shouldEqual Some(ContentCoding.gzip)
          gunzipped shouldEqual "Yeah!"
        }
      }
      "produce a response with no encoding if the request has an empty Accept-Encoding header" in {
        val encoders = encodeResponseWith(ContentCoding.gzip, ContentCoding.deflate, ContentCoding.identity)
        Post() ~> addHeader(acceptEncoding("")) ~> encoders(completeOk) ~> check {
          status shouldEqual Status.Ok
          header[`Content-Encoding`] shouldEqual None
        }
        // with a body, Pekko too picks the first encoder: an empty header leaves every coding at q=1
        Post() ~> addHeader(acceptEncoding("")) ~> encoders(yeah) ~> check {
          header[`Content-Encoding`].map(_.contentCoding) shouldEqual Some(ContentCoding.gzip)
        }
      }
      "negotiate the correct content encoding" in {
        Post()                                                                                      ~> addHeader(acceptEncoding("identity;q=0.5, deflate;q=0, gzip")) ~>
        encodeResponseWith(ContentCoding.identity, ContentCoding.deflate, ContentCoding.gzip)(yeah) ~> check {
          header[`Content-Encoding`].map(_.contentCoding) shouldEqual Some(ContentCoding.gzip)
          gunzipped shouldEqual "Yeah!"
        }
        Post()                                                              ~> addHeader(acceptEncoding("*, deflate;q=0.2")) ~>
        encodeResponseWith(ContentCoding.deflate, ContentCoding.gzip)(yeah) ~> check {
          header[`Content-Encoding`].map(_.contentCoding) shouldEqual Some(ContentCoding.gzip)
        }
      }
      "reject the request if it has an Accept-Encoding header with an encoding that matches but is blacklisted" in {
        Post() ~> addHeader(acceptEncoding("gzip;q=0")) ~> encodeResponseWith(ContentCoding.gzip)(yeah) ~> check {
          rejection shouldEqual UnacceptedResponseEncodingRejection(Set(ContentCoding.gzip))
        }
      }
    }

    "the decodeRequest directive" should {
      "decode the request content if it has a `Content-Encoding: identity` header and the content is not encoded" in {
        Post("/", "yes") ~> addHeader(`Content-Encoding`(ContentCoding.identity)) ~> decodeRequest(echo) ~> check {
          responseAs[String] shouldEqual "yes"
        }
      }
      "reject the request if it has a `Content-Encoding: deflate` header but the request is encoded with gzip" in {
        Post("/", gzipped("Hello"))      ~> addHeader(`Content-Encoding`(ContentCoding.deflate)) ~>
        Route.seal(decodeRequest(drain)) ~> check {
          status shouldEqual Status.BadRequest
          responseAs[String] shouldEqual "The request's encoding is corrupt"
        }
      }
    }

    "the (decodeRequest & encodeResponse) compound directive" should {
      val decodeEncode = decodeRequest & encodeResponse
      "decode a gzip encoded request and produce a none encoded response if the request has no Accept-Encoding header" in {
        Post("/", gzipped("Hello")) ~> addHeader(`Content-Encoding`(ContentCoding.gzip)) ~> decodeEncode(echo) ~>
        check {
          header[`Content-Encoding`] shouldEqual None
          header[`Content-Type`] shouldEqual Some(textPlain)
          responseAs[String] shouldEqual "Hello"
        }
      }
      "decode a gzip encoded request and produce a deflate encoded response if the request has an `Accept-Encoding: deflate` header" in {
        Post("/", gzipped("Hello"))                 ~> addHeaders(`Content-Encoding`(ContentCoding.gzip),
          `Accept-Encoding`(ContentCoding.deflate)) ~> decodeEncode(echo) ~> check {
          header[`Content-Encoding`].map(_.contentCoding) shouldEqual Some(ContentCoding.deflate)
          inflated shouldEqual "Hello"
        }
      }
      "decode an unencoded request and produce a gzip encoded response if the request has an `Accept-Encoding: gzip` header" in {
        Post("/", "Hello") ~> addHeader(`Accept-Encoding`(ContentCoding.gzip)) ~> decodeEncode(echo) ~> check {
          header[`Content-Encoding`].map(_.contentCoding) shouldEqual Some(ContentCoding.gzip)
          gunzipped shouldEqual "Hello"
        }
      }
    }

    "the default marshaller" should {
      "allow compressed responses with no body for informational messages" in {
        Get() ~> addHeader(`Accept-Encoding`(ContentCoding.compress)) ~> encodeResponse(complete(Status.Continue)) ~>
        check { status shouldEqual Status.Continue }
      }
      "allow gzipped responses with no body for 204 messages" in {
        Get() ~> addHeader(`Accept-Encoding`(ContentCoding.gzip)) ~> encodeResponse(complete(Status.NoContent)) ~>
        check { status shouldEqual Status.NoContent }
      }
    }
  }
}
