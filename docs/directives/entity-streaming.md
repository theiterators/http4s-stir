---
id: directives-entity-streaming
title: Entity Streaming Directives
sidebar_position: 25
---

# Entity Streaming Directives

Framed entity streaming reads a request body as a stream of elements and renders a stream of elements as a
response body, with the framing described by an `EntityStreamingSupport` in implicit scope.
`EntityStreamingSupport.json()` splits a JSON array (or newline-separated objects) into one object per element and
renders responses as a JSON array; `EntityStreamingSupport.csv()` works line by line. Both take a maximum frame
size (8 KiB by default); a larger frame fails the request with `400 Bad Request`.

## asSourceOf

An `EntityDecoder` for `entity` that yields a `fs2.Stream[IO, T]`. Each frame is decoded as UTF-8 text with an
in-scope `Unmarshaller[String, T]`. A `Content-Type` the support does not accept rejects with an
`UnsupportedRequestContentTypeRejection`.

```scala
import io.circe.parser.decode
import pl.iterators.stir.common.EntityStreamingSupport
import pl.iterators.stir.unmarshalling.Unmarshaller

case class Measurement(id: String, value: Double)

implicit val measurementUnmarshaller: Unmarshaller[String, Measurement] =
  Unmarshaller.strict(json => decode[Measurement](json).fold(throw _, identity))
implicit val jsonStreaming: EntityStreamingSupport = EntityStreamingSupport.json()

val route: Route = post {
  entity(asSourceOf[Measurement]) { measurements =>
    onSuccess(measurements.map(_.value).compile.foldMonoid) { total =>
      complete(s"Total: $total")
    }
  }
}
```

`asSourceOf[T](support)` takes the support explicitly. Combine with `withoutSizeLimit` or a generous
`withSizeLimit` for unbounded uploads.

## Completing with a stream

With an `EntityStreamingSupport` in scope, `complete` accepts a `fs2.Stream[IO, T]` for any `T` with an
`EntityEncoder`; each element is encoded and the frames are joined by the support, which also sets the response
content type.

```scala
import fs2.Stream
import org.http4s.circe.CirceEntityEncoder._

implicit val jsonStreaming: EntityStreamingSupport = EntityStreamingSupport.json()

val route: Route = get {
  complete(Stream.emits(List(Measurement("a", 1.0), Measurement("b", 2.5))).covary[IO])
}
// [{"id":"a","value":1.0},{"id":"b","value":2.5}]
```
