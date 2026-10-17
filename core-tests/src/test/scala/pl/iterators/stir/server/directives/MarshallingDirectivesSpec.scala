package pl.iterators.stir.server.directives

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import fs2.Stream
import io.circe.Json
import org.http4s.{
  Charset,
  DecodeFailure,
  DecodeResult,
  EntityDecoder,
  Header,
  Headers,
  HttpVersion,
  InvalidMessageBodyFailure,
  MediaRange,
  MediaType,
  QValue,
  Response,
  Status
}
import org.http4s.circe._
import org.http4s.headers.{ `Content-Length`, `Content-Type`, Accept, MediaRangeAndQValue }
import org.scalatest.Inside
import org.typelevel.ci.CIString
import pl.iterators.stir.common.EntityStreamingSupport
import pl.iterators.stir.marshalling.{ ToResponseMarshallable, ToResponseMarshaller }
import pl.iterators.stir.server._

class MarshallingDirectivesSpec extends RoutingSpec with Inside {
  override implicit def runtime: IORuntime = IORuntime.global

  val jsonBody = """{"a":1}"""
  val echoJson: Route = entity(as[Json]) { json => complete(json.noSpaces) }

  "response content negotiation" should {
    val text: Route = complete("foo")
    "serve the response when the client accepts its media type or sends no Accept" in {
      Get() ~> text                                    ~> check { responseAs[String] shouldEqual "foo" }
      Get() ~> addHeader(Accept(MediaRange.`text/*`))  ~> text ~> check { responseAs[String] shouldEqual "foo" }
      Get() ~> addHeader(Accept(MediaRange.`*/*`))     ~> text ~> check { responseAs[String] shouldEqual "foo" }
      Get() ~> addHeader(Accept(MediaType.text.plain)) ~> text ~> check { responseAs[String] shouldEqual "foo" }
    }
    "reject with UnacceptedResponseContentTypeRejection when the client accepts none of the produced types" in {
      Get() ~> addHeader(Accept(MediaType.application.xml)) ~> text ~> check {
        rejection shouldEqual
        UnacceptedResponseContentTypeRejection(Set(`Content-Type`(MediaType.text.plain, Charset.`UTF-8`)))
      }
    }
    "treat a q=0 Accept entry as not accepted" in {
      Get() ~> addHeader(Accept(MediaRangeAndQValue(MediaType.text.plain, QValue.Zero))) ~> text ~> check {
        rejection shouldBe a[UnacceptedResponseContentTypeRejection]
      }
    }
    "complete with 406 and Pekko's message when sealed" in {
      Get() ~> addHeader(Accept(MediaType.application.xml)) ~> Route.seal(text) ~> check {
        status shouldEqual Status.NotAcceptable
        responseAs[String] shouldEqual
        "Resource representation is only available with these types:\ntext/plain; charset=UTF-8"
      }
    }
    "let an alternative that produces an accepted type win" in {
      val json: Route = complete(Json.obj("a" -> Json.fromInt(1)))
      Get() ~> addHeader(Accept(MediaType.application.json)) ~> (text ~ json) ~> check {
        responseAs[String] shouldEqual """{"a":1}"""
      }
    }
    "not negotiate an explicit Response or a bare status" in {
      Get() ~> addHeader(Accept(MediaType.application.xml)) ~> complete(Response[IO]().withEntity("raw")) ~> check {
        responseAs[String] shouldEqual "raw"
      }
      Get() ~> addHeader(Accept(MediaType.application.xml)) ~> complete(Status.NoContent) ~> check {
        status shouldEqual Status.NoContent
      }
    }
    "let the most specific matching range decide, before its quality" in {
      Get()                                                      ~> addHeader(Accept(MediaRangeAndQValue(MediaRange.`text/*`, QValue.fromThousandths(500).toOption.get),
        MediaRangeAndQValue(MediaType.text.plain, QValue.Zero))) ~> text ~> check {
        rejection shouldBe a[UnacceptedResponseContentTypeRejection]
      }
      val wildcardOff =
        Accept(MediaRangeAndQValue(MediaRange.`*/*`, QValue.Zero), MediaRangeAndQValue(MediaType.text.plain))
      Get() ~> addHeader(wildcardOff) ~> text                 ~> check { responseAs[String] shouldEqual "foo" }
      Get() ~> addHeader(wildcardOff) ~> complete(Json.obj()) ~> check {
        rejection shouldBe a[UnacceptedResponseContentTypeRejection]
      }
    }
    "ignore a charset parameter of an Accept range" in {
      val jsonUtf8 = MediaType.application.json.withExtensions(Map("charset" -> "utf8"))
      Get() ~> addHeader(Accept(MediaRangeAndQValue(jsonUtf8))) ~> complete(Json.obj()) ~> check {
        responseAs[String] shouldEqual "{}"
      }
    }
    "match an Accept range with other parameters only when the produced type carries them" in {
      val html = ToResponseMarshaller.negotiated[String](s =>
        IO.pure(Response[IO]().withEntity(s).withContentType(`Content-Type`(MediaType.text.html))))
      val htmlRoute: Route = completeWith(html) { send => send("<p/>") }
      val level1 = MediaType.text.html.withExtensions(Map("level" -> "1"))
      Get()     ~> addHeader(Accept(MediaRangeAndQValue(level1, QValue.Zero), MediaRangeAndQValue(MediaType.text.html)))  ~>
      htmlRoute ~> check { responseAs[String] shouldEqual "<p/>" }
      Get()     ~> addHeader(Accept(MediaRangeAndQValue(MediaType.text.plain.withExtensions(Map("format" -> "flowed"))))) ~>
      text      ~> check { rejection shouldBe a[UnacceptedResponseContentTypeRejection] }
    }
    "negotiate a value computed in an IO, a ToResponseMarshallable computed in an IO and a stream" in {
      val xml = addHeader(Accept(MediaType.application.xml))
      Get() ~> xml ~> complete(IO.pure("foo"))                         ~> check { rejection shouldBe a[UnacceptedResponseContentTypeRejection] }
      Get() ~> xml ~> complete(IO.pure(ToResponseMarshallable("foo"))) ~> check {
        rejection shouldBe a[UnacceptedResponseContentTypeRejection]
      }
      implicit val support: EntityStreamingSupport = EntityStreamingSupport.json()
      Get() ~> xml ~> complete(Stream.emits(List(Json.obj())).covary[IO]) ~> check {
        rejection shouldBe a[UnacceptedResponseContentTypeRejection]
      }
    }
    "list every type the alternatives produce in the sealed 406" in {
      val json: Route = complete(Json.obj("a" -> Json.fromInt(1)))
      Get() ~> addHeader(Accept(MediaType.application.xml)) ~> Route.seal(text ~ json) ~> check {
        status shouldEqual Status.NotAcceptable
        responseAs[String] shouldEqual
        "Resource representation is only available with these types:\ntext/plain; charset=UTF-8\napplication/json"
      }
    }
    "let exception and rejection handlers see the Accept header and still reach the client" in {
      object Boom extends RuntimeException("boom")
      val acceptEcho: Route = (headerValueByType[Accept] & get) { accept =>
        complete(Accept.headerInstance.value(accept))
      }
      val exceptions = handleExceptions(ExceptionHandler { case Boom => acceptEcho })
      Get() ~> addHeader(Accept(MediaType.application.json)) ~> exceptions(failWith(Boom)) ~> check {
        status shouldEqual Status.Ok
        responseAs[String] shouldEqual "application/json"
      }
      val rejections = handleRejections(RejectionHandler.newBuilder().handleNotFound(acceptEcho).result())
      Get() ~> addHeader(Accept(MediaType.application.json)) ~> rejections(reject) ~> check {
        status shouldEqual Status.Ok
        responseAs[String] shouldEqual "application/json"
      }
    }
    "serve a route run with every representation acceptable whatever Accept says" in {
      Get() ~> addHeader(Accept(MediaType.application.xml)) ~> mapRequestContext(_.withAcceptAll)(text) ~> check {
        responseAs[String] shouldEqual "foo"
      }
    }
  }

  "the status and value marshallers (Pekko's FromStatusCodeAndXYZMarshallerSpec)" should {
    val routes =
      path("200-text")(complete(Status.Ok, "ok")) ~
      path("201-text")(complete(Status.Created, "created")) ~
      path("400-text")(complete(Status.BadRequest, "bad-request")) ~
      path("404-headers-text")(complete(Status.NotFound, Headers(Header.Raw(CIString("X-A"), "a")), "not-found"))
    def accepting(range: MediaRange) = addHeader(Accept(range))
    val textPlain = `Content-Type`(MediaType.text.plain, Charset.`UTF-8`)

    "return 200 text when text/plain is accepted, 406 when it is not" in {
      Post("/200-text") ~> accepting(MediaRange.`text/*`) ~> Route.seal(routes) ~> check {
        status shouldEqual Status.Ok
        header[`Content-Type`] shouldEqual Some(textPlain)
        responseAs[String] shouldEqual "ok"
      }
      Post("/200-text") ~> accepting(MediaRange.`application/*`) ~> Route.seal(routes) ~> check {
        status shouldEqual Status.NotAcceptable
        responseAs[String] should include("text/plain")
      }
    }
    "return 201 text when text/plain is accepted, 406 when it is not" in {
      Post("/201-text") ~> accepting(MediaRange.`text/*`) ~> Route.seal(routes) ~> check {
        status shouldEqual Status.Created
        responseAs[String] shouldEqual "created"
      }
      Post("/201-text") ~> accepting(MediaRange.`application/*`) ~> Route.seal(routes) ~> check {
        status shouldEqual Status.NotAcceptable
        responseAs[String] should include("text/plain")
      }
    }
    "return 400 text when text/plain is accepted" in {
      Post("/400-text") ~> accepting(MediaRange.`text/*`) ~> Route.seal(routes) ~> check {
        status shouldEqual Status.BadRequest
        header[`Content-Type`] shouldEqual Some(textPlain)
        responseAs[String] shouldEqual "bad-request"
      }
    }
    "allow not explicitly Accept-ed content type to be returned if response code is non-2xx" in {
      Post("/400-text") ~> accepting(MediaRange.`application/*`) ~> Route.seal(routes) ~> check {
        status shouldEqual Status.BadRequest
        header[`Content-Type`] shouldEqual Some(textPlain)
        responseAs[String] shouldEqual "bad-request"
      }
      Post("/404-headers-text") ~> addHeader(Accept(MediaType.application.json)) ~> Route.seal(routes) ~> check {
        status shouldEqual Status.NotFound
        header[`Content-Type`] shouldEqual Some(textPlain)
        responseAs[String] shouldEqual "not-found"
      }
    }
  }

  "The 'completeWith' directive" should {
    "provide a completion function converting custom objects to a response using the in-scope marshaller" in {
      Get() ~> completeWith(instanceOf[String]) { prod => prod("42") } ~> check {
        responseAs[String] shouldEqual "42"
      }
    }
    "reject with UnacceptedResponseContentTypeRejection if no acceptable marshaller is in scope" in {
      Get() ~> addHeader(Accept(MediaType.text.css)) ~> completeWith(instanceOf[String]) { prod => prod("42") } ~>
      check {
        rejection shouldBe a[UnacceptedResponseContentTypeRejection]
      }
    }
    "let an exception thrown by the inner block reach handleExceptions" in {
      object Boom extends RuntimeException("boom")
      Get() ~> completeWith(instanceOf[String]) { _ => throw Boom } ~> check {
        status shouldEqual Status.InternalServerError
      }
    }
  }

  "The 'entity' directive" should {
    "decode a body whose Content-Type matches the decoder" in {
      Post("/", jsonBody) ~> addHeader(`Content-Type`(MediaType.application.json)) ~> echoJson ~> check {
        responseAs[String] shouldEqual jsonBody
      }
    }
    "decode a body without Content-Type" in {
      Post("/", jsonBody) ~> removeHeader("Content-Type") ~> echoJson ~> check {
        responseAs[String] shouldEqual jsonBody
      }
    }
    "reject with UnsupportedRequestContentTypeRejection when the Content-Type does not match" in {
      Post("/", jsonBody) ~> addHeader(`Content-Type`(MediaType.text.plain)) ~> echoJson ~> check {
        rejection shouldEqual UnsupportedRequestContentTypeRejection(Set(MediaType.application.json),
          Some(MediaType.text.plain))
      }
    }
    "complete with 415 for a mismatching Content-Type when sealed" in {
      Post("/", jsonBody) ~> addHeader(`Content-Type`(MediaType.text.plain)) ~> Route.seal(echoJson) ~> check {
        status shouldEqual Status.UnsupportedMediaType
        responseAs[String] shouldEqual
        "The request's Content-Type [text/plain] is not supported. Expected:\napplication/json"
      }
    }
    "cancel the UnsupportedRequestContentTypeRejection when another alternative accepts the type" in {
      Post("/", "plain") ~> addHeader(`Content-Type`(MediaType.text.plain)) ~> {
        echoJson ~ entity(as[String]) { s => complete("text:" + s) }
      } ~> check { responseAs[String] shouldEqual "text:plain" }
    }
    "reject with MalformedRequestContentRejection when the body cannot be parsed" in {
      Post("/", "nope") ~> addHeader(`Content-Type`(MediaType.application.json)) ~> echoJson ~> check {
        inside(rejection) { case MalformedRequestContentRejection(_, _: DecodeFailure) => }
      }
    }
    "complete with 400 for an unparseable body when sealed" in {
      Post("/", "nope") ~> addHeader(`Content-Type`(MediaType.application.json)) ~> Route.seal(echoJson) ~> check {
        status shouldEqual Status.BadRequest
        responseAs[String] should startWith("The request content was malformed:")
      }
    }
    "reject with ValidationRejection when the decoder fails with an IllegalArgumentException" in {
      val positive: EntityDecoder[IO, Int] = EntityDecoder.text[IO].flatMapR { s =>
        val n = s.trim.toInt
        if (n > 0) DecodeResult.successT[IO, Int](n)
        else DecodeResult.failureT[IO, Int](InvalidMessageBodyFailure("not positive",
          Some(new IllegalArgumentException("must be positive"))))
      }
      Post("/", "-1") ~> entity(positive) { n => complete(n.toString) } ~> check {
        inside(rejection) { case ValidationRejection("must be positive", Some(_: IllegalArgumentException)) => }
      }
    }
    "decode a String whatever the Content-Type is" in {
      Post("/", jsonBody) ~> addHeader(`Content-Type`(MediaType.application.json)) ~> {
        entity(as[String]) { s => complete(s) }
      } ~> check { responseAs[String] shouldEqual jsonBody }
    }
    "reject with RequestEntityExpectedRejection when the request has no entity" in {
      Put() ~> echoJson                                              ~> check { rejection shouldEqual RequestEntityExpectedRejection }
      Put() ~> addHeader(`Content-Type`(MediaType.application.json)) ~> echoJson ~> check {
        rejection shouldEqual RequestEntityExpectedRejection
      }
    }
    "complete with 400 'Request entity expected but not supplied' for a missing entity when sealed" in {
      Put() ~> Route.seal(echoJson) ~> check {
        status shouldEqual Status.BadRequest
        responseAs[String] shouldEqual "Request entity expected but not supplied"
      }
    }
    "prefer the UnsupportedRequestContentTypeRejection when an empty request has an unsupported Content-Type" in {
      Put() ~> addHeader(`Content-Type`(MediaType.text.plain)) ~> echoJson ~> check {
        rejection shouldBe an[UnsupportedRequestContentTypeRejection]
      }
    }
    "report a malformed length-less HTTP/2 body as malformed, not as missing" in {
      val http2 = Post("/", "nope").withHttpVersion(HttpVersion.`HTTP/2`).removeHeader[`Content-Length`]
      http2 ~> addHeader(`Content-Type`(MediaType.application.json)) ~> echoJson ~> check {
        rejection shouldBe a[MalformedRequestContentRejection]
      }
    }
    "decode a missing entity as an empty String" in {
      Put() ~> entity(as[String]) { s => complete("[" + s + "]") } ~> check { responseAs[String] shouldEqual "[]" }
    }
    "cancel the RequestEntityExpectedRejection when another alternative accepts the request" in {
      val acceptsThenRejects: Route = entity(as[String]) { _ => reject(MissingCookieRejection("c")) }
      Put() ~> (echoJson ~ acceptsThenRejects)                  ~> check { rejections shouldEqual List(MissingCookieRejection("c")) }
      Put() ~> (echoJson ~ reject(MissingCookieRejection("c"))) ~> check {
        rejections shouldEqual List(RequestEntityExpectedRejection, MissingCookieRejection("c"))
      }
    }
  }
}
