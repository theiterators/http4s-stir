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
libraryDependencies += "pl.iterators" %% "http4s-stir" % "0.4.1"
libraryDependencies += "pl.iterators" %% "http4s-stir-testkit" % "0.4.1" % Test
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
//> using dep pl.iterators::http4s-stir::0.4.1
```

For tests:

```scala
//> using test.dep pl.iterators::http4s-stir-testkit::0.4.1
```

## Mill

```scala
def ivyDeps = Agg(
  ivy"pl.iterators::http4s-stir:0.4.1"
)
```

## Scala.js and Scala Native

http4s-stir is cross-published for Scala.js and Scala Native. In SBT cross-projects, use `%%%` instead of `%%`:

```scala
libraryDependencies += "pl.iterators" %%% "http4s-stir" % "0.4.1"
```

This selects the correct artifact for your target platform automatically.
