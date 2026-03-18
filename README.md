# http4s-stir

[![Maven Central](https://img.shields.io/maven-central/v/pl.iterators/http4s-stir_3)](https://central.sonatype.com/artifact/pl.iterators/http4s-stir_3)
[![CI](https://github.com/theiterators/http4s-stir/actions/workflows/ci.yml/badge.svg)](https://github.com/theiterators/http4s-stir/actions/workflows/ci.yml)

[Pekko HTTP](https://github.com/apache/incubator-pekko-http) (Akka HTTP) style DSL directives for [http4s](https://github.com/http4s/http4s) with cats-effect IO. About 85% of all directives have been ported. Includes a test kit similar to Pekko's.

Cross-compiled for JVM, Scala.js, and Scala Native. Supports Scala 2.13 and Scala 3.

## Installation

```scala
// build.sbt
libraryDependencies += "pl.iterators" %% "http4s-stir" % "0.4.1"
libraryDependencies += "pl.iterators" %% "http4s-stir-testkit" % "0.4.1" % Test
```

## Quick example

A complete example you can run with `scala-cli run .`:

```scala 3
// Main.scala
//> using dep pl.iterators::http4s-stir::0.4.1
//> using dep org.http4s::http4s-ember-server::0.23.33
//> using dep org.http4s::http4s-circe::0.23.33
//> using dep io.circe::circe-generic::0.14.15
//> using dep org.typelevel::cats-effect::3.7.0

import org.http4s.Status
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.circe.CirceEntityDecoder.*
import io.circe.*
import io.circe.generic.semiauto.*
import cats.effect.{IO, IOApp}
import pl.iterators.stir.server.*
import pl.iterators.stir.server.Directives.*

var orders: List[Item] = Nil

final case class Item(name: String, id: Long)
final case class Order(items: List[Item])

given Codec[Item] = deriveCodec[Item]
given Codec[Order] = deriveCodec[Order]

def fetchItem(itemId: Long): IO[Option[Item]] = IO.delay {
  orders.find(o => o.id == itemId)
}
def saveOrder(order: Order): IO[List[Item]] = {
  orders = order.items ::: orders
  IO.delay(orders)
}

val route: Route =
  concat(
    get {
      pathPrefix("item" / LongNumber) { id =>
        val maybeItem: IO[Option[Item]] = fetchItem(id)
        onSuccess(maybeItem) {
          case Some(item) => complete(item)
          case None       => complete(Status.NotFound)
        }
      }
    },
    post {
      path("create-order") {
        entity(as[Order]) { order =>
          val saved: IO[List[Item]] = saveOrder(order)
          onSuccess(saved) { _ =>
            complete("order created")
          }
        }
      }
    }
  )

object Main extends IOApp.Simple {
  val run = EmberServerBuilder
    .default[IO]
    .withHttpApp(route.toHttpRoutes.orNotFound)
    .build
    .use(_ => IO.never)
}
```

### Testing

http4s-stir includes a test kit with familiar `~>` routing test syntax:

```scala 3
// Main.test.scala
//> using test.dep pl.iterators::http4s-stir-testkit:0.4.1
//> using test.dep org.http4s::http4s-circe:0.23.33
//> using test.dep org.specs2::specs2-core:5.5.8

import org.http4s.Status
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.circe.CirceEntityDecoder.*
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.specs2.mutable.Specification
import pl.iterators.stir.testkit.Specs2RouteTest

class MainRoutesSpec extends Specification with Specs2RouteTest {
  override implicit val runtime: IORuntime = IORuntime.global

  sequential
  "The routes" should {
    "create order" in {
      Post("/create-order", Order(List(Item("foo", 42)))) ~> route ~> check {
        responseAs[String] must contain("order created")
      }
    }
    "retrieve an item if present" in {
      orders = List(Item("foo", 42))
      Get("/item/42") ~> route ~> check {
        responseAs[Item] must beEqualTo(Item("foo", 42))
      }
    }
    "return 404 if item is not present" in {
      orders = List.empty
      Get("/item/42") ~> route ~> check {
        status must beEqualTo(Status.NotFound)
      }
    }
  }
}
```

Run with `scala-cli test .`.

For a more comprehensive example showcasing additional directives, see [examples/Service.scala](https://github.com/theiterators/http4s-stir/blob/master/examples/src/main/scala/Service.scala). Run it locally with `sbt ~examples/reStart`.

## http4s-dsl compatibility

There's a compatibility layer, [`Http4sDirectives`](https://github.com/theiterators/http4s-stir/blob/master/core/src/main/scala/pl/iterators/stir/server/directives/Http4sDirectives.scala), that lets you embed existing http4s-dsl routes within stir routes.

## What's missing

Some Pekko HTTP directives are absent or modified:

* `CacheConditionDirectives`, `CodingDirectives`, `RangeDirectives`
* Directory listing in `FileAndResourceDirectives`
* `checkSameOrigin` in `HeaderDirectives`
* Multipart form handling in `FormFieldDirectives`
* `AttributeDirectives`, `FramedEntityStreamingDirectives`
* Most of `WebSocketDirectives`
* Strict entity conversion, `withSizeLimit`, `withoutSizeLimit`, `requestEntityEmpty`, `requestEntityPresent`, `rejectEmptyResponse`
* Testkit differences: synchronous execution, no chunk support, limited request building, no WebSocket testing

## What's with the name?

> **stir something up** (pv)
>
> *to cause an unpleasant emotion or problem to begin or grow*

Some love http4s DSL but dislike Pekko's. Others feel the opposite. This library stirs things up by combining both.

## Contributing

Issues and PRs welcome at [github.com/theiterators/http4s-stir](https://github.com/theiterators/http4s-stir/issues).

## License

Apache License 2.0. See [LICENSE](https://github.com/theiterators/http4s-stir/blob/master/LICENSE).

## Acknowledgements

http4s-stir incorporates code adapted from [Pekko HTTP](https://github.com/apache/incubator-pekko-http), a fork of [Akka HTTP](https://github.com/akka/akka-http).
