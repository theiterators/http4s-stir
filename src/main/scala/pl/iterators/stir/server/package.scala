package pl.iterators.stir

import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import org.http4s.{HttpApp, HttpRoutes, Request, Response}

package object server {
  type Route = RequestContext => IO[RouteResult]

  implicit class ToHttpRoutes(val route: Route) extends AnyVal {
    def toHttpApp: HttpApp[IO] = {
      val sealedRoute = {
        import directives.ExecutionDirectives._
        (handleExceptions(ExceptionHandler.default()) & handleRejections(RejectionHandler.default))
          .tapply(_ => route)
      }
      Kleisli[IO, Request[IO], Response[IO]](req => sealedRoute(RequestContext(req)).map {
        case RouteResult.Complete(response) => response
        case RouteResult.Rejected(_) => throw new IllegalStateException("RouteResult.Rejected should not be returned from a sealed route")
      })
    }

    def toHttpRoutes: HttpRoutes[IO] = {
      Kleisli(req => OptionT(route(RequestContext(req)).map {
        case RouteResult.Complete(response) => Some(response)
        case _ => None
      }))
    }
  }

  type RouteGenerator[T] = T => Route
  type Directive0 = Directive[Unit]
  type Directive1[T] = Directive[Tuple1[T]]
  type PathMatcher0 = PathMatcher[Unit]
  type PathMatcher1[T] = PathMatcher[Tuple1[T]]
}
