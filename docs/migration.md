---
id: migration
title: Migration from Pekko/Akka HTTP
sidebar_position: 26
---

# Migration from Pekko/Akka HTTP

This guide covers the key differences between Pekko HTTP (or Akka HTTP) and http4s-stir, and lists the directives and features that are not yet implemented.

## Key differences

| Concept | Pekko HTTP | http4s-stir |
|---|---|---|
| Effect type | `Future` | `IO` (cats-effect) |
| Streams | Akka Streams (`Source` / `Flow` / `Sink`) | fs2 (`Stream` / `Pipe`) |
| Server bootstrap | `ActorSystem` + `Http().bindAndHandle` | `EmberServerBuilder` |
| Marshalling | spray-json / akka-http-circe | http4s-circe (`EntityEncoder` / `EntityDecoder`) |
| Route type | `Route = RequestContext => Future[RouteResult]` | `Route = RequestContext => IO[RouteResult]` |
| Configuration | `application.conf` / `ActorSystem` settings | cats-effect `IOApp` |
| Future directives | `onComplete(future)` / `onSuccess(future)` | `onComplete(io)` / `onSuccess(io)` (same names, `IO` instead of `Future`) |

## Directive mapping

Most directives have the **same names and signatures** as their Pekko HTTP counterparts. The primary difference is that `Future` is replaced by `IO` throughout. For example:

- `onComplete(io)` replaces `onComplete(future)`
- `onSuccess(io)` replaces `onSuccess(future)`
- `complete(IO(...))` replaces `complete(Future(...))`
- `entity(as[T])` works identically, using http4s `EntityDecoder` instead of Akka `Unmarshaller`

Path directives, method directives, parameter directives, header directives, and most other directive categories are API-compatible. Code migration typically involves updating imports and replacing `Future` with `IO`.

## What is not implemented

The following directive categories and individual directives are **not available** in http4s-stir:

### Unimplemented directive categories

| Category | Status |
|---|---|
| `RangeDirectives` | Not implemented (trait is empty) |
| `FramedEntityStreamingDirectives` | Not implemented (trait is empty) |

### Unimplemented individual directives

- **FileAndResourceDirectives**: directory listing is not supported
- `withPrecompressedMediaTypeSupport`
- `extractRequestTimeout` / `withRequestTimeoutResponse`

## Behaviour changes in 0.7

- `EntityRejection` is gone. `entity(as[T])` rejects an unsupported `Content-Type` with
  `UnsupportedRequestContentTypeRejection` (415), an unparseable body with `MalformedRequestContentRejection`
  (400) and an `IllegalArgumentException` from the decoder with `ValidationRejection` (400).
- `as[String]` accepts every content type, through an `EntityDecoder[IO, String]` the directives bring into
  scope. A spec that mixes in `Directives` decodes `responseAs[String]` with it too; code that defines its own
  `EntityDecoder[IO, String]` next to the directives must hide one of the two.
- `getFromResource` and `getFromFile` reject when nothing is found instead of completing 404 or 500.
- `authenticateBasic*` and `authenticateOAuth2*` treat credentials of another scheme as missing, so
  `.optional` and `withAnonymousUser` apply.
- The testkit builds request bodies with `withEntity`, so requests carry `Content-Type` and `Content-Length`.
- `fileUpload` and friends no longer require a part `Content-Type`; a missing one is `application/octet-stream`.

## Testkit differences

The http4s-stir testkit differs from the Pekko HTTP testkit in the following ways:

- **Synchronous execution**: all test assertions run synchronously; there is no async test support.
- **No chunk/streaming support**: streamed or chunked responses cannot be tested incrementally.
- **Limited request building**: some minor header convenience methods available in the Pekko HTTP testkit are missing.
- **WebSocket testing**: `isWebSocketUpgrade` and `expectWebSocketUpgradeWithProtocol` work as in Pekko for the handshake decision; the testkit cannot exchange frames.
