val scala_2_13             = "2.13.10"
val scala_3                = "3.3.0"
val mainScalaVersion       = scala_2_13
val supportedScalaVersions = Seq(scala_2_13, scala_3)

ThisBuild / crossScalaVersions := supportedScalaVersions
ThisBuild / scalaVersion := mainScalaVersion

lazy val baseSettings = Seq(
  organization := "pl.iterators",
  organizationName := "Iterators",
  organizationHomepage := Some(url("https://www.iteratorshq.com")),
  version := "0.0.1-SNAPSHOT",
  scalacOptions ++= Seq(
    "-release:8",
    "-Ymacro-annotations",
    "-Ywarn-macros:after",
    "-deprecation", // Emit warning and location for usages of deprecated APIs.
    "-encoding",
    "utf-8", // Specify character encoding used by source files.
    "-explaintypes", // Explain type errors in more detail.
    "-feature", // Emit warning and location for usages of features that should be imported explicitly.
    "-language:existentials", // Existential types (besides wildcard types) can be written and inferred
    "-language:experimental.macros", // Allow macro definition (besides implementation and application)
    "-language:higherKinds", // Allow higher-kinded types
    "-language:implicitConversions", // Allow definition of implicit functions called views
    "-unchecked", // Enable additional warnings where generated code depends on assumptions.
    "-Xcheckinit", // Wrap field accessors to throw an exception on uninitialized access.
    "-Xlint:constant", // Evaluation of a constant arithmetic expression results in an error.
    "-Xlint:delayedinit-select", // Selecting member of DelayedInit.
    "-Xlint:doc-detached", // A Scaladoc comment appears to be detached from its element.
    "-Xlint:inaccessible", // Warn about inaccessible types in method signatures.
    "-Xlint:infer-any", // Warn when a type argument is inferred to be `Any`.
    "-Xlint:missing-interpolator", // A string literal appears to be missing an interpolator id.
    "-Xlint:nullary-unit", // Warn when nullary methods return Unit.
    "-Xlint:option-implicit", // Option.apply used implicit view.
    "-Xlint:package-object-classes", // Class or object defined in package object.
    "-Xlint:poly-implicit-overload", // Parameterized overloaded implicit methods are not visible as view bounds.
    "-Xlint:private-shadow", // A private field (or class parameter) shadows a superclass field.
    "-Xlint:stars-align", // Pattern sequence wildcard must align with sequence component.
    "-Xlint:type-parameter-shadow", // A local type parameter shadows a type already in scope.
    "-Ywarn-extra-implicit", // Warn when more than one implicit parameter section is defined.
    "-Ywarn-numeric-widen", // Warn when numerics are widened.
    "-Ywarn-unused:implicits", // Warn if an implicit parameter is unused.
    "-Ywarn-unused:imports", // Warn if an import selector is not referenced.
    "-Ywarn-unused:locals", // Warn if a local definition is unused.
    "-Ywarn-unused:params", // Warn if a value parameter is unused.
    "-Ywarn-unused:patvars", // Warn if a variable bound in a pattern is unused.
    "-Ywarn-unused:privates", // Warn if a private member is unused.
    "-Ywarn-value-discard", // Warn when non-Unit expression results are unused.
  ),
  scalafmtOnCompile := true
)

val http4s = Seq(
  "org.http4s" %% "http4s-dsl" % "0.23.21",
  "org.http4s" %% "http4s-ember-server" % "0.23.21"
)

val http4sClient = Seq(
  "org.http4s" %% "http4s-ember-client" % "0.23.21"
)

val circe = Seq(
  "io.circe" %% "circe-core" % "0.14.5",
  "io.circe" %% "circe-generic" % "0.14.5",
  "io.circe" %% "circe-parser" % "0.14.5",
  "org.http4s" %% "http4s-circe" % "0.23.21"
)

val logback = Seq(
  "ch.qos.logback" % "logback-classic" % "1.4.7"
)

lazy val core = project
  .in(file("core"))
  .settings(baseSettings: _*)
  .settings(
    name := "http4s-stir",
    libraryDependencies ++= http4s
  )

lazy val coreTests = project
  .in(file("core-tests"))
  .settings(baseSettings: _*)
  .settings(
    name := "http4s-stir-tests",
    libraryDependencies ++= http4s ++ Seq(
      "org.scalatest" %% "scalatest" % "3.2.15" % Test,
      "org.specs2" %% "specs2-core" % "4.19.2" % Test
    )
  ).dependsOn(
  testkit % "test",
  core % "test->test"
)

lazy val testkit = project
  .in(file("testkit"))
  .settings(baseSettings: _*)
  .settings(
    name := "http4s-stir-testkit",
    libraryDependencies ++= http4s ++ http4sClient ++ Seq(
      "org.scalatest" %% "scalatest" % "3.2.15" % "provided",
      "org.specs2" %% "specs2-core" % "4.19.2" % "provided"
    )
  ).dependsOn(core)

lazy val examples = project
  .in(file("examples"))
  .settings(baseSettings: _*)
  .settings(
    name := "http4s-stir-examples",
    libraryDependencies ++= http4s ++ circe ++ logback ++ Seq(
      "org.specs2" %% "specs2-core" % "4.19.2" % Test,
      "org.scalatest" %% "scalatest" % "3.2.15" % Test
    )
  )
  .dependsOn(core, testkit % Test)

lazy val stir = project
  .in(file("."))
  .aggregate(
    core,
    testkit,
    examples,
    coreTests
  )
  .settings(baseSettings: _*)
  .settings(
    name := "stir",
    description := "Akka-like (Pekko-like) DSL for http4s"
  )