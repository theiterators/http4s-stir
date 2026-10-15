package pl.iterators.stir.server.directives

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.http4s.{ Response, Status }

import scala.concurrent.duration._

class TimeoutDirectivesSpec extends RoutingSpec {
  override implicit def runtime: IORuntime = IORuntime.global

  "Request Timeout" should {
    "be configurable in routing layer" in {

      val route = path("timeout") {
        withRequestTimeout(3.seconds) {
          val response: IO[String] = slowIO() // very slow
          complete(response)
        }
      }

      Get("/timeout") ~!> route ~> check {
        status should ===(Status.ServiceUnavailable)
      }
    }
  }

  "allow mapping the response" in {
    val timeoutResponse = Response[IO](Status.GatewayTimeout).withEntity(
      "Unable to serve response within time limit, please enhance your calm.")

    val route =
      path("timeout") {
        // needs to be long because of the race between wRT and wRTR
        withRequestTimeout(1.second) {
          withRequestTimeoutResponse(_ => timeoutResponse) {
            val response: IO[String] = slowIO() // very slow
            complete(response)
          }
        }
      } ~
      path("equivalent") {
        // updates timeout and handler at
        withRequestTimeout(1.second, _ => timeoutResponse) {
          val response: IO[String] = slowIO() // very slow
          complete(response)
        }
      }

    Get("/timeout") ~!> route ~> check {
      status should ===(Status.GatewayTimeout)
    }

    Get("/equivalent") ~!> route ~> check {
      status should ===(Status.GatewayTimeout)
    }
  }

  "extractRequestTimeout" should {
    "extract the timeout set by an enclosing withRequestTimeout" in {
      Get() ~> withRequestTimeout(3.seconds) { extractRequestTimeout { echoComplete } } ~> check {
        responseAs[String] shouldEqual "3 seconds"
      }
    }
    "extract the innermost timeout" in {
      Get() ~> withRequestTimeout(3.seconds) {
        withRequestTimeout(500.millis) { extractRequestTimeout { echoComplete } }
      } ~> check {
        responseAs[String] shouldEqual "500 milliseconds"
      }
    }
    "extract Duration.Inf when no timeout is set" in {
      Get() ~> extractRequestTimeout { echoComplete } ~> check { responseAs[String] shouldEqual "Duration.Inf" }
    }
    "extract Duration.Inf inside withoutRequestTimeout" in {
      Get() ~> withRequestTimeout(3.seconds) { withoutRequestTimeout { extractRequestTimeout { echoComplete } } } ~>
      check {
        responseAs[String] shouldEqual "Duration.Inf"
      }
    }
  }

  "withRequestTimeoutResponse" should {
    "leave the route alone when no timeout is set" in {
      Get() ~> withRequestTimeoutResponse(_ => Response[IO](Status.GatewayTimeout)) { completeOk } ~> check {
        status shouldEqual Status.Ok
      }
    }
  }

  def slowIO(): IO[String] = IO.never[String]
}
