package pl.iterators.stir.testkit

import cats.effect.IO
import org.http4s.{ Header, Protocol, Request, Uri }
import org.http4s.headers.{ Connection, Upgrade }
import org.typelevel.ci._

/** Pekko's `WS(uri, clientSideHandler, subprotocols)` for the in-memory testkit. */
trait WSTestRequestBuilding {

  /**
   * A `GET` WebSocket upgrade request (`Connection: Upgrade`, `Upgrade: websocket`, `Sec-WebSocket-Version: 13`, a
   * `Sec-WebSocket-Key`, and `Sec-WebSocket-Protocol` when subprotocols are offered) whose handler, once the route
   * upgrades, is driven by the given probe.
   */
  def WS(uri: String, probe: WSProbe, subprotocols: String*): Request[IO] =
    WS(Uri.unsafeFromString(uri), probe, subprotocols: _*)

  def WS(uri: Uri, probe: WSProbe, subprotocols: String*): Request[IO] = {
    val upgrade = Request[IO](uri = uri).putHeaders(
      Connection(ci"Upgrade"),
      Upgrade(Protocol(ci"websocket", None)),
      Header.Raw(ci"Sec-WebSocket-Version", "13"),
      Header.Raw(ci"Sec-WebSocket-Key", "dGhlIHNhbXBsZSBub25jZQ=="))
    val offering =
      if (subprotocols.isEmpty) upgrade
      else upgrade.putHeaders(Header.Raw(ci"Sec-WebSocket-Protocol", subprotocols.mkString(", ")))
    offering.withAttribute(WSProbe.RequestKey, probe)
  }
}

object WSTestRequestBuilding extends WSTestRequestBuilding
