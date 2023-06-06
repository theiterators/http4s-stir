package pl.iterators.stir.server.directives

import cats.effect.IO
import pl.iterators.stir.server.directives.RouteDirectives.reject
import pl.iterators.stir.server.{Route, RouteResult}

trait RouteConcatenation {
  implicit class RouteWithConcatenation(val route: Route) {
    def ~(other: Route): Route = { req =>
      route(req).flatMap {
        case x: RouteResult.Complete => IO.pure(x)
        case RouteResult.Rejected(innerRejection) =>
          other(req).map {
            case x: RouteResult.Complete => x
            case RouteResult.Rejected(outerRejection) => RouteResult.Rejected(innerRejection ++ outerRejection)
          }
      }
    }
  }

  /**
   * Tries the supplied routes in sequence, returning the result of the first route that doesn't reject the request.
   * This is an alternative to direct usage of the infix ~ operator. The ~ can be prone to programmer error, because if
   * it is omitted, the program will still be syntactically correct, but will not actually attempt to match multiple
   * routes, as intended.
   *
   * @param routes subroutes to concatenate
   * @return the concatenated route
   */
  def concat(routes: Route*): Route = routes.foldLeft[Route](reject)(_ ~ _)
}

object RouteConcatenation extends RouteConcatenation