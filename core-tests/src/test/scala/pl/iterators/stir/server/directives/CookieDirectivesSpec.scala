package pl.iterators.stir.server.directives

import cats.effect.unsafe.IORuntime
import org.http4s.{HttpDate, RequestCookie, ResponseCookie, Status}
import org.http4s.headers.{Cookie, `Set-Cookie`}
import pl.iterators.stir.server._

class CookieDirectivesSpec extends RoutingSpec {
  override implicit def runtime: IORuntime = IORuntime.global
  val deletedTimeStamp = HttpDate.unsafeFromEpochSecond(-2208988800L)

  "The 'cookie' directive" should {
    "extract the respectively named cookie" in {
      Get() ~> addHeader(Cookie(RequestCookie("fancy",  "pants"))) ~> {
        cookie("fancy") { echoComplete }
      } ~> check { responseAs[String] shouldEqual "fancy=pants" }
    }
    "reject the request if the cookie is not present" in {
      Get() ~> {
        cookie("fancy") { echoComplete }
      } ~> check { rejection shouldEqual MissingCookieRejection("fancy") }
    }
    "properly pass through inner rejections" in {
      Get() ~> addHeader(Cookie(RequestCookie("fancy",  "pants"))) ~> {
        cookie("fancy") { c => reject(ValidationRejection("Dont like " + c.content, None)) }
      } ~> check { rejection shouldEqual ValidationRejection("Dont like pants", None) }
    }
  }

  "The 'deleteCookie' directive" should {
    "add a respective Set-Cookie headers to successful responses" in {
      Get() ~> {
        deleteCookie("myCookie", "test.com") { completeOk }
      } ~> check {
        status shouldEqual Status.Ok
        header[`Set-Cookie`].head.head shouldEqual `Set-Cookie`(ResponseCookie("myCookie", "deleted", expires = Option(deletedTimeStamp),
          domain = Some("test.com")))
      }
    }

    "support deleting multiple cookies at a time" in {
      Get() ~> {
        deleteCookie(ResponseCookie("myCookie", "test.com"), ResponseCookie("myCookie2", "foobar.com")) { completeOk }
      } ~> check {
        status shouldEqual Status.Ok
        headers.get[`Set-Cookie`].get.toList shouldEqual List(
          `Set-Cookie`(ResponseCookie("myCookie", "deleted", expires = Option(deletedTimeStamp))),
          `Set-Cookie`(ResponseCookie("myCookie2", "deleted", expires = Option(deletedTimeStamp))))
      }
    }
  }

  "The 'optionalCookie' directive" should {
    "produce a `Some(cookie)` extraction if the cookie is present" in {
      Get() ~> addHeader(Cookie(RequestCookie("abc", "123"))) ~> {
        optionalCookie("abc") { echoComplete }
      } ~> check { responseAs[String] shouldEqual "Some(abc=123)" }
    }
    "produce a `None` extraction if the cookie is not present" in {
      Get() ~> optionalCookie("abc") { echoComplete } ~> check { responseAs[String] shouldEqual "None" }
    }
    "let rejections from its inner route pass through" in {
      Get() ~> {
        optionalCookie("test-cookie") { _ =>
          validate(false, "ouch") { completeOk }
        }
      } ~> check { rejection shouldEqual ValidationRejection("ouch", None) }
    }
  }

  "The 'setCookie' directive" should {
    "add a respective Set-Cookie headers to successful responses" in {
      Get() ~> {
        setCookie(ResponseCookie("myCookie", "test.com")) { completeOk }
      } ~> check {
        status shouldEqual Status.Ok
        header[`Set-Cookie`].head.head shouldEqual `Set-Cookie`(ResponseCookie("myCookie", "test.com"))
      }
    }

    "support setting multiple cookies at a time" in {
      Get() ~> {
        setCookie(ResponseCookie("myCookie", "test.com"), ResponseCookie("myCookie2", "foobar.com")) { completeOk }
      } ~> check {
        status shouldEqual Status.Ok
        headers.get[`Set-Cookie`].get.toList shouldEqual List(
          `Set-Cookie`(ResponseCookie("myCookie", "test.com")), `Set-Cookie`(ResponseCookie("myCookie2", "foobar.com")))
      }
    }
  }
}