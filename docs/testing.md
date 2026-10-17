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
import org.specs2.mutable.Specification
import pl.iterators.stir.testkit.Specs2RouteTest

class MyRouteSpec extends Specification with Specs2RouteTest {
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

## Timeouts

`~>` and `~!>` wait at most one second for the route to complete or reject, and reading the response body in
`response`, `responseAs`, `chunks` or `trailer` is bounded the same way (Pekko's `pekko.http.testkit.routes.timeout`).
A route that hangs fails the test with "Request was neither completed nor rejected within 1 second" instead of
hanging the suite, and an endless body fails with "Response entity was not read within 1 second" under `~>`. With
`~!>` the server and client start outside the bound; the request and the body read are inside it as one step (so an
endless body is reported as the request not completing), and the test server shuts down at once, so a hanging route
fails within the timeout rather than after Ember's graceful-shutdown period. Declare
an implicit `RouteTestTimeout` in the suite to change the bound, which tests about time (slow routes, request
timeouts) must do:

```scala
import pl.iterators.stir.testkit.RouteTestTimeout
import scala.concurrent.duration._

implicit val routeTestTimeout: RouteTestTimeout = RouteTestTimeout(5.seconds)
```

The bound relies on cancellation: work the route runs in an uncancelable region (for example a blocking call inside
`IO.blocking` that never returns) still has to finish before the failure is reported.

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

## WebSocket Routes

`WS(uri, probe, subprotocols*)` builds a WebSocket upgrade request (`Connection: Upgrade`, `Upgrade: websocket`,
`Sec-WebSocket-Version`, `Sec-WebSocket-Key` and, when subprotocols are given, `Sec-WebSocket-Protocol`), and a
`WSProbe` plays the client, as Pekko's `WS` and `WSProbe` do. When the route accepts the upgrade, the in-memory
testkit (`~>`) connects the probe to the handler the directive was given; inside `check`, `sendMessage` sends a
text, binary or raw frame to the handler and `sendCompletion()` ends the client's stream, while `expectMessage()`,
`expectMessage(text)`, `expectMessage(bytes)`, `expectNoMessage()` and `expectCompletion()` assert on what the
handler sends back. `expectMessage` and `expectCompletion` wait for the `RouteTestTimeout` in scope when the probe was
created (`WSProbe()` also needs the implicit `IORuntime`) and `expectNoMessage` for 100 ms (or the duration given); a
failed expectation throws an `AssertionError`. A handler that fails makes every later expectation fail with its
error, and once it completed every later `expectMessage` fails too. Handlers still running when the suite ends are
cancelled by the testkit's `cleanUp()`.

```scala
import pl.iterators.stir.testkit.WSProbe

val wsClient = WSProbe()

WS("/greeter", wsClient, "json") ~> handleWebSocketMessagesForProtocol(ws, greeter, "json") ~> check {
  expectWebSocketUpgradeWithProtocol { protocol => protocol shouldEqual "json" }
  wsClient.sendMessage("Peter")
  wsClient.expectMessage("Hello Peter!")
  wsClient.sendCompletion()
  wsClient.expectCompletion()
}
```

`isWebSocketUpgrade` is true when the route accepted the upgrade, and `expectWebSocketUpgradeWithProtocol` asserts
the upgrade and hands the selected `Sec-WebSocket-Protocol` to the given function. The probe talks to the handler as
the directive received it: the ping/pong filtering and frame defragmentation the server adds are not applied, and
the real handshake is the server's. Over a real server (`~!>`) frames are not exchanged.

## Streamed Responses

`chunks` returns the response body as the list of `fs2.Chunk`s the route produced, `chunksStream` the same as a
`Stream[IO, Chunk[Byte]]`, and `trailer` the trailer headers attached with `withTrailerHeaders`. Both chunk
accessors read the raw body; for a one-shot body use either them or `responseAs`, not both.

```scala
Get("/events") ~> route ~> check {
  chunks.map(chunk => new String(chunk.toArray, "UTF-8")) shouldEqual List("event 1\n", "event 2\n")
}
```

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

`Query` builds a request with the `QUERY` method (`MethodDirectives.QUERY`).

`WS(uri, probe, subprotocols*)` builds a WebSocket upgrade request driven by a `WSProbe`; see "WebSocket Routes".

A request built with a body (`Post("/", body)`) carries the `Content-Type` and `Content-Length` the body's
`EntityEncoder` produces, as a real client would send; override with `addHeader`.

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
| `addAttribute(key, value)` | Set a request attribute (a `org.typelevel.vault.Key`), read in a route with `attribute(key)`. |

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
