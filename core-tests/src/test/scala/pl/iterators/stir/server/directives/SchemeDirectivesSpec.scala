package pl.iterators.stir.server.directives

import cats.effect.unsafe.IORuntime
import org.http4s.Status
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
      Put("http://localhost/", "Hello") ~> scheme("http") { completeOk } ~> check { response.status shouldEqual Status.Ok }
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
      Put("https://localhost/", "Hello") ~> scheme("https") { completeOk } ~> check { response.status shouldEqual Status.Ok }
    }
    "reject requests with an http Uri scheme" in {
      Get("http://localhost/") ~> scheme("https") { completeOk } ~> check {
        rejections shouldEqual List(SchemeRejection("https"))
      }
    }
  }
}