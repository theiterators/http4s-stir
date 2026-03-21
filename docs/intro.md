---
id: intro
title: Introduction
slug: /
sidebar_position: 1
---

# http4s-stir

http4s-stir brings the [Pekko HTTP](https://github.com/apache/incubator-pekko-http) (formerly Akka HTTP) routing DSL to [http4s](https://github.com/http4s/http4s) with [cats-effect](https://typelevel.org/cats-effect/) IO. It allows you to define http4s services using the directive-based routing style familiar to Pekko HTTP and Akka HTTP users.

## Who is this for?

- **Teams migrating from Pekko HTTP or Akka HTTP** to the Typelevel ecosystem. Reuse your existing knowledge of directives, path matchers, and route composition while running on http4s and cats-effect IO.
- **http4s developers** who prefer the Pekko HTTP directive DSL over the http4s-dsl pattern-matching style.

## Feature highlights

- **Cross-platform.** Published for JVM, Scala.js, and Scala Native.
- **Scala 2.13 and Scala 3.** Full cross-compilation support.
- **~85% directive coverage.** Path, method, parameter, header, entity, marshalling, exception handling, rejection handling, file serving, WebSocket, and more.
- **Testkit included.** Route testing with the familiar `~>` syntax, compatible with both ScalaTest and Specs2.
- **http4s-dsl interop.** Embed existing http4s-dsl routes inside stir route trees via `httpRoutesOf`.

## Next steps

- [Installation](installation.md) -- add http4s-stir to your project.
- [Quick Start](quick-start.md) -- build a small CRUD API from scratch.
