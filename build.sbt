import scala.collection.immutable.Seq

val scala_2_13 = "2.13.10"
val scala_3 = "3.3.0"
val mainScalaVersion = scala_3
val supportedScalaVersions = Seq(scala_2_13, scala_3)

ThisBuild / crossScalaVersions := supportedScalaVersions
ThisBuild / scalaVersion := mainScalaVersion

ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("11"), JavaSpec.temurin("17"))
ThisBuild / githubWorkflowPublishTargetBranches := Seq(RefPredicate.Equals(Ref.Branch("master")))
ThisBuild / tlBaseVersion := "0.1"
ThisBuild / tlCiHeaderCheck := false
ThisBuild / tlSonatypeUseLegacyHost := true

lazy val noPublishSettings =
  Seq(
    publishArtifact := false,
    skip / publish := true)

lazy val baseSettings = Seq(
  organization := "pl.iterators",
  organizationName := "Iterators",
  organizationHomepage := Some(url("https://www.iteratorshq.com")),
  pomIncludeRepository := const(true),
  scalacOptions ++= {
    Seq(
      "-encoding",
      "UTF-8",
      "-feature",
      "-language:implicitConversions") ++
    (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((3, _)) => Seq(
          "-unchecked",
          "-source:3.0-migration")
      case _ => Seq(
          "-deprecation",
          "-Xfatal-warnings",
          "-Wunused:imports,privates,locals",
          "-Wvalue-discard")
    })
  },
  licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0")),
  developers := List(
    Developer(id = "lsowa",
      name = "Łukasz Sowa",
      email = "lsowa@iteratorshq.com",
      url = url("https://github.com/luksow"))),
  scmInfo := Some(
    ScmInfo(browseUrl = url("https://github.com/theiterators/http4s-stir"),
      connection = "scm:git:https://github.com/theiterators/http4s-stir.git")),
  crossScalaVersions := supportedScalaVersions,
  scalafmtOnCompile := true)

val http4s = Seq(
  "org.http4s" %% "http4s-dsl" % "0.23.21",
  "org.http4s" %% "http4s-ember-server" % "0.23.21")

val http4sClient = Seq(
  "org.http4s" %% "http4s-ember-client" % "0.23.21")

val circe = Seq(
  "io.circe" %% "circe-core" % "0.14.5",
  "io.circe" %% "circe-generic" % "0.14.5",
  "io.circe" %% "circe-parser" % "0.14.5",
  "org.http4s" %% "http4s-circe" % "0.23.21")

val logback = Seq(
  "ch.qos.logback" % "logback-classic" % "1.4.7")

lazy val core = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .withoutSuffixFor(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("core"))
  .settings(baseSettings: _*)
  .settings(
    name := "http4s-stir",
    libraryDependencies ++= http4s,
    Compile / doc / scalacOptions -= "-Xfatal-warnings")

lazy val coreTests = project
  .in(file("core-tests"))
  .settings(baseSettings: _*)
  .settings(noPublishSettings: _*)
  .settings(
    name := "http4s-stir-tests",
    libraryDependencies ++= http4s ++ circe ++ Seq(
      "org.scalatest" %% "scalatest" % "3.2.15" % Test,
      "org.specs2" %% "specs2-core" % "4.19.2" % Test)).dependsOn(
    testkit.jvm % "test",
    core.jvm % "test->test")

lazy val testkit = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .withoutSuffixFor(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("testkit"))
  .settings(baseSettings: _*)
  .settings(
    name := "http4s-stir-testkit",
    libraryDependencies ++= http4s ++ http4sClient ++ Seq(
      "org.scalatest" %% "scalatest" % "3.2.15" % "provided",
      "org.specs2" %% "specs2-core" % "4.19.2" % "provided")).dependsOn(core)

lazy val examples = project
  .in(file("examples"))
  .settings(baseSettings: _*)
  .settings(noPublishSettings: _*)
  .settings(
    name := "http4s-stir-examples",
    libraryDependencies ++= http4s ++ circe ++ logback ++ Seq(
      "org.specs2" %% "specs2-core" % "4.19.2" % Test,
      "org.scalatest" %% "scalatest" % "3.2.15" % Test))
  .dependsOn(core.jvm, testkit.jvm % Test)

lazy val root = tlCrossRootProject.aggregate(core, testkit, examples, coreTests)
