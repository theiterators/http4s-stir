package pl.iterators.stir.server.directives

import cats.effect.IO
import org.http4s.{ Request, Response, Status }
import pl.iterators.stir.server.{ Directive, Directive0, Directive1, RouteResult }

import scala.concurrent.duration.Duration

/**
 * @groupname timeout Timeout directives
 * @groupprio timeout 160
 */
trait TimeoutDirectives {

//  /**
//   * Return the currently set request timeout.
//   *
//   * Note that this may be changed in inner directives.
//   *
//   * @group timeout
//   */
//  def extractRequestTimeout: Directive1[Duration] = Directive { inner => ctx =>
//    val timeout = ctx.request.header[`Timeout-Access`] match {
//      case Some(t) => t.timeoutAccess.getTimeout
//      case _ =>
//        ctx.log.warning("extractRequestTimeout was used in route however no request-timeout is set!")
//        Duration.Inf
//    }
//    inner(Tuple1(timeout))(ctx)
//  }

  /**
   * @group timeout
   */
  def withoutRequestTimeout: Directive0 =
    withRequestTimeout(Duration.Inf)

  /**
   * Tries to set a new request timeout and handler (if provided) at the same time.
   *
   * Due to the inherent raciness it is not guaranteed that the update will be applied before
   * the previously set timeout has expired!
   *
   * @group timeout
   */
  def withRequestTimeout(timeout: Duration): Directive0 =
    withRequestTimeout(timeout, None)

  /**
   * Tries to set a new request timeout and handler (if provided) at the same time.
   *
   * Due to the inherent raciness it is not guaranteed that the update will be applied before
   * the previously set timeout has expired!
   *
   * @param handler optional custom "timeout response" function. If left None, the default timeout Response[IO] will be used.
   *
   * @group timeout
   */
  def withRequestTimeout(timeout: Duration, handler: Request[IO] => Response[IO]): Directive0 =
    withRequestTimeout(timeout, Some(handler))

  /**
   * Tries to set a new request timeout and handler (if provided) at the same time.
   *
   * Due to the inherent raciness it is not guaranteed that the update will be applied before
   * the previously set timeout has expired!
   *
   * @param handler optional custom "timeout response" function. If left None, the default timeout HttpResponse will be used.
   *
   * @group timeout
   */
  def withRequestTimeout(timeout: Duration, handler: Option[Request[IO] => Response[IO]]): Directive0 =
    Directive { inner => ctx =>
      lazy val handlerToApply = handler match {
        case Some(h) => h(ctx.request)
        case _       => Response[IO](Status.ServiceUnavailable)
      }
      inner(())(ctx).timeoutTo(timeout, IO.pure(RouteResult.Complete(handlerToApply)))
    }

//  /**
//   * Tries to set a new request timeout handler, which produces the timeout response for a
//   * given request. Note that the handler must produce the response synchronously and shouldn't block!
//   *
//   * Due to the inherent raciness it is not guaranteed that the update will be applied before
//   * the previously set timeout has expired!
//   *
//   * @group timeout
//   */
//  def withRequestTimeoutResponse(handler: HttpRequest => HttpResponse): Directive0 =
//    Directive { inner => ctx =>
//      ctx.request.header[`Timeout-Access`] match {
//        case Some(t) => t.timeoutAccess.updateHandler(handler)
//        case _       => ctx.log.warning("withRequestTimeoutResponse was used in route however no request-timeout is set!")
//      }
//      inner(())(ctx)
//    }
}

object TimeoutDirectives extends TimeoutDirectives
