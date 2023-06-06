package pl.iterators.stir.server.directives

import cats.effect.IO
import org.http4s.{EntityEncoder, Headers, Request, Response, Status, Uri}
import pl.iterators.stir.server.{Rejection, RouteResult, StandardRoute, ToResponseMarshallable}

trait RouteDirectives {
  /**
   * Rejects the request with an empty set of rejections.
   *
   * @group route
   */
  def reject: StandardRoute = RouteDirectives._reject

  /**
   * Rejects the request with the given rejections.
   *
   * @group route
   */
  def reject(rejections: Rejection*): StandardRoute =
    StandardRoute(_.reject(rejections: _*))

  /**
   * Completes the request with redirection response of the given type to the given URI.
   *
   * @group route
   */
  def redirect(uri: Uri, redirectionType: Status): StandardRoute =
    StandardRoute(_.redirect(uri, redirectionType))

  /**
   * Completes the request using the given arguments.
   *
   * @group route
   */
  def complete(m: => ToResponseMarshallable): StandardRoute =
    StandardRoute(_.complete(m))

  /**
   * Completes the request using the given arguments.
   *
   * @group route
   */
  def complete[T](status: Status, v: => T)(implicit m: EntityEncoder[IO, T]): StandardRoute =
    StandardRoute(_.complete((status, v)))

  /**
   * Completes the request using the given arguments.
   *
   * @group route
   */
  def complete[T](status: Status, headers: Headers, v: => T)(implicit m: EntityEncoder[IO, T]): StandardRoute =
    complete((status, headers, v))

  /**
   * Bubbles the given error up the response chain, where it is dealt with by the closest `handleExceptions`
   * directive and its ExceptionHandler.
   *
   * @group route
   */
  def failWith(error: Throwable): StandardRoute =
    StandardRoute(_.fail(error))

  /**
   * Handle the request using a function.
   *
   * @group route
   */
  def handle(handler: Request[IO] => IO[Response[IO]]): StandardRoute = { ctx => handler(ctx.request).map(RouteResult.Complete) }

  /**
   * Handle the request using a function.
   *
   * @group route
   */
  def handleSync(handler: Request[IO] => Response[IO]): StandardRoute = { ctx => IO.pure(RouteResult.Complete(handler(ctx.request))) }

  /**
   * Handle the request using an asynchronous partial function.
   *
   * This directive can be used to include external components request processing components defined as PartialFunction
   * (like those provided by akka-grpc) into a routing tree defined as routes.
   *
   * When the partial function is not defined for a request, the request is rejected with an empty list of rejections
   * which is equivalent to a "Not Found" rejection.
   *
   * @group route
   */
  def handle(handler: PartialFunction[Request[IO], IO[Response[IO]]]): StandardRoute =
    handle(handler, Nil)

  /**
   * Handle the request using an asynchronous partial function.
   *
   * This directive can be used to include external components request processing components defined as PartialFunction
   * (like those provided by akka-grpc) into a routing tree defined as routes.
   *
   * @param rejections The list of rejections to reject with if the handler is not defined for a request.
   * @group route
   */
  def handle(handler: PartialFunction[Request[IO], IO[Response[IO]]], rejections: Seq[Rejection]): StandardRoute = { ctx =>
    handler
      .andThen(_.map(RouteResult.Complete))
      .applyOrElse[Request[IO], IO[RouteResult]](ctx.request, _ => ctx.reject(rejections: _*))
  }

  /**
   * Handle the request using a synchronous partial function.
   *
   * This directive can be used to include external components request processing components defined as PartialFunction
   * (like those provided by akka-grpc) into a routing tree defined as routes.
   *
   * When the partial function is not defined for a request, the request is rejected with an empty list of rejections
   * which is equivalent to a "Not Found" rejection.
   *
   * @group route
   */
  def handleSync(handler: PartialFunction[Request[IO], Response[IO]]): StandardRoute =
    handleSync(handler, Nil)

  /**
   * Handle the request using a synchronous partial function.
   *
   * This directive can be used to include external components request processing components defined as PartialFunction
   * (like those provided by akka-grpc) into a routing tree defined as routes.
   *
   * @param rejections The list of rejections to reject with if the handler is not defined for a request.
   * @group route
   */
  def handleSync(handler: PartialFunction[Request[IO], Response[IO]], rejections: Seq[Rejection]): StandardRoute = { ctx =>
    handler
      .andThen(res => IO.pure(RouteResult.Complete(res)))
      .applyOrElse[Request[IO], IO[RouteResult]](ctx.request, _ => ctx.reject(rejections: _*))
  }
}

object RouteDirectives extends RouteDirectives {
  private val _reject = StandardRoute(_.reject())
}