---
id: directives-websocket
title: WebSocket Directives
sidebar_position: 21
---

# WebSocket Directives

WebSocket directives handle WebSocket upgrade requests within stir routes. A request that is not a WebSocket upgrade (no `Connection: Upgrade` / `Upgrade: websocket` headers) is rejected with an `ExpectedWebSocketRequestRejection` (400 when sealed), so a plain GET on the same path can be served by the next route alternative.

## handleWebSocketMessages

Handles WebSocket requests by upgrading the connection. Two overloads are available.

### Pipe-based handler

Accepts a `Pipe[IO, WebSocketFrame, WebSocketFrame]` that transforms incoming frames into outgoing frames. This is suitable for echo-like or request-response WebSocket interactions.

```scala
import fs2.Pipe
import org.http4s.websocket.WebSocketFrame

val echo: Pipe[IO, WebSocketFrame, WebSocketFrame] = identity

val route: Route = path("ws-echo") {
  handleWebSocketMessages(ws, echo)
}
```

### Separate send and receive

Accepts a `send: Stream[IO, WebSocketFrame]` for outgoing messages and a `receive: Pipe[IO, WebSocketFrame, Unit]` for processing incoming messages. This is suitable for scenarios where sending and receiving are independent.

```scala
import fs2.{ Pipe, Stream }
import org.http4s.websocket.WebSocketFrame
import scala.concurrent.duration._

val send: Stream[IO, WebSocketFrame] =
  Stream.awakeEvery[IO](1.second).map(_ => WebSocketFrame.Text("Hello"))

val receive: Pipe[IO, WebSocketFrame, Unit] =
  _.evalMap(frame => IO.println(s"Received: $frame"))

val route: Route = path("ws") {
  handleWebSocketMessages(ws, send, receive)
}
```

## extractOfferedWsProtocols

Extracts the subprotocols the client offered in `Sec-WebSocket-Protocol` as a `Seq[String]` (empty when the
header is absent). Rejects non-upgrade requests with an `ExpectedWebSocketRequestRejection`.

## handleWebSocketMessagesForProtocol

Like the pipe-based `handleWebSocketMessages`, but only accepts the upgrade when the client offered the given
subprotocol (compared case-insensitively); the handshake response then names it. Otherwise the request is
rejected with an `UnsupportedWebSocketSubprotocolRejection`, which the default handler turns into a 400 whose
`Sec-WebSocket-Protocol` header lists every protocol the rejecting routes support. Chain several of these to
support several subprotocols:

```scala
val route: Route = path("ws") {
  handleWebSocketMessagesForProtocol(ws, jsonHandler, "json") ~
  handleWebSocketMessagesForProtocol(ws, binaryHandler, "binary")
}
```

## handleWebSocketMessagesForOptionalProtocol

`handleWebSocketMessagesForOptionalProtocol(ws, handler, Some("json"))` behaves like
`handleWebSocketMessagesForProtocol`; with `None` any upgrade request is accepted.

## Testing

In the in-memory testkit (`~>`) an accepted upgrade completes with `101 Switching Protocols` and, for the protocol
variants, the selected `Sec-WebSocket-Protocol` header (`isWebSocketUpgrade` and
`expectWebSocketUpgradeWithProtocol` check them); the real handshake and the frames are handled by the
server and are not exercised. That placeholder response is what http4s hands to the server as the carrier of the
WebSocket context; ember replaces it with its own handshake. The directives are verified with ember only. Send `Connection: Upgrade`, `Upgrade: websocket`, `Sec-WebSocket-Version: 13` and a
`Sec-WebSocket-Key` header to simulate a client.

## Integration with http4s

Both overloads of `handleWebSocketMessages` require a `WebSocketBuilder2[IO]` instance. This is provided by the http4s server builder via `withHttpWebSocketApp`. The `WebSocketBuilder2[IO]` must be threaded through to your route definition.

```scala
import cats.effect._
import com.comcast.ip4s._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.websocket.WebSocketBuilder2
import pl.iterators.stir.server.Directives._
import pl.iterators.stir.server.Route

def routes(ws: WebSocketBuilder2[IO]): Route =
  path("ws-echo") {
    val echo: fs2.Pipe[IO, org.http4s.websocket.WebSocketFrame, org.http4s.websocket.WebSocketFrame] = identity
    handleWebSocketMessages(ws, echo)
  }

val server = EmberServerBuilder
  .default[IO]
  .withHost(ipv4"0.0.0.0")
  .withPort(port"8080")
  .withHttpWebSocketApp(ws => routes(ws).toHttpRoutes.orNotFound)
  .build
```

The key integration point is `withHttpWebSocketApp`, which provides the `WebSocketBuilder2[IO]` that must be passed to `handleWebSocketMessages`. Without it, WebSocket upgrades cannot be performed.
