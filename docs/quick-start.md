---
id: quick-start
title: Quick Start
sidebar_position: 3
---

# Quick Start

This guide walks through building a small CRUD API with http4s-stir. By the end you will have a running server that creates, lists, and retrieves items.

## Domain model

Define a simple `Item` case class and derive Circe codecs for JSON serialization:

```scala
import io.circe.Codec
import io.circe.generic.semiauto._

case class Item(id: Long, name: String)

implicit val itemCodec: Codec[Item] = deriveCodec[Item]
```

We will store items in a mutable list for brevity. In a real application you would use a database backed by `Resource` or `Ref`.

```scala
var items: List[Item] = Nil
```

## Define routes

Import the stir directives and http4s Circe integration, then define a `Route`:

```scala
import cats.effect.IO
import org.http4s.Status
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.circe.CirceEntityDecoder._
import pl.iterators.stir.server.Route
import pl.iterators.stir.server.Directives._

val route: Route =
  pathPrefix("items") {
    (get & pathEndOrSingleSlash) {
      complete(Status.Ok -> items)
    } ~
    (post & pathEndOrSingleSlash & entity(as[Item])) { item =>
      items = item :: items
      complete(Status.Created -> item)
    } ~
    (get & path(LongNumber)) { id =>
      items.find(_.id == id) match {
        case Some(item) => complete(Status.Ok -> item)
        case None       => complete(Status.NotFound -> "Item not found")
      }
    } ~
    (delete & path(LongNumber)) { id =>
      items = items.filterNot(_.id == id)
      complete(Status.NoContent)
    }
  }
```

Key concepts used above:

- `pathPrefix` and `path` define URL structure. `LongNumber` is a path matcher that extracts a `Long`.
- `get`, `post`, `delete` filter by HTTP method.
- `entity(as[Item])` deserializes the request body into an `Item` using Circe.
- `complete` produces a response. It accepts a status code, a status-body tuple, or a marshallable value.
- `~` concatenates routes; the first matching branch wins.

## Wire up the server

Convert the `Route` to an http4s `HttpApp` and serve it with Ember:

```scala
import cats.effect.IOApp
import com.comcast.ip4s._
import org.http4s.ember.server.EmberServerBuilder

object Main extends IOApp.Simple {
  val run =
    EmberServerBuilder
      .default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(port"8080")
      .withHttpApp(route.toHttpRoutes.orNotFound)
      .build
      .use(_ => IO.never)
}
```

`route.toHttpRoutes` converts the stir `Route` into an http4s `HttpRoutes[IO]`. Calling `.orNotFound` seals it into an `HttpApp[IO]` that returns 404 for unmatched paths.

## Test with curl

Start the server, then:

```bash
# Create an item
curl -X POST http://localhost:8080/items \
  -H 'Content-Type: application/json' \
  -d '{"id": 1, "name": "Widget"}'

# List all items
curl http://localhost:8080/items

# Get a single item
curl http://localhost:8080/items/1

# Delete an item
curl -X DELETE http://localhost:8080/items/1
```

## Testing with the testkit

http4s-stir ships a testkit that lets you test routes without starting a server. The `~>` operator sends a request through the route and captures the response for assertions.

With ScalaTest:

```scala
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import cats.effect.unsafe.IORuntime
import org.http4s.Status
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.circe.CirceEntityDecoder._
import pl.iterators.stir.testkit.ScalatestRouteTest

class ItemsRouteSpec extends AnyWordSpec with Matchers with ScalatestRouteTest {
  override implicit val runtime: IORuntime = IORuntime.global

  "POST /items" should {
    "create an item" in {
      Post("/items", Item(1, "Widget")) ~> route ~> check {
        status shouldEqual Status.Created
        responseAs[Item] shouldEqual Item(1, "Widget")
      }
    }
  }

  "GET /items/1" should {
    "return 404 when item does not exist" in {
      items = Nil
      Get("/items/1") ~> route ~> check {
        status shouldEqual Status.NotFound
      }
    }
  }
}
```

The testkit provides `Get`, `Post`, `Put`, `Delete`, and other request builders. Inside the `check` block you have access to `status`, `responseAs[T]`, `header`, and other response inspectors.
