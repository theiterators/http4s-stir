package pl.iterators.stir.server.directives

import cats.data.NonEmptyList
import cats.effect.unsafe.IORuntime
import org.http4s.{ Header, Status, Uri }
import org.http4s.headers.Origin.HostList
import org.http4s.headers.{ `User-Agent`, Connection, Origin }
import org.scalatest.Inside
import org.typelevel.ci.CIString
import pl.iterators.stir.server._

import java.util.Locale

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
    val route = (headerValueByType[Origin] & get) { origin =>
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
    "reject a request if no header matches a custom one, and use the custom header's name for the rejection" in {
      import HeaderDirectivesSpec.XCustomHeader
      val custom = headerValueByType[XCustomHeader]
      val route = custom { header => complete(s"Custom-Value: ${header.value}") }
      Get("abc") ~> route                                                   ~> check { inside(rejection) { case MissingHeaderRejection("X-Custom-Header") => } }
      Get("abc") ~> addHeader(Header.Raw(CIString("X-Custom-Header"), "v")) ~> route ~> check {
        responseAs[String] shouldEqual "Custom-Value: v"
      }
    }
  }

  "The headerValueByName directive under a Turkish default locale" should {
    "extract a header whose name contains a capital I" in {
      val previous = Locale.getDefault
      Locale.setDefault(Locale.forLanguageTag("tr-TR"))
      try {
        Get("abc")                                                 ~> addHeader(Header.Raw(CIString("If-Match"), "\"xyzzy\"")) ~>
        headerValueByName("If-Match") { value => complete(value) } ~> check {
          responseAs[String] shouldEqual "\"xyzzy\""
        }
      } finally Locale.setDefault(previous)
    }
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
    val route = (optionalHeaderValueByType[Origin] & get) {
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

  "The checkSameOrigin directive" should {
    val correctOrigin = Origin.Host(Uri.Scheme.http, Uri.RegName("localhost"), Some(8080))
    val route = checkSameOrigin(correctOrigin) { complete("Result") }

    "pass if the Origin header is in the allowed list" in {
      Get("abc") ~> addHeader(Origin.HostList(NonEmptyList.one(correctOrigin)): Origin) ~> route ~> check {
        responseAs[String] shouldEqual "Result"
      }
    }
    "reject with MissingHeaderRejection if the Origin header is absent" in {
      Get("abc") ~> route ~> check { rejection shouldEqual MissingHeaderRejection("Origin") }
    }
    "reject with InvalidOriginRejection if the Origin header is not allowed" in {
      val other = Origin.Host(Uri.Scheme.http, Uri.RegName("invalid.com"), None)
      Get("abc") ~> addHeader(Origin.HostList(NonEmptyList.one(other)): Origin) ~> route ~> check {
        rejection shouldEqual InvalidOriginRejection(Seq(correctOrigin))
      }
    }
    "complete with 403 when sealed and the origin is not allowed" in {
      val other = Origin.Host(Uri.Scheme.http, Uri.RegName("invalid.com"), None)
      Get("abc") ~> addHeader(Origin.HostList(NonEmptyList.one(other)): Origin) ~> Route.seal(route) ~> check {
        status shouldEqual Status.Forbidden
        responseAs[String] shouldEqual "Allowed `Origin` header values: http://localhost:8080"
      }
    }
  }
}

object HeaderDirectivesSpec {
  final case class XCustomHeader(value: String)
  object XCustomHeader {
    implicit val header: Header[XCustomHeader, Header.Single] =
      Header.create(CIString("X-Custom-Header"), _.value, s => Right(XCustomHeader(s)))
  }
}
