---
id: directives-route
title: Route Directives
sidebar_position: 5
---

# Route Directives

Route directives are terminal directives that produce a `StandardRoute` -- a route that does not pass the request to an inner route. They complete, reject, redirect, or fail the request.

## complete

Completes the request with a response. Several overloads are available.

**Complete with a string:**

```scala
val route: Route = path("hello") {
  complete("Hello, world!")
}
```

**Complete with a status code:**

```scala
val route: Route = path("no-content") {
  complete(Status.NoContent)
}
```

**Complete with a status code and a body:**

```scala
val route: Route = path("created") {
  complete(Status.Created, "Resource created")
}
```

**Complete with a status code, headers, and a body:**

```scala
val route: Route = path("custom") {
  complete(Status.Ok, Headers(Header.Raw(ci"X-Custom", "value")), "With headers")
}
```

**Complete with a marshallable value (e.g., a case class with a Circe encoder):**

```scala
case class User(name: String, age: Int)

val route: Route = path("user") {
  complete(User("Alice", 30))
}
```

**Complete with an `IO[T]`:**

```scala
val route: Route = path("async") {
  complete(IO.pure("Async result"))
}
```

**Complete with a `Response[IO]`:**

```scala
val route: Route = path("raw") {
  complete(Response[IO](Status.Ok).withEntity("Raw response"))
}
```

## reject

Rejects the request. The route does not handle the request and allows alternatives to be tried.

**Reject with no rejections (empty rejection):**

```scala
val route: Route = reject
```

**Reject with specific rejections:**

```scala
val route: Route = reject(ValidationRejection("Invalid input"))
```

Multiple rejections can be provided:

```scala
val route: Route = reject(
  ValidationRejection("Field A is invalid"),
  ValidationRejection("Field B is invalid")
)
```

## redirect

Redirects the request to a different URI with the given status code.

```scala
val route: Route = path("old") {
  redirect(uri"/new", Status.MovedPermanently)
}
```

```scala
val route: Route = path("temp") {
  redirect(uri"/other", Status.TemporaryRedirect)
}
```

## failWith

Fails the request with an exception. The exception propagates up the route tree until it is handled by a `handleExceptions` directive or the default exception handler.

```scala
val route: Route = path("error") {
  failWith(new RuntimeException("Something went wrong"))
}
```

## handle

Converts a function or partial function from `Request[IO]` to `IO[Response[IO]]` into a route.

**Total function:**

```scala
val route: Route = handle { request =>
  IO.pure(Response[IO](Status.Ok).withEntity(s"Handled: ${request.uri}"))
}
```

**Partial function (rejects with empty rejections if not defined):**

```scala
val route: Route = handle {
  case request if request.uri.path.renderString.startsWith("/api") =>
    IO.pure(Response[IO](Status.Ok).withEntity("API response"))
}
```

**Partial function with custom rejections:**

```scala
val route: Route = handle(
  { case request if request.uri.path.renderString.startsWith("/api") =>
    IO.pure(Response[IO](Status.Ok).withEntity("API response"))
  },
  Seq(ValidationRejection("Not an API request"))
)
```

## handleSync

Like `handle`, but the handler function is synchronous.

**Total function:**

```scala
val route: Route = handleSync { request =>
  Response[IO](Status.Ok).withEntity(s"Sync: ${request.uri}")
}
```

**Partial function:**

```scala
val route: Route = handleSync {
  case request if request.uri.path.renderString.startsWith("/static") =>
    Response[IO](Status.Ok).withEntity("Static content")
}
```

**Partial function with custom rejections:**

```scala
val route: Route = handleSync(
  { case request if request.uri.path.renderString.startsWith("/static") =>
    Response[IO](Status.Ok).withEntity("Static content")
  },
  Seq(ValidationRejection("Not a static request"))
)
```
