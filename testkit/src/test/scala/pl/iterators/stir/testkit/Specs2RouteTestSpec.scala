package pl.iterators.stir.testkit

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.http4s.{ Header, Response }
import org.typelevel.ci.CIString
import pl.iterators.stir.server.Directives._
import pl.iterators.stir.server._
import org.http4s.Status._
import fs2._
import org.http4s.Method.{ GET, PUT }
import org.specs2.execute.FailureException
import org.specs2.mutable.Specification

import scala.concurrent.duration._

class Specs2RouteTestSpec extends Specification with Specs2RouteTest {
  override implicit val runtime: IORuntime = IORuntime.global

  "The ScalatestRouteTest should support" should {

    "evaluateTo failing instead of hanging on an IO that never completes" in {
      (IO.never[Int] must evaluateTo(1)) must throwA[java.util.concurrent.TimeoutException]
    }

    "haveFailedWith failing, and saying it timed out, on an IO that never completes" in {
      (IO.never[Int] must haveFailedWith(new RuntimeException("x"))) must throwA[FailureException].like {
        case FailureException(failure) => failure.message must contain("TimeoutException")
      }
    }

    "failing a test whose route never completes once the route test timeout elapses" in {
      implicit val timeout: RouteTestTimeout = RouteTestTimeout(300.millis)
      val never: Route = _ => IO.never
      (Get() ~> never ~> check { ko("the check block must not run") }) must throwA[FailureException].like {
        case FailureException(failure) =>
          failure.message must contain("Request was neither completed nor rejected within 300 milliseconds")
      }
    }

    "the most simple and direct route test" in {
      Get() ~> complete(Response[IO]()) ~> { rr => rr.response.status } shouldEqual Response[IO]().status
    }

    "a test using a directive and some checks" in {
      val pinkHeader = Header.Raw(CIString("Fancy"), "pink")
      Get() ~> addHeader(pinkHeader) ~> {
        respondWithHeader(pinkHeader) {
          complete("abc")
        }
      } ~> check {
        status shouldEqual Ok
        responseEntity.through(text.utf8.decode).compile.string should evaluateTo("abc")
        header("Fancy") shouldEqual Some(pinkHeader)
      }
    }

    "proper rejection collection" in {
      Post("/abc", "content") ~> {
        (get | put) {
          complete("naah")
        }
      } ~> check {
        rejections shouldEqual List(MethodRejection(GET), MethodRejection(PUT))
      }
    }

    "failing the test inside the route" in {

      val route = get {
        failure("BOOM")
        complete(Response[IO]())
      }

      {
        Get() ~> route
      } must throwA[org.specs2.execute.FailureException]
    }

    "failing an assertion inside the route" in {
      val route = get {
        throw new AssertionError("test")
      }

      {
        Get() ~> route
      } must throwA[java.lang.AssertionError]
    }

    "internal server error" in {

      val route = get {
        throw new RuntimeException("BOOM")
      }

      Get().~>(route).~>(check {
        status shouldEqual InternalServerError
      })
    }
  }
}
