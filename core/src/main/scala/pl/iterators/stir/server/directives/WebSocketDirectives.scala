package pl.iterators.stir.server.directives

import cats.effect.{ IO, SyncIO }
import fs2.{ Pipe, Stream }
import org.http4s.{ Header, Headers, Protocol, Request, Response, Status }
import org.http4s.headers.{ Connection, Upgrade }
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame
import org.typelevel.ci._
import org.typelevel.vault.Key
import pl.iterators.stir.server._

/**
 * @groupname websocket WebSocket directives
 * @groupprio websocket 230
 */
trait WebSocketDirectives {
  import BasicDirectives._
  import RouteDirectives._
  import WebSocketDirectives._

  /**
   * Extracts the list of WebSocket subprotocols offered by the client in the `Sec-WebSocket-Protocol` header if
   * this is a WebSocket upgrade request. Rejects with an [[ExpectedWebSocketRequestRejection]] otherwise.
   *
   * @group websocket
   */
  def extractOfferedWsProtocols: Directive1[Seq[String]] =
    extractRequest.flatMap { request =>
      if (isWebSocketUpgrade(request)) provide(offeredProtocols(request))
      else reject(ExpectedWebSocketRequestRejection): Directive1[Seq[String]]
    }

  /**
   * Handles WebSocket upgrade requests with the given handler and rejects other requests with an
   * [[ExpectedWebSocketRequestRejection]].
   *
   * @group websocket
   */
  def handleWebSocketMessages(ws: WebSocketBuilder2[IO], handler: Pipe[IO, WebSocketFrame, WebSocketFrame]): Route =
    handleWebSocketMessagesForOptionalProtocol(ws, handler, None)

  /**
   * Handles WebSocket upgrade requests with the given send stream and receive pipe and rejects other requests
   * with an [[ExpectedWebSocketRequestRejection]].
   *
   * @group websocket
   */
  def handleWebSocketMessages(ws: WebSocketBuilder2[IO], send: Stream[IO, WebSocketFrame],
      receive: Pipe[IO, WebSocketFrame, Unit]): Route =
    requireWebSocketUpgrade { _ =>
      ws.withOnNonWebSocketRequest(IO.pure(switchingProtocols(None))).build(send, receive)
        .map(response => RouteResult.Complete(withHandler(response, WebSocketHandler.Separate(send, receive))))
    }

  /**
   * Handles WebSocket upgrade requests with the given handler if the given subprotocol is offered in the request
   * and rejects other requests with an [[ExpectedWebSocketRequestRejection]] or an
   * [[UnsupportedWebSocketSubprotocolRejection]].
   *
   * @group websocket
   */
  def handleWebSocketMessagesForProtocol(ws: WebSocketBuilder2[IO], handler: Pipe[IO, WebSocketFrame, WebSocketFrame],
      subprotocol: String): Route =
    handleWebSocketMessagesForOptionalProtocol(ws, handler, Some(subprotocol))

  /**
   * Handles WebSocket upgrade requests with the given handler and rejects other requests with an
   * [[ExpectedWebSocketRequestRejection]].
   *
   * If `subprotocol` is `None` any WebSocket upgrade request is accepted. If it is `Some(protocol)` the request is
   * only accepted when the client offered `protocol` (case-insensitively) in `Sec-WebSocket-Protocol`; the
   * handshake response then names it. Otherwise the request is rejected with an
   * [[UnsupportedWebSocketSubprotocolRejection]]. Chain several of these routes to support several subprotocols.
   *
   * @group websocket
   */
  def handleWebSocketMessagesForOptionalProtocol(ws: WebSocketBuilder2[IO],
      handler: Pipe[IO, WebSocketFrame, WebSocketFrame], subprotocol: Option[String]): Route =
    extractOfferedWsProtocols { offered =>
      subprotocol match {
        case Some(protocol) if !offered.exists(_.equalsIgnoreCase(protocol)) =>
          reject(UnsupportedWebSocketSubprotocolRejection(protocol))
        case selected =>
          val headers = selected.fold(Headers.empty)(p => Headers(Header.Raw(SecWebSocketProtocol, p)))
          _ =>
            ws.withHeaders(headers).withOnNonWebSocketRequest(IO.pure(switchingProtocols(selected)))
              .build(handler).map(response =>
                RouteResult.Complete(withHandler(response, WebSocketHandler.Combined(handler))))
      }
    }

  private def requireWebSocketUpgrade: Directive0 =
    extractRequest.flatMap { request =>
      if (isWebSocketUpgrade(request)) pass else reject(ExpectedWebSocketRequestRejection)
    }
}

object WebSocketDirectives extends WebSocketDirectives {
  private val SecWebSocketProtocol = ci"Sec-WebSocket-Protocol"
  private val WebSocketProtocolName = ci"websocket"

  /** The handler a WebSocket directive was given, as the user wrote it (before http4s' ping/pong and defragmentation). */
  private[stir] sealed trait WebSocketHandler
  private[stir] object WebSocketHandler {
    final case class Combined(pipe: Pipe[IO, WebSocketFrame, WebSocketFrame]) extends WebSocketHandler
    final case class Separate(send: Stream[IO, WebSocketFrame], receive: Pipe[IO, WebSocketFrame, Unit])
        extends WebSocketHandler
  }

  /**
   * Set on the upgrade response by the WebSocket directives, so that the in-memory testkit can exchange frames with
   * the handler (http4s keeps its own WebSocket context private). Servers ignore it.
   */
  private[stir] val WebSocketHandlerKey: Key[WebSocketHandler] = Key.newKey[SyncIO, WebSocketHandler].unsafeRunSync()

  private def withHandler(response: Response[IO], handler: WebSocketHandler): Response[IO] =
    response.withAttribute(WebSocketHandlerKey, handler)

  private[stir] def isWebSocketUpgrade(request: Request[IO]): Boolean =
    request.headers.get[Connection].exists(_.hasUpgrade) &&
    request.headers.get[Upgrade].exists(_.values.exists(_.name == WebSocketProtocolName))

  private[stir] def offeredProtocols(request: Request[IO]): Seq[String] =
    request.headers.get(SecWebSocketProtocol).toList.flatMap(_.toList)
      .flatMap(_.value.split(',').toList).map(_.trim).filter(_.nonEmpty)

  /**
   * The response http4s returns from `build` when the server does not perform the handshake itself; with the
   * upgrade already verified it only ever reaches the in-memory testkit, which checks for a 101.
   */
  private def switchingProtocols(subprotocol: Option[String]): Response[IO] = {
    val upgrade = Response[IO](Status.SwitchingProtocols)
      .putHeaders(Upgrade(Protocol(WebSocketProtocolName, None)), Connection(ci"Upgrade"))
    subprotocol.fold(upgrade)(p => upgrade.putHeaders(Header.Raw(SecWebSocketProtocol, p)))
  }
}
