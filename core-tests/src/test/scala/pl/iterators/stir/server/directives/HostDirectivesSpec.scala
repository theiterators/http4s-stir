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
    "names no host when the URI authority and the Host header disagree, where Pekko answers 400" in {
      // a proxy routing on Host must not be bypassed by an absolute-form target naming another host
      Get("http://admin.internal/x") ~> addHeader(Host("public.example")) ~> extractHost(echoComplete) ~> check {
        responseAs[String] shouldEqual ""
      }
      Get("http://admin.internal/x") ~> addHeader(Host("public.example")) ~> host("admin.internal")(completeOk) ~>
      check { handled shouldEqual false }
      Get("http://example.com:8080/x") ~> addHeader(Host("example.com", Some(9090))) ~> extractHost(echoComplete) ~>
      check { responseAs[String] shouldEqual "" }
      Get("http://Example.com/x") ~> addHeader(Host("example.COM")) ~> extractHost(echoComplete) ~> check {
        responseAs[String] shouldEqual "example.com"
      }
    }
    "drop a scheme's default port before comparing, wss and a missing scheme included" in {
      Get("/x").withUri(Uri.unsafeFromString("wss://example.com:443/x")) ~> addHeader(Host("example.com")) ~>
      extractHost(echoComplete)                                          ~> check { responseAs[String] shouldEqual "example.com" }
      Get("/x").withUri(Uri.unsafeFromString("//example.com:80/x"))      ~> addHeader(Host("example.com")) ~>
      extractHost(echoComplete)                                          ~> check { responseAs[String] shouldEqual "example.com" }
    }
    "lowercase a host name, as Pekko's URI parser does" in {
      Get("http://Example.COM/x") ~> host("example.com")(completeOk) ~> check { status shouldEqual Status.Ok }
      Get("/x")                   ~> addHeader(Host("Example.COM"))  ~> extractHost(echoComplete) ~> check {
        responseAs[String] shouldEqual "example.com"
      }
    }
    "fall back to the Host header for an empty URI authority" in {
      Get("/x").withUri(Uri.unsafeFromString("http:///x")) ~> addHeader(Host("fallback.org")) ~>
      extractHost(echoComplete)                            ~> check { responseAs[String] shouldEqual "fallback.org" }
    }
    "gives an IPv6 host without brackets" in {
      Get("http://[::1]:8080/x") ~> extractHost(echoComplete)            ~> check { responseAs[String] shouldEqual "::1" }
      Get("/x")                  ~> addHeader(Host("[::1]", Some(8080))) ~> extractHost(echoComplete) ~> check {
        responseAs[String] shouldEqual "::1"
      }
    }
    "reads the host of an HTTP/2 request, which brings it as :authority in the URI and no Host header" in {
      // what a backend that keeps :authority hands the route (Ember 0.23.36 drops it, see host-and-scheme.md)
      Request[IO](Method.GET, Uri.unsafeFromString("http://example.com:8443/x"), HttpVersion.`HTTP/2`) ~>
      host("example.com")(extractHost(echoComplete))                                                   ~> check { responseAs[String] shouldEqual "example.com" }
    }
  }
}
