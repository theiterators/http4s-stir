package pl.iterators.stir.server.directives

import cats.effect.IO
import cats.effect.std.Console
import fs2.{ Pull, Stream }
import org.http4s.server.middleware.Logger
import org.http4s.{ Headers, Request, Response }
import org.typelevel.ci.CIString
import pl.iterators.stir.server.{ Directive, Directive0, RouteResult }

trait DebuggingDirectives {

  /**
   * Produces a log entry for every incoming request.
   *
   * @group debugging
   */
  def logRequest(logHeaders: Boolean = true, logBody: Boolean = true,
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      maxBodyBytes: Int = DebuggingDirectives.DefaultLogLength,
      logAction: Option[String => IO[Unit]] = None): Directive0 = {
    Directive { inner => ctx =>
      val log = logAction.getOrElse { (s: String) =>
        DebuggingDirectives.logger(s)
      }
      val logWithTrimmingIndicator = indicateTrimming(maxBodyBytes, ctx.request.contentLength).andThen(log)
      val logWithBodyNotConsumedIndicator = indicateBodyNotConsumed(ctx.request.contentLength).andThen(log)

      if (logBody && !ctx.request.isChunked && ctx.request.contentLength.exists(_ > 0)) {
        IO.ref(false).flatMap { bodyConsumedRef =>
          val newBody = ctx.request.body.pull.unconsN(maxBodyBytes, allowFewer = true).flatMap {
            case Some((head, tail)) =>
              Pull.output(head) >>
              Pull.eval {
                bodyConsumedRef.update(_ => true) *> Logger.logMessage[IO, Request[IO]](
                  ctx.request.withBodyStream(Stream.chunk(head)))(logHeaders,
                  logBody = true, redactHeadersWhen)(logWithTrimmingIndicator)
              } >>
              tail.pull.echo
            case None =>
              Pull.eval {
                bodyConsumedRef.update(_ => true) *> Logger.logMessage[IO, Request[IO]](ctx.request)(logHeaders,
                  logBody = false, redactHeadersWhen)(log)
              }
          }.stream
          val newRequest = ctx.request.withBodyStream(newBody)
          inner(())(ctx.copy(request = newRequest)).flatTap {
            _ =>
              bodyConsumedRef.get.flatMap {
                bodyConsumed =>
                  if (!bodyConsumed) {
                    Logger.logMessage[IO, Request[IO]](newRequest)(logHeaders, logBody = false, redactHeadersWhen)(
                      logWithBodyNotConsumedIndicator)
                  } else {
                    IO.unit
                  }
              }
          }
        }
      } else {
        Logger.logMessage[IO, Request[IO]](ctx.request)(logHeaders, logBody = false, redactHeadersWhen)(log).flatMap(
          _ =>
            inner(())(ctx))
      }
    }
  }

  /**
   * Produces a log entry for every [[RouteResult]].
   *
   * @group debugging
   */
  def logResult(logHeaders: Boolean = true, logBody: Boolean = true,
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      maxBodyBytes: Int = DebuggingDirectives.DefaultLogLength,
      logAction: Option[String => IO[Unit]] = None): Directive0 = {
    Directive { inner => ctx =>
      val log = logAction.getOrElse { (s: String) =>
        DebuggingDirectives.logger(s)
      }
      inner(())(ctx).flatMap {
        case RouteResult.Complete(response) =>
          val logWithTrimmingIndicator = indicateTrimming(maxBodyBytes, response.contentLength).andThen(log)
          if (logBody && !response.isChunked) {
            val newBody = response.body.pull.unconsN(maxBodyBytes, allowFewer = true).flatMap {
              case Some((head, tail)) =>
                Pull.output(head) >>
                Pull.eval {
                  Logger.logMessage[IO, Response[IO]](response.withBodyStream(Stream.chunk(head)))(logHeaders,
                    logBody = true, redactHeadersWhen)(logWithTrimmingIndicator)
                } >>
                tail.pull.echo
              case None => Pull.eval {
                  Logger.logMessage[IO, Response[IO]](response)(logHeaders, logBody = false, redactHeadersWhen)(log)
                }
            }.stream
            IO.pure(RouteResult.Complete(response.copy(body = newBody)))
          } else {
            Logger.logMessage[IO, Response[IO]](response)(logHeaders, logBody = false, redactHeadersWhen)(log).as(
              RouteResult.Complete(response))
          }
        case RouteResult.Rejected(rejections) =>
          log(s"Request was rejected with rejections: ${rejections.mkString(", ")}").as(
            RouteResult.Rejected(rejections))
      }
    }
  }

  /**
   * Produces a log entry for every incoming request and [[RouteResult]].
   *
   * @group debugging
   */
  def logRequestResult(logHeaders: Boolean = true, logBody: Boolean = true,
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      maxBodyBytes: Int = DebuggingDirectives.DefaultLogLength,
      logAction: Option[String => IO[Unit]] = None): Directive0 = {
    logResult(logHeaders, logBody, redactHeadersWhen, maxBodyBytes, logAction) & logRequest(logHeaders, logBody,
      redactHeadersWhen,
      maxBodyBytes,
      logAction)
  }

  private def indicateTrimming(maxBodyBytes: Int, contentLength: Option[Long]): String => String = { log =>
    contentLength match {
      case Some(length) if length > maxBodyBytes =>
        s"$log ... ($length bytes total)"
      case None =>
        s"$log ... (??? bytes total)"
      case _ =>
        log
    }
  }

  private def indicateBodyNotConsumed(contentLength: Option[Long]): String => String = { log =>
    contentLength match {
      case Some(length) =>
        s"$log body=<not consumed> ($length bytes total)"
      case None =>
        s"$log body=<not consumed> (??? bytes total)"
    }
  }
}

object DebuggingDirectives extends DebuggingDirectives {
  private def logger[A](a: A) = Console[IO].println(a)
  private val DefaultLogLength: Int = 4096
}
