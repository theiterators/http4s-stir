---
id: directives-timeout
title: Timeout Directives
sidebar_position: 18
---

# Timeout Directives

Timeout directives set a request timeout for a route. stir has no server-level request timeout (ember's idle timeout is configured on the server builder), so a timeout exists only where `withRequestTimeout` sets one; the directives inside it can read and reshape it.

## withRequestTimeout

Sets a timeout for the inner route. If the inner route does not complete within the specified duration, the timeout response is returned: the one set by `withRequestTimeoutResponse` inside, else the handler given here, else an empty `503 Service Unavailable`. The inner route is cancelled when the timeout fires (its finalizers run); Pekko lets it keep running and answers its default timeout with a text body.

```scala
import scala.concurrent.duration._

val route: Route = path("slow-operation") {
  withRequestTimeout(5.seconds) {
    complete {
      longRunningIO.map(_ => Status.Ok -> "Done")
    }
  }
}
```

With a custom timeout handler:

```scala
import scala.concurrent.duration._
import org.http4s.{ Request, Response, Status }

val route: Route = path("slow-operation") {
  withRequestTimeout(5.seconds, (_: Request[IO]) => Response[IO](Status.GatewayTimeout)) {
    complete {
      longRunningIO.map(_ => Status.Ok -> "Done")
    }
  }
}
```

## withoutRequestTimeout

Runs the inner route without a route-local request timeout (`extractRequestTimeout` reports `Duration.Inf` inside). It cannot cancel the timeout of an enclosing `withRequestTimeout`; nest it the other way round.

```scala
val route: Route = path("streaming") {
  withoutRequestTimeout {
    complete {
      longLivedStreamingResponse
    }
  }
}
```

## withRequestTimeoutResponse

Replaces the response the nearest enclosing `withRequestTimeout` produces when it times out. Does nothing when
no timeout is set.
Due to the inherent raciness, a handler set after the timer already fired is not applied.

```scala
import scala.concurrent.duration._
import org.http4s.{ Response, Status }

val route: Route = withRequestTimeout(5.seconds) {
  path("slow-operation") {
    withRequestTimeoutResponse(_ => Response[IO](Status.GatewayTimeout).withEntity("Still working, retry later")) {
      complete(longRunningIO)
    }
  }
}
```

## extractRequestTimeout

Extracts the timeout set by the nearest enclosing `withRequestTimeout`, or `Duration.Inf` when none is set.

```scala
val route: Route = withRequestTimeout(5.seconds) {
  extractRequestTimeout { timeout =>
    complete(s"This route must answer within $timeout")
  }
}
```
