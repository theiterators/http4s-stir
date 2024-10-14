package pl.iterators.stir.testkit

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import fs2.Stream
import org.http4s.{ EntityBody, Response }
import pl.iterators.stir.server.{ Rejection, RejectionHandler, RouteResult }

trait RouteTestResultComponent {

  def failTest(msg: String): Nothing

  /**
   * A receptacle for the response or rejections created by a route.
   */
  class RouteTestResult() {
    private[this] var result: Option[Either[Seq[Rejection], Response[IO]]] = None
//    private[this] val latch = new CountDownLatch(1)

    def handled: Boolean = synchronized { result.isDefined && result.get.isRight }

    def rejections: Seq[Rejection] = synchronized {
      result match {
        case Some(Left(rejections)) => rejections
        case Some(Right(response))  => failTest("Request was not rejected, response was " + response)
        case None                   => failNeitherCompletedNorRejected()
      }
    }

    def response(implicit runtime: IORuntime): Response[IO] = rawResponse.withEntity(entity)

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
      rawResponse.body.compile.toVector.map { bytes =>
        Stream.emits(bytes): Stream[IO, Byte]
      }.unsafeRunSync()

    private def failNeitherCompletedNorRejected(): Nothing =
      failTest("Request was neither completed nor rejected" /*within " + timeout */ )
  }
}
