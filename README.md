# http4s-stir

Welcome to http4s-stir, a library designed to bridge the gap between Pekko HTTP (Akka HTTP) and http4s. This README provides an overview of the library, its usage, project status, and more.

http4s-stir offers [Pekko HTTP](https://github.com/apache/incubator-pekko-http)-style (Akka HTTP-style) DSL directives for [http4s](https://github.com/http4s/http4s) using cats-effect's IO as an effect system. About 85% of all directives have been ported. Some were omitted due to a lack of support in http4s, while others were modified to fit http4s' distinct architecture. For specifics, refer to the [Missing](#missing) section below.

Additionally, there's a compatibility layer, [`Http4sDirectives`](https://github.com/theiterators/http4s-stir/blob/master/core/src/main/scala/pl/iterators/stir/server/directives/Http4sDirectives.scala), for http4s-dsl routes.

http4s-stir also furnishes a test kit akin to Pekko's (Akka's).

## How to use it

### Installation

In SBT:

```scala
libraryDependencies += "pl.iterators" %% "http4s-stir" % "0.1"
libraryDependencies += "pl.iterators" %% "http4s-stir-testkit" % "0.1" % Test // if you need this
```

For `scala-cli` see [this example](#example).

### Example

Here's an example in Scala 3 that you can run using scala-cli:

```scala
// Main.scala
//> using dep org.typelevel::cats-effect:3.5.1
//> using dep org.http4s::http4s-dsl:0.23.23
//> using dep org.http4s::http4s-ember-server:0.23.23
//> using dep org.http4s::http4s-circe:0.23.23
//> using dep io.circe::circe-core:0.14.5
//> using dep io.circe::circe-generic:0.14.5
//> using dep pl.iterators::http4s-stir:0.1

import org.http4s.Status
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.circe.CirceEntityDecoder.*
import io.circe.*
import io.circe.generic.semiauto.*
import cats.effect.IO
import pl.iterators.stir.server.*
import pl.iterators.stir.server.Directives.*
import cats.effect.IOApp

// example rewritten from https://pekko.apache.org/docs/pekko-http/current/introduction.html#using-apache-pekko-http
var orders: List[Item] = Nil

// domain model
final case class Item(name: String, id: Long)
final case class Order(items: List[Item])

// formats for unmarshalling and marshalling
given Codec[Item] = deriveCodec[Item]
given Codec[Order] = deriveCodec[Order]

// (fake) async database query api
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
        // there might be no item for a given id
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
          onSuccess(saved) {
            _ => // we are not interested in the result value `Done` but only in the fact that it was successful
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

```scala 3
// Main.test.scala
//> using test.dep org.specs2::specs2-core:4.19.2
//> using test.dep pl.iterators::http4s-stir-testkit:0.1

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
                orders.head must beEqualTo(Item("foo", 42))
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

For a more comprehensive example showcasing additional directives see [examples](https://github.com/theiterators/http4s-stir/blob/master/examples/src/main/scala/Service.scala).

## Why this library?

Here's why I embarked on this project:

- After the license change for Akka, many contemplated transitioning to http4s and the Typelevel stack. I wanted to simplify this migration.
- While I'm a fan of cats-effect, I find the http4s DSL verbose and clunky. Marrying Pekko HTTP (Akka HTTP) with cats-effect seemed inelegant, so http4s-stir could be the remedy.
- I was curious about the internals of both Pekko HTTP and http4s and wanted to determine the feasibility of this project.
- And, of course, a bit of playful provocation - [see the next section](#whats-with-the-name).

## What's with the name?

> **stir something up** (pv)
> 
> *to cause an unpleasant emotion or problem to begin or grow*

There are folks who adore http4s but detest Pekko's (or Akka's) DSL. Conversely, there are those who champion Pekko's (or Akka's) but disdain http4s DSL. I aimed to ruffle feathers from both camps with a hybrid library.

## Project status

This library is in preview, intended to collect initial feedback. Yet, I am dedicated to its ongoing maintenance and enhancement, especially as it undergoes real-world testing. Contributions are very welcome.

## Missing

Certain directives from the original are either absent or have been modified:

* Assuming and converting to/from strict entity
* `CacheConditionDirectives`
* `CodingDirectives`
* directory listing in `FileAndResourceDirectives`
* `RangeDirectives`
* `checkSameOrigin` in `HeaderDirectives`
* handling of multipart forms in `FormFieldDirectives` (but I don't like it anyway)
* Some of how akka configures things
  * `withSizeLimit`
  * `withoutSizeLimit`
  * `requestEntityEmpty`
  * `requestEntityPresent`
  * `rejectEmptyResponse`
  * `extractRequestTimeout`
  * `withRequestTimeoutResponse`
* AttributeDirectives
* FramedEntityStreamingDirectives
* WebSocketDirectives in large part
* Testkit needed significant changes
  * Not async anymore
  * Chunks not supported
  * Request building incomplete (missing some minor header methods)
  * All websocket thingies
  * Some logic of transparent headers and default host info

## Support

### Encountering a Problem?

If you run into any issues, unexpected behavior, or errors, we encourage you to report them. Your feedback is invaluable and helps us improve.

### Have a Feature Request?

If there's a feature you'd like to see, or if you have an idea that would make this project even better, we'd love to hear about it!

### How to Report an Issue or Feature Request

Please create a new issue in our [http4-stir](https://github.com/theiterators/http4s-stir/issues). Ensure you provide as much detail as possible:

1. **For issues:**
  - Describe the issue you're facing.
  - Steps to reproduce.
  - Expected behavior.
  - Actual behavior.

2. **For feature requests:**
  - Describe the feature and why you believe it would be useful.
  - Any reference or example from other projects/tools, if applicable.

By providing detailed information, you'll help us address your concerns more efficiently.

Thank you for your contributions and for helping make this project better for everyone!

## License

http4s-stir is under the Apache License, Version 2.0 ("the License"). You must comply with this License to use this software. A [full license text](https://github.com/theiterators/http4s-stir/blob/master/LICENSE) is available in the repository.

## Acknowledgements

http4s-stir incorporates significant portions of code adapted from [Pekko HTTP](https://github.com/apache/incubator-pekko-http), a fork of [Akka HTTP](https://github.com/akka/akka-http).
