package pl.iterators.stir.server.directives

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import fs2.{ Pipe, Stream }
import org.http4s.{ Header, Protocol, Status }
import org.http4s.headers.{ Connection, Upgrade }
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame
import org.scalatest.exceptions.TestFailedException
import org.typelevel.ci._
import pl.iterators.stir.server._

class WebSocketDirectivesSpec extends RoutingSpec {
  override implicit def runtime: IORuntime = IORuntime.global

  val ws: WebSocketBuilder2[IO] = WebSocketBuilder2[IO].unsafeRunSync()
  val echo: Pipe[IO, WebSocketFrame, WebSocketFrame] = identity

  val upgradeHeaders: RequestTransformer = addHeaders(
    Connection(ci"Upgrade"),
    Upgrade(Protocol(ci"websocket", None)),
    Header.Raw(ci"Sec-WebSocket-Version", "13"),
    Header.Raw(ci"Sec-WebSocket-Key", "dGhlIHNhbXBsZSBub25jZQ=="))

  val offeringProtocols: RequestTransformer = addHeader(Header.Raw(ci"Sec-WebSocket-Protocol", "foo, bar"))

  "handleWebSocketMessages" should {
    "accept a WebSocket upgrade request" in {
      Get("/ws") ~> upgradeHeaders ~> handleWebSocketMessages(ws, echo) ~> check {
        status shouldEqual Status.SwitchingProtocols
      }
    }
    "reject a request that is not a WebSocket upgrade" in {
      Get("/ws") ~> handleWebSocketMessages(ws, echo) ~> check {
        rejection shouldEqual ExpectedWebSocketRequestRejection
      }
    }
    "let the next alternative handle a plain request" in {
      Get("/ws") ~> (handleWebSocketMessages(ws, echo) ~ complete("plain")) ~> check {
        responseAs[String] shouldEqual "plain"
      }
    }
    "complete with 400 for a plain request when sealed" in {
      Get("/ws") ~> Route.seal(handleWebSocketMessages(ws, echo)) ~> check {
        status shouldEqual Status.BadRequest
        responseAs[String] shouldEqual "Expected WebSocket Upgrade request"
      }
    }
    "reject a plain request with the send/receive overload too" in {
      Get("/ws") ~> handleWebSocketMessages(ws, Stream.empty, _.drain) ~> check {
        rejection shouldEqual ExpectedWebSocketRequestRejection
      }
    }
  }

  "extractOfferedWsProtocols" should {
    "extract the protocols offered in Sec-WebSocket-Protocol" in {
      Get("/ws") ~> upgradeHeaders ~> offeringProtocols ~> extractOfferedWsProtocols { echoComplete } ~> check {
        responseAs[String] shouldEqual "List(foo, bar)"
      }
    }
    "extract an empty list when none are offered" in {
      Get("/ws") ~> upgradeHeaders ~> extractOfferedWsProtocols { echoComplete } ~> check {
        responseAs[String] shouldEqual "List()"
      }
    }
    "reject a request that is not a WebSocket upgrade" in {
      Get("/ws") ~> extractOfferedWsProtocols { echoComplete } ~> check {
        rejection shouldEqual ExpectedWebSocketRequestRejection
      }
    }
  }

  "handleWebSocketMessagesForProtocol" should {
    "accept the upgrade and select the protocol when it is offered" in {
      Get("/ws") ~> upgradeHeaders ~> offeringProtocols ~> handleWebSocketMessagesForProtocol(ws, echo, "bar") ~>
      check {
        status shouldEqual Status.SwitchingProtocols
        header("Sec-WebSocket-Protocol").map(_.value) shouldEqual Some("bar")
      }
    }
    "match the protocol case-insensitively" in {
      Get("/ws") ~> upgradeHeaders ~> offeringProtocols ~> handleWebSocketMessagesForProtocol(ws, echo, "BAR") ~>
      check {
        status shouldEqual Status.SwitchingProtocols
      }
    }
    "reject with UnsupportedWebSocketSubprotocolRejection when the protocol is not offered" in {
      Get("/ws") ~> upgradeHeaders ~> offeringProtocols ~> handleWebSocketMessagesForProtocol(ws, echo, "baz") ~>
      check {
        rejection shouldEqual UnsupportedWebSocketSubprotocolRejection("baz")
      }
    }
    "complete with 400 listing the supported protocols when sealed" in {
      val route = handleWebSocketMessagesForProtocol(ws, echo, "baz") ~
        handleWebSocketMessagesForProtocol(ws, echo, "qux")
      Get("/ws") ~> upgradeHeaders ~> offeringProtocols ~> Route.seal(route) ~> check {
        status shouldEqual Status.BadRequest
        header("Sec-WebSocket-Protocol").map(_.value) shouldEqual Some("baz, qux")
        responseAs[String] shouldEqual
        "None of the websocket subprotocols offered in the request are supported. Supported are 'baz','qux'."
      }
    }
    "reject a plain request with ExpectedWebSocketRequestRejection" in {
      Get("/ws") ~> handleWebSocketMessagesForProtocol(ws, echo, "bar") ~> check {
        rejection shouldEqual ExpectedWebSocketRequestRejection
      }
    }
  }

  "the testkit WebSocket helpers" should {
    "report an accepted upgrade with isWebSocketUpgrade" in {
      Get("/ws") ~> upgradeHeaders ~> handleWebSocketMessages(ws, echo) ~> check {
        isWebSocketUpgrade shouldEqual true
      }
    }
    "report a plain response as no upgrade" in {
      Get("/ws") ~> complete("plain") ~> check { isWebSocketUpgrade shouldEqual false }
    }
    "pass the selected protocol to expectWebSocketUpgradeWithProtocol" in {
      Get("/ws") ~> upgradeHeaders ~> offeringProtocols ~> handleWebSocketMessagesForProtocol(ws, echo, "bar") ~>
      check {
        expectWebSocketUpgradeWithProtocol { protocol =>
          protocol shouldEqual "bar"
          ()
        }
      }
    }
    "fail expectWebSocketUpgradeWithProtocol when no protocol was selected" in {
      Get("/ws") ~> upgradeHeaders ~> handleWebSocketMessages(ws, echo) ~> check {
        an[TestFailedException] should be thrownBy
        expectWebSocketUpgradeWithProtocol { _ => () }
      }
    }
  }

  "handleWebSocketMessagesForOptionalProtocol" should {
    "accept any upgrade when no protocol is required" in {
      Get("/ws") ~> upgradeHeaders ~> handleWebSocketMessagesForOptionalProtocol(ws, echo, None) ~> check {
        status shouldEqual Status.SwitchingProtocols
        header("Sec-WebSocket-Protocol") shouldEqual None
      }
    }
  }
}
