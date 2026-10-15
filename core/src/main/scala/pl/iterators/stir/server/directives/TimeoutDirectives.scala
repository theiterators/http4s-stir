package pl.iterators.stir.server.directives

import cats.effect.{ IO, Ref, SyncIO }
import org.http4s.{ Request, Response, Status }
import org.typelevel.vault.Key
import pl.iterators.stir.server.{ Directive, Directive0, Directive1, RouteResult }

import scala.concurrent.duration.Duration

/**
 * @groupname timeout Timeout directives
 * @groupprio timeout 160
 */
trait TimeoutDirectives {
  import BasicDirectives._
  import TimeoutDirectives._

  /**
   * Return the currently set request timeout: the one set by the nearest enclosing `withRequestTimeout`, or
   * `Duration.Inf` when no timeout is set (stir has no server-level request timeout; ember's idle timeout is
   * configured on the server builder).
   *
   * @group timeout
   */
  def extractRequestTimeout: Directive1[Duration] =
    extract(_.request.attributes.lookup(RequestTimeoutKey).fold(Duration.Inf: Duration)(_.timeout))

  /**
   * Runs the inner route without a route-local request timeout. It does not cancel the timeout of an enclosing
   * `withRequestTimeout`; nest it the other way round.
   *
   * @group timeout
   */
  def withoutRequestTimeout: Directive0 =
    withRequestTimeout(Duration.Inf)

  /**
   * Sets a request timeout for the inner route. If the inner route does not complete within `timeout` the request
   * is completed with the timeout response: the one set by `withRequestTimeoutResponse` inside, else the handler
   * given here, else `503 Service Unavailable`.
   *
   * @group timeout
   */
  def withRequestTimeout(timeout: Duration): Directive0 =
    withRequestTimeout(timeout, None)

  /**
   * Sets a new request timeout and handler at the same time.
   *
   * @param handler custom "timeout response" function.
   *
   * @group timeout
   */
  def withRequestTimeout(timeout: Duration, handler: Request[IO] => Response[IO]): Directive0 =
    withRequestTimeout(timeout, Some(handler))

  /**
   * Sets a new request timeout and handler (if provided) at the same time.
   *
   * @param handler optional custom "timeout response" function. If left None, the default timeout Response[IO] will be used.
   *
   * @group timeout
   */
  def withRequestTimeout(timeout: Duration, handler: Option[Request[IO] => Response[IO]]): Directive0 =
    Directive { inner => ctx =>
      IO.ref(handler).flatMap { handlerRef =>
        val request =
          ctx.request.withAttributes(ctx.request.attributes.insert(RequestTimeoutKey,
            RequestTimeoutAccess(timeout, handlerRef)))
        val timeoutResponse: IO[RouteResult] =
          handlerRef.get.map(h => RouteResult.Complete(h.fold(Response[IO](Status.ServiceUnavailable))(_(request))))
        inner(())(ctx.copy(request = request)).timeoutTo(timeout, timeoutResponse)
      }
    }

  /**
   * Sets the response the nearest enclosing `withRequestTimeout` produces when it times out. Does nothing when no
   * request timeout is set.
   *
   * @group timeout
   */
  def withRequestTimeoutResponse(handler: Request[IO] => Response[IO]): Directive0 =
    Directive { inner => ctx =>
      ctx.request.attributes.lookup(RequestTimeoutKey).fold(IO.unit)(_.handler.set(Some(handler))) *>
      inner(())(ctx)
    }
}

object TimeoutDirectives extends TimeoutDirectives {

  /** The timeout of the nearest enclosing `withRequestTimeout` and the handler it will use, updatable from inside. */
  private[stir] final case class RequestTimeoutAccess(timeout: Duration,
      handler: Ref[IO, Option[Request[IO] => Response[IO]]])

  private[stir] val RequestTimeoutKey: Key[RequestTimeoutAccess] =
    Key.newKey[SyncIO, RequestTimeoutAccess].unsafeRunSync()
}
