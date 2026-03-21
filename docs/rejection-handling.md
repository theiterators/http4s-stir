---
id: rejection-handling
title: Rejection Handling
sidebar_position: 22
---

# Rejection Handling

## The Rejection Model

When a directive cannot handle a request, it *rejects* the request by producing one or more `Rejection` values rather than completing with a response. Rejections accumulate across route alternatives composed with `~` (or `concat`). If no alternative route handles the request, the collected rejections are processed by the `RejectionHandler` to produce an appropriate HTTP response.

For example, a `get` directive rejects non-GET requests with a `MethodRejection`. If the route structure offers multiple alternatives, each alternative may add its own rejections. `TransformationRejection` values are used internally to cancel out redundant rejections (e.g., multiple `MethodRejection` values when the method did match in one branch).

```scala
val route =
  path("order") {
    get {
      complete("Received GET")
    } ~
    post {
      complete("Received POST")
    }
  }
```

A `PUT /order` request would be rejected by both the `get` and `post` directives. The resulting rejections are collected and passed to the `RejectionHandler`, which produces a `405 Method Not Allowed` response.

## Built-in Rejection Types

| Rejection | Description |
|---|---|
| `MethodRejection(supported)` | The HTTP method is not supported. Contains the `Method` that the rejecting directive supports. |
| `SchemeRejection(supported)` | The URI scheme is not supported. Contains the scheme string the directive expects. |
| `MissingQueryParamRejection(parameterName)` | A required query parameter was not found. |
| `MalformedQueryParamRejection(parameterName, errorMsg, cause)` | A query parameter was present but could not be interpreted. |
| `InvalidRequiredValueForQueryParamRejection(parameterName, expectedValue, actualValue)` | A query parameter value did not match the required value. |
| `MissingHeaderRejection(headerName)` | A required HTTP header was not found. |
| `MalformedHeaderRejection(headerName, errorMsg, cause)` | An HTTP header value was malformed. |
| `MissingCookieRejection(cookieName)` | A required cookie was not found. |
| `MissingFormFieldRejection(fieldName)` | A required form field was not found. |
| `MalformedFormFieldRejection(fieldName, errorMsg, cause)` | A form field could not be interpreted. |
| `EntityRejection(decodeFailure)` | The request content type is unsupported or the entity could not be decoded. |
| `MalformedRequestContentRejection(message, cause)` | Unmarshalling the request content failed with a non-`IllegalArgumentException` error. |
| `ValidationRejection(message, cause)` | An expected value was semantically invalid. Produced by the `validate` directive and by `IllegalArgumentException` from domain constructors. |
| `AuthenticationFailedRejection(cause, challenge)` | Authentication failed. The `cause` is either `CredentialsMissing` or `CredentialsRejected`. |
| `AuthorizationFailedRejection` | The authenticated user is not authorized to access the resource. |
| `TransformationRejection(transform)` | A special rejection that carries a transformation function to cancel or modify other rejections. Used internally by directives. |

## Building a Custom RejectionHandler

Use `RejectionHandler.newBuilder()` to construct a handler:

```scala
import pl.iterators.stir.server._
import pl.iterators.stir.server.Directives._
import org.http4s.Status

val myRejectionHandler = RejectionHandler.newBuilder()
  .handle { case MissingQueryParamRejection(param) =>
    complete(Status.BadRequest, s"Missing parameter: $param")
  }
  .handleAll[MethodRejection] { rejections =>
    val methods = rejections.map(_.supported.name).mkString(", ")
    complete(Status.MethodNotAllowed, s"Supported methods: $methods")
  }
  .handleNotFound {
    complete(Status.NotFound, "Resource not found")
  }
  .result()
```

### Builder Methods

- **`.handle { case SomeRejection => route }`** -- Handle a specific rejection type by pattern matching. The first matching handler wins.
- **`.handleAll[T] { rejections => route }`** -- Handle all rejections of type `T` at once. The `rejections` sequence is guaranteed to be non-empty.
- **`.handleNotFound { route }`** -- Handle the case when no route matched the request (empty rejection list).
- **`.result()`** -- Build and return the immutable `RejectionHandler`.

## Transforming Rejection Responses

The `mapRejectionResponse` method transforms the HTTP response produced by a `RejectionHandler`. It can only be called on a built handler (one returned by `.result()`).

```scala
val handler = RejectionHandler.newBuilder()
  .handleNotFound {
    complete(Status.NotFound, "Not here")
  }
  .result()
  .mapRejectionResponse { response =>
    response.putHeaders(org.http4s.headers.`Cache-Control`(
      org.http4s.CacheDirective.`no-cache`()
    ))
  }
```

## Chaining Handlers with `withFallback`

Combine two handlers so that the second acts as a fallback when the first does not handle a rejection:

```scala
val combined = primaryHandler.withFallback(secondaryHandler)
```

If the primary handler does not match a rejection, the secondary handler is consulted.

## The `handleRejections` Directive

Apply a custom `RejectionHandler` to the inner route using the `handleRejections` directive from `ExecutionDirectives`:

```scala
val route =
  handleRejections(myRejectionHandler) {
    path("order") {
      get {
        complete("order")
      }
    }
  }
```

Rejections produced by the inner route are intercepted and processed by the provided handler. If the handler does not cover a rejection, it propagates outward.

## Route Sealing

`Route.seal` wraps a route with default rejection handling and exception handling. A sealed route will never produce unhandled rejections; instead, they are converted to appropriate HTTP error responses.

```scala
val sealedRoute = Route.seal(route)
```

`Route.seal` accepts implicit `RejectionHandler` and `ExceptionHandler` parameters, defaulting to `RejectionHandler.default` and the default `ExceptionHandler`:

```scala
implicit val myHandler: RejectionHandler = myRejectionHandler
val sealedRoute = Route.seal(route) // uses myHandler
```

The default `RejectionHandler` maps all built-in rejection types to appropriate HTTP status codes and error messages. Calling `.seal` on a `RejectionHandler` attaches `RejectionHandler.default` as a fallback, ensuring all rejections are covered.
