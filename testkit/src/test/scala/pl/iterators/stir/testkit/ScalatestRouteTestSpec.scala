package pl.iterators.stir.testkit

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.http4s.{ Header, Response }
import org.scalatest.exceptions.TestFailedException
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.typelevel.ci.CIString
import pl.iterators.stir.server.Directives._
import pl.iterators.stir.server._
import org.http4s.Status._
import fs2._
import org.http4s.Method.{ GET, PUT }
import org.http4s.headers.`X-Forwarded-Proto`

class ScalatestRouteTestSpec extends AnyFreeSpec with Matchers with ScalatestRouteTest {
  override implicit val runtime: IORuntime = IORuntime.global

  "The ScalatestRouteTest should support" - {

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

    "a test using ~!> and some checks" in {
      // raw here, should have been parsed into modelled header when going through an actual server when using `~!>`
      val extraHeader = Header.Raw(CIString("X-Forwarded-Proto"), "abc")
      Get() ~!> {
        respondWithHeader(extraHeader) {
          complete("abc")
        }
      } ~> check {
        status shouldEqual Ok
        responseEntity.through(text.utf8.decode).compile.string should evaluateTo("abc")
        header[`X-Forwarded-Proto`].get shouldEqual `X-Forwarded-Proto`.parse("abc").getOrElse(
          throw new TestFailedException("Failed to parse header", 0))
      }
    }
//
//    "a test checking a route that returns infinite chunks" in {
//      Get() ~> {
//        val infiniteSource =
//          Source.unfold(0L)(acc => Some((acc + 1, acc)))
//            .throttle(1, 20.millis)
//            .map(i => ByteString(i.toString))
//        complete(HttpEntity(ContentTypes.`application/octet-stream`, infiniteSource))
//      } ~> check {
//        status shouldEqual OK
//        contentType shouldEqual ContentTypes.`application/octet-stream`
//        val future = chunksStream.take(5).runFold(Vector.empty[Int])(_ :+ _.data.utf8String.toInt)
//        future.futureValue shouldEqual (0 until 5).toVector
//
//      }
//    }
//
    "proper rejection collection" in {
      Post("/abc", "content") ~> {
        (get | put) {
          complete("naah")
        }
      } ~> check {
        rejections shouldEqual List(MethodRejection(GET), MethodRejection(PUT))
      }
    }

//    "running on pekko dispatcher threads" in Await.result(Future {
//      // https://github.com/apache/incubator-pekko-http/pull/2526
//      // Check will block while waiting on the response, this might lead to starvation
//      // on the BatchingExecutor of pekko's dispatcher if the blocking is not managed properly.
//      Get() ~> complete(Future(HttpResponse())) ~> check {
//        status shouldEqual OK
//      }
//    }, 5.seconds)

//    "separation of route execution from checking" in {
//      val pinkHeader = RawHeader("Fancy", "pink")
//
//      case object Command
//      val service = TestProbe()
//      val handler = TestProbe()
//      implicit def serviceRef: ActorRef = service.ref
//      implicit val askTimeout: Timeout = 1.second.dilated
//
//      val result =
//        Get() ~> pinkHeader ~> {
//          respondWithHeader(pinkHeader) {
//            complete(handler.ref.ask(Command).mapTo[String])
//          }
//        } ~> runRoute
//
//      handler.expectMsg(Command)
//      handler.reply("abc")
//
//      check {
//        status shouldEqual OK
//        responseEntity shouldEqual HttpEntity(ContentTypes.`text/plain(UTF-8)`, "abc")
//        header("Fancy") shouldEqual Some(pinkHeader)
//      }(result)
//    }
//
    "failing the test inside the route" in {

      val route = get {
        fail()
      }

      assertThrows[TestFailedException] {
        Get() ~> route
      }
    }

    "throwing an AssertionError inside the route" in {
      val route = get {
        throw new AssertionError("test")
      }

      assertThrows[AssertionError] {
        Get() ~> route
      }
    }

    "internal server error" in {

      val route = get {
        throw new RuntimeException("BOOM")
      }

      Get() ~> route ~> check {
        status shouldEqual InternalServerError
      }
    }

//    "fail if testing a HEAD request with ~> and `transparent-head-request = on`" in {
//      def runTest(): Unit = Head() ~> complete("Ok") ~> check {}
//
//      val ex = the[Exception] thrownBy (runTest())
//      ex.getMessage shouldEqual
//        "`pekko.http.server.transparent-head-requests = on` not supported in RouteTest using `~>`. " +
//          "Use `~!>` instead for a full-stack test, e.g. `req ~!> route ~> check {...}`"
//    }
  }
}
