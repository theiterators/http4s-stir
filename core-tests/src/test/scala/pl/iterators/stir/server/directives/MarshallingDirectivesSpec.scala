package pl.iterators.stir.server.directives

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import io.circe.Json
import org.http4s.{ DecodeFailure, DecodeResult, EntityDecoder, InvalidMessageBodyFailure, MediaType, Status }
import org.http4s.circe._
import org.http4s.headers.`Content-Type`
import org.scalatest.Inside
import pl.iterators.stir.server._

class MarshallingDirectivesSpec extends RoutingSpec with Inside {
  override implicit def runtime: IORuntime = IORuntime.global

  val jsonBody = """{"a":1}"""
  val echoJson: Route = entity(as[Json]) { json => complete(json.noSpaces) }

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
