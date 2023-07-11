package pl.iterators.stir.server.directives

import cats.effect.unsafe.IORuntime
import org.http4s.Status
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
}