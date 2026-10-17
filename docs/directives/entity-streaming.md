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

### Failures

Framing errors (an object or line over the limit, a body that is not an array of objects, trailing content after the
last object, a body cut off inside an object) fail the element stream with a `400 Bad Request`
`IllegalRequestException` as soon as the offending byte is read; the elements before it are still delivered. When
the route consumes the stream before completing, as above, the exception reaches the `ExceptionHandler` and the
client gets the 400. When the route streams the elements straight into the response, the status has already been
sent and the response body fails instead. An element the `Unmarshaller` cannot decode fails the stream with the
unmarshaller's own exception (a 500 under the default handler); throw an `IllegalRequestException` from the
unmarshaller to answer with a 400.

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

## Newline separated rendering

`withFramingRenderer` replaces the renderer, as Pekko's `withFramingRenderer` does. Newline separated JSON objects
instead of an array:

```scala
import fs2.{ Chunk, Pipe }

val newlineSeparated: Pipe[IO, Chunk[Byte], Byte] =
  frames => frames.flatMap(frame => Stream.chunk(frame) ++ Stream.emit('\n'.toByte))

implicit val ndjsonStreaming: EntityStreamingSupport = EntityStreamingSupport.json().withFramingRenderer(newlineSeparated)
// {"id":"a","value":1.0}
// {"id":"b","value":2.5}
```

The renderer receives one encoded element per chunk and produces the response bytes; the content type stays the
support's (`withContentType` changes it).

## Parallel (un)marshalling

Elements are encoded and decoded one at a time by default. `withParallelMarshalling(parallelism, unordered)`, as
Pekko's, lets `asSourceOf` unmarshal and the stream marshaller encode up to `parallelism` elements at once, through
fs2's `parEvalMap`; with `unordered = true` (`parEvalMapUnordered`) an element is emitted as soon as it is ready, so
a large element does not hold back the smaller ones after it, at the price of the order.

```scala
implicit val parallelStreaming: EntityStreamingSupport =
  EntityStreamingSupport.json().withParallelMarshalling(parallelism = 4, unordered = false)
```

`parallelism` must be at least 1. Parallelism only pays when the `Unmarshaller` or `EntityEncoder` does real work
per element, such as a database lookup or a large serialisation; the framing itself is sequential.
