package pl.iterators.stir.testkit

import scala.concurrent.duration._

/**
 * How long `~>` and `~!>` wait for the route to complete or reject, and how long reading the response body may take
 * (Pekko's `pekko.http.testkit.routes.timeout`, one second by default). Declare an implicit `RouteTestTimeout` in a
 * test suite to change it there.
 */
final case class RouteTestTimeout(duration: FiniteDuration)

object RouteTestTimeout {
  implicit val default: RouteTestTimeout = RouteTestTimeout(1.second)
}
