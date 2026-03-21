---
id: directives-method
title: Method Directives
sidebar_position: 7
---

# Method Directives

Method directives filter requests by their HTTP method. A request that does not match the required method is rejected with a `MethodRejection`.

## get

Passes only GET requests. All other methods are rejected.

```scala
val route: Route = path("resource") {
  get {
    complete("GET response")
  }
}
```

## post

Passes only POST requests.

```scala
val route: Route = path("resource") {
  post {
    complete("POST response")
  }
}
```

## put

Passes only PUT requests.

```scala
val route: Route = path("resource") {
  put {
    complete("PUT response")
  }
}
```

## patch

Passes only PATCH requests.

```scala
val route: Route = path("resource") {
  patch {
    complete("PATCH response")
  }
}
```

## delete

Passes only DELETE requests.

```scala
val route: Route = path("resource") {
  delete {
    complete("DELETE response")
  }
}
```

## head

Passes only HEAD requests.

```scala
val route: Route = path("resource") {
  head {
    complete("HEAD response")
  }
}
```

## options

Passes only OPTIONS requests.

```scala
val route: Route = path("resource") {
  options {
    complete("OPTIONS response")
  }
}
```

## Combining Method Directives

Method directives are commonly combined with route concatenation to handle multiple methods on the same path:

```scala
val route: Route = path("resource") {
  get {
    complete("GET response")
  } ~
  post {
    complete("POST response")
  } ~
  delete {
    complete("DELETE response")
  }
}
```

## extractMethod

Extracts the HTTP method of the request as a `Method` value.

```scala
val route: Route = path("info") {
  extractMethod { method =>
    complete(s"Method: ${method.name}")
  }
}
```

## method

Matches a specific HTTP method value. This is the general form used internally by the convenience directives (`get`, `post`, etc.).

```scala
val route: Route = path("resource") {
  method(Method.GET) {
    complete("GET response")
  }
}
```

## overrideMethodWithParameter

Overrides the request HTTP method with the value of a query parameter. This is useful for supporting HTML forms or older browsers that only support GET and POST.

If the query parameter is not present, the directive has no effect. If the parameter value is not a valid HTTP method, the request is completed with `501 Not Implemented`.

```scala
val route: Route = overrideMethodWithParameter("_method") {
  path("resource") {
    put {
      complete("PUT via override")
    } ~
    delete {
      complete("DELETE via override")
    }
  }
}
// POST /resource?_method=PUT -> handled as PUT
// POST /resource?_method=DELETE -> handled as DELETE
```
