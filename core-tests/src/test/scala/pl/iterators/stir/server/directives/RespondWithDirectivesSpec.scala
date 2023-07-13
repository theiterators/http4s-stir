package pl.iterators.stir.server.directives

import cats.effect.unsafe.IORuntime
import org.http4s.Header
import org.typelevel.ci.CIString

class RespondWithDirectivesSpec extends RoutingSpec {
  override implicit def runtime: IORuntime = IORuntime.global

  val customHeader = Header.Raw(CIString("custom"), "custom")
  val customHeader2 = Header.Raw(CIString("custom2"), "custom2")
  val existingHeader = Header.Raw(CIString("custom"), "existing")

  "respondWithHeader" should {
    val customHeader = Header.Raw(CIString("custom"), "custom")
    "add the given header to successful responses" in {
      Get() ~> {
        respondWithHeader(customHeader) { completeOk }
      } ~> check { headers.headers shouldEqual customHeader :: Nil }
    }
  }
  "respondWithHeaders" should {
    "add the given headers to successful responses" in {
      Get() ~> {
        respondWithHeaders(customHeader, customHeader2) { completeOk }
      } ~> check { headers.headers shouldEqual customHeader :: customHeader2 :: Nil }
    }
  }
  "respondWithDefaultHeader" should {
    def route(extraHeaders: Header.Raw*) = respondWithDefaultHeader(customHeader) {
      respondWithHeaders(extraHeaders.toList) {
        completeOk
      }
    }

    "add the given header to a response if the header was missing before" in {
      Get() ~> route() ~> check { headers.headers shouldEqual customHeader :: Nil }
    }
    "not change a response if the header already existed" in {
      Get() ~> route(existingHeader) ~> check { headers.headers shouldEqual existingHeader :: Nil }
    }
  }
  "respondWithDefaultHeaders" should {
    def route(extraHeaders: Header.Raw*) = respondWithDefaultHeaders(customHeader, customHeader2) {
      respondWithHeaders(extraHeaders.toList) {
        completeOk
      }
    }

    "add the given headers to a response if the header was missing before" in {
      Get() ~> route() ~> check { headers.headers shouldEqual customHeader :: customHeader2 :: Nil }
    }
    "not update an existing header" in {
      Get() ~> route(existingHeader) ~> check {
        headers.headers shouldEqual List(existingHeader, customHeader2)
      }
    }
  }
}
