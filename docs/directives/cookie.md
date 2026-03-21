---
id: directives-cookie
title: Cookie Directives
sidebar_position: 13
---

# Cookie Directives

Cookie directives provide extraction and manipulation of HTTP cookies on requests and responses. They operate on http4s `RequestCookie` and `ResponseCookie` types.

## cookie

Extracts a `RequestCookie` by name from the request. If the cookie is not present, the request is rejected with a `MissingCookieRejection`.

```scala
val route: Route = path("dashboard") {
  cookie("session_id") { sessionCookie =>
    complete(s"Session: ${sessionCookie.content}")
  }
}
```

## optionalCookie

Extracts a cookie by name as an `Option[RequestCookie]`. If the cookie is not present, `None` is extracted instead of rejecting the request.

```scala
val route: Route = path("welcome") {
  optionalCookie("username") {
    case Some(cookie) => complete(s"Welcome back, ${cookie.content}")
    case None         => complete("Welcome, guest")
  }
}
```

## setCookie

Adds a `Set-Cookie` response header with the given `ResponseCookie`. Multiple cookies can be set at once.

```scala
import org.http4s.ResponseCookie

val route: Route = path("login") {
  post {
    setCookie(ResponseCookie("session_id", "abc123")) {
      complete("Logged in")
    }
  }
}
```

## deleteCookie

Adds a `Set-Cookie` response header that expires the cookie, effectively deleting it from the client. Accepts either a cookie name (with optional domain and path) or a `ResponseCookie` instance.

```scala
val route: Route = path("logout") {
  post {
    deleteCookie("session_id") {
      complete("Logged out")
    }
  }
}
```
