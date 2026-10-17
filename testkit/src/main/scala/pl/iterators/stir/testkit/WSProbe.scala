package pl.iterators.stir.testkit

import cats.effect.{ Fiber, IO, Outcome, Ref, SyncIO }
import cats.effect.std.Queue
import cats.effect.unsafe.IORuntime
import fs2.Stream
import org.http4s.websocket.WebSocketFrame
import org.typelevel.vault.Key
import pl.iterators.stir.server.directives.WebSocketDirectives.WebSocketHandler
import scodec.bits.ByteVector

import java.util.concurrent.{ CancellationException, TimeoutException }
import scala.concurrent.duration._

/**
 * A client-side probe for WebSocket handlers in the in-memory testkit, as Pekko's `WSProbe`: build the upgrade request
 * with `WS(uri, probe, subprotocols*)`, run it through the route with `~>`, and inside `check` send frames to the
 * handler and assert on the frames it sends back. The handler runs as the directive was given it: the ping/pong
 * filtering and the defragmentation the server performs are not applied.
 *
 * The frames the handler sends are buffered, so an expectation may be made at any time; `expectMessage` and
 * `expectCompletion` wait for the route test timeout in scope when the probe was created, `expectNoMessage` for
 * 100 ms by default. A failed expectation throws an `AssertionError`, as Pekko's probe does; a handler that fails
 * makes every later expectation fail with its error, and once it completed every later `expectMessage` fails. The
 * handler runs in the background until it completes or the suite's `cleanUp()` cancels it.
 */
final class WSProbe private (
    toHandler: Queue[IO, Option[WebSocketFrame]],
    fromHandler: Queue[IO, WSProbe.Event],
    clientCompleted: Ref[IO, Boolean],
    running: Ref[IO, Option[Fiber[IO, Throwable, Unit]]],
    timeout: FiniteDuration)(implicit runtime: IORuntime) {
  import WSProbe.{ Completed, Event, Failed, Received }

  /** Sends the given frame to the handler. */
  def sendMessage(frame: WebSocketFrame): Unit =
    clientCompleted.get.flatMap { completed =>
      if (completed) IO.raiseError(new AssertionError(s"Cannot send $frame after sendCompletion()"))
      else toHandler.offer(Some(frame))
    }.unsafeRunSync()

  /** Sends a text frame to the handler. */
  def sendMessage(text: String): Unit = sendMessage(WebSocketFrame.Text(text))

  /** Sends a binary frame to the handler. */
  def sendMessage(bytes: ByteVector): Unit = sendMessage(WebSocketFrame.Binary(bytes))

  /** Completes the stream of frames the handler receives. */
  def sendCompletion(): Unit = (clientCompleted.set(true) *> toHandler.offer(None)).unsafeRunSync()

  /** Expects a frame from the handler within the route test timeout and returns it. */
  def expectMessage(): WebSocketFrame = next(timeout) match {
    case Right(Received(frame)) => frame
    case Right(Completed)       => fail("Expected a message but the handler completed")
    case Right(Failed(error))   => fail("Expected a message but the handler failed: " + error, error)
    case Left(error)            => fail(s"Expected a message but none arrived within $timeout", error)
  }

  /** Expects a text frame (reassembled when fragmented) with the given payload. */
  def expectMessage(text: String): Unit = expectMessage() match {
    case first: WebSocketFrame.Text =>
      val collected = new String(continuation(first.data, first.last).toArray, "UTF-8")
      if (collected != text) fail(s"""Expected Text("$text") but got Text("$collected")""")
    case other => fail(s"""Expected Text("$text") but got $other""")
  }

  /** Expects a binary frame (reassembled when fragmented) with the given payload. */
  def expectMessage(bytes: ByteVector): Unit = expectMessage() match {
    case first: WebSocketFrame.Binary =>
      val collected = continuation(first.data, first.last)
      if (collected != bytes) fail(s"Expected Binary($bytes) but got Binary($collected)")
    case other => fail(s"Expected Binary($bytes) but got $other")
  }

  /** Expects that the handler sends nothing, completes nothing and fails nothing for 100 ms. */
  def expectNoMessage(): Unit = expectNoMessage(100.millis)

  /** Expects that the handler sends nothing, completes nothing and fails nothing for the given time. */
  def expectNoMessage(max: FiniteDuration): Unit = next(max) match {
    case Right(Received(frame)) => fail(s"Expected no message but got $frame")
    case Right(Completed)       => fail("Expected no message but the handler completed")
    case Right(Failed(error))   => fail("Expected no message but the handler failed: " + error, error)
    case Left(_)                => ()
  }

  /** Expects the handler to complete its stream of frames, without failing, within the route test timeout. */
  def expectCompletion(): Unit = next(timeout) match {
    case Right(Completed)       => ()
    case Right(Received(frame)) => fail(s"Expected completion but got $frame")
    case Right(Failed(error))   => fail("Expected completion but the handler failed: " + error, error)
    case Left(error)            => fail(s"Expected completion but the handler is still running after $timeout", error)
  }

  /**
   * Runs the handler against the probe's queues in the background; called by the testkit when the route upgrades.
   * The handler "completes" when its outgoing frames end (the pipe's output, or the `send` stream); a failure of
   * either side fails it; a cancellation by `cleanUp()` is reported as a failure too.
   */
  private[testkit] def connect(handler: WebSocketHandler): IO[Unit] = {
    val incoming = Stream.fromQueueNoneTerminated(toHandler)
    def forward(frames: Stream[IO, WebSocketFrame]): IO[Unit] =
      frames.evalMap(frame => fromHandler.offer(Received(frame))).compile.drain *> fromHandler.offer(Completed)
    val run = handler match {
      case WebSocketHandler.Combined(pipe)          => forward(incoming.through(pipe))
      case WebSocketHandler.Separate(send, receive) => forward(send).both(incoming.through(receive).compile.drain).void
    }
    val reported = run.guaranteeCase {
      case Outcome.Errored(error) => fromHandler.offer(Failed(error))
      case Outcome.Canceled()     => fromHandler.offer(Failed(new CancellationException("The handler was cancelled")))
      case Outcome.Succeeded(_)   => IO.unit
    }
    reported.start.flatMap(fiber => running.set(Some(fiber)))
  }

  /** Cancels the handler if it is still running; called by the suite's `cleanUp()`. */
  private[testkit] def close(): IO[Unit] = running.getAndSet(None).flatMap(_.fold(IO.unit)(_.cancel))

  /** The next event, within the given time; a terminal event is put back so that it stays visible. */
  private def next(within: FiniteDuration): Either[TimeoutException, Event] =
    fromHandler.take.timeout(within).attempt.flatMap {
      case Right(event @ Received(_))       => IO.pure(Right(event))
      case Right(terminal)                  => fromHandler.offer(terminal).as(Right(terminal))
      case Left(timedOut: TimeoutException) => IO.pure(Left(timedOut))
      case Left(error)                      => IO.raiseError(error)
    }.unsafeRunSync()

  /** Collects the continuation frames of a fragmented message. */
  private def continuation(data: ByteVector, last: Boolean): ByteVector =
    if (last) data
    else expectMessage() match {
      case WebSocketFrame.Continuation(more, isLast) => continuation(data ++ more, isLast)
      case other                                     => fail(s"Expected a continuation frame but got $other")
    }

  private def fail(message: String, cause: Throwable = null): Nothing =
    if (cause == null) throw new AssertionError(message) else throw new AssertionError(message, cause)
}

object WSProbe {

  /** Creates a probe; `expectMessage` and `expectCompletion` wait for the route test timeout in scope here. */
  def apply()(implicit timeout: RouteTestTimeout, runtime: IORuntime): WSProbe =
    (for {
      toHandler <- Queue.unbounded[IO, Option[WebSocketFrame]]
      fromHandler <- Queue.unbounded[IO, Event]
      clientCompleted <- Ref.of[IO, Boolean](false)
      running <- Ref.of[IO, Option[Fiber[IO, Throwable, Unit]]](None)
    } yield new WSProbe(toHandler, fromHandler, clientCompleted, running, timeout.duration)).unsafeRunSync()

  /** Set on the request by `WS(...)`; the testkit connects the probe to the handler the route upgrades with. */
  private[testkit] val RequestKey: Key[WSProbe] = Key.newKey[SyncIO, WSProbe].unsafeRunSync()

  /** What the handler side produced: a frame, or one of the two terminal events. */
  private sealed trait Event
  private final case class Received(frame: WebSocketFrame) extends Event
  private case object Completed extends Event
  private final case class Failed(error: Throwable) extends Event
}
