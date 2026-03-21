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
| `AttributeDirectives` | Not implemented (trait is empty) |
| `CacheConditionDirectives` | Not implemented (trait is empty) |
| `CodingDirectives` | Not implemented (trait is empty) |
| `RangeDirectives` | Not implemented (trait is empty) |
| `FramedEntityStreamingDirectives` | Not implemented (trait is empty) |

### Unimplemented individual directives

- **FormFieldDirectives**: multipart form handling is not supported
- **FileAndResourceDirectives**: directory listing is not supported
- `withSizeLimit` / `withoutSizeLimit`
- `requestEntityEmpty` / `requestEntityPresent`
- `rejectEmptyResponse`
- `extractRequestTimeout` / `withRequestTimeoutResponse`

## Testkit differences

The http4s-stir testkit differs from the Pekko HTTP testkit in the following ways:

- **Synchronous execution**: all test assertions run synchronously; there is no async test support.
- **No chunk/streaming support**: streamed or chunked responses cannot be tested incrementally.
- **Limited request building**: some minor header convenience methods available in the Pekko HTTP testkit are missing.
- **No WebSocket testing**: the testkit does not support WebSocket route testing.
