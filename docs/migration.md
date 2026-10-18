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

Path directives, method directives, parameter directives, header directives, and most other directive categories are
API-compatible. Code migration typically involves updating imports and replacing `Future` with `IO`. The exceptions
whose signatures differ:

- WebSocket: the directives take the server's `WebSocketBuilder2[IO]` and work on http4s `WebSocketFrame`s
  (`handleWebSocketMessages(ws, pipe)`); `extractWebSocketUpgrade` is replaced by the builder.
- Security: authenticators receive stir's `CredentialsHelper` (wrapping http4s' `Credentials`) where Pekko passes its
  `Credentials`.
- Debugging: `logRequest`, `logResult` and `logRequestResult` take named options (`logHeaders`, `logBody`,
  `redactHeadersWhen`, `maxBodyBytes`, `logAction`, `redaction`) instead of Pekko's `LoggingMagnet` (a marker string
  and log level), so `logRequest("marker")` does not compile (see the debugging directives).
- `listDirectoryContents` takes stir's implicit `DirectoryRenderer`, not Pekko's `DirectoryRenderer` marshaller.

## What is not implemented

Every Pekko HTTP directive category has a counterpart in http4s-stir. The few directives without one are tied to
the Pekko runtime and have no meaning on http4s: `withExecutionContext` / `extractExecutionContext`,
`withMaterializer` / `extractMaterializer`, `extractActorSystem`, `withLog` / `extractLog`, `withSettings` /
`mapSettings` / `extractSettings` / `extractParserSettings` and `onCompleteWithBreaker` (needs a circuit breaker).
Settings-driven behaviour (range limits, strict form timeout, size limits, the decode limit) is set per directive
instead. Other differences that remain:

- `Marshaller.oneOf` and `Accept-Charset` negotiation are not ported: http4s encoders have one content type and a
  fixed charset. Chain `complete(json) ~ complete(text)` to offer several representations.
- WebSocket `permessage-deflate` (the `shouldCompress` overloads) needs server support http4s 0.23 lacks.
- The resource directives (`getFromResource`, `getFromResourceDirectory`) only work on the JVM: on Scala Native and
  Scala.js an application using them fails to link (no `java.net.URL`; Native's class loader has only
  `getResourceAsStream`).
- `complete(status)` and `complete((status, headers))` for a status that allows an entity send an empty body, and so
  does the default exception handler's 500; Pekko sends the status's default message as `text/plain` (for example
  "The requested resource could not be found." for 404, "There was an internal server error." for 500).
- `HeaderMagnet` and `ModeledCustomHeader` are not ported: `headerValueByType[T]` and `optionalHeaderValueByType[T]`
  use http4s' `Header[T, _]` type class; define a custom header with `Header.create`.
- `StrictForm` and `StrictForm.FileData` are not ported: the form-field directives read the text parts of a multipart
  body and skip a part with a `filename` (Pekko returns its content); read file parts with `fileUpload` /
  `storeUploadedFile`.
- `RejectionError` is not ported: a failed `IO` always reaches the exception handler; to reject, recover the `IO` and
  `reject(...)` (e.g. with `onComplete`).
- `toStrictEntity` / `extractStrictEntity` answer an entity over `maxBytes` with 413 Payload Too Large; Pekko answers
  400 "Request too large".
- The default exception handler logs an `IllegalRequestException` as "Error during processing of request: '...'.
  Completing with 409 Conflict response. ..." like other errors; Pekko warns "Illegal request: '...'. ...".
- `toStrictEntity` / `extractStrictEntity` answer a request body stream that fails (other than by the size limit or
  the timeout) with a 500 from the exception handler; Pekko answers its parser's `EntityStreamException` with 400.
- `parameter("x".repeated)` returns the values in the order of the query string; Pekko returns them reversed.
- A header that http4s cannot parse is ignored where Pekko may parse it leniently (`selectPreferredLanguage` reads
  `Accept-Language` q-values as leniently as Pekko).
- There is no default request-body size limit (Pekko: `max-content-length`, 8 MiB): `entity(as[String])` reads a body
  of any size. Wrap the routes that read bodies, or the whole route, in `withSizeLimit`
  (`withSizeLimit(8L * 1024 * 1024)(route).toHttpApp`).
- The testkit's `~>` sends the request URI as given, with no default host (Pekko's testkit uses
  `http://example.com`): `extractUri` is relative and `host(...)` needs the request to name its host.
- `withRequestTimeout` cancels the inner route when it fires and its default answer is an empty 503; Pekko keeps the
  route running and answers with a text body.
- The CSV framing of `EntityStreamingSupport.csv()` drops empty lines, strips a `\r` before `\n` and accepts a last
  line without a newline; Pekko's line framing does none of these.
- stir has no `Option`, `Either` or `Try` marshallers. With circe's codecs in scope `complete(option)` still compiles
  but sends `null` with 200 where Pekko answers 404 for `None`, and `as[Option[T]]` decodes a JSON `null` rather
  than an empty body; match on the option and complete explicitly.
- `extractUri` gives the request URI as received (relative under HTTP/1); Pekko gives the effective, absolute URI.
- An empty file is served with the `Content-Type` of its extension; Pekko sends an empty entity without one.
- Over Ember's HTTP/2 a request carrying its host only in `:authority` (prior knowledge, HTTP/2 over TLS) has no host
  for the host directives (`extractHost` gives `""`, `host(...)` rejects): Ember 0.23.36 drops `:authority`.
- A request whose URI authority and `Host` header disagree is answered 400 by Pekko; stir names no host for it
  (`extractHost` gives `""`, so `host(...)` rejects).
- `extractClientIP` skips an `X-Forwarded-For` whose first entry is not an address (`unknown`) and goes on to
  `X-Real-Ip` and the connection; Pekko returns `RemoteAddress.Unknown` for it.
- Content negotiation differs from Pekko for wildcard ranges with parameters: stir ignores `charset` on every range
  (Pekko only on `type/subtype` ranges, so its `text/*;charset=...` never matches) and does not count a wildcard
  range's parameters towards its specificity.
- `fileUploadAll` creates its temporary files with `File.createTempFile` (the default permissions minus the umask);
  Pekko creates them owner-only. The spool files of the other upload directives are owner-only.
- `Route.seal` takes no log action; use `handleExceptions(ExceptionHandler.seal(handler)(logAction))` around the route.

## Behaviour changes in 0.7.1

- `CredentialsHelper.Provided#verify` compares the secrets character by character. It compared the low 8 bits of
  each character, as Pekko does, so a password whose characters differ from the secret's only in their high byte was
  accepted (`Aukasz` for `Łukasz`). Correct passwords verify as before.
- `extractClientIP` no longer reads a `Remote-Address` header (Pekko ignores it too): a client could send one and
  choose the address it reported. Without a usable `X-Forwarded-For` or `X-Real-Ip` it gives the connection's address.
  Those two headers can be sent by clients too, so use `extractDirectClientIP` for access control unless a proxy sets
  or strips them.
- Literal path segments are compared decoded, as Pekko does: `path("café")` also matches `/caf%c3%a9` (lower-case
  escapes) and `path("foo")` matches `/fo%6F`. They were compared in http4s' encoding, so only the escapes it
  produces itself matched.
- `selectPreferredLanguage` compares language tags case-insensitively, as Pekko does: `Accept-Language: EN-us`
  selects `en-US`. It still returns the alternative as it was given. When equally specific ranges match the same
  language (`en;q=0.1, EN`) the higher q-value counts, as in Pekko; the first one in the header used to.
- `Segment`, `Segments` and regex path matchers no longer match the empty segment of a `//`, as Pekko does:
  `pathPrefix("a" / Segment)` rejects `/a//b` (it extracted `""`), and `Segments` stops before an empty segment.

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
- `parameterMap` keeps the last value of a repeated parameter (`?x=1&x=2` gives `x -> 2`, was `1`) and
  `parameterMultiMap` lists a parameter without a value (`?flag`) with one empty value (was none), as Pekko does; a
  bare `?` gives empty maps (was `Map("" -> "")` and `Map("" -> List())`). So `?flag=on&flag` now gives `flag -> ""`.
- The parameter directives parse the query string themselves, as Pekko does, instead of through http4s' `Query`: `;`
  no longer separates parameters (`?a;b=c` is the parameter `a;b`; a query rebuilt through http4s' `Query` API,
  e.g. by `withQueryParam` in a middleware, was already split there), `parameter("x")` takes the first occurrence even
  when it has no value (`?x&x=1` gives `""`, was `"1"`), and `.repeated` lists an occurrence without a value as `""`
  (was skipped).
- Logged request URIs mask a sensitive query value up to the next `&`, as the parameter directives now read it, and keep
  the query as received: `?token=t;user=u` logs as `?token=REDACTED` (was `?token=REDACTED&user=u`).
- The form directives parse url-encoded bodies themselves instead of through http4s' `UrlForm` decoder: `;` no
  longer separates fields (`a=1;b=2` is one field, as in Pekko) and characters that http4s rejected with a 400 when
  unencoded (spaces, quotes, brackets, non-ASCII) are accepted literally.
- `IntNumber`, `LongNumber`, `HexIntNumber`, `HexLongNumber` and one-group regex matchers leave an absolute
  remainder, so `IntNumber / "x"` matches `/42/x` as in Pekko. All number and regex matchers (including
  `DoubleNumber` and `JavaUUID`) no longer skip an unconsumed leading slash: `rawPathPrefix(IntNumber)` rejects `/23`,
  `"a" ~ IntNumber` rejects `/a/23`, and `pathSuffix(IntNumber)` leaves `/orders/` for `/orders/123` and rejects
  `/orders/123/`.
- `respondWithHeader(s)` adds its headers in front of the inner route's and keeps a same-named header the route
  set, as Pekko does; 0.6.1 dropped the added header in that case. To replace a header, use
  `mapResponseHeaders(_.put(header))`. `respondWithDefaultHeader(s)` adds a header only when the route did not set
  it, and now puts the missing defaults first, as Pekko does (0.6.1 appended them).
- `ExceptionHandler.seal(handler)(logAction)` passes the log action to the default fallback; 0.6.1 ignored it.
- `overrideMethodWithParameter` answers `501 Not Implemented` for an unknown method name and matches names
  independently of the default locale.
- `withRangeSupport` and the coding directives (`decodeRequest`, `encodeResponse` and friends) are new in 0.7.
  `withRangeSupport` rejects a `Range` request for a response of unknown length with
  `UnsatisfiableRangeRejection(ranges, -1)` (416 when sealed); `decodeRequest` bounds the decoded body to 8 MiB
  (`CodingDirectives.DefaultDecodeMaxBytes`, Pekko's `decode-max-size`, 413 above it) and answers a corrupt
  compressed body with 400 (`MalformedRequestContentRejection` from `entity`), as Pekko does.
- The disk-based upload directives no longer decode the whole multipart body in memory: `storeUploadedFile(s)` and
  `fileUploadAll` spool parts above 64 KiB to disk, `fileUploadSpooled` above 50 MiB, and a body with more than 1000
  parts is rejected with a 400.
- `fileUploadAll` deletes its temporary files once the response body has been consumed, or as soon as the route
  rejects, fails or is cancelled; a part stream kept past the request fails. 0.6.1 kept each file until its stream was
  read and relied on `deleteOnExit`.
- The testkit bounds `~>` and `~!>` with an implicit `RouteTestTimeout`, 1 second by default as in Pekko: a route
  that does not answer in time fails the test with "Request was neither completed nor rejected within 1 second", and
  reading the response (`response`, `responseAs`, `chunks`, `trailer`) is bounded the same way ("Response entity was
  not read within ...").
  Declare `implicit val routeTestTimeout: RouteTestTimeout = RouteTestTimeout(5.seconds)` for slower routes.
- The static file and resource directives type content like Pekko: text types carry `charset=UTF-8`, an unknown
  extension is `application/octet-stream`, and `name.ext.gz` / `.svgz` are served with the inner type and
  `Content-Encoding: gzip`. They answer conditional requests (`ETag`, `Last-Modified`, 304 and 412) and `Range`
  requests (206, 416) as Pekko does.
- `getFromResource` rejects a directory entry inside a jar instead of serving it as an empty body.
- `getFromResource` / `getFromResourceDirectory` reject a directory on a class-path folder whose path has a space or a
  non-ASCII character; they used to serve the JDK's listing of its file names.
- `logRequestResultWith(show)` is new; a `show` that fails fails the request, as in Pekko.
- `complete(status, value)` (with or without headers) sends no entity when the status allows none (1xx, 204, 205, 304),
  as Pekko does; the value used to be sent as the body. Without an entity such a response is not negotiated either
  (Pekko still answers a 204 with an unacceptable value's type with 406).
- `headerValueByType[T]` rejects with `MissingHeaderRejection` naming the header as its http4s `Header` instance does
  (`X-Custom-Header`), as Pekko names a modeled custom header; it used the Scala class name (`XCustomHeader`), which
  only matched for http4s' own header classes. It now needs a `Header[T, _]` instance instead of a `ClassTag`.
- `entity` rejects every failure, as Pekko does: a request body stream that fails (a dropped connection, a broken
  upstream) is a `MalformedRequestContentRejection` carrying the error, and a decoder raising an
  `IllegalArgumentException` a `ValidationRejection`; both used to reach the exception handler as a 500. So does any
  other error a custom `EntityDecoder` raises (a 400 rejection where it used to be a logged 500), and an I/O error
  while http4s spools a large multipart part to a temporary file. `formFieldSeq`, `formFieldMap` and
  `formFieldMultiMap` likewise reject a body that fails while it is buffered (a body over the size limit still
  answers 413, a slow one 408); a corrupt compressed form body now reaches the rejection handler, whose 400 reads
  "The request content was malformed:" followed by the message.
- `complete` negotiates the response's `Content-Type` against the request's `Accept` header, as Pekko does: a
  representation the client does not accept is rejected with `UnacceptedResponseContentTypeRejection` (406 when sealed,
  "Resource representation is only available with these types:" followed by the types). Clients sending `*/*` or no
  `Accept` see no difference; a client sending `Accept: application/json` to `complete("text")` now gets a 406 instead
  of the text. Error responses (4xx, 5xx: `complete(Status.NotFound, "not found")`) are sent whatever the client
  accepts, as Pekko sends them through an opaque fallback, and exception and rejection handlers are exempt. To keep the old behaviour for a
  route, wrap it in `mapRequestContext(_.withAcceptAll)`; see "Content negotiation" in the marshalling directives.
- `selectPreferredLanguage` reads `Accept-Language` q-values as leniently as Pekko: `q=.5` and spaces around `;` and
  `=` are accepted (the header used to be ignored, picking the first language), and a q-value with more than three
  decimals is truncated instead of rounded (`q=0.4999` now ranks below `q=0.5`).
- Under `toHttpApp` and the testkit a request body that cannot be handed out again fails a later read at once with an
  `IllegalStateException` ("The request entity was read before ...") instead of waiting on the connection until the
  idle timeout or arriving truncated, as Pekko fails the second materialization of a streamed entity: a chunked body
  (even an empty one), an HTTP/2 body with bytes and an in-memory body without a length can be read once, and a body
  of known length cannot be read again after a read that stopped partway (it failed mid-stream, or the reader stopped before
  asking for the end). A body known to be empty (no body, `Content-Length: 0`, an HTTP/2 body found empty) is
  readable again, and so is a body of known length read to its end when it is in memory (the testkit) or arrived over
  Ember together with the request head; a later read of a larger one fails with Ember's "Body Has Been Consumed
  Completely Already" (a 500, or a `MalformedRequestContentRejection` under `entity` and the form map directives),
  much as Pekko re-reads only an entity that arrived strict. Alternatives that each run a form directive read the
  body anew each (`formField(...) ~ formField(...)`), so wrap them in `toStrictEntity` to let several of them read
  the body.
  This applies to in-memory test bodies too (a body built with `withBodyStream` has no length, so it can be read
  once); `toHttpRoutes` hands the next routes the request as received and is not covered.
- `extractHost` and `host` read the authority of the request URI first, then the `Host` header, as Pekko reads the
  effective URI: an absolute-form request is matched by its URI's host (when its `Host` header disagrees, no host
  is named), a host name is lowercased (`Example.COM` matches `host("example.com")`), and an IPv6 host comes
  without brackets (`::1`, was `[::1]`).

## Testkit differences

The http4s-stir testkit differs from the Pekko HTTP testkit in the following ways:

- **Synchronous execution**: all test assertions run synchronously; there is no async test support. `~>` waits for the
  route, so `~> runRoute` does not separate running the route from checking it as Pekko's does.
- **Chunk inspection**: `chunks`, `chunksStream` and `trailer` expose the body chunks and trailer headers; there is no `ChunkStreamPart` model.
- **Limited request building**: some minor header convenience methods available in the Pekko HTTP testkit are missing.
- **WebSocket testing**: `WS(uri, probe, subprotocols*)` and `WSProbe` exchange frames with the handler in memory, as in Pekko, but the probe talks to the handler as the directive received it (no ping/pong filtering or defragmentation) and over `~!>` no frames are exchanged. `isWebSocketUpgrade` and `expectWebSocketUpgradeWithProtocol` work as in Pekko.
- **Timeouts**: `RouteTestTimeout` bounds `~>` and `~!>` (1 second by default) and fails the test on the test thread.
