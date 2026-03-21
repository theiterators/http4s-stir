---
id: exception-handling
title: Exception Handling
sidebar_position: 23
---

# Exception Handling

## Overview

`ExceptionHandler` is a `PartialFunction[Throwable, Route]`. It intercepts exceptions thrown during route evaluation and converts them into HTTP responses. If no custom handler is provided, unhandled non-fatal exceptions result in a `500 Internal Server Error` response.

## Creating a Custom Handler

Construct an `ExceptionHandler` by passing a partial function to `ExceptionHandler.apply`:

```scala
import pl.iterators.stir.server._
import pl.iterators.stir.server.Directives._
import org.http4s.Status

val myExceptionHandler = ExceptionHandler {
  case _: ArithmeticException =>
    complete(Status.BadRequest, "Arithmetic error")
  case _: IllegalArgumentException =>
    complete(Status.BadRequest, "Invalid argument")
}
```

The partial function matches on exception types and returns a `Route` that produces the desired error response.

## Chaining Handlers with `withFallback`

Combine two handlers so that the second acts as a fallback when the first does not match:

```scala
val combined = primaryHandler.withFallback(secondaryHandler)
```

If `primaryHandler` does not handle a given exception, `secondaryHandler` is consulted. A handler that has already been sealed (see below) will not accept a fallback, since it already handles all non-fatal exceptions.

## Sealing a Handler

The `seal` method attaches the default handler as a fallback, ensuring all non-fatal exceptions are covered:

```scala
val sealed = myExceptionHandler.seal()
```

An optional `logAction` parameter allows customizing how exceptions are logged:

```scala
import cats.effect.IO

val sealed = myExceptionHandler.seal(
  logAction = Some((throwable, message) => IO.println(message))
)
```

## Default Behavior

The default `ExceptionHandler` (returned by `ExceptionHandler.default()`) handles all non-fatal exceptions by:

1. Logging the error message and stack trace to stderr.
2. Completing the request with `500 Internal Server Error`.

## The `handleExceptions` Directive

Apply a custom `ExceptionHandler` to an inner route using the `handleExceptions` directive from `ExecutionDirectives`:

```scala
val route =
  handleExceptions(myExceptionHandler) {
    path("divide") {
      complete((1 / 0).toString)
    }
  }
```

Exceptions thrown during evaluation of the inner route -- whether thrown synchronously or produced by a failed `IO` -- are caught and processed by the provided handler. If the handler does not match the exception, it propagates outward.

## Relationship with Route.seal

`Route.seal` applies both a `RejectionHandler` and an `ExceptionHandler` to a route. It accepts an implicit `ExceptionHandler` parameter:

```scala
implicit val myHandler: ExceptionHandler = myExceptionHandler
val sealedRoute = Route.seal(route)
```

If no implicit `ExceptionHandler` is in scope, `Route.seal` uses the default handler. The `ExceptionHandler.seal` companion method handles `null` safely by falling back to `ExceptionHandler.default()`.

When converting a route to an `HttpApp[IO]` via `.toHttpApp`, the route is automatically sealed with the default exception and rejection handlers.
