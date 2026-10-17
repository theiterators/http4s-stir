---
id: directives-misc
title: Miscellaneous Directives
sidebar_position: 20
---

# Miscellaneous Directives

Miscellaneous directives cover validation, client IP extraction, content negotiation, and request/response logging.

## validate

Checks the given condition before running the inner route. If the condition evaluates to `false`, the request is rejected with a `ValidationRejection` containing the provided error message.

```scala
val route: Route = (post & entity(as[Beer])) { beer =>
  validate(beer.abv >= 0 && beer.abv <= 100, "ABV must be between 0 and 100") {
    complete(Status.Created -> beer)
  }
}
```

## extractClientIP

Extracts the client's IP address as an `Option[IpAddress]`. The IP is resolved from the following sources in order of priority:

1. `X-Forwarded-For` header
2. `X-Real-Ip` header
3. `Remote-Address` header
4. TCP connection remote address

```scala
import com.comcast.ip4s.IpAddress

val route: Route = extractClientIP { maybeIp =>
  complete(s"Client IP: ${maybeIp.map(_.toString).getOrElse("unknown")}")
}
```

## extractDirectClientIP

Extracts the remote address of the TCP connection the request arrived on, ignoring `X-Forwarded-For` and
friends. `None` when the server did not record the connection (the in-memory testkit does not).

```scala
val route: Route = extractDirectClientIP { maybeIp =>
  complete(s"Connected from: ${maybeIp.map(_.toString).getOrElse("unknown")}")
}
```

## requestEntityEmpty / requestEntityPresent

`requestEntityEmpty` passes only requests without a body and rejects the others with an empty rejection set.
`requestEntityPresent` does the opposite and rejects with a `RequestEntityExpectedRejection` (400 when sealed).
A request counts as empty when it declares `Content-Length: 0` or declares neither `Content-Length` nor
`Transfer-Encoding`; an in-memory test request built from a raw stream carries no `Content-Length`, so build it
with `withEntity` or add the header.

Over HTTP/2 a request body usually carries neither header. Pekko then decides from the frames (whether the stream
ended with the headers), which http4s does not expose, so stir answers the same question by reading the first chunk
of the body: `toHttpApp` and the testkit route a length-less HTTP/2 request with a body that reads its first chunk
once, on demand, and hands the reader (one of the alternatives of a route, `entity`, `decodeRequest`, ...) the whole
body. So `requestEntityEmpty` on such a request waits until the client sends its first bytes or ends the stream. A
network body cannot be replayed, so a body with bytes can be read once: a second read (an alternative after a
rejection, or a second alternative running a form directive, as each buffers the body anew) fails at once with an
`IllegalStateException` (a 400 rejection when read through `decodeRequest`) instead of getting a truncated body; wrap
the route in `toStrictEntity` to let several alternatives read it. A
body that a directive replaced (`mapRequest`, `toStrictEntity`, ...) is judged by its headers, a length-less one
taken as present, as is a length-less HTTP/2 body under `toHttpRoutes` (a request it rejects goes on to the next
routes, which must get the body untouched) or on a hand-built `RequestContext`. The look is released when the
response body ends; reading the body in the background after that may fail for a body that holds resources.

```scala
val route: Route = post {
  requestEntityPresent {
    entity(as[String]) { body => complete(s"Got $body") }
  }
}
```

## rejectEmptyResponse

Turns a response without a body (`Content-Length: 0` or no entity) into an empty rejection, so the next route
alternative is tried.

```scala
val route: Route =
  rejectEmptyResponse { complete(Status.Ok) } ~
  complete("fallback")
```

## withSizeLimit / withoutSizeLimit

`withSizeLimit(maxBytes)` fails the request entity with an `EntityStreamSizeException` when more than
`maxBytes` are consumed, or immediately when the declared `Content-Length` exceeds the limit. The default
handlers complete it with `413 Payload Too Large` (through a `MalformedRequestContentRejection` from `entity`,
or through the exception handler elsewhere). The check runs only when the entity is consumed. Nested limits
apply the innermost one to the original entity; `withoutSizeLimit` restores the original, unlimited entity. Both
leave a body alone that another directive replaced in between (for example one buffered by `toStrictEntity` or
decoded by `decodeRequest`), and limit or restore that body instead.

```scala
val route: Route = withSizeLimit(1024 * 1024) {
  entity(as[String]) { body => complete(s"${body.length} chars") }
}
```

## selectPreferredLanguage

Inspects the request's `Accept-Language` header and determines which of the given language alternatives is preferred by the client, following RFC 7231 Section 5.3.5 negotiation logic. If the client has equal preference for multiple alternatives, the argument order serves as a tiebreaker. If no `Accept-Language` header is present, the first language is returned. q-values are read as leniently as Pekko reads them (`q=.5`, more than three decimals, spaces around `;` and `=`); a header that still cannot be parsed is ignored the same way.

```scala
import org.http4s.LanguageTag

val route: Route = selectPreferredLanguage(LanguageTag("en"), LanguageTag("de"), LanguageTag("fr")) { lang =>
  complete(s"Selected language: $lang")
}
```

## Debugging Directives

Debugging directives log requests and responses for diagnostic purposes. `logRequest`, `logResult` and
`logRequestResult` accept the same parameters (`logRequestResultWith` takes a function instead, see below):

| Parameter | Type | Default | Description |
|---|---|---|---|
| `logHeaders` | `Boolean` | `true` | Whether to include headers in the log output |
| `logBody` | `Boolean` | `true` | Whether to include the body in the log output |
| `redactHeadersWhen` | `CIString => Boolean` | `Headers.SensitiveHeaders.contains` | Header-only additions to the masked header names; unioned with `redaction.headers` |
| `maxBodyBytes` | `Int` | `4096` | Maximum number of body bytes to log |
| `logAction` | `Option[String => IO[Unit]]` | `None` | Custom log action; defaults to console output. Its errors are swallowed: logging never fails a request |
| `redaction` | `LogRedaction` | `LogRedaction.default` | What is masked in headers, the URI, bodies and rejections (see below) |

### logRequest

Produces a log entry for every incoming request.

```scala
val route: Route = logRequest() {
  complete("Logged request")
}
```

With custom parameters:

```scala
val route: Route = logRequest(logHeaders = true, logBody = false) {
  complete("Headers only")
}
```

### logResult

Produces a log entry for every route result, including both completed responses and rejections.

```scala
val route: Route = logResult() {
  complete("Logged result")
}
```

### logRequestResult

Produces log entries for both the incoming request and the route result. This is a convenience directive that combines `logRequest` and `logResult`.

```scala
val route: Route = logRequestResult() {
  path("api") {
    complete("Logged both ways")
  }
}
```

With a custom log action:

```scala
import org.typelevel.log4cats.Logger

val route: Route = logRequestResult(logAction = Some(msg => Logger[IO].info(msg))) {
  complete("Custom logger")
}
```

### logRequestResultWith

Logs what a function of the request and the `RouteResult` returns, when it returns `Some`, as Pekko's
`logRequestResult(show)` with an `Option[LogEntry]`. `show(request)` is applied before the inner route and the
resulting function after it, so it can look at the response or the rejections. A `None` logs nothing; a failing
function fails the request, as in Pekko (the exception handler answers with a 500), while a failing log action is
swallowed like in the other debugging directives. Logging only rejections:

```scala
import cats.effect.IO
import org.http4s.Request
import pl.iterators.stir.server.{ Route, RouteResult }

val rejectionLogger: Request[IO] => RouteResult => IO[Option[String]] = request => {
  case RouteResult.Rejected(rejections) =>
    IO.pure(Some(s"${request.method} ${request.uri} was rejected: ${rejections.mkString(", ")}"))
  case _ => IO.pure(None)
}

val route: Route = logRequestResultWith(rejectionLogger) {
  parameter("id".as[Int]) { id => complete(s"id $id") }
}
```

Treat the response body as opaque: it is the very stream the client will read. Reading it in the function re-runs
its effects (a `Stream` completed through the streaming marshaller runs twice, an unbounded one never ends),
consumes a one-shot body, and fires finalizers early (the upload directives delete their files when the body
ends). Only a body built from a strict value (`complete("text")`, `complete(json)`) is safe to read; to log body
prefixes use `logResult(maxBodyBytes = ...)`, which tees the bytes as the client reads them.

### Redacting sensitive data

With no configuration, values under conventionally named keys never reach the log:

- header values whose name matches (`X-Api-Key`, `Proxy-Authorization`, `X-Csrf-Token`, …) render as `<REDACTED>`,
  in addition to `Authorization`, `Cookie` and `Set-Cookie`;
- query parameter values and the userinfo of the request URI, and of `Referer`/`Location` headers, render as `REDACTED`
  (`/login?token=REDACTED`; a query value is masked up to the next `&`, as the parameter directives read it, and across
  `;` separators, and the query is logged as received); so do the values of a query-shaped fragment (`/cb#access_token=REDACTED&state=s`, the
  shape the OAuth 2.0 implicit grant writes into `Location`): pairs are separated by `&` as `URLSearchParams` reads
  them, and names are matched as written, after each `?` of a hash route (`#/login?id_token=REDACTED`) and across
  legacy `;` separators, so a value is masked under any of those readings (a fragment with more than 16 `?` is
  masked whole, since each costs a pass); other fragments (`#/users/42`) stay as received;
  an unparseable `Referer` or `Location` renders as `<REDACTED>`;
- JSON bodies are logged compact with the value under any matching key, at any depth, replaced by `"REDACTED"`; a body
  cut at `maxBodyBytes` is closed so that the logged text is still valid JSON;
- form bodies have the value of every matching field replaced by `REDACTED`;
- `text/plain` bodies are shown as they are; every other body (multipart, binary, XML, HTML) is logged as
  `body=<hidden> (N bytes total)`;
- the four rejections that carry a value (`MalformedQueryParamRejection`, `MalformedFormFieldRejection`,
  `InvalidRequiredValueForQueryParamRejection`, `MalformedHeaderRejection`) render as `Name(field,<REDACTED>)` when
  the field name matches, and `InvalidRequiredValueForQueryParamRejection` masks both the expected and the actual
  value (`InvalidRequiredValueForQueryParamRejection(field,<REDACTED>,<REDACTED>)`); a `MalformedHeaderRejection` for
  `Referer` or `Location` is always masked, and one for a header selected by `redactHeadersWhen` is masked too.

A name matches when, after lowercasing and splitting on `-`, `_`, other punctuation and camelCase boundaries, one of
its words is one of `auth authorization apikey certificate cookie credential csrf cvc cvv jwt key otp pass passphrase
passwd password privatekey pwd salt secret session sessionid sig signature ssn token xsrf` (or its plural), or ends with
`token`, `secret`, `password` or `passwd`. Header names are matched lowercased, as HTTP/2 delivers them, so
`X-Access-Key` matches and `X-AccessKey` does not. `email`, `code` and client IP headers are deliberately not in the
list; the usual additions are `LogRedaction.default.addNames("code", "pin", "iban")`.

The policy is one argument, `redaction`, whose common operations keep every default in place:

```scala
import pl.iterators.stir.server.Directives._
import pl.iterators.stir.server.directives.{ BodyRedactor, LogRedaction }

// also treat "pin" as sensitive: headers, query strings, JSON keys, form fields, rejections
logRequestResult(redaction = LogRedaction.default.addNames("pin")) { route }

// also mask a path segment; query and userinfo masking stay in place
logRequestResult(redaction = LogRedaction.default.redactPath("/password-reset/{token}")) { route }

// one endpoint logs only the shape of its request JSON; every other body keeps the default
val tokenRequests = BodyRedactor.when(_.request.exists(_.uri.path.renderString == "/oauth/token"))(
  BodyRedactor.jsonKeepOnly(Set("grant_type", "scope")))
logRequestResult(redaction = LogRedaction.default.withBodyRedactor(tokenRequests)) { route }
// {"grant_type":"password","username":"alice","password":"x"} logs as
// {"grant_type":"password","username":"REDACTED","password":"REDACTED"}

// also mask card numbers in the values of any text body. Rejections do not see value rules, so hide their values.
val card = "\\b[0-9]{13,19}\\b".r
logRequestResult(redaction =
  LogRedaction.default.redactBodyValues(card.replaceAllIn(_, "REDACTED")).hideRejectionValues) { route }
// {"card":4111111111111111,"note":"card 4111111111111111"} logs as {"card":"REDACTED","note":"card REDACTED"}

// hide every body, keep name-based masking elsewhere
logRequestResult(redaction = LogRedaction.default.withBodyRedactor(BodyRedactor.hidden)) { route }

// everything at once
logRequestResult(redaction =
  LogRedaction.default.addNames("pin", "iban").redactPath("/users/*/tokens/{token}").hideRejectionValues) { route }

// the 0.5.0 output, except that control characters are escaped, logAction errors are swallowed and JSON content
// types with parameters are logged as text; and the same plus one name masked in every channel
logRequestResult(redaction = LogRedaction.none) { route }
logRequestResult(redaction = LogRedaction.none.addNames("pin")) { route }
```

`addNames`, `redactPath`, `redactUri`, `redactBodyValues` and `hideRejectionValues` add to the defaults; `withNames`
and `withBodyRedactor` replace one (the predicate, or the name-based body masking for the bodies the redactor does
not `Skip`). `BodyRedactor` offers `hidden`, `passThrough`, `jsonKeys`, `jsonKeepOnly`, `formFields`, `json`, `when`,
`apply`, `eval` and `fromLogBodyText` (an adapter for an http4s `logBody` function); redactors chain with `orElse`.

**Truncated and repaired bodies.** A user function (`json`, `apply`, `eval`, `redactBodyValues`) is never called on a
body cut at `maxBodyBytes` unless the redactor was built with `prefixSafe = true`; the body is logged as
`body=<hidden> (N bytes total)`. A cut can split a card number or drop the member a decision depends on. Pass
`prefixSafe = true` to assert that the function decides by keys alone (an allow-list, a key deny-list over a parsed
tree) and may run on a prefix. `json` and `redactBodyValues` additionally scan the body first and, unless
`prefixSafe = true`, are not called on JSON that does not parse as one whole value (an unterminated string, a trailing
comma); such a body is logged as `body=<hidden> (unparseable from position N)` followed by the size. `apply`, `eval`
and `fromLogBodyText` receive a complete body as it is, malformed or not, and must cope with it themselves.

**What is not covered.** Rejections with free text (`ValidationRejection`, `MalformedRequestContentRejection`) are
logged as they are; XML and multipart bodies are hidden, not parsed; client IP headers (`X-Forwarded-For`, `Host`) are
logged; a secret in a path segment needs `redactPath`; chunked bodies and request bodies without `Content-Length` are
logged without a body part; response bodies cannot be scoped by route (`LoggedBody.request` is `None` for them). The
`logAction` hook receives the finished line and remains the last resort.

**Failures.** A redactor or predicate that throws never fails the request or the response: the affected part of the
line renders as `<redaction failed: ExceptionClass>`, where `ExceptionClass` is the fully qualified class name (for
example `java.lang.IllegalStateException`), and nothing raw is logged. A predicate that throws while deciding a
rejection makes the field count as sensitive: it renders as `<REDACTED>`, not as `<redaction failed: …>`.
