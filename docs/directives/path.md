---
id: directives-path
title: Path Directives
sidebar_position: 6
---

# Path Directives

Path directives match and consume segments of the request URI path. They work in conjunction with path matchers to extract typed values from path segments.

## Path Matching Directives

### path

Matches the remaining path completely (after consuming a leading slash) against the given path matcher. The path must be fully consumed for the directive to pass.

```scala
val route: Route = path("hello") {
  complete("Hello!")
}
// Matches: /hello
// Does not match: /hello/world
```

With extraction:

```scala
val route: Route = path("user" / IntNumber) { userId =>
  complete(s"User $userId")
}
// Matches: /user/42 (extracts 42)
```

### pathPrefix

Matches a prefix of the remaining path after consuming a leading slash. The rest of the path is left for inner directives to match.

```scala
val route: Route = pathPrefix("api" / "v1") {
  path("users") {
    complete("Users endpoint")
  }
}
// Matches: /api/v1/users
```

### rawPathPrefix

Like `pathPrefix`, but does not implicitly consume a leading slash. Applies the matcher directly to the unmatched path.

```scala
val route: Route = rawPathPrefix(Slash ~ "api") {
  complete("API")
}
```

### pathPrefixTest

Like `pathPrefix`, but does not consume the matched path prefix. Useful for lookahead checks.

```scala
val route: Route = pathPrefixTest("api") {
  // The "api" segment is still available for inner directives
  pathPrefix("api" / "v1") {
    complete("v1")
  }
}
```

### rawPathPrefixTest

Like `rawPathPrefix`, but does not consume the matched path prefix. Tests whether the unmatched path has the given prefix using raw (unencoded) matching, without implicitly consuming a leading slash.

```scala
val route: Route = rawPathPrefixTest(Slash ~ "api") {
  // The matched prefix is still available for inner directives
  rawPathPrefix(Slash ~ "api") {
    complete("API")
  }
}
```

### pathSuffix

Matches a suffix of the remaining unmatched path and consumes it. The matcher operates in reversed-segment order.

```scala
val route: Route = pathSuffix("json") {
  complete("JSON endpoint")
}
// pathSuffix("baz" / "bar") matches /foo/bar/baz
```

### pathSuffixTest

Like `pathSuffix`, but does not consume the matched suffix.

```scala
val route: Route = pathSuffixTest("json") {
  complete("JSON detected")
}
```

### pathEnd

Matches only if the remaining unmatched path is empty. Use this to ensure the path has been fully consumed.

```scala
val route: Route = pathPrefix("api") {
  pathEnd {
    complete("API root")
  }
}
// Matches: /api
// Does not match: /api/users
```

### pathEndOrSingleSlash

Matches if the remaining unmatched path is empty or consists of a single trailing slash.

```scala
val route: Route = pathPrefix("api") {
  pathEndOrSingleSlash {
    complete("API root")
  }
}
// Matches: /api and /api/
```

### pathSingleSlash

Matches only if the remaining path is a single slash.

```scala
val route: Route = pathSingleSlash {
  complete("Root with slash")
}
// Matches: /
```

### ignoreTrailingSlash

Tries the inner route. If it rejects with empty rejections, retries with the trailing slash toggled (added or removed).

```scala
val route: Route = ignoreTrailingSlash {
  path("hello") {
    complete("Hello!")
  }
}
// Matches both: /hello and /hello/
```

### redirectToTrailingSlashIfMissing

If the request path does not end with a slash, redirects to the same URI with a trailing slash appended.

```scala
val route: Route = redirectToTrailingSlashIfMissing(Status.MovedPermanently) {
  path("dir" /) {
    complete("Directory listing")
  }
}
// GET /dir -> 301 redirect to /dir/
```

### redirectToNoTrailingSlashIfPresent

If the request path ends with a slash (and is not the root path `/`), redirects to the same URI without the trailing slash.

```scala
val route: Route = redirectToNoTrailingSlashIfPresent(Status.Found) {
  pathPrefix("users") {
    concat(
      pathEnd {
        complete("User list")
      },
      path(Segment) { userId =>
        complete(s"User $userId")
      }
    )
  }
}
// GET /users/ -> 302 redirect to /users
```

## Path Matchers

Path matchers are applied to the unmatched portion of the request path. They consume path segments, extract typed values, and can be composed with operators.

### Slash

Matches a single slash character (`/`).

### PathEnd

Matches the very end of the path (empty remaining path with no trailing slash).

### Remaining

Matches and extracts the entire remaining unmatched path as a `String`.

```scala
val route: Route = path("files" / Remaining) { filePath =>
  complete(s"File: $filePath")
}
// /files/a/b/c -> extracts "a/b/c"
```

### RemainingPath

Matches and extracts the entire remaining unmatched path as a `Uri.Path` value.

```scala
val route: Route = path("proxy" / RemainingPath) { remainingPath =>
  complete(s"Forwarding: $remainingPath")
}
```

### Segment

Matches a single path segment and extracts it as a `String`.

```scala
val route: Route = path("user" / Segment) { name =>
  complete(s"User: $name")
}
// /user/alice -> extracts "alice"
```

### Segments

Matches up to 128 remaining segments and extracts them as a `List[String]`. Can also be parameterized with a count or range.

```scala
val route: Route = path("browse" / Segments) { segments =>
  complete(s"Path: ${segments.mkString("/")}")
}
```

```scala
// Exactly 3 segments
path("a" / Segments(3)) { segments => complete(segments.toString) }

// Between 1 and 5 segments
path("b" / Segments(1, 5)) { segments => complete(segments.toString) }
```

### IntNumber

Matches a sequence of decimal digits and extracts their `Int` value. Does not match values exceeding `Int.MaxValue`.

```scala
val route: Route = path("item" / IntNumber) { id =>
  complete(s"Item $id")
}
```

### LongNumber

Matches a sequence of decimal digits and extracts their `Long` value. Does not match values exceeding `Long.MaxValue`.

```scala
val route: Route = path("record" / LongNumber) { id =>
  complete(s"Record $id")
}
```

### HexIntNumber

Matches a sequence of hexadecimal digits and extracts their `Int` value.

```scala
val route: Route = path("color" / HexIntNumber) { color =>
  complete(s"Color: 0x${color.toHexString}")
}
```

### HexLongNumber

Matches a sequence of hexadecimal digits and extracts their `Long` value.

### DoubleNumber

Matches a decimal number (optionally signed, with optional decimal point) and extracts its `Double` value.

```scala
val route: Route = path("price" / DoubleNumber) { price =>
  complete(s"Price: $price")
}
```

### JavaUUID

Matches a UUID string in standard format and extracts a `java.util.UUID`.

```scala
val route: Route = path("entity" / JavaUUID) { uuid =>
  complete(s"Entity: $uuid")
}
// /entity/550e8400-e29b-41d4-a716-446655440000
```

### Neutral

Always matches, consumes nothing, and extracts nothing. Serves as the neutral element in path matcher composition.

## Matcher Composition

### `/` (Slash Concatenation)

Joins two matchers with an implicit slash between them.

```scala
val matcher = "api" / "v1" / IntNumber
// Matches: api/v1/42

val route: Route = path(matcher) { id =>
  complete(s"ID: $id")
}
```

### `~` (Direct Append)

Concatenates two matchers directly without inserting a slash.

```scala
val matcher = "item-" ~ IntNumber
// Matches the segment: item-42

val route: Route = path(matcher) { id =>
  complete(s"Item $id")
}
```

### `|` (Alternatives)

Tries the left matcher first; if it does not match, tries the right matcher. Both matchers must produce the same extraction type.

```scala
val matcher = "users" | "people"
// Matches: users or people

val route: Route = path(matcher) {
  complete("Person list")
}
```

### `?` / `optional`

Makes a matcher optional. If the matcher does not match, the directive still passes but extracts `None` (for `PathMatcher1`) or `Unit` (for `PathMatcher0`).

```scala
val route: Route = path("items" / IntNumber.?) { maybeId =>
  complete(s"ID: $maybeId")
}
// /items/42 -> Some(42)
// /items    -> None
```

### `repeat(count)` and `repeat(min, max, separator)`

Matches the underlying matcher a specified number of times, extracting a `List` of results.

```scala
// Exactly 3 segments
val route: Route = path(Segment.repeat(3, separator = Slash)) { segments =>
  complete(segments.mkString(", "))
}
```

```scala
// Between 1 and 5 integer segments
val route: Route = path(IntNumber.repeat(1, 5, separator = Slash)) { numbers =>
  complete(numbers.mkString(", "))
}
```

### `tmap` and `tflatMap`

Transform the extracted tuple value of a matcher.

```scala
val evenNumber = IntNumber.tmap { case Tuple1(n) => Tuple1(n * 2) }

val route: Route = path("double" / evenNumber) { n =>
  complete(s"Doubled: $n")
}
```

```scala
val positiveNumber = IntNumber.tflatMap {
  case Tuple1(n) if n > 0 => Some(Tuple1(n))
  case _                   => None
}

val route: Route = path("positive" / positiveNumber) { n =>
  complete(s"Positive: $n")
}
```
