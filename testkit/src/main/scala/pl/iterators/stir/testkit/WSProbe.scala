package pl.iterators.stir.testkit

import cats.effect.{ IO, Ref, SyncIO }
import cats.effect.std.Queue
import cats.effect.unsafe.IORuntime
import fs2.Stream
import org.http4s.websocket.WebSocketFrame
import org.typelevel.vault.Key
import pl.iterators.stir.server.directives.WebSocketDirectives.WebSocketHandler
import scodec.bits.ByteVector

import java.util.concurrent.TimeoutException
import scala.concurrent.duration._

/**
 * A client-side probe for WebSocket handlers in the in-memory testkit, as Pekko's `WSProbe`: build the upgrade request
 * with `WS(uri, probe, subprotocols*)`, run it through the route with `~>`, and inside `check` send frames to the
 * handler and assert on the frames it sends back. The handler runs as the directive was given it: the ping/pong
 * filtering and the defragmentation the server performs are not applied.
 *
 * The frames the handler sends are buffered, so an expectation may be made at any time; `expectMessage` waits for
 * the route test timeout and `expectNoMessage` for 100 ms by default. A failed expectation throws an
 * `AssertionError`, as Pekko's probe does.
 */
final class WSProbe private (
    toHandler: Queue[IO, Option[WebSocketFrame]],
    fromHandler: Queue[IO, Option[WebSocketFrame]],
    failure: Ref[IO, Option[Throwable]],
    timeout: FiniteDuration)(implicit runtime: IORuntime) {

  /** Sends the given frame to the handler. */
  def sendMessage(frame: WebSocketFrame): Unit = toHandler.offer(Some(frame)).unsafeRunSync()

  /** Sends a text frame to the handler. */
  def sendMessage(text: String): Unit = sendMessage(WebSocketFrame.Text(text))

  /** Sends a binary frame to the handler. */
  def sendMessage(bytes: ByteVector): Unit = sendMessage(WebSocketFrame.Binary(bytes))

  /** Completes the stream of frames the handler receives. */
  def sendCompletion(): Unit = toHandler.offer(None).unsafeRunSync()

  /** Expects a frame from the handler within the route test timeout and returns it. */
  def expectMessage(): WebSocketFrame = next(timeout) match {
    case Right(Some(frame)) => frame
    case Right(None)        => fail("Expected a message but the handler completed")
    case Left(error)        => fail(s"Expected a message but none arrived within $timeout", error)
  }

  /** Expects a text frame (reassembled when fragmented) with the given payload. */
  def expectMessage(text: String): Unit = expectMessage() match {
    case first: WebSocketFrame.Text =>
      val collected = new String(continuation(first.data, first.last).toArray, "UTF-8")
      assertEqual(collected, text, s"""Expected Text("$text") but got Text("$collected")""")
    case other => fail(s"""Expected Text("$text") but got $other""")
  }

  /** Expects a binary frame (reassembled when fragmented) with the given payload. */
  def expectMessage(bytes: ByteVector): Unit = expectMessage() match {
    case first: WebSocketFrame.Binary =>
      val collected = continuation(first.data, first.last)
      assertEqual(collected, bytes, s"Expected Binary($bytes) but got Binary($collected)")
    case other => fail(s"Expected Binary($bytes) but got $other")
  }

  /** Expects that the handler sends nothing for 100 ms. */
  def expectNoMessage(): Unit = expectNoMessage(100.millis)

  /** Expects that the handler sends nothing for the given time. */
  def expectNoMessage(max: FiniteDuration): Unit = next(max) match {
    case Right(Some(frame)) => fail(s"Expected no message but got $frame")
    case Right(None)        => fail("Expected no message but the handler completed")
    case Left(_)            => ()
  }

  /** Expects the handler to complete its stream of frames within the route test timeout. */
  def expectCompletion(): Unit = next(timeout) match {
    case Right(None)        => ()
    case Right(Some(frame)) => fail(s"Expected completion but got $frame")
    case Left(error)        => fail(s"Expected completion but the handler is still running after $timeout", error)
  }

  /** Runs the handler against the probe's queues in the background; called by the testkit when the route upgrades. */
  private[testkit] def connect(handler: WebSocketHandler): IO[Unit] = {
    val incoming = Stream.fromQueueNoneTerminated(toHandler)
    val run = handler match {
      case WebSocketHandler.Combined(pipe) => incoming.through(pipe).enqueueNoneTerminated(fromHandler).compile.drain
      case WebSocketHandler.Separate(send, receive) =>
        send.enqueueNoneTerminated(fromHandler).compile.drain.both(incoming.through(receive).compile.drain).void
    }
    run.handleErrorWith(error => failure.set(Some(error)) *> fromHandler.offer(None)).start.void
  }

  private def next(within: FiniteDuration): Either[TimeoutException, Option[WebSocketFrame]] =
    fromHandler.take.timeout(within).attempt.unsafeRunSync() match {
      case Right(frame)                 => Right(frame)
      case Left(timedOut: TimeoutException) => Left(timedOut)
      case Left(error)                  => throw error
    }

  /** Collects the continuation frames of a fragmented message. */
  private def continuation(data: ByteVector, last: Boolean): ByteVector =
    if (last) data
    else expectMessage() match {
      case WebSocketFrame.Continuation(more, isLast) => continuation(data ++ more, isLast)
      case other                                     => fail(s"Expected a continuation frame but got $other")
    }

  private def assertEqual[A](actual: A, expected: A, message: => String): Unit =
    if (actual != expected) fail(message)

  /** Throws an `AssertionError`; when the handler failed, the expectation is reported with the handler's error. */
  private def fail(message: String, cause: Throwable = null): Nothing =
    failure.get.unsafeRunSync() match {
      case Some(error) =>
        throw new AssertionError(message.replaceAll(" but .*$", "") + s" but the handler failed: $error", error)
      case None => if (cause == null) throw new AssertionError(message) else throw new AssertionError(message, cause)
    }
}

object WSProbe {

  /** Creates a probe; `expectMessage` and `expectCompletion` wait for the route test timeout. */
  def apply()(implicit timeout: RouteTestTimeout, runtime: IORuntime): WSProbe =
    (for {
      toHandler <- Queue.unbounded[IO, Option[WebSocketFrame]]
      fromHandler <- Queue.unbounded[IO, Option[WebSocketFrame]]
      failure <- Ref.of[IO, Option[Throwable]](None)
    } yield new WSProbe(toHandler, fromHandler, failure, timeout.duration)).unsafeRunSync()

  /** Set on the request by `WS(...)`; the testkit connects the probe to the handler the route upgrades with. */
  private[testkit] val RequestKey: Key[WSProbe] = Key.newKey[SyncIO, WSProbe].unsafeRunSync()
}
