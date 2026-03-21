---
id: directives-io
title: IO Directives
sidebar_position: 12
---

# IO Directives

IO directives provide cats-effect `IO` equivalents of Pekko HTTP's `FutureDirectives`. They allow running effectful computations and feeding results into the route structure, bridging the gap between `IO`-based application logic and the directive DSL.

## onComplete

Runs the given `IO[T]` and provides the result as a `Try[T]` to the inner route. Both success and failure cases are handled within the route, giving full control over error responses.

```scala
import scala.util.{Success, Failure}
import cats.effect.IO

val route: Route = path("data") {
  onComplete(IO.delay(fetchData())) {
    case Success(data) => complete(data)
    case Failure(ex)   => complete((Status.InternalServerError, ex.getMessage))
  }
}
```

## onSuccess

Runs the given `IO[T]` and extracts the value of type `T` on success. If the `IO` fails, the failure is propagated to the nearest `ExceptionHandler`. If `T` is already a tuple, it is expanded into the corresponding number of extractions.

```scala
import cats.effect.IO

val route: Route = path("user" / Segment) { id =>
  onSuccess(IO.delay(loadUser(id))) { user =>
    complete(user.name)
  }
}
```

## completeOrRecoverWith

Attempts to complete the request with the result of the given `IO[T]` on success. If the `IO` fails, the `Throwable` is extracted and passed to the inner route for custom error handling. Requires an implicit `ToResponseMarshaller[T]` in scope.

```scala
import cats.effect.IO

val route: Route = path("resource") {
  completeOrRecoverWith(IO.delay(loadResource())) { ex =>
    complete((Status.InternalServerError, s"Failed: ${ex.getMessage}"))
  }
}
```
