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

Every Pekko HTTP directive category has a counterpart in http4s-stir. The few directives without one are tied to
the Pekko runtime and have no meaning on http4s: `withExecutionContext` / `extractExecutionContext`,
`withMaterializer` / `extractMaterializer`, `extractActorSystem`, `withLog` / `extractLog`, `withSettings` /
`mapSettings` / `extractSettings` / `extractParserSettings`, `onCompleteWithBreaker` (needs a circuit breaker) and
`completeWith` (promise based; use `complete(io)`). Settings-driven behaviour (range limits, strict form timeout,
size limits) is set per directive instead.

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
- `getFromFile`, `getFromResource`, `getFromDirectory`, `getFromResourceDirectory` and the browseable directory
  directives take an implicit `ContentTypeResolver`; code that passed them as function values (`xs.map(getFromDirectory)`)
  now needs `xs.map(getFromDirectory(_))`.
- `entity(as[T])` on a request known to have no body rejects with `RequestEntityExpectedRejection` (400, "Request
  entity expected but not supplied") instead of `MalformedRequestContentRejection`, as Pekko does; `as[String]`
  still decodes it as `""`.
- `parameterSeq` and `formFieldSeq` return the pairs in the order of the query string or body, as Pekko does, and
  `parameterSeq` includes a parameter without a value (`?flag`) as `("flag", "")` instead of dropping it.
- The form directives parse url-encoded bodies themselves instead of through http4s' `UrlForm` decoder: `;` no
  longer separates fields (`a=1;b=2` is one field, as in Pekko) and characters that http4s rejected with a 400 when
  unencoded (spaces, quotes, brackets, non-ASCII) are accepted literally.
- `complete` negotiates the response's `Content-Type` against the request's `Accept` header, as Pekko does: a
  representation the client does not accept is rejected with `UnacceptedResponseContentTypeRejection` (406 when sealed,
  "Resource representation is only available with these types:" followed by the types). Clients sending `*/*` or no
  `Accept` see no difference; a client sending `Accept: application/json` to `complete(Status.NotFound, "not found")`
  now gets a 406 instead of the text. Exception and rejection handlers are exempt. To keep the old behaviour for a
  route, wrap it in `mapRequestContext(_.withAcceptAll)`; see "Content negotiation" in the marshalling directives.

## Testkit differences

The http4s-stir testkit differs from the Pekko HTTP testkit in the following ways:

- **Synchronous execution**: all test assertions run synchronously; there is no async test support.
- **Chunk inspection**: `chunks`, `chunksStream` and `trailer` expose the body chunks and trailer headers; there is no `ChunkStreamPart` model.
- **Limited request building**: some minor header convenience methods available in the Pekko HTTP testkit are missing.
- **WebSocket testing**: `isWebSocketUpgrade` and `expectWebSocketUpgradeWithProtocol` work as in Pekko for the handshake decision; the testkit cannot exchange frames.
