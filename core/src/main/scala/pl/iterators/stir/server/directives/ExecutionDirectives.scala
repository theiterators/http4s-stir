package pl.iterators.stir.server.directives

import cats.effect.IO
import pl.iterators.stir.server.{ Directive, Directive0, ExceptionHandler, Rejection, RejectionHandler, RouteResult }

import scala.util.control.NonFatal

/**
 * @groupname execution Execution directives
 * @groupprio execution 60
 */
trait ExecutionDirectives {
  import BasicDirectives._

  /**
   * Transforms exceptions thrown during evaluation of its inner route using the given
   * [[pl.iterators.stir.server.ExceptionHandler]].
   *
   * @group execution
   */
  def handleExceptions(handler: ExceptionHandler): Directive0 =
    Directive { innerRouteBuilder => ctx =>
      def handleException: PartialFunction[Throwable, IO[RouteResult]] =
        handler.andThen(_(ctx))
      try innerRouteBuilder(())(ctx).recoverWith(handleException)
      catch {
        case NonFatal(e) =>
          handleException.applyOrElse[Throwable, IO[RouteResult]](e, throw _)
      }
    }

  /**
   * Transforms rejections produced by its inner route using the given
   * [[akka.http.scaladsl.server.RejectionHandler]].
   *
   * @group execution
   */
  def handleRejections(handler: RejectionHandler): Directive0 =
    extractRequestContext.flatMap { ctx =>
      val maxIterations = 8
      // allow for up to `maxIterations` nested rejections from RejectionHandler before bailing out
      def handle(rejections: Seq[Rejection], originalRejections: Seq[Rejection], iterationsLeft: Int = maxIterations)
          : IO[RouteResult] =
        if (iterationsLeft > 0) {
          handler(rejections) match {
            case Some(route) => recoverRejectionsWith(handle(_, originalRejections, iterationsLeft - 1))(route)(ctx)
            case None        => IO.pure(RouteResult.Rejected(rejections))
          }
        } else
          sys.error(s"Rejection handler still produced new rejections after $maxIterations iterations. " +
            s"Is there an infinite handler cycle? Initial rejections: $originalRejections final rejections: $rejections")

      recoverRejectionsWith { rejections =>
        val transformed = RejectionHandler.applyTransformations(rejections)
        handle(transformed, transformed)
      }
    }
}

object ExecutionDirectives extends ExecutionDirectives
