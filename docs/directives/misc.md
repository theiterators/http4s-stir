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
| `redactHeadersWhen` | `CIString => Boolean` | `Headers.SensitiveHeaders.contains` | Predicate to redact sensitive headers |
| `maxBodyBytes` | `Int` | `4096` | Maximum number of body bytes to log |
| `logAction` | `Option[String => IO[Unit]]` | `None` | Custom log action; defaults to console output |

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
