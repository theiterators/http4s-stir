---
id: directives-coding
title: Coding Directives
sidebar_position: 23
---

# Coding Directives

Coding directives compress responses and decompress requests with `gzip` and `deflate` through fs2's
`Compression`, on every platform.

## encodeResponse

Encodes the response with the coding the client prefers in `Accept-Encoding`, choosing between `identity`,
`gzip` and `deflate` (in that order on ties). Without the header, or when the client lists neither `gzip` nor
`deflate`, the response is left alone; it is rejected with an `UnacceptedResponseEncodingRejection` (406 when
sealed) only when the client explicitly refuses `identity` as well. Responses that already carry
`Content-Encoding`, have no entity, or have a non-compressible media type are never touched. `Content-Length` is
removed from an encoded response.

```scala
val route: Route = encodeResponse {
  complete("a large text body")
}
```

## encodeResponseWith

Like `encodeResponse` but with an explicit list of codings. Without `Accept-Encoding` the first one is used;
when the client accepts none of them the request is rejected with an `UnacceptedResponseEncodingRejection`
(406 when sealed).

```scala
import org.http4s.ContentCoding

val route: Route = encodeResponseWith(ContentCoding.gzip) {
  complete("always gzipped unless refused")
}
```

## responseEncodingAccepted

Passes when the client lists the given coding (or `*`) with a positive q-value in `Accept-Encoding`, or sends no
`Accept-Encoding` at all; rejects with an `UnacceptedResponseEncodingRejection` otherwise.

## decodeRequest

Decompresses a `gzip` or `deflate` encoded request body before the inner route reads it; unencoded requests and
requests known to carry no body (`Content-Length: 0`, or HTTP/1.x with neither a length nor chunking) pass through. Any other `Content-Encoding` is rejected with
`UnsupportedRequestEncodingRejection`s (400 when sealed). A corrupt compressed body is reported as a
`MalformedRequestContentRejection` by `entity`. The decoded request carries `Transfer-Encoding: chunked` in place
of its `Content-Length`, so `requestEntityPresent` still sees a body. Put `withSizeLimit` *inside* `decodeRequest`
to bound the decompressed size; outside it only bounds the compressed bytes.

```scala
val route: Route = decodeRequest {
  entity(as[String]) { body => complete(s"${body.length} chars") }
}
```

## decodeRequestWith

`decodeRequestWith(coding)` accepts only requests encoded with that coding (or any request when the coding is
`identity`). `decodeRequestWith(codings: _*)` tries each in turn.

## requestEncodedWith

Rejects with an `UnsupportedRequestEncodingRejection` unless the request's `Content-Encoding` (`identity` when
absent) matches the given coding.

## withPrecompressedMediaTypeSupport

Adds `Content-Encoding: gzip` to a response that one of the static file directives served from a `.svgz` file
(the only precompressed media type Pekko recognises) when no `Content-Encoding` is set yet. http4s media types
carry no "gzipped" flag, so the directive looks at the served file name instead. `encodeResponse` never compresses
such a response, so the two directives can be nested in either order.

```scala
val route: Route = pathPrefix("static") {
  withPrecompressedMediaTypeSupport {
    getFromDirectory("/var/www/static")
  }
}
```
