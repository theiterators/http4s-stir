package pl.iterators.stir.server.directives

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.http4s.Status
import org.scalatest.Inside
import pl.iterators.stir.server.ExceptionHandler

class IODirectivesSpec extends RoutingSpec with Inside {
  override implicit def runtime: IORuntime = IORuntime.global
  class TestException(msg: String) extends Exception(msg)
  object TestException extends Exception("XXX")
  def throwTestException[T](msgPrefix: String): T => Nothing = t => throw new TestException(msgPrefix + t)

  val showEx = handleExceptions(ExceptionHandler {
    case e: TestException => complete(Status.InternalServerError, "Oops. " + e)
  })

//  trait TestWithCircuitBreaker {
//    val breakerResetTimeout = 500.millis.dilated
//    val breaker =
//      new CircuitBreaker(system.scheduler, maxFailures = 1, callTimeout = 10.seconds.dilated, breakerResetTimeout)
//    def openBreaker() = breaker.withCircuitBreaker(IO.raiseError(new Exception("boom")))
//  }

  "The `onComplete` directive" should {
    "unwrap a IO in the success case" in {
      var i = 0
      def nextNumber() = { i += 1; i }
      val route = onComplete(IO.pure(nextNumber())) { echoComplete }
      Get() ~> route ~> check {
        responseAs[String] shouldEqual "Success(1)"
      }
      Get() ~> route ~> check {
        responseAs[String] shouldEqual "Success(2)"
      }
    }
    "unwrap a IO in the failure case" in {
      Get() ~> onComplete(IO.raiseError[String](new RuntimeException("no"))) { echoComplete } ~> check {
        responseAs[String] shouldEqual "Failure(java.lang.RuntimeException: no)"
      }
    }
    "catch an exception in the success case" in {
      Get() ~> showEx(onComplete(IO.pure("ok")) { throwTestException("EX when ") }) ~> check {
        status shouldEqual Status.InternalServerError
        responseAs[String] shouldEqual s"Oops. pl.iterators.stir.server.directives.IODirectivesSpec$$TestException: EX when Success(ok)"
      }
    }
    "catch an exception in the failure case" in {
      Get() ~> showEx(
        onComplete(IO.raiseError[String](new RuntimeException("no"))) { throwTestException("EX when ") }) ~> check {
        status shouldEqual Status.InternalServerError
        responseAs[String] shouldEqual s"Oops. pl.iterators.stir.server.directives.IODirectivesSpec$$TestException: EX when Failure(java.lang.RuntimeException: no)"
      }
    }
  }

//  "The `onCompleteWithBreaker` directive" should {
//    "unwrap a Future in the success case" in new TestWithCircuitBreaker {
//      var i = 0
//      def nextNumber() = { i += 1; i }
//      val route = onCompleteWithBreaker(breaker)(IO.pure(nextNumber())) { echoComplete }
//      Get() ~> route ~> check {
//        responseAs[String] shouldEqual "Success(1)"
//      }
//      Get() ~> route ~> check {
//        responseAs[String] shouldEqual "Success(2)"
//      }
//    }
//    "unwrap a Future in the failure case" in new TestWithCircuitBreaker {
//      Get() ~> onCompleteWithBreaker(breaker)(IO.raiseError[String](new RuntimeException("no"))) {
//        echoComplete
//      } ~> check {
//        responseAs[String] shouldEqual "Failure(java.lang.RuntimeException: no)"
//      }
//    }
//    "fail fast if the circuit breaker is open" in new TestWithCircuitBreaker {
//      openBreaker()
//      // since this is timing sensitive, try a few times to observe the breaker open
//      awaitAssert(
//        Get() ~> onCompleteWithBreaker(breaker)(IO.pure(1)) { echoComplete } ~> check {
//          inside(rejection) {
//            case CircuitBreakerOpenRejection(_) =>
//          }
//        }, breakerResetTimeout / 2)
//    }
//    "stop failing fast when the circuit breaker closes" in new TestWithCircuitBreaker {
//      openBreaker()
//      // observe that it opened
//      awaitAssert(breaker.isOpen should ===(true))
//      // since this is timing sensitive, try a few times to observe the breaker closed
//      awaitAssert({
//        Get() ~> onCompleteWithBreaker(breaker)(IO.pure(1)) { echoComplete } ~> check {
//          responseAs[String] shouldEqual "Success(1)"
//        }
//      }, breakerResetTimeout + 1.second)
//    }
//    "catch an exception in the success case" in new TestWithCircuitBreaker {
//      Get() ~> showEx(
//        onCompleteWithBreaker(breaker)(IO.pure("ok")) { throwTestException("EX when ") }) ~> check {
//        status shouldEqual StatusCodes.InternalServerError
//        responseAs[String] shouldEqual s"Oops. org.apache.pekko.http.scaladsl.server.directives.FutureDirectivesSpec$$TestException: EX when Success(ok)"
//      }
//    }
//    "catch an exception in the failure case" in new TestWithCircuitBreaker {
//      Get() ~> showEx(onCompleteWithBreaker(breaker)(IO.raiseError[String](new RuntimeException("no"))) {
//        throwTestException("EX when ")
//      }) ~> check {
//        status shouldEqual StatusCodes.InternalServerError
//        responseAs[String] shouldEqual s"Oops. org.apache.pekko.http.scaladsl.server.directives.FutureDirectivesSpec$$TestException: EX when Failure(java.lang.RuntimeException: no)"
//      }
//    }
//  }

  "The `onSuccess` directive" should {
    "unwrap a IO in the success case" in {
      Get() ~> onSuccess(IO.pure("yes")) { echoComplete } ~> check {
        responseAs[String] shouldEqual "yes"
      }
    }
    "propagate the exception in the failure case" in //EventFilter[TestException.type](
//      occurrences = 1,
//      message = BasicRouteSpecs.defaultExnHandler500Error("XXX")).intercept {
      Get() ~> onSuccess(IO.raiseError[Int](TestException)) { echoComplete } ~> check {
        status shouldEqual Status.InternalServerError
//      }
    }
    "catch an exception in the success case" in {
      Get() ~> showEx(onSuccess(IO.pure("ok")) { throwTestException("EX when ") }) ~> check {
        status shouldEqual Status.InternalServerError
        responseAs[String] shouldEqual s"Oops. pl.iterators.stir.server.directives.IODirectivesSpec$$TestException: EX when ok"
      }
    }
    "catch an exception in the failure case" in // EventFilter[TestException.type](
//      occurrences = 1,
//      message = BasicRouteSpecs.defaultExnHandler500Error("XXX")).intercept {
      Get() ~> onSuccess(IO.raiseError[Unit](TestException)) { throwTestException("EX when ") } ~> check {
        status shouldEqual Status.InternalServerError
        responseAs[String] shouldEqual ""
      }
//    }
  }

  "The `completeOrRecoverWith` directive" should {
    "complete the request with the IO's value if the IO succeeds" in {
      Get() ~> completeOrRecoverWith(IO.pure("yes")) { echoComplete } ~> check {
        responseAs[String] shouldEqual "yes"
      }
    }
    "don't call the inner route if the IO succeeds" in {
      Get() ~> completeOrRecoverWith(IO.pure("ok")) { throwTestException("EX when ") } ~> check {
        status shouldEqual Status.Ok
        responseAs[String] shouldEqual "ok"
      }
    }
    "recover using the inner route if the IO fails" in {
      val route = completeOrRecoverWith(IO.raiseError[String](TestException)) {
        case e => complete(s"Exception occurred: ${e.getMessage}")
      }

      Get() ~> route ~> check {
        responseAs[String] shouldEqual "Exception occurred: XXX"
      }
    }
    "catch an exception during recovery" in {
      Get() ~> showEx(
        completeOrRecoverWith(IO.raiseError[String](TestException)) { throwTestException("EX when ") }) ~> check {
        status shouldEqual Status.InternalServerError
        responseAs[String] shouldEqual s"Oops. pl.iterators.stir.server.directives.IODirectivesSpec$$TestException: EX when pl.iterators.stir.server.directives.IODirectivesSpec$$TestException$$: XXX"
      }
    }
  }
}