package pl.iterators.stir.server.directives

import cats.effect.IO
import cats.effect.std.Console
import fs2.Pull
import org.http4s.Headers
import org.typelevel.ci.CIString
import pl.iterators.stir.server.{ Directive, Directive0, RouteResult }

trait DebuggingDirectives {

  /**
   * Produces a log entry for every incoming request. Secrets are redacted according to `redaction`
   * (`LogRedaction.default` masks conventionally named values in headers, the URI, JSON and form bodies and
   * rejections; `LogRedaction.none` reproduces the 0.5.0 output, except that control characters in bodies and
   * rejection lines are escaped, a JSON content type with parameters is logged as text instead of hex, and a failing
   * `logAction` is swallowed).
   *
   * @group debugging
   */
  def logRequest(logHeaders: Boolean = true, logBody: Boolean = true,
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      maxBodyBytes: Int = DebuggingDirectives.DefaultLogLength,
      logAction: Option[String => IO[Unit]] = None,
      redaction: LogRedaction = LogRedaction.default): Directive0 = {
    Directive { inner => ctx =>
      val log = DebuggingDirectives.safeLog(logAction)
      val config = LogRendering.Config(logHeaders, redactHeadersWhen, redaction)
      val request = ctx.request
      def line = LogRendering.requestLine(request, config)

      if (logBody && !request.isChunked && request.contentLength.exists(_ > 0)) {
        IO.ref(false).flatMap { bodyConsumedRef =>
          val newBody = request.body.pull.unconsN(maxBodyBytes, allowFewer = true).flatMap {
            case Some((head, tail)) =>
              Pull.output(head) >>
              Pull.eval {
                bodyConsumedRef.set(true) *>
                LogRendering.renderBody(request, head, maxBodyBytes, config).flatMap(part => log(s"$line $part"))
              } >>
              tail.pull.echo
            case None =>
              Pull.eval(bodyConsumedRef.set(true) *> log(line))
          }.stream
          val newRequest = request.withBodyStream(newBody)
          inner(())(ctx.copy(request = newRequest)).flatTap { _ =>
            bodyConsumedRef.get.flatMap { bodyConsumed =>
              if (bodyConsumed) IO.unit
              else log(s"$line ${LogRendering.notConsumed(request.contentLength)}")
            }
          }
        }
      } else {
        log(line).flatMap(_ => inner(())(ctx))
      }
    }
  }

  /**
   * Produces a log entry for every [[RouteResult]]; see [[logRequest]] for `redaction`.
   *
   * @group debugging
   */
  def logResult(logHeaders: Boolean = true, logBody: Boolean = true,
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      maxBodyBytes: Int = DebuggingDirectives.DefaultLogLength,
      logAction: Option[String => IO[Unit]] = None,
      redaction: LogRedaction = LogRedaction.default): Directive0 = {
    Directive { inner => ctx =>
      val log = DebuggingDirectives.safeLog(logAction)
      val config = LogRendering.Config(logHeaders, redactHeadersWhen, redaction)
      inner(())(ctx).flatMap {
        case RouteResult.Complete(response) =>
          val line = LogRendering.responseLine(response, config)
          if (logBody && !response.isChunked) {
            val newBody = response.body.pull.unconsN(maxBodyBytes, allowFewer = true).flatMap {
              case Some((head, tail)) =>
                Pull.output(head) >>
                Pull.eval {
                  LogRendering.renderBody(response, head, maxBodyBytes, config).flatMap(part => log(s"$line $part"))
                } >>
                tail.pull.echo
              case None => Pull.eval(log(line))
            }.stream
            IO.pure(RouteResult.Complete(response.copy(body = newBody)))
          } else {
            log(line).as(RouteResult.Complete(response))
          }
        case RouteResult.Rejected(rejections) =>
          log(LogRendering.rejectionLine(rejections, config)).as(RouteResult.Rejected(rejections))
      }
    }
  }

  /**
   * Produces a log entry for every incoming request and [[RouteResult]]; see [[logRequest]] for `redaction`.
   *
   * @group debugging
   */
  def logRequestResult(logHeaders: Boolean = true, logBody: Boolean = true,
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      maxBodyBytes: Int = DebuggingDirectives.DefaultLogLength,
      logAction: Option[String => IO[Unit]] = None,
      redaction: LogRedaction = LogRedaction.default): Directive0 = {
    logResult(logHeaders, logBody, redactHeadersWhen, maxBodyBytes, logAction, redaction) &
    logRequest(logHeaders, logBody, redactHeadersWhen, maxBodyBytes, logAction, redaction)
  }
}

object DebuggingDirectives extends DebuggingDirectives {
  private def logger[A](a: A) = Console[IO].println(a)
  private val DefaultLogLength: Int = 4096

  /** The log action with its errors swallowed: logging never fails a request or a response. */
  private def safeLog(logAction: Option[String => IO[Unit]]): String => IO[Unit] = {
    val action = logAction.getOrElse((s: String) => logger(s))
    s => IO.defer(action(s)).attempt.void
  }
}
