---
id: directives-host-and-scheme
title: Host and Scheme Directives
sidebar_position: 15
---

# Host and Scheme Directives

Host directives provide filtering and extraction based on the request's host (its URI authority or `Host` header). Scheme directives operate on the URI scheme (e.g., `http` or `https`).

## Host Directives

### extractHost

Extracts the hostname of the request as a `String`, as Pekko does from the request's effective URI: the authority of
the request URI (an absolute-form request, or an HTTP/2 request's `:authority`), else the `Host` header (RFC 7230
5.4). A host name is lowercased (as Pekko's URI parser does), an IPv6 address comes without brackets in its canonical
form (`::1`), and the result is `""` when the request names no host, or when the URI authority and the `Host` header
disagree (Pekko answers 400 then; here `host(...)` rejects rather than trust either). Ember 0.23.36 drops an HTTP/2
request's `:authority` (it parses the value as a URI and finds no authority), so an HTTP/2 request that carries its
host only there (prior knowledge, HTTP/2 over TLS) has no host over Ember and `host(...)` rejects it; the first
request of an `h2c` upgrade, or one with a `host` header, still has one.

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

Extracts the URI scheme of the request as a `String`. On a server the request line usually carries no scheme, so
the directive first looks at the connection the request arrived on (`https` for a TLS connection, `http`
otherwise) and only then at the request line; it yields an empty string when neither is known. Behind a proxy that
terminates TLS the connection is plain `http`; read `X-Forwarded-Proto` yourself in that case.

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
