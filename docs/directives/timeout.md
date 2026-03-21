---
id: directives-timeout
title: Timeout Directives
sidebar_position: 18
---

# Timeout Directives

Timeout directives control per-route request timeout behavior. They allow overriding the server-level timeout for specific routes.

## withRequestTimeout

Sets a timeout for the inner route. If the inner route does not complete within the specified duration, a `503 Service Unavailable` response is returned. An optional handler function can be provided to customize the timeout response.

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

Due to the inherent raciness of timeout mechanisms, it is not guaranteed that the timeout will be applied before a previously set timeout has expired.

## withoutRequestTimeout

Disables the request timeout for the inner route by setting it to `Duration.Inf`.

```scala
val route: Route = path("streaming") {
  withoutRequestTimeout {
    complete {
      longLivedStreamingResponse
    }
  }
}
```
