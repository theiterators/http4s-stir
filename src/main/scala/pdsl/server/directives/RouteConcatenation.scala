package pdsl.server.directives

import cats.effect.IO
import pdsl.server.{Route, RouteResult}

object RouteConcatenation {

  implicit class RouteWithConcatenation(val route: Route) {
    def ~(other: Route): Route = { req =>
      route(req).flatMap {
        case x: RouteResult.Complete => IO.pure(x)
        case RouteResult.Reject(innerRejection) =>
          other(req).map {
            case x: RouteResult.Complete => x
            case RouteResult.Reject(outerRejection) => RouteResult.Reject(innerRejection ++ outerRejection)
          }
      }
    }
  }
}