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
      logAction: Option[String => IO[Unit]] = None): Directive0 = {
    val log = logAction.getOrElse { (s: String) =>
      DebuggingDirectives.logger.info(s)
    }
    Directive { inner => ctx =>
      if (logBody) {
        IO.ref(Vector.empty[Chunk[Byte]])
          .flatMap { vec =>
            val collectChunks: Pipe[IO, Byte, Nothing] =
              _.chunks.flatMap(c => Stream.exec(vec.update(_ :+ c)))
            val pipe: Pipe[IO, Byte, Byte] = _.observe(collectChunks)
            val bodyForLog = Stream.eval(vec.get).flatMap(v => Stream.emits(v)).unchunks
            val logRequest = Logger.logMessage[IO, Request[IO]](ctx.request.withBodyStream(bodyForLog))(logHeaders,
              logBody, redactHeadersWhen)(log)
            val bodyForRouting = pipe(ctx.request.body)
            val newCtx = ctx.copy(request = ctx.request.withBodyStream(bodyForRouting))
            inner(())(newCtx).flatMap {
              case RouteResult.Complete(response) =>
                IO.pure(RouteResult.Complete(response.withBodyStream(response.body.onFinalizeWeak(logRequest))))
              case RouteResult.Rejected(rejections) =>
                logRequest.as(RouteResult.Rejected(rejections))
            }
          }
      } else {
        inner(())(ctx).flatMap {
          case RouteResult.Complete(response) =>
            Logger.logMessage[IO, Request[IO]](ctx.request)(logHeaders, logBody, redactHeadersWhen)(log)
              .as(RouteResult.Complete(response))
          case RouteResult.Rejected(rejections) =>
            Logger.logMessage[IO, Request[IO]](ctx.request)(logHeaders, logBody, redactHeadersWhen)(log)
              .as(RouteResult.Rejected(rejections))
        }
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
      logAction: Option[String => IO[Unit]] = None): Directive0 = {
    val log = logAction.getOrElse { (s: String) =>
      DebuggingDirectives.logger.info(s)
    }
    Directive { inner => ctx =>
      inner(())(ctx).flatMap {
        case RouteResult.Complete(response) =>
          if (logBody) {
            IO.ref(Vector.empty[Chunk[Byte]]).map { vec =>
              val newBody = Stream.eval(vec.get).flatMap(v => Stream.emits(v)).unchunks
              // Cannot Be Done Asynchronously - Otherwise All Chunks May Not Be Appended Previous to Finalization
              val logPipe: Pipe[IO, Byte, Byte] =
                _.observe(_.chunks.flatMap(c => Stream.exec(vec.update(_ :+ c))))
                  .onFinalizeWeak(Logger.logMessage[IO, Response[IO]](response.withBodyStream(newBody))(logHeaders,
                    logBody, redactHeadersWhen)(log))
              RouteResult.Complete(response.withBodyStream(logPipe(response.body)))
            }
          } else {
            Logger.logMessage[IO, Response[IO]](response)(logHeaders, logBody, redactHeadersWhen)(log).as(
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
      logAction: Option[String => IO[Unit]] = None): Directive0 = {
    logResult(logHeaders, logBody, redactHeadersWhen, logAction) & logRequest(logHeaders, logBody, redactHeadersWhen,
      logAction)
  }
}

object DebuggingDirectives extends DebuggingDirectives {
  private val logger = log4cats.slf4j.Slf4jFactory.create[IO].getLogger
}
