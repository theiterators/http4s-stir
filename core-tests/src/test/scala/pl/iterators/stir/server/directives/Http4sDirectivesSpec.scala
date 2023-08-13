package pl.iterators.stir.server.directives

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.http4s.Status
import org.scalatest.Inside
import org.http4s.dsl.io._

class Http4sDirectivesSpec extends RoutingSpec with Inside {
  override implicit def runtime: IORuntime = IORuntime.global

  val route = httpRoutesOf {
    case GET -> Root / "test" => Status.Ok()
  } ~ pathPrefix("api" / Segment) { version =>
    httpRoutesOf {
        case GET -> Root / "test" => Status.Ok(version)
    }
  }

  "httpRoutesOf directive" should {
    "return a response for a matching request" in {
      Get("/test") ~> route ~> check {
        status shouldBe Status.Ok
      }
    }

    "return a rejection for a non-matching request" in {
      Get("/test2") ~> route ~> check {
        handled shouldBe false
        rejections shouldBe Nil
      }
    }

    "return a response for a matching request with path prefix" in {
      Get("/api/v1/test") ~> route ~> check {
        status shouldBe Status.Ok
        responseAs[String] shouldBe "v1"
      }
    }
  }
}
