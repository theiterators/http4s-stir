package pl.iterators.stir.server.directives

import cats.effect.IO
import fs2.{ Pipe, Stream }
import org.http4s.{ Header, Headers, Request, Response, Status }
import org.http4s.headers.{ Connection, Upgrade }
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame
import org.typelevel.ci._
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
      ws.withOnNonWebSocketRequest(IO.pure(switchingProtocols(None))).build(send, receive).map(RouteResult.Complete(_))
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
              .build(handler).map(RouteResult.Complete(_))
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
  private def switchingProtocols(subprotocol: Option[String]): Response[IO] =
    subprotocol.fold(Response[IO](Status.SwitchingProtocols))(p =>
      Response[IO](Status.SwitchingProtocols).putHeaders(Header.Raw(SecWebSocketProtocol, p)))
}
