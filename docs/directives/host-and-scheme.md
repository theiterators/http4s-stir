---
id: directives-host-and-scheme
title: Host and Scheme Directives
sidebar_position: 15
---

# Host and Scheme Directives

Host directives provide filtering and extraction based on the request's `Host` header. Scheme directives operate on the URI scheme (e.g., `http` or `https`).

## Host Directives

### extractHost

Extracts the hostname from the request's `Host` header as a `String`.

```scala
val route: Route = extractHost { hostname =>
  complete(s"Host: $hostname")
}
```

### host (name matching)

Rejects the request if the hostname does not match any of the given names.

```scala
val route: Route = host("example.com", "www.example.com") {
  complete("Matched host")
}
```

### host (predicate)

Rejects the request if the predicate returns `false` for the hostname.

```scala
val route: Route = host(_.endsWith(".example.com")) {
  complete("Subdomain of example.com")
}
```

### host (regex)

Matches the hostname against a regular expression. If the regex contains no capturing group, the matched prefix is extracted. If it contains exactly one capturing group, the captured string is extracted. More than one capturing group throws an `IllegalArgumentException`.

```scala
import scala.util.matching.Regex

val route: Route = host("""(\w+)\.example\.com""".r) { subdomain =>
  complete(s"Subdomain: $subdomain")
}
```

## Scheme Directives

### extractScheme

Extracts the URI scheme from the request as a `String`.

```scala
val route: Route = extractScheme { scheme =>
  complete(s"Scheme: $scheme")
}
```

### scheme

Rejects the request if the URI scheme does not match the given name. Rejected requests receive a `SchemeRejection`.

```scala
val route: Route = scheme("https") {
  complete("Secure connection")
}
```
