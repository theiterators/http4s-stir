---
id: directives-attribute
title: Attribute Directives
sidebar_position: 24
---

# Attribute Directives

http4s keeps per-request attributes in a `Vault` keyed by `org.typelevel.vault.Key`; servers use it for the
connection information, and middleware or outer directives can store anything there with `mapRequest`.

## attribute

Extracts the attribute stored under the given key. If it is absent the request is rejected with a
`MissingAttributeRejection`, which the default handler turns into `500 Internal Server Error`: a missing attribute
is a server-side wiring error, not a client mistake.

```scala
import cats.effect.SyncIO
import org.typelevel.vault.Key

val userKey: Key[String] = Key.newKey[SyncIO, String].unsafeRunSync()

val route: Route =
  mapRequest(request => request.withAttributes(request.attributes.insert(userKey, "alice"))) {
    attribute(userKey) { user =>
      complete(s"Hello, $user")
    }
  }
```

## optionalAttribute

Extracts the attribute as an `Option[T]`, `None` when it is absent.

```scala
val route: Route = optionalAttribute(userKey) {
  case Some(user) => complete(s"Hello, $user")
  case None       => complete("Hello, stranger")
}
```
