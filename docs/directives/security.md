---
id: directives-security
title: Security Directives
sidebar_position: 14
---

# Security Directives

Security directives provide authentication and authorization for routes using standard HTTP mechanisms. They support HTTP Basic authentication (RFC 2617) and OAuth 2.0 Bearer Token authentication (RFC 6750).

## Credentials

The `CredentialsHelper` type represents authentication credentials extracted from a request. It is passed to authenticator functions and supports pattern matching:

```scala
import pl.iterators.stir.server.directives.CredentialsHelper

val myAuthenticator: CredentialsHelper => Option[User] = {
  case p @ CredentialsHelper.Provided(identifier) if p.verify("s3cret") =>
    Some(User(identifier))
  case _ =>
    None
}
```

`CredentialsHelper.Provided` exposes a `verify(secret)` method that performs a timing-safe comparison. A `verify(secret, hasher)` variant is available for hashed secrets. For custom comparison logic, `provideVerify(verifier: String => Boolean)` and `provideVerify(secret: String, verifier: (String, String) => Boolean)` methods allow supplying a custom verification function. `CredentialsHelper.Missing` represents the absence of credentials.

## extractCredentials

Extracts the `Option[Credentials]` from the request's `Authorization` header. Returns `None` if no credentials are present.

```scala
val route: Route = extractCredentials {
  case Some(credentials) => complete(s"Credentials: $credentials")
  case None              => complete("No credentials")
}
```

## authenticateBasic

Wraps the inner route with HTTP Basic authentication. The authenticator function receives `CredentialsHelper` and returns `Option[T]`. If authentication fails, the request is rejected with an `AuthenticationFailedRejection` containing a `Basic` challenge for the given realm.

```scala
def check(credentials: CredentialsHelper): Option[String] = credentials match {
  case p @ CredentialsHelper.Provided(username) if p.verify("password") => Some(username)
  case _ => None
}

val route: Route = authenticateBasic("my-realm", check) { username =>
  complete(s"Hello, $username")
}
```

## authenticateBasicAsync

Asynchronous variant of `authenticateBasic`. The authenticator returns `IO[Option[T]]`, allowing credential verification against a database or external service.

```scala
def checkAsync(credentials: CredentialsHelper): IO[Option[String]] = credentials match {
  case p @ CredentialsHelper.Provided(username) =>
    verifyInDatabase(username, p).map {
      case true  => Some(username)
      case false => None
    }
  case _ => IO.pure(None)
}

val route: Route = authenticateBasicAsync("my-realm", checkAsync) { username =>
  complete(s"Hello, $username")
}
```

## authenticateBasicPF

Partial function variant of `authenticateBasic`. Only credentials matching the partial function are considered valid.

```scala
val route: Route = authenticateBasicPF("my-realm", {
  case p @ CredentialsHelper.Provided(username) if p.verify("password") => username
}) { username =>
  complete(s"Hello, $username")
}
```

## authenticateBasicPFAsync

Asynchronous partial function variant. Combines the partial function matching of `authenticateBasicPF` with the asynchronous evaluation of `authenticateBasicAsync`.

```scala
val route: Route = authenticateBasicPFAsync("my-realm", {
  case p @ CredentialsHelper.Provided(username) => verifyUser(username, p).map(_ => username)
}) { username =>
  complete(s"Hello, $username")
}
```

## authenticateOAuth2

Wraps the inner route with OAuth 2.0 Bearer Token authentication. The API is identical to `authenticateBasic` but issues a `Bearer` challenge on failure. Credentials are extracted from the `Authorization: Bearer <token>` header or from an `access_token` query parameter.

```scala
def validateToken(credentials: CredentialsHelper): Option[User] = credentials match {
  case p @ CredentialsHelper.Provided(token) if isValidToken(token) => Some(User(token))
  case _ => None
}

val route: Route = authenticateOAuth2("my-realm", validateToken) { user =>
  complete(s"Authenticated as ${user.name}")
}
```

## authenticateOAuth2Async, authenticateOAuth2PF, authenticateOAuth2PFAsync

Async, partial function, and async partial function variants of `authenticateOAuth2`. Their signatures mirror the corresponding `authenticateBasic` variants.

## authenticateOrRejectWithChallenge

Low-level directive that lifts an authenticator function into a directive. The authenticator receives `Option[Credentials]` and returns `IO[AuthenticationResult[T]]`, which is either `Right(user)` on success or `Left(challenge)` on failure.

```scala
import org.http4s.Challenge

val route: Route = authenticateOrRejectWithChallenge[User] { credentials: Option[Credentials] =>
  credentials match {
    case Some(BasicCredentials(username, password)) if verify(username, password) =>
      IO.pure(Right(User(username)))
    case _ =>
      IO.pure(Left(Challenge("Basic", "my-realm")))
  }
} { user =>
  complete(s"Hello, ${user.name}")
}
```

## AuthenticationDirective Combinators

`AuthenticationDirective` provides two combinators for modifying authentication behavior:

### optional

Returns `Some(user)` if credentials were supplied and valid, or `None` if no credentials were present. Credentials that are present but invalid still result in rejection.

```scala
val route: Route = authenticateBasic("my-realm", check).optional { maybeUser =>
  maybeUser match {
    case Some(user) => complete(s"Hello, $user")
    case None       => complete("Hello, anonymous")
  }
}
```

### withAnonymousUser

Provides the given anonymous user object when no credentials are supplied.

```scala
val route: Route = authenticateBasic("my-realm", check).withAnonymousUser("guest") { user =>
  complete(s"Hello, $user")
}
```

## authorize

Applies a synchronous authorization check. If the check returns `false`, the request is rejected with an `AuthorizationFailedRejection`.

Two overloads are available: a by-name `Boolean` for simple checks, and a `RequestContext => Boolean` function for checks that need access to the request.

```scala
val route: Route = authenticateBasic("my-realm", check) { user =>
  authorize(user.hasPermission("admin")) {
    complete("Admin area")
  }
}

// Using the RequestContext overload
val route2: Route = authorize(ctx => ctx.request.uri.path.toString.startsWith("/admin")) {
  complete("Admin area")
}
```

## authorizeAsync

Asynchronous variant of `authorize`. The check returns `IO[Boolean]`. If the `IO` fails or evaluates to `false`, the request is rejected with an `AuthorizationFailedRejection`.

Two overloads are available: a by-name `IO[Boolean]` for simple checks, and a `RequestContext => IO[Boolean]` function for checks that need access to the request.

```scala
val route: Route = authenticateBasic("my-realm", check) { user =>
  authorizeAsync(checkPermissionAsync(user, "admin")) {
    complete("Admin area")
  }
}

// Using the RequestContext overload
val route2: Route = authorizeAsync(ctx => checkPathPermission(ctx.request.uri.path)) {
  complete("Authorized")
}
```
