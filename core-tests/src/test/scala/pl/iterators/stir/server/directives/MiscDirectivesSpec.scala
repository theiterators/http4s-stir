package pl.iterators.stir.server.directives

import cats.effect.unsafe.IORuntime
import com.comcast.ip4s.IpAddress
import org.http4s.Header
import org.http4s.headers.`X-Forwarded-For`
import org.typelevel.ci.CIString

class MiscDirectivesSpec extends RoutingSpec {
  override implicit def runtime: IORuntime = IORuntime.global

  "the extractClientIP directive" should {
    "extract from a X-Forwarded-For header" in {
      Get() ~> addHeaders(`X-Forwarded-For`(remoteAddress("2.3.4.5")),
        Header.Raw(CIString("x-real-ip"), "1.2.3.4")) ~> {
        extractClientIP { echoComplete }
      } ~> check { responseAs[String] shouldEqual "Some(2.3.4.5)" }
    }
    "extract from a (synthetic) Remote-Address header" in {
      Get() ~> addHeader(Header.Raw(CIString("Remote-Address"), "1.2.3.4")) ~> {
        extractClientIP { echoComplete }
      } ~> check { responseAs[String] shouldEqual "Some(1.2.3.4)" }
    }
    "extract from a X-Real-IP header" in {
      Get() ~> addHeader(Header.Raw(CIString("X-Real-Ip"), "1.2.3.4")) ~> {
        extractClientIP { echoComplete }
      } ~> check { responseAs[String] shouldEqual "Some(1.2.3.4)" }
    }
    "select X-Real-Ip when both X-Real-Ip and Remote-Address headers are present" in {
      Get() ~> addHeaders(Header.Raw(CIString("X-Real-Ip"), "1.2.3.4"),
        Header.Raw(CIString("Remote-Address"), "1.2.3.4")) ~> {
        extractClientIP { echoComplete }
      } ~> check { responseAs[String] shouldEqual "Some(1.2.3.4)" }
    }
    "extract unknown when no headers" in {
      Get() ~> {
        extractClientIP { echoComplete }
      } ~> check { responseAs[String] shouldEqual "None" }
    }
  }

//  "the selectPreferredLanguage directive" should {
//    "Accept-Language: de, en".test { selectFrom =>
//      selectFrom("de", "en") shouldEqual "de"
//      selectFrom("en", "de") shouldEqual "en"
//    }
//    "Accept-Language: en, de;q=.5".test { selectFrom =>
//      selectFrom("de", "en") shouldEqual "en"
//      selectFrom("en", "de") shouldEqual "en"
//    }
//    "Accept-Language: en;q=.5, de".test { selectFrom =>
//      selectFrom("de", "en") shouldEqual "de"
//      selectFrom("en", "de") shouldEqual "de"
//    }
//    "Accept-Language: en-US, en;q=.7, *;q=.1, de;q=.5".test { selectFrom =>
//      selectFrom("en", "en-US") shouldEqual "en-US"
//      selectFrom("de", "en") shouldEqual "en"
//      selectFrom("de", "hu") shouldEqual "de"
//      selectFrom("de-DE", "hu") shouldEqual "de-DE"
//      selectFrom("hu", "es") shouldEqual "hu"
//      selectFrom("es", "hu") shouldEqual "es"
//    }
//    "Accept-Language: en, *;q=.5, de;q=0".test { selectFrom =>
//      selectFrom("es", "de") shouldEqual "es"
//      selectFrom("de", "es") shouldEqual "es"
//      selectFrom("es", "en") shouldEqual "en"
//    }
//    "Accept-Language: en, *;q=0".test { selectFrom =>
//      selectFrom("es", "de") shouldEqual "es"
//      selectFrom("de", "es") shouldEqual "de"
//      selectFrom("es", "en") shouldEqual "en"
//    }
//  }
//
//  "the withSizeLimit directive" should {
//    "not apply if entity is not consumed" in {
//      val route = withSizeLimit(500) { completeOk }
//
//      Post("/abc", entityOfSize(500)) ~> route ~> check {
//        status shouldEqual StatusCodes.OK
//      }
//
//      Post("/abc", entityOfSize(501)) ~> route ~> check {
//        status shouldEqual StatusCodes.OK
//      }
//    }
//
//    "apply if entity is consumed" in {
//      val route = withSizeLimit(500) {
//        entity(as[String]) { _ =>
//          completeOk
//        }
//      }
//
//      Post("/abc", entityOfSize(500)) ~> route ~> check {
//        status shouldEqual StatusCodes.OK
//      }
//
//      Post("/abc", entityOfSize(501)) ~> Route.seal(route) ~> check {
//        status shouldEqual StatusCodes.PayloadTooLarge
//        entityAs[String] should include("exceeded size limit")
//      }
//    }
//
//    "apply if form data is fully consumed into a map" in {
//      val route =
//        withSizeLimit(64) {
//          formFieldMap { _ =>
//            completeOk
//          }
//        }
//
//      Post("/abc", formDataOfSize(32)) ~> route ~> check {
//        status shouldEqual StatusCodes.OK
//      }
//
//      Post("/abc", formDataOfSize(128)) ~> Route.seal(route) ~> check {
//        status shouldEqual StatusCodes.PayloadTooLarge
//        responseAs[String] shouldEqual "The request content was malformed:\n" +
//          "EntityStreamSizeException: incoming entity size (134) " +
//          "exceeded size limit (64 bytes)! " +
//          "This may have been a parser limit (set via `pekko.http.[server|client].parsing.max-content-length`), " +
//          "a decoder limit (set via `pekko.http.routing.decode-max-size`), " +
//          "or a custom limit set with `withSizeLimit`."
//      }
//    }
//
//    "properly handle nested directives by applying innermost `withSizeLimit` directive" in {
//      val route =
//        withSizeLimit(500) {
//          withSizeLimit(800) {
//            entity(as[String]) { _ =>
//              completeOk
//            }
//          }
//        }
//
//      Post("/abc", entityOfSize(800)) ~> route ~> check {
//        status shouldEqual StatusCodes.OK
//      }
//
//      Post("/abc", entityOfSize(801)) ~> Route.seal(route) ~> check {
//        status shouldEqual StatusCodes.PayloadTooLarge
//        entityAs[String] should include("exceeded size limit")
//      }
//
//      val route2 =
//        withSizeLimit(500) {
//          withSizeLimit(400) {
//            entity(as[String]) { _ =>
//              completeOk
//            }
//          }
//        }
//
//      Post("/abc", entityOfSize(400)) ~> route2 ~> check {
//        status shouldEqual StatusCodes.OK
//      }
//
//      Post("/abc", entityOfSize(401)) ~> Route.seal(route2) ~> check {
//        status shouldEqual StatusCodes.PayloadTooLarge
//        entityAs[String] should include("exceeded size limit")
//      }
//    }
//  }
//
//  "the withoutSizeLimit directive" should {
//    "skip request entity size verification" in {
//      val route =
//        withSizeLimit(500) {
//          withoutSizeLimit {
//            entity(as[String]) { _ =>
//              completeOk
//            }
//          }
//        }
//
//      Post("/abc", entityOfSize(501)) ~> route ~> check {
//        status shouldEqual StatusCodes.OK
//      }
//    }
//  }
//
//  implicit class AddStringToIn(acceptLanguageHeaderString: String) {
//    def test(body: VarArgsFunction1[String, String] => Unit): Unit =
//      s"properly handle `$acceptLanguageHeaderString`" in {
//        val Array(name, value) = acceptLanguageHeaderString.split(':')
//        val acceptLanguageHeader = HttpHeader.parse(name.trim, value) match {
//          case HttpHeader.ParsingResult.Ok(h: `Accept-Language`, Nil) => h
//          case result                                                 => fail(result.toString)
//        }
//        body { availableLangs =>
//          val selected = Promise[String]()
//          val first = Language(availableLangs.head)
//          val more = availableLangs.tail.map(Language(_))
//          Get() ~> addHeader(acceptLanguageHeader) ~> {
//            selectPreferredLanguage(first, more: _*) { lang =>
//              complete(lang.toString)
//            }
//          } ~> check(selected.complete(Try(responseAs[String])))
//          Await.result(selected.future, 1.second.dilated)
//        }
//      }
//  }

  def remoteAddress(ip: String) = IpAddress.fromString(ip)

//  private def entityOfSize(size: Int) = HttpEntity(ContentTypes.`text/plain(UTF-8)`, "0" * size)

//  private def formDataOfSize(size: Int) = FormData(Map("field" -> ("0" * size)))
}
