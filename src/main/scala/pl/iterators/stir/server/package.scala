package pl.iterators.stir

import cats.data.Kleisli
import cats.effect.IO
import org.http4s.{HttpApp, Request, Response, Status}

package object server {
  type Route = RequestContext => IO[RouteResult]

  implicit class ToHttpRoutes(val route: Route) extends AnyVal {
    def toHttpRoutes: HttpApp[IO] = {
      Kleisli[IO, Request[IO], Response[IO]](req => route(RequestContext(req)).map {
        case RouteResult.Complete(response) => response
        case RouteResult.Rejected(_) => Response[IO](status = Status.NotFound)
      })
    }
  }

  type RouteGenerator[T] = T => Route
  type Directive0 = Directive[Unit]
  type Directive1[T] = Directive[Tuple1[T]]
  type PathMatcher0 = PathMatcher[Unit]
  type PathMatcher1[T] = PathMatcher[Tuple1[T]]
}
