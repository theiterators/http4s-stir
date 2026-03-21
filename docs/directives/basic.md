---
id: directives-basic
title: Basic Directives
sidebar_position: 19
---

# Basic Directives

Basic directives are foundational low-level building blocks for constructing custom directives. They provide mechanisms for extracting values from requests, transforming requests and responses, and manipulating route results and rejections.

## Extraction

### extract

Extracts a single value from the `RequestContext` using the given function.

```scala
val extractHost: Directive1[String] = extract(_.request.uri.host.map(_.value).getOrElse("unknown"))

val route: Route = extractHost { host =>
  complete(s"Host: $host")
}
```

### extractRequest

Extracts the current `Request[IO]` instance.

```scala
val route: Route = extractRequest { request =>
  complete(s"Method: ${request.method}, URI: ${request.uri}")
}
```

### extractRequestContext

Extracts the full `RequestContext`.

```scala
val route: Route = extractRequestContext { ctx =>
  complete(s"Unmatched path: ${ctx.unmatchedPath}")
}
```

### extractUri

Extracts the complete request URI.

```scala
val route: Route = extractUri { uri =>
  complete(s"Full URI: $uri")
}
```

### extractRequestEntity

Extracts the request entity body as an `EntityBody[IO]` (alias for `Stream[IO, Byte]`).

```scala
val route: Route = extractRequestEntity { body =>
  complete(Status.Ok -> body)
}
```

### extractDataBytes

Extracts the request body as a `Stream[IO, Byte]`. Functionally equivalent to `extractRequestEntity`.

```scala
val route: Route = extractDataBytes { bytes =>
  val collected = bytes.through(fs2.text.utf8.decode).compile.string
  onSuccess(collected) { text =>
    complete(s"Received: $text")
  }
}
```

### extractUnmatchedPath

Extracts the yet-unmatched portion of the request path.

```scala
val route: Route = pathPrefix("api") {
  extractUnmatchedPath { remaining =>
    complete(s"Remaining path: $remaining")
  }
}
```

### extractMatchedPath

Extracts the already-matched portion of the request path.

```scala
val route: Route = pathPrefix("api" / "v1") {
  extractMatchedPath { matched =>
    complete(s"Matched: $matched")
  }
}
```

## Providing Values

### provide

Injects a single value into a directive, making it available to the inner route.

```scala
val route: Route = provide("injected-value") { value =>
  complete(value)
}
```

### tprovide

Injects a tuple of values into a directive.

```scala
val route: Route = tprovide(("hello", 42)) { (greeting, number) =>
  complete(s"$greeting $number")
}
```

### textract

Extracts a tuple of values from the `RequestContext`.

```scala
val extractMethodAndPath = textract(ctx => (ctx.request.method, ctx.request.uri.path))
```

### pass

A no-op directive that always passes the request to its inner route unchanged. Useful as an identity element in directive composition.

```scala
val maybeAuth: Directive0 = if (authEnabled) authenticateBasic("realm", auth) else pass

val route: Route = maybeAuth {
  complete("OK")
}
```

## Request Transformation

### mapRequest

Transforms the incoming `Request[IO]` before passing it to the inner route.

```scala
import org.http4s.Header
import org.typelevel.ci.CIString

val route: Route = mapRequest(_.putHeaders(Header.Raw(CIString("X-Modified"), "true"))) {
  extractRequest { req =>
    complete(s"Header present: ${req.headers.get(CIString("X-Modified")).isDefined}")
  }
}
```

### mapRequestContext

Transforms the `RequestContext` before passing it to the inner route.

```scala
val route: Route = mapRequestContext(ctx => ctx.copy(unmatchedPath = ctx.unmatchedPath)) {
  complete("OK")
}
```

## Response Transformation

### mapResponse

Transforms the `Response[IO]` produced by the inner route.

```scala
import org.http4s.Header
import org.typelevel.ci.CIString

val route: Route = mapResponse(_.putHeaders(Header.Raw(CIString("X-Served-By"), "stir"))) {
  complete("Hello")
}
```

### mapResponseEntity

Transforms the response entity body (`EntityBody[IO]`) produced by the inner route.

```scala
val route: Route = mapResponseEntity(_.through(fs2.text.utf8.decode).through(fs2.text.utf8.encode)) {
  complete("Re-encoded content")
}
```

### mapResponseHeaders

Transforms the response `Headers` produced by the inner route.

```scala
import org.typelevel.ci._

val route: Route = mapResponseHeaders(_.removeHeader(ci"X-Internal")) {
  complete("Cleaned headers")
}
```

## Route Transformation

### mapInnerRoute

Transforms the inner route itself, allowing you to wrap or modify route behavior.

```scala
val route: Route = mapInnerRoute { inner =>
  ctx => inner(ctx) // pass through unchanged
} {
  complete("OK")
}
```

## Route Result Transformation

These directives operate on `RouteResult` values produced by inner routes. They are primarily used when building custom directives.

- **`mapRouteResult(f: RouteResult => RouteResult)`** -- transforms the route result synchronously.
- **`mapRouteResultIO(f: IO[RouteResult] => IO[RouteResult])`** -- transforms the entire `IO[RouteResult]` effect, useful for error handling.
- **`mapRouteResultWith(f: RouteResult => IO[RouteResult])`** -- transforms the route result with an effectful function.
- **`mapRouteResultPF(f: PartialFunction[RouteResult, RouteResult])`** -- transforms matching route results; non-matching results pass through unchanged.
- **`mapRouteResultWithPF(f: PartialFunction[RouteResult, IO[RouteResult]])`** -- effectful variant of `mapRouteResultPF`.

## Path Manipulation

### mapUnmatchedPath

Transforms the unmatched path of the `RequestContext` before passing it to the inner route.

```scala
val route: Route = mapUnmatchedPath(path => Uri.Path.unsafeFromString(path.toString.toLowerCase)) {
  path("api") {
    complete("Case-insensitive match")
  }
}
```

## Rejection Manipulation

### mapRejections

Transforms the list of rejections produced by the inner route.

```scala
val route: Route = mapRejections(_.filter(!_.isInstanceOf[MethodRejection])) {
  get {
    complete("GET only")
  }
}
```

### recoverRejections

Converts rejections into a `RouteResult`, allowing rejected requests to be recovered.

```scala
val route: Route = recoverRejections { rejections =>
  RouteResult.Complete(Response[IO](Status.NotFound))
} {
  reject
}
```

### recoverRejectionsWith

Effectful variant of `recoverRejections` that returns `IO[RouteResult]`.

### cancelRejection

Cancels a specific rejection from the list of rejections produced by the inner route.

```scala
val route: Route = cancelRejection(MethodRejection(Method.POST)) {
  get {
    complete("OK")
  }
}
```

### cancelRejections

Cancels rejections matching the given classes or filter function.

```scala
val route: Route = cancelRejections(classOf[MethodRejection]) {
  get {
    complete("OK")
  }
}
```
