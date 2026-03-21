---
id: directives-marshalling
title: Marshalling Directives
sidebar_position: 11
---

# Marshalling Directives

Marshalling directives handle the conversion between HTTP request/response bodies and Scala types. http4s-stir uses the http4s `EntityDecoder` and `EntityEncoder` type classes under the hood, making it compatible with any http4s marshalling ecosystem including Circe, jsoniter, and others.

## entity

Unmarshals the request body to the given type `T`. Requires an implicit `EntityDecoder[IO, T]` in scope. If unmarshalling fails, the request is rejected with an `EntityRejection`.

```scala
import org.http4s.EntityDecoder

case class User(name: String, age: Int)

val route: Route = path("users") {
  post {
    entity(as[User]) { user =>
      complete(s"Created user: ${user.name}")
    }
  }
}
```

## as

A helper that returns the in-scope `EntityDecoder[IO, T]` for the given type. It is used as the argument to `entity`:

```scala
entity(as[String]) { body =>
  complete(s"Body: $body")
}
```

## handleWith

Combines unmarshalling the request body, applying a function, and marshalling the result into a single directive. Requires both an implicit `EntityDecoder[IO, A]` for the input type and a `ToResponseMarshaller[B]` for the output type.

```scala
case class Request(query: String)
case class Response(result: String)

val process: Request => Response = req => Response(s"Result for: ${req.query}")

val route: Route = path("process") {
  post {
    handleWith(process)
  }
}
```

## Circe Integration

For JSON marshalling with Circe, import the http4s Circe entity codecs. Any type with a Circe `Encoder` or `Decoder` in scope will automatically gain `EntityEncoder` and `EntityDecoder` instances.

```scala
import io.circe.generic.auto._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.circe.CirceEntityDecoder._

case class Item(name: String, price: Double)

val route: Route = path("items") {
  post {
    entity(as[Item]) { item =>
      complete(Item(item.name, item.price * 1.1))
    }
  }
}
```

## ToResponseMarshaller

`ToResponseMarshaller[T]` is a type class that defines how a value of type `T` is converted into an `IO[Response[IO]]`. It is used by the `complete` directive to produce HTTP responses. The following implicit instances are provided:

- **`EntityEncoder[IO, E]`** -- any type with an http4s `EntityEncoder` can be completed directly. The response uses status `200 OK`.
- **`(Status, E)`** -- a tuple of HTTP status and entity produces a response with the given status.
- **`(Status, Headers)`** -- a tuple of status and headers produces a response with no body.
- **`(Status, Headers, E)`** -- a tuple of status, headers, and entity produces a fully specified response.
- **`Status`** -- a bare status code produces an empty-body response.
- **`IO[E]`** -- an `IO` wrapping any marshallable type is flatMapped to produce the response.
- **`Response[IO]`** -- a raw http4s `Response[IO]` is passed through directly.

```scala
import org.http4s.Status
import org.http4s.Headers
import org.http4s.headers.`Content-Type`
import org.http4s.MediaType

// Complete with just a body (200 OK)
complete("Hello, world!")

// Complete with a status code and body
complete((Status.Created, """{"id": 1}"""))

// Complete with status, headers, and body
complete((Status.Ok, Headers(`Content-Type`(MediaType.application.json)), """{"ok": true}"""))

// Complete with just a status code
complete(Status.NoContent)

// Complete with an IO value
complete(IO.pure("Async result"))
```

## ToResponseMarshallable

`ToResponseMarshallable` is a wrapper that pairs a value with its `ToResponseMarshaller` instance. The `complete` directive accepts `ToResponseMarshallable`, and any type `T` with a `ToResponseMarshaller[T]` in scope is implicitly converted to `ToResponseMarshallable`.

## instanceOf

A helper that summons the implicit `ToResponseMarshaller[T]` for a given type. Useful when you need to pass a marshaller explicitly.

```scala
val stringMarshaller: ToResponseMarshaller[String] = instanceOf[String]
```

## Unmarshaller

`Unmarshaller[A, B]` is a type class for converting a value of type `A` into `IO[B]`. It is used internally by the parameter, form field, and path directives for type conversion from `String` values.

Key operations:

- **`map[C](f: B => C)`** -- transforms the output type.
- **`flatMap[C](f: B => IO[C])`** -- chains with an effectful transformation.
- **`andThen[C](other: Unmarshaller[B, C])`** -- composes two unmarshallers sequentially.
- **`withDefaultValue[BB >: B](default: BB)`** -- provides a fallback when no content is available.
- **`recover[C >: B](pf: PartialFunction[Throwable, C])`** -- handles errors with a partial function.

Built-in unmarshallers are provided for standard types such as `String`, `Int`, `Long`, `Double`, `Float`, `Short`, `Byte`, `Boolean`, and `BigDecimal`.
