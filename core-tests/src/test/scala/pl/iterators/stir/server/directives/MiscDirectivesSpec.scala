package pl.iterators.stir.server.directives

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.comcast.ip4s._
import org.http4s.{ Header, Request, Status, UrlForm }
import org.http4s.headers.`X-Forwarded-For`
import org.typelevel.ci.CIString
import pl.iterators.stir.server._

class MiscDirectivesSpec extends RoutingSpec {
  override implicit def runtime: IORuntime = IORuntime.global

  "the extractClientIP directive" should {
    "extract from a X-Forwarded-For header" in {
      Get()                                           ~> addHeaders(`X-Forwarded-For`(remoteAddress("2.3.4.5")),
        Header.Raw(CIString("x-real-ip"), "1.2.3.4")) ~> {
        extractClientIP { echoComplete }
      } ~> check { responseAs[String] shouldEqual "Some(2.3.4.5)" }
    }
    "extract from a (synthetic) Remote-Address header" in {
      Get() ~> addHeader(Header.Raw(CIString("Remote-Address"), "1.2.3.4")) ~> {
        extractClientIP { echoComplete }
      } ~> check { responseAs[String] shouldEqual "Some(1.2.3.4)" }
    }
    "extract from a X-Real-IP header" in {
      Get() ~> addHeader(Header.Raw(CIString("X-Real-Ip"), "1.2.3.4")) ~> {
        extractClientIP { echoComplete }
      } ~> check { responseAs[String] shouldEqual "Some(1.2.3.4)" }
    }
    "select X-Real-Ip when both X-Real-Ip and Remote-Address headers are present" in {
      Get()                                                ~> addHeaders(Header.Raw(CIString("X-Real-Ip"), "1.2.3.4"),
        Header.Raw(CIString("Remote-Address"), "1.2.3.4")) ~> {
        extractClientIP { echoComplete }
      } ~> check { responseAs[String] shouldEqual "Some(1.2.3.4)" }
    }
    "extract unknown when no headers" in {
      Get() ~> {
        extractClientIP { echoComplete }
      } ~> check { responseAs[String] shouldEqual "None" }
    }
  }

  def remoteAddress(ip: String) = IpAddress.fromString(ip)

  def withConnection(ip: String): Request[IO] => Request[IO] = request =>
    request.withAttributes(request.attributes.insert(Request.Keys.ConnectionInfo,
      Request.Connection(SocketAddress(ipv4"127.0.0.1", port"8080"),
        SocketAddress(IpAddress.fromString(ip).get, port"40000"), secure = false)))

  private def entityOfSize(size: Int) = "0" * size
  private def formDataOfSize(size: Int) = UrlForm("field" -> ("0" * size))

  "the extractDirectClientIP directive" should {
    "extract the remote address of the connection and ignore headers" in {
      Get() ~> addHeader(`X-Forwarded-For`(remoteAddress("2.3.4.5"))) ~> withConnection("1.2.3.4") ~> {
        extractDirectClientIP { echoComplete }
      } ~> check { responseAs[String] shouldEqual "Some(1.2.3.4)" }
    }
    "extract None when the connection is unknown" in {
      Get() ~> addHeader(`X-Forwarded-For`(remoteAddress("2.3.4.5"))) ~> {
        extractDirectClientIP { echoComplete }
      } ~> check { responseAs[String] shouldEqual "None" }
    }
    "extract the loopback address over a real server" in {
      Get() ~!> extractDirectClientIP { echoComplete } ~> check { responseAs[String] shouldEqual "Some(127.0.0.1)" }
    }
  }

  "the requestEntityEmpty and requestEntityPresent directives" should {
    "treat a request without body as empty" in {
      Get() ~> requestEntityEmpty { completeOk }   ~> check { status shouldEqual Status.Ok }
      Get() ~> requestEntityPresent { completeOk } ~> check { rejection shouldEqual RequestEntityExpectedRejection }
    }
    "treat a request with Content-Length: 0 as empty" in {
      Post("/", "") ~> requestEntityEmpty { completeOk } ~> check { status shouldEqual Status.Ok }
    }
    "treat a request with a body as present" in {
      Post("/", "abc") ~> requestEntityPresent { completeOk } ~> check { status shouldEqual Status.Ok }
      Post("/", "abc") ~> requestEntityEmpty { completeOk }   ~> check { rejections shouldEqual Nil }
    }
    "treat a GET without body as empty over a real server" in {
      Get() ~!> (requestEntityEmpty { complete("empty") } ~ complete("present")) ~> check {
        responseAs[String] shouldEqual "empty"
      }
    }
    "complete with 400 when an entity is expected but missing" in {
      Get() ~> Route.seal(requestEntityPresent { completeOk }) ~> check {
        status shouldEqual Status.BadRequest
        responseAs[String] shouldEqual "Request entity expected but not supplied"
      }
    }
  }

  "the rejectEmptyResponse directive" should {
    "turn a response without body into a rejection" in {
      Get() ~> (rejectEmptyResponse { complete(Status.Ok) } ~ complete("fallback")) ~> check {
        responseAs[String] shouldEqual "fallback"
      }
    }
    "turn a response with an empty string body into a rejection" in {
      Get() ~> (rejectEmptyResponse { complete("") } ~ complete("fallback")) ~> check {
        responseAs[String] shouldEqual "fallback"
      }
    }
    "leave a response with a body alone" in {
      Get() ~> (rejectEmptyResponse { complete("body") } ~ complete("fallback")) ~> check {
        responseAs[String] shouldEqual "body"
      }
    }
  }

  "the withSizeLimit directive" should {
    "not apply if entity is not consumed" in {
      val route = withSizeLimit(500) { completeOk }
      Post("/abc", entityOfSize(500)) ~> route ~> check { status shouldEqual Status.Ok }
      Post("/abc", entityOfSize(501)) ~> route ~> check { status shouldEqual Status.Ok }
    }
    "apply if entity is consumed" in {
      val route = withSizeLimit(500) { entity(as[String]) { _ => completeOk } }
      Post("/abc", entityOfSize(500)) ~> route             ~> check { status shouldEqual Status.Ok }
      Post("/abc", entityOfSize(501)) ~> Route.seal(route) ~> check {
        status shouldEqual Status.PayloadTooLarge
        responseAs[String] should include("exceeded size limit")
      }
    }
    "apply to a chunked body consumed over a real server" in {
      val route = withSizeLimit(500) { entity(as[String]) { _ => completeOk } }
      Post("/abc", entityOfSize(501)) ~> removeHeader("Content-Length") ~!> route ~> check {
        status shouldEqual Status.PayloadTooLarge
      }
    }
    "apply if form data is fully consumed into a map" in {
      val route = withSizeLimit(64) { formFieldMap { _ => completeOk } }
      Post("/abc", formDataOfSize(32))  ~> route             ~> check { status shouldEqual Status.Ok }
      Post("/abc", formDataOfSize(128)) ~> Route.seal(route) ~> check {
        status shouldEqual Status.PayloadTooLarge
        responseAs[String] should include("exceeded size limit")
      }
    }
    "properly handle nested directives by applying innermost `withSizeLimit` directive" in {
      val route = withSizeLimit(500) { withSizeLimit(800) { entity(as[String]) { _ => completeOk } } }
      Post("/abc", entityOfSize(800)) ~> route             ~> check { status shouldEqual Status.Ok }
      Post("/abc", entityOfSize(801)) ~> Route.seal(route) ~> check { status shouldEqual Status.PayloadTooLarge }

      val route2 = withSizeLimit(500) { withSizeLimit(400) { entity(as[String]) { _ => completeOk } } }
      Post("/abc", entityOfSize(400)) ~> route2             ~> check { status shouldEqual Status.Ok }
      Post("/abc", entityOfSize(401)) ~> Route.seal(route2) ~> check { status shouldEqual Status.PayloadTooLarge }
    }
  }

  "the withoutSizeLimit directive" should {
    "skip request entity size verification" in {
      val route = withSizeLimit(500) { withoutSizeLimit { entity(as[String]) { _ => completeOk } } }
      Post("/abc", entityOfSize(501)) ~> route ~> check { status shouldEqual Status.Ok }
    }
  }
}
