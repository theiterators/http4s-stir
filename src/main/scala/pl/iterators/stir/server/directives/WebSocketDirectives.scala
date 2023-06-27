package pl.iterators.stir.server.directives

import cats.effect.IO
import fs2.{Stream, Pipe}
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame
import pl.iterators.stir.server.{Route, RouteResult}

/**
 * @groupname websocket WebSocket directives
 * @groupprio websocket 230
 */
trait WebSocketDirectives {
//  /**
//   * Extract the WebSocketUpgrade attribute if this is a WebSocket request.
//   * Rejects with an [[ExpectedWebSocketRequestRejection]], otherwise.
//   *
//   * @group websocket
//   */
//  def extractWebSocketUpgrade: Directive1[WebSocketUpgrade] =
//    optionalAttribute(webSocketUpgrade).flatMap {
//      case Some(upgrade) => provide(upgrade)
//      case None          => reject(ExpectedWebSocketRequestRejection)
//    }
//
//  /**
//   * Extract the list of WebSocket subprotocols as offered by the client in the [[Sec-WebSocket-Protocol]] header if
//   * this is a WebSocket request. Rejects with an [[ExpectedWebSocketRequestRejection]], otherwise.
//   *
//   * @group websocket
//   */
//  def extractOfferedWsProtocols: Directive1[immutable.Seq[String]] =
//    extractWebSocketUpgrade.map(_.requestedProtocols)
//
  /**
   * Handles WebSocket requests with the given handler and responses to other requests with an
   * [[NotImplemented]] status code.
   *
   * @group websocket
   */
  def handleWebSocketMessages(ws: WebSocketBuilder2[IO], handler: Pipe[IO, WebSocketFrame, WebSocketFrame]): Route =
    _ => ws.build(handler).map(RouteResult.Complete)

  /**
   * Handles WebSocket requests with the given handlers and responses to other requests with an
   * [[NotImplemented]] status code.
   *
   * @group websocket
   */
  def handleWebSocketMessages(ws: WebSocketBuilder2[IO], send: Stream[IO, WebSocketFrame], receive: Pipe[IO, WebSocketFrame, Unit]): Route =
    _ => ws.build(send, receive).map(RouteResult.Complete)

//  /**
//   * Handles WebSocket requests with the given handler if the given subprotocol is offered in the request and
//   * rejects other requests with an [[ExpectedWebSocketRequestRejection]] or an [[UnsupportedWebSocketSubprotocolRejection]].
//   *
//   * @group websocket
//   */
//  def handleWebSocketMessagesForProtocol(handler: Flow[Message, Message, Any], subprotocol: String): Route =
//    handleWebSocketMessagesForOptionalProtocol(handler, Some(subprotocol))
//
//  /**
//   * Handles WebSocket requests with the given handler and rejects other requests with an
//   * [[ExpectedWebSocketRequestRejection]].
//   *
//   * If the `subprotocol` parameter is None any WebSocket request is accepted. If the `subprotocol` parameter is
//   * `Some(protocol)` a WebSocket request is only accepted if the list of subprotocols supported by the client (as
//   * announced in the WebSocket request) contains `protocol`. If the client did not offer the protocol in question
//   * the request is rejected with an [[UnsupportedWebSocketSubprotocolRejection]] rejection.
//   *
//   * To support several subprotocols you may chain several `handleWebSocketMessagesForOptionalProtocol` routes.
//   *
//   * @group websocket
//   */
//  def handleWebSocketMessagesForOptionalProtocol(handler: Flow[Message, Message, Any], subprotocol: Option[String]): Route =
//    extractWebSocketUpgrade { upgrade =>
//      if (subprotocol.forall(sub => upgrade.requestedProtocols.exists(_ equalsIgnoreCase sub)))
//        complete(upgrade.handleMessages(handler, subprotocol))
//      else
//        reject(UnsupportedWebSocketSubprotocolRejection(subprotocol.get)) // None.forall == true
//    }
}