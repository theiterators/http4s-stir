---
id: directives-respond-with
title: Respond With Directives
sidebar_position: 16
---

# Respond With Directives

Respond-with directives add headers to HTTP responses produced by inner routes. They are useful for applying cross-cutting response headers such as CORS, caching, or custom application headers.

## respondWithHeader

Unconditionally adds the given header to all responses from the inner route. The new header is prepended to the existing headers -- if a header with the same name already exists, both the new and the existing header will be present in the response.

```scala
import org.http4s.Header
import org.typelevel.ci.CIString

val route: Route = respondWithHeader(Header.Raw(CIString("X-Custom"), "value")) {
  complete("Response with custom header")
}
```

## respondWithDefaultHeader

Adds the given header to responses only if a header with the same name is not already present. Existing headers are preserved.

```scala
import org.http4s.Header
import org.typelevel.ci.CIString

val route: Route = respondWithDefaultHeader(Header.Raw(CIString("Cache-Control"), "max-age=3600")) {
  complete("Response with default cache header")
}
```

## respondWithHeaders

Unconditionally adds multiple headers to all responses from the inner route. The new headers are prepended to the existing headers -- if headers with the same names already exist, both new and existing headers will be present in the response.

A `Seq`-based overload and a varargs overload are available:

```scala
import org.http4s.Header
import org.typelevel.ci.CIString

val corsHeaders = Seq(
  Header.Raw(CIString("Access-Control-Allow-Origin"), "*"),
  Header.Raw(CIString("Access-Control-Allow-Methods"), "GET, POST, PUT, DELETE")
)

val route: Route = respondWithHeaders(corsHeaders) {
  complete("CORS-enabled response")
}

// Varargs overload
val route2: Route = respondWithHeaders(
  Header.Raw(CIString("Access-Control-Allow-Origin"), "*"),
  Header.Raw(CIString("Access-Control-Allow-Methods"), "GET, POST, PUT, DELETE")
) {
  complete("CORS-enabled response")
}
```

## respondWithDefaultHeaders

Adds multiple headers to responses, but only those headers whose names are not already present in the response.

A `Seq`-based overload and a varargs overload are available:

```scala
import org.http4s.Header
import org.typelevel.ci.CIString

val defaultHeaders = Seq(
  Header.Raw(CIString("X-Frame-Options"), "DENY"),
  Header.Raw(CIString("X-Content-Type-Options"), "nosniff")
)

val route: Route = respondWithDefaultHeaders(defaultHeaders) {
  complete("Response with security headers")
}

// Varargs overload
val route2: Route = respondWithDefaultHeaders(
  Header.Raw(CIString("X-Frame-Options"), "DENY"),
  Header.Raw(CIString("X-Content-Type-Options"), "nosniff")
) {
  complete("Response with security headers")
}
```
