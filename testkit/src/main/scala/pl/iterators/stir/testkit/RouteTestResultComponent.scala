package pl.iterators.stir.testkit

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import fs2.Stream
import org.http4s.{ EntityBody, Response }
import pl.iterators.stir.server.{ Rejection, RejectionHandler, RouteResult }

import scala.concurrent.TimeoutException

trait RouteTestResultComponent {

  def failTest(msg: String): Nothing

  /**
   * A receptacle for the response or rejections created by a route.
   */
  class RouteTestResult(timeout: RouteTestTimeout) {
    private[this] var result: Option[Either[Seq[Rejection], Response[IO]]] = None

    /** Runs `io` under the route test timeout, failing the test with `what` + " within <timeout>" when it elapses. */
    private[testkit] def awaitWithinTimeout[A](io: IO[A], what: String)(implicit runtime: IORuntime): A =
      failOnTimeout(io.timeout(timeout.duration), what)

    /** Runs `io`; a `TimeoutException` fails the test on the test thread (so the failure keeps its location). */
    private[testkit] def failOnTimeout[A](io: IO[A], what: String)(implicit runtime: IORuntime): A =
      io.attempt.unsafeRunSync() match {
        case Right(value)              => value
        case Left(_: TimeoutException) => failTest(what + " within " + timeout.duration)
        case Left(error)               => throw error
      }

    def handled: Boolean = synchronized { result.isDefined && result.get.isRight }

    def rejections: Seq[Rejection] = synchronized {
      result match {
        case Some(Left(rejections)) => rejections
        case Some(Right(response))  => failTest("Request was not rejected, response was " + response)
        case None                   => failNeitherCompletedNorRejected()
      }
    }

    def response(implicit runtime: IORuntime): Response[IO] = rawResponse.withBodyStream(entity)

    /** Returns a "fresh" entity with a "fresh" unconsumed byte- or chunk stream (if not strict) */
    def entity(implicit runtime: IORuntime): EntityBody[IO] = entityRecreator(runtime)

//    def chunks: Seq[ChunkStreamPart] =
//      entity match {
//        case HttpEntity.Chunked(_, chunks) => awaitAllElements[ChunkStreamPart](chunks)
//        case _                             => Nil
//      }
//
//    def chunksStream: Source[ChunkStreamPart, Any] =
//      rawResponse.entity match {
//        case HttpEntity.Chunked(_, data) => data
//        case _                           => Source.empty
//      }

    def ~>[T](f: RouteTestResult => T): T = f(this)

    private[testkit] def rawResponse: Response[IO] = synchronized {
      result match {
        case Some(Right(response))        => response
        case Some(Left(Nil))              => failTest("Request was rejected")
        case Some(Left(rejection :: Nil)) => failTest("Request was rejected with rejection " + rejection)
        case Some(Left(rejections))       => failTest("Request was rejected with rejections " + rejections)
        case None                         => failNeitherCompletedNorRejected()
      }
    }

    private[testkit] def handleResult(rr: RouteResult): Unit =
      synchronized {
        if (result.isEmpty) {
          result = rr match {
            case RouteResult.Complete(response)   => Some(Right(response))
            case RouteResult.Rejected(rejections) => Some(Left(RejectionHandler.applyTransformations(rejections)))
          }
//          latch.countDown()
        } else failTest("Route completed/rejected more than once")
      }

    private[testkit] def handleResponse(r: Response[IO]): Unit =
      synchronized {
        if (result.isEmpty) {
          result = Some(Right(r))
//          latch.countDown()
        } else failTest("Route completed/rejected more than once")
      }

//    private[testkit] def awaitResult: this.type = scala.concurrent.blocking {
////      latch.await(timeout.toMillis, MILLISECONDS)
//      this
//    }

    private[this] lazy val entityRecreator: IORuntime => EntityBody[IO] = implicit runtime =>
      awaitWithinTimeout(rawResponse.body.compile.toVector.map(bytes => Stream.emits(bytes): Stream[IO, Byte]),
        "Response entity was not read")

    private def failNeitherCompletedNorRejected(): Nothing =
      failTest("Request was neither completed nor rejected within " + timeout.duration)
  }
}
