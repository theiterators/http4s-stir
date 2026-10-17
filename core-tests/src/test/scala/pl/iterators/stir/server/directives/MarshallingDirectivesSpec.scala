package pl.iterators.stir.server.directives

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import io.circe.Json
import org.http4s.{
  Charset,
  DecodeFailure,
  DecodeResult,
  EntityDecoder,
  InvalidMessageBodyFailure,
  MediaRange,
  MediaType,
  QValue,
  Response,
  Status
}
import org.http4s.circe._
import org.http4s.headers.{ `Content-Type`, Accept, MediaRangeAndQValue }
import org.scalatest.Inside
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
  }
}
