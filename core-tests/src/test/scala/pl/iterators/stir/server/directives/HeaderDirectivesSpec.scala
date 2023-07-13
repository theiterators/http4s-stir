package pl.iterators.stir.server.directives

import cats.data.NonEmptyList
import cats.effect.unsafe.IORuntime
import org.http4s.{ Header, Uri }
import org.http4s.headers.Origin.HostList
import org.http4s.headers.{ `User-Agent`, Connection, Host, Origin }
import org.scalatest.Inside
import org.typelevel.ci.CIString
import pl.iterators.stir.server._

class HeaderDirectivesSpec extends RoutingSpec with Inside {
  override implicit def runtime: IORuntime = IORuntime.global
  "The headerValuePF directive" should {
    lazy val myHeaderValue = headerValuePF {
      case h if Connection.parse(h.value).isRight =>
        Connection.parse(h.value).getOrElse(throw new IllegalStateException("")).values.head
    }

    "extract the respective header value if a matching request header is present" in {
      Get("/abc") ~> addHeader(Connection(CIString("close"))) ~> myHeaderValue { echoComplete } ~> check {
        responseAs[String] shouldEqual "close"
      }
    }

    "reject with an empty rejection set if no matching request header is present" in {
      Get("/abc") ~> myHeaderValue { echoComplete } ~> check { rejections shouldEqual Nil }
    }

    "reject with a MalformedHeaderRejection if the extract function throws an exception" in {
      Get("/abc") ~> addHeader(Connection(CIString("close"))) ~> {
        headerValuePF { case _ => sys.error("Naah!") } { echoComplete }
      } ~> check {
        inside(rejection) { case MalformedHeaderRejection("Connection", "Naah!", _) => }
      }
    }
  }

  "The headerValueByType directive" should {
    val route =
      (headerValueByType[Origin] & get) { origin =>
        val hostList = origin.asInstanceOf[HostList]
        complete(s"The first origin was ${hostList.hosts.head}")
      }
    "extract a header if the type is matching" in {
      val host = Origin.Host(Uri.Scheme.http, Uri.RegName("localhost"), Some(8080))
      val originHeader: Origin = HostList(NonEmptyList.one(host))
      Get("abc") ~> addHeader(originHeader) ~> route ~> check {
        responseAs[String] shouldEqual "The first origin was http://localhost:8080"
      }
    }
    "reject a request if no header of the given type is present" in {
      Get("abc") ~> route ~> check {
        inside(rejection) {
          case MissingHeaderRejection("Origin") =>
        }
      }
    }
    "reject a request for missing header, and format it properly when header included special characters (e.g. `-`)" in {
      val route = (headerValueByType[`User-Agent`] & get) { agent =>
        complete(s"Agent: ${agent}")
      }
      Get("abc") ~> route ~> check {
        inside(rejection) {
          case MissingHeaderRejection("User-Agent") =>
        }
      }
    }
//    "reject a request if no header matches a custom one, and use the custom header's name for the rejection " in {
//      val route = headerValueByType(XCustomHeader) { customValue =>
//        complete(s"Custom-Value: $customValue")
//      }
//      Get("abc") ~> route ~> check {
//        inside(rejection) {
//          case MissingHeaderRejection("X-Custom-Header") =>
//        }
//      }
//    }
  }

  "The headerValueByName directive" should {
    lazy val route =
      headerValueByName("Referer") { referer =>
        complete(s"The referer was $referer")
      }

    "extract a header if the name is matching" in {
      Get("abc") ~> addHeader(Header.Raw(CIString("Referer"), "http://example.com")) ~> route ~> check {
        responseAs[String] shouldEqual "The referer was http://example.com"
      }
    }

//    "extract a header with Symbol name" in {
//      lazy val symbolRoute =
//        headerValueByName(Symbol("Referer")) { referer =>
//          complete(s"The symbol referer was $referer")
//        }
//
//      Get("abc") ~> addHeader(Header.Raw(CIString("Referer"), "http://example.com/symbol")) ~> symbolRoute ~> check {
//        responseAs[String] shouldEqual "The symbol referer was http://example.com/symbol"
//      }
//    }

    "reject a request if no header of the given type is present" in {
      Get("abc") ~> route ~> check {
        inside(rejection) {
          case MissingHeaderRejection("Referer") =>
        }
      }
    }
  }

  "The optionalHeaderValueByName directive" should {
    lazy val route =
      optionalHeaderValueByName("Referer") { referer =>
        complete(s"The referer was $referer")
      }

    "extract a header if the name is matching" in {
      Get("abc") ~> addHeader("Referer", "http://example.com") ~> route ~> check {
        responseAs[String] shouldEqual "The referer was Some(http://example.com)"
      }
    }

//    "extract a header with Symbol name" in {
//      lazy val symbolRoute =
//        optionalHeaderValueByName(Symbol("Referer")) { referer =>
//          complete(s"The symbol referer was $referer")
//        }
//
//      Get("abc") ~> RawHeader("Referer", "http://example.com/symbol") ~> symbolRoute ~> check {
//        responseAs[String] shouldEqual "The symbol referer was Some(http://example.com/symbol)"
//      }
//    }

    "extract None if no header of the given name is present" in {
      Get("abc") ~> route ~> check {
        responseAs[String] shouldEqual "The referer was None"
      }
    }
  }

  "The optionalHeaderValue directive" should {
    lazy val myHeaderValue = optionalHeaderValue {
      case h if Connection.parse(h.value).isRight =>
        Some(Connection.parse(h.value).getOrElse(throw new IllegalStateException("")).values.head)
      case _ => None
    }

    "extract the respective header value if a matching request header is present" in {
      Get("/abc") ~> addHeader(Connection(CIString("close"))) ~> myHeaderValue { echoComplete } ~> check {
        responseAs[String] shouldEqual "Some(close)"
      }
    }

    "extract None if no matching request header is present" in {
      Get("/abc") ~> myHeaderValue { echoComplete } ~> check { responseAs[String] shouldEqual "None" }
    }

    "reject with a MalformedHeaderRejection if the extract function throws an exception" in {
      Get("/abc") ~> addHeader(Connection(CIString("close"))) ~> {
        val myHeaderValue = optionalHeaderValue { case _ => sys.error("Naaah!") }
        myHeaderValue { echoComplete }
      } ~> check {
        inside(rejection) { case MalformedHeaderRejection("Connection", "Naaah!", _) => }
      }
    }
  }

  "The optionalHeaderValueByType directive" should {
    val route =
      (optionalHeaderValueByType[Origin] & get) {
        case Some(origin) => complete(s"The first origin was ${origin.asInstanceOf[Origin.HostList].hosts.head}")
        case None         => complete("No Origin header found.")
      }
    "extract Some(header) if the type is matching" in {
      val host = Origin.Host(Uri.Scheme.http, Uri.RegName("localhost"), Some(8080))
      val originHeader: Origin = HostList(NonEmptyList.one(host))
      Get("abc") ~> addHeader(originHeader) ~> route ~> check {
        responseAs[String] shouldEqual "The first origin was http://localhost:8080"
      }
    }
    "extract None if no header of the given type is present" in {
      Get("abc") ~> route ~> check {
        responseAs[String] shouldEqual "No Origin header found."
      }
    }
  }
//
//  "The checkSameOrigin directive" should {
//    val correctOrigin = HttpOrigin("http://localhost:8080")
//    val route = checkSameOrigin(HttpOriginRange(correctOrigin)) {
//      complete("Result")
//    }
//    "handle request with correct origin headers" in {
//      Get("abc") ~> Origin(correctOrigin) ~> route ~> check {
//        status shouldEqual StatusCodes.OK
//        responseAs[String] shouldEqual "Result"
//      }
//    }
//    "reject request with missed origin header" in {
//      Get("abc") ~> route ~> check {
//        inside(rejection) {
//          case MissingHeaderRejection(headerName) => headerName shouldEqual Origin.name
//        }
//      }
//    }
//    "reject requests with invalid origin header value" in {
//      val invalidHttpOrigin = HttpOrigin("http://invalid.com")
//      val invalidOriginHeader = Origin(invalidHttpOrigin)
//      Get("abc") ~> invalidOriginHeader ~> route ~> check {
//        inside(rejection) {
//          case InvalidOriginRejection(allowedOrigins) => allowedOrigins shouldEqual Seq(correctOrigin)
//        }
//      }
//      Get("abc") ~> invalidOriginHeader ~> Route.seal(route) ~> check {
//        status shouldEqual StatusCodes.Forbidden
//        responseAs[String] should include(s"${correctOrigin.value}")
//      }
//    }
//  }
//
//  "The HeaderMagnet" should {
//    "get the header name from the ModeledCustomHeaderCompanion" in {
//      val companionMagnet: HeaderMagnet[XCustomHeader] = XCustomHeader
//      companionMagnet.headerName shouldEqual "X-Custom-Header"
//
//      val classMagnet = HeaderMagnet.fromClassForModeledCustomHeader(classOf[XCustomHeader], XCustomHeader)
//      classMagnet.headerName shouldEqual "X-Custom-Header"
//
//      val classTagMagnet =
//        HeaderMagnet.fromClassTagForModeledCustomHeader(ClassTag(classOf[XCustomHeader]), XCustomHeader)
//      classTagMagnet.headerName shouldEqual "X-Custom-Header"
//    }
//  }
}

//object HeaderDirectivesSpec {
//  final object XCustomHeader extends ModeledCustomHeaderCompanion[XCustomHeader] {
//    override def name: String = "X-Custom-Header"
//    override def parse(value: String): Try[XCustomHeader] = Try(new XCustomHeader)
//  }
//  final class XCustomHeader extends ModeledCustomHeader[XCustomHeader] {
//    override def companion: ModeledCustomHeaderCompanion[XCustomHeader] = XCustomHeader
//    override def value(): String = "custom-value"
//    override def renderInRequests(): Boolean = true
//    override def renderInResponses(): Boolean = false
//  }
//}
