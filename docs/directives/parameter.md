---
id: directives-parameter
title: Parameter Directives
sidebar_position: 8
---

# Parameter Directives

Parameter directives extract query parameter values from the request URI. They support required, optional, typed, repeated, and default-valued parameters. If a required parameter is missing, the request is rejected with a `MissingQueryParamRejection`. If a parameter value cannot be converted to the target type, a `MalformedQueryParamRejection` is produced.

## parameter (required String)

Extracts a required query parameter as a `String`. Rejects with `MissingQueryParamRejection` if the parameter is absent.

```scala
val route: Route = path("search") {
  parameter("q") { query =>
    complete(s"Search: $query")
  }
}
// GET /search?q=http4s -> "Search: http4s"
```

## parameter with type conversion

Extracts a required query parameter and converts it to the specified type using `.as[T]`. Rejects with `MalformedQueryParamRejection` if the conversion fails.

```scala
val route: Route = path("items") {
  parameter("amount".as[Int]) { amount =>
    complete(s"Amount: $amount")
  }
}
// GET /items?amount=123 -> "Amount: 123"
// GET /items?amount=abc -> MalformedQueryParamRejection
```

## parameter (optional)

Extracts an optional query parameter using `.optional` (or `.?`). Returns `Option[T]` and never rejects due to a missing parameter.

```scala
val route: Route = path("items") {
  parameter("color".optional) { color =>
    complete(s"Color: $color")
  }
}
// GET /items?color=red -> "Color: Some(red)"
// GET /items           -> "Color: None"
```

Typed optional parameters are also supported:

```scala
val route: Route = path("items") {
  parameter("amount".as[Int].optional) { amount =>
    complete(s"Amount: $amount")
  }
}
// GET /items?amount=12 -> "Amount: Some(12)"
// GET /items           -> "Amount: None"
```

## parameter with default value

Extracts a parameter with a fallback value using `.withDefault(value)`. If the parameter is absent, the default value is used.

```scala
val route: Route = path("items") {
  parameter("page".as[Int].withDefault(1)) { page =>
    complete(s"Page: $page")
  }
}
// GET /items         -> "Page: 1"
// GET /items?page=5  -> "Page: 5"
```

## parameter (repeated)

Extracts all occurrences of a query parameter using `.repeated` (or `.*`). Returns an `Iterable[T]`. If the parameter is absent, an empty collection is returned.

```scala
val route: Route = path("filter") {
  parameter("tag".repeated) { tags =>
    complete(s"Tags: ${tags.mkString(", ")}")
  }
}
// GET /filter?tag=a&tag=b -> "Tags: a, b"
// GET /filter             -> "Tags: "
```

Typed repeated parameters:

```scala
val route: Route = path("filter") {
  parameter("id".as[Int].repeated) { ids =>
    complete(s"IDs: ${ids.mkString(", ")}")
  }
}
// GET /filter?id=3&id=5 -> "IDs: 3, 5"
```

## Multiple parameters

Extract multiple parameters at once using the `parameters` directive. Up to 22 parameters are supported.

```scala
val route: Route = path("person") {
  parameters("name", "FirstName") { (name, firstName) =>
    complete(s"$firstName $name")
  }
}
// GET /person?name=Parsons&FirstName=Ellen -> "Ellen Parsons"
```

Different parameter types can be combined freely:

```scala
val route: Route = path("search") {
  parameters("name".optional, "FirstName", "age".withDefault("29"), "eyes".optional) {
    (name, firstName, age, eyes) =>
      complete(s"$firstName $name $age $eyes")
  }
}
```

## Composing parameters with &

Individual `parameter` directives can be composed using the `&` operator, which is equivalent to using the multi-argument `parameters` directive.

```scala
val route: Route = path("items") {
  (parameter("name") & parameter("count".as[Int])) { (name, count) =>
    complete(s"$name: $count")
  }
}
```

## Mapping parameters to case classes

The result of a multi-parameter directive can be mapped to a case class using `.as(Constructor)`:

```scala
case class Color(red: Int, green: Int, blue: Int)

val route: Route = path("color") {
  parameters("red".as[Int], "green".as[Int], "blue".as[Int]).as(Color.apply _) { color =>
    complete(s"${color.red} ${color.green} ${color.blue}")
  }
}
// GET /color?red=90&green=50&blue=0 -> "90 50 0"
```

## parameterMap

Extracts all query parameters as a `Map[String, String]`. If a parameter occurs multiple times, only one value is retained.

```scala
val route: Route = path("info") {
  parameterMap { params =>
    complete(s"Parameters: $params")
  }
}
// GET /info?a=1&b=2 -> "Parameters: Map(a -> 1, b -> 2)"
```

## parameterMultiMap

Extracts all query parameters as a `Map[String, Seq[String]]`, preserving multiple values for the same key.

```scala
val route: Route = path("info") {
  parameterMultiMap { params =>
    complete(s"Parameters: $params")
  }
}
// GET /info?a=1&a=2&b=3 -> "Parameters: Map(a -> List(1, 2), b -> List(3))"
```

## parameterSeq

Extracts all query parameters as a `Seq[(String, String)]`, preserving duplicate keys. Note that the order of parameters may not match the original query string order since the implementation is based on `multiParams`, which uses a `Map` internally.

```scala
val route: Route = path("info") {
  parameterSeq { params =>
    val display = params.map { case (k, v) => s"$k -> $v" }.mkString(", ")
    complete(s"Parameters: [$display]")
  }
}
// GET /info?a=b&e=f&c=d -> "Parameters: [a -> b, c -> d, e -> f]"
```
