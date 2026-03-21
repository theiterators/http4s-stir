---
id: route-composition
title: Route Composition
sidebar_position: 4
---

# Route Composition

http4s-stir routes are composed from directives, which filter, extract, and transform requests before producing responses. This page covers the `Route` type and the operators used to combine routes and directives.

## The Route Type

A `Route` is a type alias defined as:

```scala
type Route = RequestContext => IO[RouteResult]
```

A `RouteResult` is either `RouteResult.Complete(response)` (a finished HTTP response) or `RouteResult.Rejected(rejections)` (the route did not handle the request). Rejections allow alternative routes to be tried.

## Route Concatenation with `~` and `concat`

The `~` operator tries the left route first. If it completes, the result is returned. If it rejects, the right route is tried. Rejections from both sides are accumulated.

```scala
import pl.iterators.stir.server.Directives._

val route: Route =
  path("hello") {
    complete("Hello!")
  } ~
  path("goodbye") {
    complete("Goodbye!")
  }
```

The `concat` function is an alternative that avoids the risk of accidentally omitting the `~` operator:

```scala
val route: Route =
  concat(
    path("hello") {
      complete("Hello!")
    },
    path("goodbye") {
      complete("Goodbye!")
    }
  )
```

## Directive Conjunction with `&`

The `&` operator combines two directives so that both must pass. Their extractions are joined into a single tuple.

```scala
val route: Route =
  (path("user" / Segment) & get) { userId =>
    complete(s"GET user $userId")
  }
```

When both directives extract values, the extractions are concatenated:

```scala
val route: Route =
  (path("order" / IntNumber) & parameter("detail".as[String])) { (orderId, detail) =>
    complete(s"Order $orderId, detail: $detail")
  }
```

## Directive Alternatives with `|`

The `|` operator tries the left directive first. If it rejects, the right directive is tried. Both directives must produce the same extraction type.

```scala
val route: Route =
  (path("file" / Segment) | path("document" / Segment)) { name =>
    complete(s"Resource: $name")
  }
```

## Nesting Directives

Directives can be nested to build up filtering and extraction logic incrementally:

```scala
val route: Route =
  pathPrefix("api") {
    pathPrefix("v1") {
      path("users") {
        get {
          complete("User list")
        } ~
        post {
          complete("Create user")
        }
      }
    }
  }
```

## Sealing Routes with `Route.seal`

`Route.seal` wraps a route with default exception handling and rejection conversion. A sealed route always produces a complete response -- it never returns a rejection or a failed effect.

```scala
val unsealedRoute: Route =
  path("hello") {
    complete("Hello!")
  }

val sealedRoute: Route = Route.seal(unsealedRoute)
```

You can provide custom handlers:

```scala
implicit val customRejectionHandler: RejectionHandler = RejectionHandler.newBuilder()
  .handleNotFound {
    complete(Status.NotFound, "Custom not found")
  }
  .result()

implicit val customExceptionHandler: ExceptionHandler = ExceptionHandler {
  case ex: RuntimeException =>
    complete(Status.InternalServerError, ex.getMessage)
}

val sealedRoute: Route = Route.seal(unsealedRoute)
```

When converting a `Route` to an http4s `HttpApp[IO]` via `.toHttpApp`, sealing is applied automatically.

## Converting to http4s Types

Routes can be converted to standard http4s types for use with http4s servers:

```scala
val route: Route = path("hello") { complete("Hello!") }

// Always produces a response (applies default sealing)
val httpApp: HttpApp[IO] = route.toHttpApp

// Returns None for unmatched requests
val httpRoutes: HttpRoutes[IO] = route.toHttpRoutes
```
