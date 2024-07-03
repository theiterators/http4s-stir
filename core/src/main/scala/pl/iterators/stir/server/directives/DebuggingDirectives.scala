package pl.iterators.stir.server.directives

import cats.effect.IO
import fs2.{ Chunk, Pipe, Stream }
import org.http4s.server.middleware.Logger
import org.http4s.{ Headers, Request, Response }
import org.typelevel.ci.CIString
import org.typelevel.log4cats
import pl.iterators.stir.server.{ Directive, Directive0, RouteResult }

trait DebuggingDirectives {

  /**
   * Produces a log entry for every incoming request.
   *
   * @group debugging
   */
  def logRequest(logHeaders: Boolean = true, logBody: Boolean = true,
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      maxLogLength: Int = Int.MaxValue,
      logAction: Option[String => IO[Unit]] = None): Directive0 = {
    val log = trimLog(maxLogLength).andThen(logAction.getOrElse { (s: String) =>
      DebuggingDirectives.logger.info(s)
    })

    Directive { inner => ctx =>
      if (logBody && !ctx.request.isChunked) {
        IO.ref(Vector.empty[Chunk[Byte]])
          .flatMap { vec =>
            val newBody = Stream.eval(vec.get)
              .flatMap(chunks => Stream.emits(chunks).covary[IO])
              .flatMap(chunks => Stream.chunk(chunks).covary[IO])
            val newRequest = ctx.request.withBodyStream(
              ctx.request.body.observe(_.chunks.flatMap(chunk => Stream.eval(vec.update(_ :+ chunk)).drain)))

            val newCtx = ctx.copy(request = ctx.request.withBodyStream(newBody))
            Logger.logMessage[IO, Request[IO]](newRequest)(logHeaders, logBody = true, redactHeadersWhen)(log).flatMap(
              _ =>
                inner(())(newCtx))
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
      maxLogLength: Int = Int.MaxValue,
      logAction: Option[String => IO[Unit]] = None): Directive0 = {
    val log = trimLog(maxLogLength).andThen(logAction.getOrElse { (s: String) =>
      DebuggingDirectives.logger.info(s)
    })

    Directive { inner => ctx =>
      inner(())(ctx).flatMap {
        case RouteResult.Complete(response) =>
          if (logBody && !response.isChunked) {
            Logger.logMessage[IO, Response[IO]](response)(logHeaders, logBody = true, redactHeadersWhen)(log).as(
              RouteResult.Complete(response))
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
      maxLogLength: Int = Int.MaxValue,
      logAction: Option[String => IO[Unit]] = None): Directive0 = {
    logResult(logHeaders, logBody, redactHeadersWhen, maxLogLength, logAction) & logRequest(logHeaders, logBody,
      redactHeadersWhen,
      maxLogLength,
      logAction)
  }

  private def trimLog(maxLogLength: Int): String => String = { log =>
    if (log.length > maxLogLength) log.take(maxLogLength) + "..." else log
  }
}

object DebuggingDirectives extends DebuggingDirectives {
  private val logger = log4cats.slf4j.Slf4jFactory.create[IO].getLogger
}
