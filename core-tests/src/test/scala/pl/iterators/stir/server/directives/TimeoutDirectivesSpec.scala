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
//      path("timeout") {
//        // needs to be long because of the race between wRT and wRTR
//        withRequestTimeout(1.second) {
//          withRequestTimeoutResponse(request => timeoutResponse) {
//            val response: IO[String] = slowIO() // very slow
//            complete(response)
//          }
//        }
//      } ~
      path("equivalent") {
        // updates timeout and handler at
        withRequestTimeout(1.second, _ => timeoutResponse) {
          val response: IO[String] = slowIO() // very slow
          complete(response)
        }
      }

//    Get("/timeout") ~!> route ~> check {
//      status should ===(Status.RequestTimeout)
//    }

    Get("/equivalent") ~!> route ~> check {
      status should ===(Status.GatewayTimeout)
    }
  }

  def slowIO(): IO[String] = IO.never[String]
}
