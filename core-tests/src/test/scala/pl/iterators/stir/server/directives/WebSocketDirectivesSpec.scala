package pl.iterators.stir.server.directives

import cats.effect.{ Deferred, IO }
import cats.effect.unsafe.IORuntime
import fs2.{ Pipe, Stream }
import org.http4s.{ Header, Protocol, Status }
import org.http4s.headers.{ Connection, Upgrade }
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame
import org.scalatest.exceptions.TestFailedException
import org.typelevel.ci._
import pl.iterators.stir.server._
import pl.iterators.stir.testkit.{ RouteTestTimeout, WSProbe }
import scodec.bits.ByteVector

import scala.concurrent.duration._

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

  "the testkit WSProbe" should {
    // Pekko's greeter: text frames are answered, binary frames ignored
    val greeter: Pipe[IO, WebSocketFrame, WebSocketFrame] =
      _.collect { case text: WebSocketFrame.Text => WebSocketFrame.Text(s"Hello ${text.str}!") }

    "handle websocket requests" in {
      val wsClient = WSProbe()
      WS("/", wsClient) ~> handleWebSocketMessages(ws, greeter) ~> check {
        isWebSocketUpgrade shouldEqual true
        wsClient.sendMessage("Peter")
        wsClient.expectMessage("Hello Peter!")

        wsClient.sendMessage(WebSocketFrame.Binary(ByteVector("abcdef".getBytes)))
        wsClient.expectNoMessage()

        wsClient.sendMessage("John")
        wsClient.expectMessage("Hello John!")

        wsClient.sendCompletion()
        wsClient.expectCompletion()
      }
    }
    "choose subprotocol from offered ones" in {
      val wsClient = WSProbe()
      val route = handleWebSocketMessagesForProtocol(ws, echo, "echo") ~
        handleWebSocketMessagesForProtocol(ws, greeter, "greeter")
      WS("/", wsClient, "other", "echo", "greeter") ~> route ~> check {
        expectWebSocketUpgradeWithProtocol { protocol =>
          protocol shouldEqual "echo"

          wsClient.sendMessage("Peter")
          wsClient.expectMessage("Peter")

          wsClient.sendMessage(ByteVector("abcdef".getBytes))
          wsClient.expectMessage(ByteVector("abcdef".getBytes))

          wsClient.sendMessage("John")
          wsClient.expectMessage("John")

          wsClient.sendCompletion()
          wsClient.expectCompletion()
        }
      }
    }
    "drive a handler with separate send and receive sides" in {
      val wsClient = WSProbe()
      val received = Deferred[IO, String].unsafeRunSync()
      val send = Stream("a", "b").map(WebSocketFrame.Text(_)).covary[IO]
      val receive: Pipe[IO, WebSocketFrame, Unit] =
        _.collect { case text: WebSocketFrame.Text => text.str }.evalMap(received.complete(_).void)
      WS("/", wsClient) ~> handleWebSocketMessages(ws, send, receive) ~> check {
        wsClient.expectMessage("a")
        wsClient.expectMessage("b")
        wsClient.expectCompletion()
        wsClient.sendMessage("from client")
        received.get.timeout(5.seconds).unsafeRunSync() shouldEqual "from client"
      }
    }
    "fail the expectations that are not met" in {
      implicit val routeTestTimeout: RouteTestTimeout = RouteTestTimeout(200.millis) // shadows the spec-wide one
      val wsClient = WSProbe()
      WS("/", wsClient) ~> handleWebSocketMessages(ws, greeter) ~> check {
        an[AssertionError] should be thrownBy wsClient.expectMessage()
        wsClient.sendMessage("Peter")
        an[AssertionError] should be thrownBy wsClient.expectNoMessage()
        wsClient.sendMessage("John")
        an[AssertionError] should be thrownBy wsClient.expectMessage("Goodbye John!")
        wsClient.sendMessage("Mary")
        an[AssertionError] should be thrownBy wsClient.expectCompletion()
        wsClient.sendCompletion()
        an[AssertionError] should be thrownBy wsClient.expectMessage()
      }
    }
    "report a failing handler on the next expectation" in {
      val wsClient = WSProbe()
      val failing: Pipe[IO, WebSocketFrame, WebSocketFrame] =
        _.evalMap(_ => IO.raiseError[WebSocketFrame](new IllegalStateException("boom")))
      WS("/", wsClient) ~> handleWebSocketMessages(ws, failing) ~> check {
        wsClient.sendMessage("Peter")
        (the[AssertionError] thrownBy wsClient.expectMessage() should have).message(
          "Expected a message but the handler failed: java.lang.IllegalStateException: boom")
      }
    }
    "leave a plain request's rejection untouched" in {
      WS("/", WSProbe()) ~> complete("plain")                    ~> check { responseAs[String] shouldEqual "plain" }
      Get("/")           ~> handleWebSocketMessages(ws, greeter) ~> check {
        rejection shouldEqual ExpectedWebSocketRequestRejection
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
