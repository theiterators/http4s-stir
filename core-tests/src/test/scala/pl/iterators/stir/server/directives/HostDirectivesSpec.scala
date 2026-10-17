package pl.iterators.stir.server.directives

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.http4s.{ HttpVersion, Method, Request, Status, Uri }
import org.http4s.headers.Host
import org.scalatest.freespec.AnyFreeSpec

class HostDirectivesSpec extends AnyFreeSpec with GenericRoutingSpec {
  override implicit def runtime: IORuntime = IORuntime.global

  "The 'host' directive" - {
    "in its simple String form should" - {
      "block requests to unmatched hosts" in {
        Get() ~> addHeader(Host("spray.io")) ~> {
          host("spray.com") { completeOk }
        } ~> check { handled shouldEqual false }
      }

      "let requests to matching hosts pass" in {
        Get() ~> addHeader(Host("spray.io")) ~> {
          host("spray.com", "spray.io") { completeOk }
        } ~> check { response.status shouldEqual Status.Ok }
      }
    }

    "in its simple RegEx form" - {
      "block requests to unmatched hosts" in {
        Get() ~> addHeader(Host("spray.io")) ~> {
          host("hairspray.*".r) { echoComplete }
        } ~> check { handled shouldEqual false }
      }

      "let requests to matching hosts pass and extract the full host" in {
        Get() ~> addHeader(Host("spray.io")) ~> {
          host("spra.*".r) { echoComplete }
        } ~> check { responseAs[String] shouldEqual "spray.io" }
      }
    }

    "in its group RegEx form" - {
      "block requests to unmatched hosts" in {
        Get() ~> addHeader(Host("spray.io")) ~> {
          host("hairspray(.*)".r) { echoComplete }
        } ~> check { handled shouldEqual false }
      }

      "let requests to matching hosts pass and extract the full host" in {
        Get() ~> addHeader(Host("spray.io")) ~> {
          host("spra(.*)".r) { echoComplete }
        } ~> check { responseAs[String] shouldEqual "y.io" }
      }
    }
  }

  "extractHost, as Pekko reads the request's effective URI," - {
    "takes the host of an absolute-form request URI" in {
      Get("http://example.com/x") ~> extractHost(echoComplete)       ~> check { responseAs[String] shouldEqual "example.com" }
      Get("http://example.com/x") ~> host("example.com")(completeOk) ~> check { status shouldEqual Status.Ok }
    }
    "prefers the URI authority to the Host header (RFC 7230 5.4)" in {
      Get("http://example.com/x") ~> addHeader(Host("other.org")) ~> extractHost(echoComplete) ~> check {
        responseAs[String] shouldEqual "example.com"
      }
    }
    "gives an IPv6 host without brackets" in {
      Get("http://[::1]:8080/x") ~> extractHost(echoComplete)            ~> check { responseAs[String] shouldEqual "::1" }
      Get("/x")                  ~> addHeader(Host("[::1]", Some(8080))) ~> extractHost(echoComplete) ~> check {
        responseAs[String] shouldEqual "::1"
      }
    }
    "reads the host of an HTTP/2 request, which brings it as :authority in the URI and no Host header" in {
      // what a backend that keeps :authority hands the route (Ember 0.23.36 drops it, see host.md)
      Request[IO](Method.GET, Uri.unsafeFromString("http://example.com:8443/x"), HttpVersion.`HTTP/2`) ~>
      host("example.com")(extractHost(echoComplete))                                                   ~> check { responseAs[String] shouldEqual "example.com" }
    }
  }
}
