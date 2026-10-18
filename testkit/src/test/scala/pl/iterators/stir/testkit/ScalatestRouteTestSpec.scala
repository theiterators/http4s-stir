package pl.iterators.stir.testkit

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.http4s.{ Header, Headers, MediaType, Response }
import org.scalatest.exceptions.TestFailedException
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.typelevel.ci.CIString
import pl.iterators.stir.server.Directives._
import pl.iterators.stir.server._
import org.http4s.Status._
import fs2._
import org.http4s.Method.{ GET, PUT }
import org.http4s.headers.{ `Content-Type`, `X-Forwarded-Proto` }

import java.util.Locale
import scala.concurrent.duration._

class ScalatestRouteTestSpec extends AnyFreeSpec with Matchers with ScalatestRouteTest {
  override implicit val runtime: IORuntime = IORuntime.global

  "The ScalatestRouteTest should support" - {

    "inspecting the chunks of a streamed response" in {
      val body = Stream.chunk(Chunk.array("ab".getBytes("UTF-8"))) ++ Stream.chunk(Chunk.array("cd".getBytes("UTF-8")))
      Get() ~> complete(Response[IO]().withBodyStream(body)) ~> check {
        chunks.map(chunk => new String(chunk.toArray, "UTF-8")) shouldEqual List("ab", "cd")
      }
    }

    "inspecting the chunk stream of a streamed response" in {
      val body = Stream.chunk(Chunk.array("ab".getBytes("UTF-8"))) ++ Stream.chunk(Chunk.array("cd".getBytes("UTF-8")))
      Get() ~> complete(Response[IO]().withBodyStream(body)) ~> check {
        chunksStream.compile.toList.unsafeRunSync().size shouldEqual 2
      }
    }

    "inspecting the trailer headers of a response" in {
      val trailers = IO.pure(Headers(Header.Raw(CIString("X-Trailer"), "done")))
      Get() ~> complete(Response[IO]().withTrailerHeaders(trailers)) ~> check {
        trailer.get(CIString("X-Trailer")).map(_.head.value) shouldEqual Some("done")
      }
    }

    "reading the trailer of a chunked response that came through a real server before its body" in {
      val chunked = Response[IO]().withBodyStream(Stream.emits("abc".getBytes("UTF-8")).covary[IO])
      Get() ~!> complete(chunked) ~> check {
        trailer shouldEqual Headers.empty
        responseAs[String] shouldEqual "abc"
      }
    }

    "failing a test whose route never completes once the route test timeout elapses" in {
      implicit val timeout: RouteTestTimeout = RouteTestTimeout(300.millis)
      val never: Route = _ => IO.never
      val failure = the[TestFailedException] thrownBy {
        Get() ~> never ~> check { fail("the check block must not run") }
      }
      failure.getMessage should include("Request was neither completed nor rejected within 300 milliseconds")
    }

    "failing a test whose response body never ends once the route test timeout elapses" in {
      implicit val timeout: RouteTestTimeout = RouteTestTimeout(300.millis)
      val endless = Stream.repeatEval(IO.sleep(10.millis).as(1.toByte))
      val failure = the[TestFailedException] thrownBy {
        Get() ~> complete(Response[IO]().withBodyStream(endless)) ~> check { responseAs[String]; () }
      }
      failure.getMessage should include("within 300 milliseconds")
    }

    "propagating a failure of the response body instead of turning it into a timeout" in {
      val failing = Stream.raiseError[IO](new IllegalStateException("body boom"))
      val failure = the[IllegalStateException] thrownBy {
        Get() ~> complete(Response[IO]().withBodyStream(failing)) ~> check { responseAs[String]; () }
      }
      failure.getMessage shouldEqual "body boom"
    }

    "failing a test whose route never completes over a real server within the timeout, not Ember's shutdown grace" in {
      implicit val timeout: RouteTestTimeout = RouteTestTimeout(500.millis)
      val never: Route = _ => IO.never
      val started = System.nanoTime()
      val failure = the[TestFailedException] thrownBy {
        Get() ~!> never ~> check { fail("the check block must not run") }
      }
      failure.getMessage should include("Request was neither completed nor rejected within 500 milliseconds")
      (System.nanoTime() - started) should be < 10.seconds.toNanos
    }

    "honouring a longer route test timeout for a slow route, also over a real server" in {
      implicit val timeout: RouteTestTimeout = RouteTestTimeout(5.seconds)
      Get()                                                ~> complete(IO.sleep(1200.millis).as("slow")) ~> check { responseAs[String] shouldEqual "slow" }
      Get() ~!> complete(IO.sleep(1200.millis).as("slow")) ~> check { responseAs[String] shouldEqual "slow" }
    }

    "failing a slow route under the default one second timeout" in {
      val failure = the[TestFailedException] thrownBy {
        Get() ~> complete(IO.sleep(2.seconds).as("slow")) ~> check { fail("the check block must not run") }
      }
      failure.getMessage should include("Request was neither completed nor rejected within 1 second")
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

    "a test checking a route that returns infinite chunks" in {
      Get() ~> {
        val infinite: Stream[IO, Byte] = Stream.iterate(0L)(_ + 1).covary[IO].metered(20.millis)
          .flatMap(i => Stream.chunk(Chunk.array(i.toString.getBytes("UTF-8"))))
        complete(
          Response[IO]().withBodyStream(infinite).withContentType(`Content-Type`(MediaType.application.`octet-stream`)))
      } ~> check {
        status shouldEqual Ok
        header[`Content-Type`].map(_.mediaType) shouldEqual Some(MediaType.application.`octet-stream`)
        chunksStream.take(5).map(c => new String(c.toArray, "UTF-8").toInt).compile.toVector.timeout(5.seconds) should
        evaluateTo((0 until 5).toVector)
      }
    }

    "a header lookup by name that is unaffected by the turkish-i problem" in {
      val previous = Locale.getDefault
      try {
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))
        val ifMatch = Header.Raw(CIString("If-Match"), "\"xyzzy\"")
        Get() ~> respondWithHeader(ifMatch) { complete("abc") } ~> check {
          header("if-match").map(_.value) shouldEqual Some("\"xyzzy\"")
        }
      } finally Locale.setDefault(previous)
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

    "adding a header twice keeps both, as Pekko" in {
      Get() ~> addHeader("X-A", "1") ~> addHeader("X-A", "2") ~> addHeaders(Header.Raw(CIString("X-A"), "3")) ~> {
        extractRequest(request =>
          complete(request.headers.get(CIString("X-A")).toList.flatMap(_.toList).map(_.value)
            .mkString(",")))
      } ~> check { responseAs[String] shouldEqual "1,2,3" }
    }

    "a response with only the headers the route sent" in {
      Get() ~> complete("hi") ~> check {
        response.headers.headers.map(_.name.toString).sorted shouldEqual List("Content-Length", "Content-Type")
      }
    }

    "a default Host header for a request that names no host, as Pekko (example.com)" in {
      Get("/x?a=1") ~> extractUri(uri => complete(uri.renderString)) ~> check {
        responseAs[String] shouldEqual "/x?a=1"
      }
      Get("/x") ~> extractHost(host => complete(host)) ~> check { responseAs[String] shouldEqual "example.com" }
      Get("/x") ~> host("example.com")(complete("ok")) ~> check { status shouldEqual Ok }
    }

    "the request's own Host header" in {
      Get("/x")                                                                                        ~> addHeader("Host", "api.test:8080") ~> extractRequest(r =>
        complete(r.headers.get(CIString("Host")).toList.flatMap(_.toList).map(_.value).mkString(","))) ~> check {
        responseAs[String] shouldEqual "api.test:8080"
      }
    }

    "no Host header for an absolute request URI" in {
      Get("https://other.test/x")                                                       ~>
      extractRequest(r => complete(r.headers.get(CIString("Host")).isDefined.toString)) ~>
      check { responseAs[String] shouldEqual "false" }
      Get("https://other.test/x") ~> extractHost(host => complete(host)) ~> check {
        responseAs[String] shouldEqual "other.test"
      }
    }

    "a custom default host" in {
      implicit val defaultHost: DefaultHostInfo = DefaultHostInfo(org.http4s.headers.Host("custom.test"))
      Get("/x") ~> extractHost(host => complete(host)) ~> check { responseAs[String] shouldEqual "custom.test" }
    }

    "evaluateTo failing instead of hanging on an IO that never completes" in {
      val failure = intercept[Exception] { IO.never[Int] should evaluateTo(1) }
      failure.getMessage should include("1 second")
    }

    "haveFailedWith failing instead of hanging on an IO that never completes" in {
      intercept[Exception] { IO.never[Int] should haveFailedWith(new RuntimeException) }
    }
  }
}
