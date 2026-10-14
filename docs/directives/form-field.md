---
id: directives-form-field
title: Form Field Directives
sidebar_position: 9
---

# Form Field Directives

Form field directives extract values from form request bodies, either `application/x-www-form-urlencoded` or the text parts of `multipart/form-data` (parts with a `filename` are files and belong to the [upload directives](file.md)). The API mirrors the [parameter directives](parameter.md), with the same support for required, optional, typed, default-valued, and repeated fields. If a required field is missing, the request is rejected with a `MissingFormFieldRejection`. If a field value cannot be converted to the target type, a `MalformedFormFieldRejection` is produced.

Every form directive (fields as well as `formFieldMap`, `formFieldSeq` and `formFieldMultiMap`) buffers the form
body in memory first (ten seconds timeout and 8 MiB limit, through `toStrictEntity`), so several of them can
read the same request. A body that does not arrive in
time fails the request with `408 Request Timeout`; a larger one with `413 Payload Too Large`.
A body whose `Content-Type` is neither `application/x-www-form-urlencoded` nor `multipart/form-data` is rejected
with an `UnsupportedRequestContentTypeRejection`; a body without `Content-Type` is parsed as a URL-encoded form.

## formField (required String)

Extracts a required form field as a `String`.

```scala
val route: Route = path("login") {
  post {
    formField("username") { username =>
      complete(s"User: $username")
    }
  }
}
```

## formField with type conversion

Extracts a form field and converts it to the specified type using `.as[T]`.

```scala
val route: Route = path("submit") {
  post {
    formField("age".as[Int]) { age =>
      complete(s"Age: $age")
    }
  }
}
```

## formField (optional)

Extracts an optional form field using `.optional`. Returns `Option[T]`.

```scala
val route: Route = path("profile") {
  post {
    formField("nickname".optional) { nickname =>
      complete(s"Nickname: $nickname")
    }
  }
}
```

## formField with default value

Extracts a form field with a fallback value using `.withDefault(value)`.

```scala
val route: Route = path("settings") {
  post {
    formField("theme".withDefault("light")) { theme =>
      complete(s"Theme: $theme")
    }
  }
}
```

## formField (repeated)

Extracts all occurrences of a form field using `.repeated`. Returns an `Iterable[T]`.

```scala
val route: Route = path("tags") {
  post {
    formField("tag".repeated) { tags =>
      complete(s"Tags: ${tags.mkString(", ")}")
    }
  }
}
```

## Multiple form fields

Extract multiple form fields at once using the `formFields` directive. Up to 22 fields are supported. The `formField` directive also accepts multiple arguments.

```scala
val route: Route = path("register") {
  post {
    formFields("username", "email", "age".as[Int]) { (username, email, age) =>
      complete(s"$username ($email), age $age")
    }
  }
}
```

Individual `formField` directives can also be composed with `&`:

```scala
val route: Route = path("register") {
  post {
    (formField("username") & formField("email")) { (username, email) =>
      complete(s"$username: $email")
    }
  }
}
```

## formFieldMap

Extracts all form fields as a `Map[String, String]`. If a field occurs multiple times, only one value is retained.

```scala
val route: Route = path("data") {
  post {
    formFieldMap { fields =>
      complete(s"Fields: $fields")
    }
  }
}
```

## formFieldMultiMap

Extracts all form fields as a `Map[String, List[String]]`, preserving multiple values for the same key.

```scala
val route: Route = path("data") {
  post {
    formFieldMultiMap { fields =>
      complete(s"Fields: $fields")
    }
  }
}
```

## formFieldSeq

Extracts all form fields as a `Seq[(String, String)]`, preserving order and duplicate keys.

```scala
val route: Route = path("data") {
  post {
    formFieldSeq { fields =>
      val display = fields.map { case (k, v) => s"$k=$v" }.mkString("&")
      complete(s"Fields: $display")
    }
  }
}
```
