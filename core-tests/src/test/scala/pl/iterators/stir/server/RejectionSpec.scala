package pl.iterators.stir.server

import cats.effect.unsafe.IORuntime
import fs2.Stream
import org.http4s.{ Charset, MediaRange, MediaType, Status }
import org.http4s.headers.{ `Content-Length`, `Content-Type` }
import pl.iterators.stir.server.directives.RoutingSpec

/** Pekko's `RejectionSpec`: `mapRejectionResponse` and the `UnsupportedRequestContentTypeRejection` message. */
class RejectionSpec extends RoutingSpec {
  override implicit def runtime: IORuntime = IORuntime.global

  "RejectionHandler.default" which {
    implicit val myRejectionHandler: RejectionHandler =
      RejectionHandler.default.mapRejectionResponse { response =>
        // wrap the handler's text in a JSON object, reading the body when the response is sent
        val wrapped = Stream.eval(response.bodyText.compile.string).flatMap { text =>
          val message = text.replaceAll("\"", "\\\\\"")
          Stream.emits(s"""{"rejection": "$message"}""".getBytes("UTF-8"))
        }
        response.withBodyStream(wrapped).removeHeader[`Content-Length`]
          .withContentType(`Content-Type`(MediaType.application.json))
      }

    val route = Route.seal(
      path("hello") {
        complete("Hello there")
      } ~
      path("unsupported-content-type") {
        parameters("provide-content-type".as[Boolean], "provide-supported".as[Boolean]) {
          (provideContentType, provideSupported) =>
            val supported: Set[MediaRange] =
              if (provideSupported) Set(MediaType.image.jpeg, MediaType.image.png) else Set.empty
            val contentType: Option[MediaType] = if (provideContentType) Some(MediaType.image.gif) else None
            reject(UnsupportedRequestContentTypeRejection(supported, contentType))
        }
      })

    "mapRejectionResponse" should {
      "not affect normal responses" in {
        Get("/hello") ~> route ~> check {
          status shouldEqual Status.Ok
          mediaType shouldEqual MediaType.text.plain
          charsetOption shouldEqual Some(Charset.`UTF-8`)
          responseAs[String] shouldEqual "Hello there"
        }
      }
      "alter rejection response" in {
        Get("/nope") ~> route ~> check {
          status shouldEqual Status.NotFound
          mediaType shouldEqual MediaType.application.json
          responseAs[String] shouldEqual """{"rejection": "The requested resource could not be found."}"""
        }
      }
    }

    "UnsupportedRequestContentTypeRejection" should {
      "format error message without contentType and supported ranges" in {
        Get("/unsupported-content-type?provide-content-type=false&provide-supported=false") ~> route ~> check {
          status shouldEqual Status.UnsupportedMediaType
          mediaType shouldEqual MediaType.application.json
          responseAs[String] shouldEqual """{"rejection": "The request's Content-Type is not supported."}"""
        }
      }
      "format error message with contentType and without supported ranges" in {
        Get("/unsupported-content-type?provide-content-type=true&provide-supported=false") ~> route ~> check {
          status shouldEqual Status.UnsupportedMediaType
          responseAs[String] shouldEqual """{"rejection": "The request's Content-Type [image/gif] is not supported."}"""
        }
      }
      "format error message without contentType and with supported ranges" in {
        Get("/unsupported-content-type?provide-content-type=false&provide-supported=true") ~> route ~> check {
          status shouldEqual Status.UnsupportedMediaType
          responseAs[String] shouldEqual
          "{\"rejection\": \"The request's Content-Type is not supported. Expected:\nimage/jpeg or image/png\"}"
        }
      }
      "format error message with contentType and with supported ranges" in {
        Get("/unsupported-content-type?provide-content-type=true&provide-supported=true") ~> route ~> check {
          status shouldEqual Status.UnsupportedMediaType
          responseAs[String] shouldEqual
          "{\"rejection\": \"The request's Content-Type [image/gif] is not supported. Expected:\nimage/jpeg or image/png\"}"
        }
      }
    }
  }
}
