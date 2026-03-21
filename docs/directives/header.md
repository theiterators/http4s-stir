---
id: directives-header
title: Header Directives
sidebar_position: 10
---

# Header Directives

Header directives extract values from HTTP request headers. Each directive has a required variant that rejects with `MissingHeaderRejection` when the header is absent, and an optional variant that returns `Option[T]` instead.

## headerValueByName

Extracts the value of a request header by its name as a `String`. Rejects with `MissingHeaderRejection` if the header is absent. The name matching is case-insensitive.

```scala
val route: Route = path("info") {
  headerValueByName("Referer") { referer =>
    complete(s"The referer was $referer")
  }
}
// Request with "Referer: http://example.com" -> "The referer was http://example.com"
// Request without Referer header -> MissingHeaderRejection("Referer")
```

## headerValueByType

Extracts a typed header using the http4s `Header.Select[T]` type class. Rejects with `MissingHeaderRejection` if no header of the given type is present.

The extraction type is `ev.F[T]`, which depends on the header kind. For singleton headers (e.g. `Content-Type`), `F[T] = T` so you get the header value directly. For recurring headers (e.g. `Set-Cookie`), `F[T] = NonEmptyList[T]`.

```scala
import org.http4s.headers.`Content-Type`

val route: Route = path("check") {
  headerValueByType[`Content-Type`] { contentType =>
    complete(s"Content-Type: $contentType")
  }
}
// Request with Content-Type header -> extracts the typed Content-Type value
// Request without Content-Type header -> MissingHeaderRejection("Content-Type")
```

## headerValue

Extracts a header value using a function `Header.Raw => Option[T]`. If the function returns `None` for all headers, the request is rejected with an empty rejection set. If the function throws an exception, the request is rejected with a `MalformedHeaderRejection`.

```scala
val route: Route = path("custom") {
  headerValue {
    case h if h.name.toString == "X-My-Header" => Some(h.value)
    case _ => None
  } { value =>
    complete(s"Header value: $value")
  }
}
```

## headerValuePF

Extracts a header value using a partial function `PartialFunction[Header.Raw, T]`. If the partial function is not defined for any header, the request is rejected with an empty rejection set.

```scala
import org.http4s.headers.Connection
import org.typelevel.ci.CIString

val route: Route = path("info") {
  headerValuePF {
    case h if Connection.parse(h.value).isRight =>
      Connection.parse(h.value).toOption.get.values.head
  } { connectionValue =>
    complete(s"Connection: $connectionValue")
  }
}
```

## optionalHeaderValueByName

Extracts the value of a request header by name as `Option[String]`. Returns `None` if the header is absent. Never rejects.

```scala
val route: Route = path("info") {
  optionalHeaderValueByName("Referer") { referer =>
    complete(s"The referer was $referer")
  }
}
// With Referer header    -> "The referer was Some(http://example.com)"
// Without Referer header -> "The referer was None"
```

## optionalHeaderValueByType

Extracts a typed header as `Option[ev.F[T]]`. Returns `None` if no header of the given type is present. Never rejects.

As with `headerValueByType`, the inner type depends on the header kind: `F[T] = T` for singleton headers, `F[T] = NonEmptyList[T]` for recurring headers.

```scala
import org.http4s.headers.`Content-Type`

val route: Route = path("check") {
  optionalHeaderValueByType[`Content-Type`] {
    case Some(ct) => complete(s"Content-Type: $ct")
    case None     => complete("No Content-Type header found.")
  }
}
```

## optionalHeaderValue

Extracts a header value using a function `Header.Raw => Option[T]`, returning `Option[T]`. Returns `None` if the function yields no result. If the function throws an exception, the request is rejected with a `MalformedHeaderRejection`.

```scala
val route: Route = path("info") {
  optionalHeaderValue {
    case h if h.name.toString == "X-Custom" => Some(h.value)
    case _ => None
  } { value =>
    complete(s"Custom header: $value")
  }
}
// With X-Custom header    -> "Custom header: Some(value)"
// Without X-Custom header -> "Custom header: None"
```

## optionalHeaderValuePF

Extracts a header value using a partial function, returning `Option[T]`. Returns `None` if the partial function is not defined for any header.

```scala
val route: Route = path("info") {
  optionalHeaderValuePF {
    case h if h.name.toString == "X-Trace-Id" => h.value
  } { traceId =>
    complete(s"Trace ID: $traceId")
  }
}
```
