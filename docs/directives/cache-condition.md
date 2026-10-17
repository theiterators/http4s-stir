---
id: directives-cache-condition
title: Cache Condition Directives
sidebar_position: 22
---

# Cache Condition Directives

## conditional

Wraps the inner route with support for conditional requests as defined by
[RFC 7232](https://tools.ietf.org/html/rfc7232). Given the resource's `EntityTag` and/or `Last-Modified` date, the
directive evaluates `If-Match`, `If-Unmodified-Since`, `If-None-Match`, `If-Modified-Since` and `If-Range` and
answers `304 Not Modified` or `412 Precondition Failed` without running the inner route when the preconditions
say so. The `ETag` and `Last-Modified` headers are added to every response except a 412, unless the inner route set them.

```scala
import org.http4s.{ EntityTag, HttpDate }

val tag = EntityTag("v42")
val modified = HttpDate.unsafeFromEpochSecond(1700000000L)

val route: Route = path("resource") {
  conditional(tag, modified) {
    complete("the resource")
  }
}
```

Three overloads exist: `conditional(eTag)`, `conditional(lastModified)` and
`conditional(eTag: Option[EntityTag], lastModified: Option[HttpDate])`. Entity tags are compared strongly for
`If-Match` and `If-Range` and weakly for `If-None-Match`, as the RFC requires.
