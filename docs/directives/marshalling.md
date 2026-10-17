---
id: directives-marshalling
title: Marshalling Directives
sidebar_position: 11
---

# Marshalling Directives

Marshalling directives handle the conversion between HTTP request/response bodies and Scala types. http4s-stir uses the http4s `EntityDecoder` and `EntityEncoder` type classes under the hood, making it compatible with any http4s marshalling ecosystem including Circe, jsoniter, and others.

## entity

Unmarshals the request body to the given type `T`. Requires an implicit `EntityDecoder[IO, T]` in scope.

If the request carries a `Content-Type` the decoder does not accept, the request is rejected with an
`UnsupportedRequestContentTypeRejection` (415 when sealed); a request without `Content-Type` is decoded anyway.
A body the decoder cannot parse yields a `MalformedRequestContentRejection` (400), and a decoder failure caused
by an `IllegalArgumentException` (for example a `require` in a constructor) yields a `ValidationRejection` (400).
A request known to carry no body (`Content-Length: 0`, or an HTTP/1.x request with neither a length nor chunking)
that the decoder cannot decode yields a `RequestEntityExpectedRejection` (400, "Request entity expected but not
supplied") instead, as Pekko's unmarshallers do on an empty entity; `as[String]` decodes it as `""`. The check reads
the headers, not the body: a chunked body with zero bytes and a length-less HTTP/2 body are not recognised as empty,
so their decoder failures are reported as they are (`requestEntityEmpty`/`requestEntityPresent` make the opposite
call for HTTP/2, see the misc directives). The 415 and the "entity expected" rejections are cancelled when
a later route alternative accepts the request.

`as[String]` accepts every content type, as in Pekko HTTP; the directives provide their own
`EntityDecoder[IO, String]` for that, which takes precedence over http4s' `text/*` decoder.

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

## Content negotiation

`complete` negotiates the produced `Content-Type` against the request's `Accept` header the way Pekko's marshallers
do: without an `Accept` header everything is acceptable; otherwise the most specific matching media range decides, a
quality of `0` meaning "not acceptable". (Two differences from Pekko for wildcard ranges with parameters: stir
ignores a `charset` parameter on every range, while Pekko ignores it only on `type/subtype` ranges, so its
`text/*;charset=utf-8` never matches; and Pekko counts a wildcard range's parameters towards its specificity.) A
representation the client does not accept is rejected with an
`UnacceptedResponseContentTypeRejection` (406 when sealed, listing the types the route can produce), so a later
alternative that produces an accepted type can still complete the request:

```scala
val route: Route = complete(user) ~ complete(user.toString) // JSON, or plain text for `Accept: text/plain`
```

Only the marshallers built on an `EntityEncoder` negotiate (`complete(value)`, `complete(status, value)`,
`complete(status, headers, value)`, `IO`s of those, streams); an explicit `Response[IO]` or a bare `Status` is sent as
it is. With a status, only a 2xx response is negotiated: `complete(Status.BadRequest, "...")` reaches a client that asks
for JSON, as Pekko adds an opaque fallback for non-2xx statuses (eagerly completing with an error message is common). A custom `ToResponseMarshaller` is opaque unless it is built with `ToResponseMarshaller.negotiated(...)` or
delegates to a negotiating one: the mark travels with the produced response. A range with parameters
(`application/json;version=2`) matches only a type that carries them, except a `charset` parameter, which is ignored
as Pekko ignores it; `Accept-Charset` is not negotiated, as http4s encoders have a fixed charset. Negotiation happens
after the value is computed, so `complete(dbCall)` runs the call before rejecting, like Pekko's `Future` marshaller.

Exception and rejection handlers run with `RequestContext.withAcceptAll` (Pekko's name), so their responses always
reach the client. The handler still sees the request's `Accept` header and can choose a representation by it; its
response is never rejected, where Pekko appends `*/*;q=MIN` to the header and a `q=0` range can still reject the
handler's response. This is a behaviour change in 0.7 for clients that send a restrictive `Accept`
(`Accept: application/json` to a route answering `complete("text")` now gets a 406); to keep the
old "always render" behaviour for a route or a whole API, run it with every representation acceptable:

```scala
val legacy: Route = mapRequestContext(_.withAcceptAll) {
  complete(Status.NotFound, "not found") // sent whatever `Accept` says
}
```

## completeWith

Uses the marshaller for the given type to produce a completion function that is passed to the inner block, as Pekko's
`completeWith`. The function may be called from any thread; the first call completes the request and later calls are
ignored (Pekko throws on a second call), and the request never completes when it is not called at all. An exception
thrown by the block reaches `handleExceptions`:

```scala
val route: Route = completeWith(instanceOf[String]) { send =>
  send("done")
}
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
