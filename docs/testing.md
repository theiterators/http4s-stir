---
id: testing
title: Testing
sidebar_position: 24
---

# Testing

## Setup

Add the `http4s-stir-testkit` dependency to your project:

```scala
libraryDependencies += "pl.iterators" %% "http4s-stir-testkit" % http4sStirVersion % Test
```

## ScalaTest Integration

Extend `ScalatestRouteTest` in your test class. An implicit `IORuntime` must be provided:

```scala
import cats.effect.unsafe.IORuntime
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import pl.iterators.stir.server.Directives._
import pl.iterators.stir.testkit.ScalatestRouteTest

class MyRouteSpec extends AnyWordSpec with Matchers with ScalatestRouteTest {
  override implicit val runtime: IORuntime = IORuntime.global

  // tests go here
}
```

## Specs2 Integration

Extend `Specs2RouteTest` in your specification:

```scala
import cats.effect.unsafe.IORuntime
import pl.iterators.stir.server.Directives._
import pl.iterators.stir.testkit.Specs2RouteTest

class MyRouteSpec extends org.specs2.mutable.Specification with Specs2RouteTest {
  override implicit val runtime: IORuntime = IORuntime.global

  // tests go here
}
```

## The `~>` Operator

The core testing pattern is `Request ~> route ~> check { assertions }`:

```scala
Get("/hello") ~> route ~> check {
  status shouldEqual Status.Ok
  responseAs[String] shouldEqual "world"
}
```

The `~>` operator applies the request to the route without starting an HTTP server. The route is sealed for exceptions but not for rejections, allowing rejection inspection in tests.

## The `~!>` Operator

The `~!>` operator runs the route through a fully fledged HTTP server (Ember). This is useful for testing directives that depend on actual server behavior, at the cost of additional overhead:

```scala
Get("/hello") ~!> route ~> check {
  status shouldEqual Status.Ok
}
```

## The `check` Block

Inside a `check { }` block, the following members are available for assertions:

### Response Inspection

| Member | Type | Description |
|---|---|---|
| `status` | `Status` | The HTTP response status code. |
| `response` | `Response[IO]` | The full response object. |
| `responseAs[T]` | `T` | Unmarshal the response body to type `T`. Requires an implicit `EntityDecoder[IO, T]`. |
| `entityAs[T]` | `T` | Alias for `responseAs[T]`. |
| `contentType` | `` `Content-Type` `` | The response `Content-Type` header. |
| `mediaType` | `MediaType` | The media type from the `Content-Type`. |
| `charset` | `Charset` | The charset from the `Content-Type`. |
| `headers` | `Headers` | All response headers. |
| `header[T]` | `Option[F[T]]` | Retrieve a typed header by type parameter. |
| `header(name)` | `Option[Header.Raw]` | Retrieve a header by name (case-insensitive). |

### Rejection Inspection

| Member | Type | Description |
|---|---|---|
| `handled` | `Boolean` | `true` if the request was handled (completed), `false` if rejected. |
| `rejections` | `Seq[Rejection]` | The list of rejections. Fails if the request was completed. |
| `rejection` | `Rejection` | The single rejection. Fails if zero or more than one rejection was produced. |

## Request Builders

The following request builders are available. Each accepts a URI string or `Uri` value, and optionally a request body:

```scala
Get("/path")
Post("/path", entity)
Put("/path", entity)
Patch("/path", entity)
Delete("/path")
Options("/path")
Head("/path")
```

Builders that accept an entity require an implicit `EntityEncoder[IO, T]` for the body type.

## Request Transformers

Transform requests before sending them to a route:

| Transformer | Description |
|---|---|
| `addHeader(header)` | Add a single header. |
| `addHeader(name, value)` | Add a header by name and value strings. |
| `addHeaders(first, more*)` | Add multiple headers. |
| `removeHeader(name)` | Remove headers by name. |
| `removeHeader[T]` | Remove headers by typed header type. |
| `removeHeaders(names*)` | Remove headers by multiple names. |
| `mapHeaders(f)` | Transform the headers collection with a function. |
| `addCredentials(credentials)` | Add an `Authorization` header with the given credentials. |

Apply transformers using `~>`:

```scala
Post("/path", entity) ~> addHeader("X-Custom", "value") ~> route ~> check {
  status shouldEqual Status.Ok
}
```

## Complete Example

```scala
import cats.effect.unsafe.IORuntime
import org.http4s.Status
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import pl.iterators.stir.server._
import pl.iterators.stir.server.Directives._
import pl.iterators.stir.testkit.ScalatestRouteTest

class OrderRouteSpec extends AnyWordSpec with Matchers with ScalatestRouteTest {
  override implicit val runtime: IORuntime = IORuntime.global

  val route =
    path("order" / IntNumber) { orderId =>
      get {
        complete(s"Order $orderId")
      } ~
      post {
        complete(Status.Created, s"Created order $orderId")
      }
    }

  "The order route" should {
    "return an order for GET requests" in {
      Get("/order/42") ~> route ~> check {
        status shouldEqual Status.Ok
        responseAs[String] shouldEqual "Order 42"
      }
    }
    "create an order for POST requests" in {
      Post("/order/42") ~> route ~> check {
        status shouldEqual Status.Created
      }
    }
    "reject PUT requests" in {
      Put("/order/42") ~> route ~> check {
        handled shouldEqual false
        rejections should not be empty
      }
    }
  }
}
```
