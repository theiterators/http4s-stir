package pl.iterators.stir.server.directives

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.comcast.ip4s._
import org.http4s.{ Request, Status }
import pl.iterators.stir.server._

class SchemeDirectivesSpec extends RoutingSpec {
  override implicit def runtime: IORuntime = IORuntime.global

  "the extractScheme directive" should {
    "extract the Uri scheme" in {
      Put("http://localhost/", "Hello") ~> extractScheme { echoComplete } ~> check {
        responseAs[String] shouldEqual "http"
      }
    }
  }

  """the scheme("http") directive""" should {
    "let requests with an http Uri scheme pass" in {
      Put("http://localhost/", "Hello") ~> scheme("http") { completeOk } ~> check {
        response.status shouldEqual Status.Ok
      }
    }
    "reject requests with an https Uri scheme" in {
      Get("https://localhost/") ~> scheme("http") { completeOk } ~> check {
        rejections shouldEqual List(SchemeRejection("http"))
      }
    }
    "cancel SchemeRejection if other scheme passed" in {
      val route =
        scheme("https") { completeOk } ~
        scheme("http") { reject }

      Put("http://localhost/", "Hello") ~> route ~> check {
        rejections should be(Nil)
      }
    }
  }

  """the scheme("https") directive""" should {
    "let requests with an https Uri scheme pass" in {
      Put("https://localhost/", "Hello") ~> scheme("https") { completeOk } ~> check {
        response.status shouldEqual Status.Ok
      }
    }
    "reject requests with an http Uri scheme" in {
      Get("http://localhost/") ~> scheme("https") { completeOk } ~> check {
        rejections shouldEqual List(SchemeRejection("https"))
      }
    }
  }

  def withConnection(secure: Boolean): Request[IO] => Request[IO] = request =>
    request.withAttributes(request.attributes.insert(Request.Keys.ConnectionInfo,
      Request.Connection(SocketAddress(ipv4"127.0.0.1", port"8080"), SocketAddress(ipv4"127.0.0.1", port"40000"),
        secure)))

  "the extractScheme directive on a server" should {
    "derive http from an insecure connection when the request line carries no scheme" in {
      Get("/") ~> withConnection(secure = false) ~> extractScheme { echoComplete } ~> check {
        responseAs[String] shouldEqual "http"
      }
    }
    "derive https from a secure connection" in {
      Get("/") ~> withConnection(secure = true) ~> extractScheme { echoComplete } ~> check {
        responseAs[String] shouldEqual "https"
      }
    }
    "prefer the connection over the request line" in {
      Get("http://localhost/") ~> withConnection(secure = true) ~> extractScheme { echoComplete } ~> check {
        responseAs[String] shouldEqual "https"
      }
    }
    "extract an empty string when neither the connection nor the request line is known" in {
      Get("/") ~> extractScheme { echoComplete } ~> check { responseAs[String] shouldEqual "" }
    }
    "extract the scheme over a real server" in {
      Get("/") ~!> extractScheme { echoComplete } ~> check { responseAs[String] shouldEqual "http" }
    }
    """let scheme("https") pass over a secure connection""" in {
      Get("/") ~> withConnection(secure = true) ~> scheme("https") { completeOk } ~> check {
        response.status shouldEqual Status.Ok
      }
    }
  }
}
