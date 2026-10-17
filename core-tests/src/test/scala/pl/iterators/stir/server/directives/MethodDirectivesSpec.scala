package pl.iterators.stir.server.directives

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.http4s.{ MediaType, Method, Response, Status }
import org.http4s.headers.{ `Content-Length`, `Content-Type` }
import pl.iterators.stir.server._

import java.util.Locale

class MethodDirectivesSpec extends RoutingSpec {
  override implicit def runtime: IORuntime = IORuntime.global

  "query" should {
    lazy val queryRoute = query { completeOk }

    "block GET requests" in {
      Get() ~> queryRoute ~> check { rejection shouldEqual MethodRejection(MethodDirectives.QUERY) }
    }
    "let QUERY requests pass" in {
      Query("/", "select *") ~> queryRoute ~> check { response.status shouldEqual Status.Ok }
    }
    "let QUERY requests pass over a real server" in {
      Query("/", "select *") ~!> (query & entity(as[String])) { body => complete(body) } ~> check {
        responseAs[String] shouldEqual "select *"
      }
    }
  }

  "get | put" should {
    lazy val getOrPut = (get | put) { completeOk }

    "block POST requests" in {
      Post() ~> getOrPut ~> check { handled shouldEqual false }
    }
    "let GET requests pass" in {
      Get() ~> getOrPut ~> check { response.status shouldEqual Status.Ok }
    }
    "let PUT requests pass" in {
      Put() ~> getOrPut ~> check { response.status shouldEqual Status.Ok }
    }
  }

  "head" should {
    val headRoute = head {
      complete(Response[IO](Status.Ok)
        .withContentType(`Content-Type`(MediaType.application.`octet-stream`))
        .putHeaders(`Content-Length`.unsafeFromLong(12345L)))
    }

    "allow manual complete" in {
      Head() ~> headRoute ~> check {
        status shouldEqual Status.Ok
        responseAs[Array[Byte]] shouldBe empty
      }
    }
    "allow manual complete over a real server, keeping the declared length" in {
      Head() ~!> headRoute ~> check {
        status shouldEqual Status.Ok
        header[`Content-Length`].map(_.length) shouldEqual Some(12345L)
        responseAs[Array[Byte]] shouldBe empty
      }
    }
  }

  "two failed `get` directives" should {
    "only result in a single Rejection" in {
      Put() ~> {
        get { completeOk } ~
        get { completeOk }
      } ~> check {
        rejections shouldEqual List(MethodRejection(Method.GET))
      }
    }
  }

  "overrideMethodWithParameter" should {
    "change the request method" in {
      Get("/?_method=put") ~> overrideMethodWithParameter("_method") {
        get { complete("GET") } ~
        put { complete("PUT") }
      } ~> check { responseAs[String] shouldEqual "PUT" }
    }
    "not affect the request when not specified" in {
      Get() ~> overrideMethodWithParameter("_method") {
        get { complete("GET") } ~
        put { complete("PUT") }
      } ~> check { responseAs[String] shouldEqual "GET" }
    }
    "complete with 501 Not Implemented when not a valid method" in {
      Get("/?_method=hallo") ~> overrideMethodWithParameter("_method") {
        get { complete("GET") } ~
        put { complete("PUT") }
      } ~> check { status shouldEqual Status.NotImplemented }
    }
    "override with QUERY" in {
      Get("/?_method=query") ~> overrideMethodWithParameter("_method") {
        get { complete("GET") } ~
        query { complete("QUERY") }
      } ~> check { responseAs[String] shouldEqual "QUERY" }
    }
    "override with a method name containing an i in the turkish locale" in {
      val previous = Locale.getDefault
      Locale.setDefault(Locale.forLanguageTag("tr-TR"))
      try {
        Get("/?_method=options") ~> overrideMethodWithParameter("_method") {
          get { complete("GET") } ~
          options { complete("OPTIONS") }
        } ~> check { responseAs[String] shouldEqual "OPTIONS" }
      } finally Locale.setDefault(previous)
    }
  }

  "MethodRejections under a successful match" should {
    "be cancelled if the match happens after the rejection" in {
      Put() ~> {
        get { completeOk } ~
        put { reject(RequestEntityExpectedRejection) }
      } ~> check {
        rejections shouldEqual List(RequestEntityExpectedRejection)
      }
    }
    "be cancelled if the match happens after the rejection (example 2)" in {
      Put() ~> {
        (get & complete(Status.Ok)) ~ (put & reject(RequestEntityExpectedRejection))
      } ~> check {
        rejections shouldEqual List(RequestEntityExpectedRejection)
      }
    }
    "be cancelled if the match happens before the rejection" in {
      Put() ~> {
        put { reject(RequestEntityExpectedRejection) } ~ get { completeOk }
      } ~> check {
        rejections shouldEqual List(RequestEntityExpectedRejection)
      }
    }
    "be cancelled if the match happens before the rejection (example 2)" in {
      Put() ~> {
        (put & reject(RequestEntityExpectedRejection)) ~ (get & complete(Status.Ok))
      } ~> check {
        rejections shouldEqual List(RequestEntityExpectedRejection)
      }
    }
  }
}
