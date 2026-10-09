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

## selectPreferredLanguage

Inspects the request's `Accept-Language` header and determines which of the given language alternatives is preferred by the client, following RFC 7231 Section 5.3.5 negotiation logic. If the client has equal preference for multiple alternatives, the argument order serves as a tiebreaker. If no `Accept-Language` header is present, the first language is returned.

```scala
import org.http4s.LanguageTag

val route: Route = selectPreferredLanguage(LanguageTag("en"), LanguageTag("de"), LanguageTag("fr")) { lang =>
  complete(s"Selected language: $lang")
}
```

## Debugging Directives

Debugging directives log requests and responses for diagnostic purposes. All three directives accept the same parameters:

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

### Redacting sensitive data

With no configuration, values under conventionally named keys never reach the log:

- header values whose name matches (`X-Api-Key`, `Proxy-Authorization`, `X-Csrf-Token`, …) render as `<REDACTED>`,
  in addition to `Authorization`, `Cookie` and `Set-Cookie`;
- query parameter values and the userinfo of the request URI, and of `Referer`/`Location` headers, render as `REDACTED`
  (`/login?token=REDACTED`); so do the values of a query-shaped fragment (`/cb#access_token=REDACTED&state=s`, the
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

#### Changes in 0.6.0

For an unconfigured `logRequestResult()`:

1. JSON and form values under conventional names are masked; query values and `Referer`/`Location` query values under
   conventional names are masked; the four field-naming rejections are masked when the field name is sensitive.
2. More header names are masked than `Authorization`, `Cookie` and `Set-Cookie`.
3. JSON bodies are logged compact, and truncated JSON closed, instead of as a raw prefix.
4. Multipart, binary, XML and HTML bodies show their size instead of content (binary was hex).
5. Control characters in bodies and rejection lines are escaped (`\n`, `\u0001`), so one entry is one line.
6. A failing `logAction` no longer fails the request or the response.
7. A URI whose query had a value masked is re-rendered: `;` separators become `&` and percent-encoding is normalised.
   A query with nothing masked is logged as received.
8. A JSON body that does not parse is logged up to the first invalid character, closed, and followed by
   `(unparseable from position N)`.

`logRequestResult(redaction = LogRedaction.none)` reproduces the 0.5.0 output except for items 5 and 6, and except that
a JSON content type with parameters (`application/json; version=2`) is logged as text, where 0.5.0 logged it as hex.

0.6.0 is source-compatible with 0.5.x call sites but not binary-compatible: the MiMa baseline restarts, so code
compiled against 0.5.x has to be recompiled.

#### Changes in 0.6.1

1. The values of a query-shaped fragment under conventional names are masked, in the request line and in
   `Referer`/`Location` (`#access_token=REDACTED&state=s`). `UriRedactor.fragment` is the building block.
2. A URI fragment is logged as received, with control characters escaped, in the request line and in a transformed
   `Referer` or `Location`; 0.6.0 let http4s re-encode `%` in it (`%41` → `%2541`).
