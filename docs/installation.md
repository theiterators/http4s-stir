---
id: installation
title: Installation
sidebar_position: 2
---

# Installation

## SBT

Add the core library and, optionally, the testkit:

```scala
// build.sbt
libraryDependencies += "pl.iterators" %% "http4s-stir" % "@VERSION@"
libraryDependencies += "pl.iterators" %% "http4s-stir-testkit" % "@VERSION@" % Test
```

You will also need http4s server and JSON dependencies. A typical set:

```scala
libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-ember-server" % "0.23.33",
  "org.http4s" %% "http4s-circe"        % "0.23.33",
  "io.circe"   %% "circe-generic"       % "0.14.15"
)
```

## scala-cli

```scala
//> using dep pl.iterators::http4s-stir::@VERSION@
```

For tests:

```scala
//> using test.dep pl.iterators::http4s-stir-testkit::@VERSION@
```

## Mill

```scala
def ivyDeps = Agg(
  ivy"pl.iterators::http4s-stir:@VERSION@"
)
```

## Scala.js and Scala Native

http4s-stir is cross-published for Scala.js and Scala Native. In SBT cross-projects, use `%%%` instead of `%%`:

```scala
libraryDependencies += "pl.iterators" %%% "http4s-stir" % "@VERSION@"
```

This selects the correct artifact for your target platform automatically.
