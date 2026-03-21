---
id: http4s-compat
title: http4s-dsl Compatibility
sidebar_position: 25
---

# http4s-dsl Compatibility

http4s-stir provides the `httpRoutesOf` directive to embed existing http4s-dsl routes inside a stir route tree. This enables gradual migration from http4s-dsl to stir, or mixing both styles in a single application.

## httpRoutesOf

The `httpRoutesOf` directive accepts a `PartialFunction[Request[IO], IO[Response[IO]]]` and converts it into a stir `Route`. If the partial function is defined for the incoming request, the response is returned. Otherwise, the request is rejected (with an empty rejection list), allowing subsequent routes in the tree to handle it.

```scala
import cats.effect.IO
import org.http4s.{Request, Response}
import pl.iterators.stir.server.Directives._

def myHttp4sRoutes(): Route = {
  import org.http4s.dsl.io._
  httpRoutesOf {
    case GET -> Root / "hello" =>
      Ok("Hello")
    case GET -> Root / "hello" / name =>
      Ok(s"Hello $name")
  }
}
```

## Combining with stir directives

`httpRoutesOf` returns a standard `Route`, so it composes with all stir directives using the `~` operator. This makes it possible to keep existing http4s-dsl routes intact while writing new routes with stir directives.

```scala
import cats.effect.IO
import org.http4s.dsl.io._
import pl.iterators.stir.server.Directives._

def routes(): Route = {
  pathPrefix("api") {
    path("items") {
      get {
        complete("items list")
      }
    }
  } ~ httpRoutesOf {
    case GET -> Root / "legacy" / "endpoint" =>
      Ok("served by http4s-dsl")
  }
}
```

## Path matching behavior

The `httpRoutesOf` directive passes the **unmatched path** from the current request context to the partial function. This means that if `httpRoutesOf` is nested inside a `pathPrefix` directive, the partial function receives only the remaining path segment, not the full request path.

```scala
import cats.effect.IO
import org.http4s.dsl.io._
import pl.iterators.stir.server.Directives._

def routes(): Route = {
  pathPrefix("v1") {
    httpRoutesOf {
      // matches /v1/status, not /status
      case GET -> Root / "status" =>
        Ok("ok")
    }
  }
}
```
