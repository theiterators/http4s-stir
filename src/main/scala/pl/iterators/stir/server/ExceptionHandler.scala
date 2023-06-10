package pl.iterators.stir.server

import cats.effect.IO
import org.http4s.Status.InternalServerError
import org.typelevel.log4cats

import scala.util.control.NonFatal

trait ExceptionHandler extends ExceptionHandler.PF {

  /**
   * Creates a new [[ExceptionHandler]] which uses the given one as fallback for this one.
   */
  def withFallback(that: ExceptionHandler): ExceptionHandler

  /**
   * "Seals" this handler by attaching a default handler as fallback if necessary.
   */
  def seal(logAction: Option[(Throwable, String) => IO[Unit]] = None): ExceptionHandler
}

object ExceptionHandler {
  type PF = PartialFunction[Throwable, Route]
  private[stir] def errorMessageTemplate(param1: String, param2: String): String = {
    s"Error during processing of request: '$param1'. Completing with $param2 response. " +
    "To change default exception handling behavior, provide a custom ExceptionHandler."
  }

  implicit def apply(pf: PF): ExceptionHandler = apply(knownToBeSealed = false)(pf)

  private def apply(knownToBeSealed: Boolean)(pf: PF): ExceptionHandler =
    new ExceptionHandler {
      def isDefinedAt(error: Throwable) = pf.isDefinedAt(error)
      def apply(error: Throwable) = pf(error)
      def withFallback(that: ExceptionHandler): ExceptionHandler =
        if (!knownToBeSealed) ExceptionHandler(knownToBeSealed = false)(this.orElse(that)) else this
      def seal(logAction: Option[(Throwable, String) => IO[Unit]] = None): ExceptionHandler =
        if (!knownToBeSealed) ExceptionHandler(knownToBeSealed = true)(this.orElse(default(logAction))) else this
    }

  /**
   * Default [[ExceptionHandler]] that discards the request's entity by default.
   */
  def default(logAction: Option[(Throwable, String) => IO[Unit]] = None): ExceptionHandler = {
    val log = logAction.getOrElse { (t: Throwable, s: String) =>
      log4cats.slf4j.loggerFactoryforSync[IO].getLogger.error(t)(s)
    }
    apply(knownToBeSealed = true) {
      case NonFatal(e) => ctx => {
          val message = Option(e.getMessage).getOrElse(s"${e.getClass.getName} (No error message supplied)")
          log(e, errorMessageTemplate(message, InternalServerError.toString())) *>
          ctx.complete(InternalServerError)
        }
    }
  }

  /**
   * Creates a sealed ExceptionHandler from the given one. Returns the default handler if the given one
   * is `null`.
   */
  def seal(handler: ExceptionHandler)(logAction: Option[(Throwable, String) => IO[Unit]] = None): ExceptionHandler =
    if (handler ne null) handler.seal() else ExceptionHandler.default(logAction)
}
