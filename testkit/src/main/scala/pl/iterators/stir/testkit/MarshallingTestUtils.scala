package pl.iterators.stir.testkit

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.http4s.{ EntityBody, EntityDecoder, Request, Response }
import pl.iterators.stir.marshalling.ToResponseMarshaller

import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }

trait MarshallingTestUtils {

  /**
   * How long the marshalling helpers and the `evaluateTo` / `haveFailedWith` matchers wait for their `IO` before
   * failing (Pekko's `pekko.http.testkit.marshalling.timeout`, 1 second by default); override it for slower ones.
   */
  def marshallingTimeout: FiniteDuration = 1.second

  /** Runs `io` within [[marshallingTimeout]]; a `TimeoutException` naming the timeout when it elapses. */
  protected def runWithinMarshallingTimeout[A](io: IO[A])(implicit runtime: IORuntime): A =
    io.timeout(marshallingTimeout).unsafeRunSync()

  def marshal[T](t: T)(implicit m: ToResponseMarshaller[T], runtime: IORuntime): EntityBody[IO] =
    runWithinMarshallingTimeout(m.toResponse(t)).body

  def marshalToResponse[T](t: T)(implicit m: ToResponseMarshaller[T], runtime: IORuntime): Response[IO] = {
    runWithinMarshallingTimeout(m.toResponse(t))
  }

  def unmarshalValue[T](request: Request[IO])(implicit um: EntityDecoder[IO, T], runtime: IORuntime): T = {
    runWithinMarshallingTimeout(um.decode(request, strict = false).value).fold(throw _, identity)
  }

  def unmarshal[T](request: Request[IO])(implicit um: EntityDecoder[IO, T], runtime: IORuntime): Try[T] = {
    runWithinMarshallingTimeout(um.decode(request, strict = false).value).fold(Failure(_), Success(_))
  }
}
