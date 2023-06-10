package pl.iterators.stir.server.directives

import cats.effect.IO
import org.http4s.server.middleware.Logger
import org.http4s.{ Headers, Request, Response }
import org.typelevel.ci.CIString
import org.typelevel.log4cats
import pl.iterators.stir.server.{ Directive0, RouteResult }

trait DebuggingDirectives {

  import BasicDirectives._
  import IODirectives._

  /**
   * Produces a log entry for every incoming request.
   *
   * @group debugging
   */
  def logRequest(logHeaders: Boolean = true, logBody: Boolean = true,
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => IO[Unit]] = None): Directive0 = {
    val log = logAction.getOrElse { (s: String) =>
      DebuggingDirectives.logger.info(s)
    }
    extractRequest.flatMap { request =>
      onComplete(Logger.logMessage[IO, Request[IO]](request)(logHeaders, logBody, redactHeadersWhen)(log)).flatMap(_ =>
        pass)
    }
  }

  /**
   * Produces a log entry for every [[RouteResult]].
   *
   * @group debugging
   */
  def logResult(logHeaders: Boolean = true, logBody: Boolean = true,
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => IO[Unit]] = None): Directive0 = {
    val log = logAction.getOrElse { (s: String) =>
      DebuggingDirectives.logger.info(s)
    }
    mapInnerRoute(_.andThen(_.flatTap {
      case RouteResult.Complete(response) =>
        Logger.logMessage[IO, Response[IO]](response)(logHeaders, logBody, redactHeadersWhen)(log)
      case RouteResult.Rejected(rejections) =>
        log(s"Request was rejected with rejections: ${rejections.mkString(", ")}")
    }))
  }

  /**
   * Produces a log entry for every incoming request and [[RouteResult]].
   *
   * @group debugging
   */
  def logRequestResult(logHeaders: Boolean = true, logBody: Boolean = true,
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => IO[Unit]] = None): Directive0 = {
    val log = logAction.getOrElse { (s: String) =>
      DebuggingDirectives.logger.info(s)
    }
    extractRequest.flatMap { request =>
      onComplete(Logger.logMessage[IO, Request[IO]](request)(logHeaders, logBody, redactHeadersWhen)(log)).flatMap {
        _ =>
          mapInnerRoute(_.andThen(_.flatTap {
            case RouteResult.Complete(response) =>
              Logger.logMessage[IO, Response[IO]](response)(logHeaders, logBody, redactHeadersWhen)(log)
            case RouteResult.Rejected(rejections) =>
              log(s"Request was rejected with rejections: ${rejections.mkString(", ")}")
          }))
      }
    }
  }
}

object DebuggingDirectives extends DebuggingDirectives {
  private val logger = log4cats.slf4j.loggerFactoryforSync[IO].getLogger
}
