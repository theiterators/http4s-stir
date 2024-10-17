val scala_2_13 = "2.13.15"
val scala_3 = "3.3.4"
val mainScalaVersion = scala_3
val supportedScalaVersions = Seq(scala_2_13, scala_3)

ThisBuild / crossScalaVersions := supportedScalaVersions
ThisBuild / scalaVersion := mainScalaVersion

ThisBuild / versionScheme := Some("early-semver")
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("11"), JavaSpec.temurin("17"))
ThisBuild / githubWorkflowPublishTargetBranches := Seq(RefPredicate.StartsWith(Ref.Tag("v")),
  RefPredicate.Equals(Ref.Branch("master")))
ThisBuild / tlBaseVersion := "0.4"
ThisBuild / tlCiHeaderCheck := false
ThisBuild / sonatypeCredentialHost := xerial.sbt.Sonatype.sonatypeLegacy

lazy val noPublishSettings =
  Seq(
    publishArtifact := false,
    skip / publish := true)

lazy val baseSettings = Seq(
  organization := "pl.iterators",
  sonatypeProfileName := organization.value,
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

val http4sDsl = Def.setting("org.http4s" %%% "http4s-dsl" % "0.23.28")
val http4sEmber = Def.setting("org.http4s" %%% "http4s-ember-server" % "0.23.28")

val fs2Core = Def.setting("co.fs2" %%% "fs2-core" % "3.11.0")
val fs2Io = Def.setting("co.fs2" %%% "fs2-io" % "3.11.0")

val http4sClient = Def.setting(
  "org.http4s" %%% "http4s-ember-client" % "0.23.28")

val circeCore = Def.setting("io.circe" %%% "circe-core" % "0.14.8")
val circeGeneric = Def.setting("io.circe" %%% "circe-generic" % "0.14.10")
val circeParser = Def.setting("io.circe" %%% "circe-parser" % "0.14.10")
val http4sCirce = Def.setting("org.http4s" %%% "http4s-circe" % "0.23.28")

val scalatest = Def.setting("org.scalatest" %%% "scalatest" % "3.2.19")
val specs2 = Def.setting("org.specs2" %%% "specs2-core" % "4.20.6")

val scalaXml = Def.setting("org.scala-lang.modules" %%% "scala-xml" % "2.2.0")

lazy val core = crossProject(JVMPlatform, NativePlatform, JSPlatform)
  .withoutSuffixFor(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("core"))
  .settings(baseSettings *)
  .settings(
    name := "http4s-stir",
    libraryDependencies ++= Seq(http4sDsl.value, http4sEmber.value) ++ Seq(fs2Core.value,
      fs2Io.value) ++ Seq(scalaXml.value),
    Compile / doc / scalacOptions -= "-Xfatal-warnings")

lazy val coreTests = crossProject(JVMPlatform)
  .withoutSuffixFor(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("core-tests"))
  .settings(baseSettings *)
  .settings(noPublishSettings *)
  .settings(
    name := "http4s-stir-tests",
    libraryDependencies ++= Seq(http4sDsl.value, http4sEmber.value) ++
    Seq(circeCore.value, circeGeneric.value, circeParser.value, http4sCirce.value) ++
    Seq(
      scalatest.value % Test,
      specs2.value % Test))
  .dependsOn(
    testkit % "test",
    core % "test->test")

lazy val testkit = crossProject(JVMPlatform)
  .withoutSuffixFor(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("testkit"))
  .settings(baseSettings *)
  .settings(
    name := "http4s-stir-testkit",
    libraryDependencies ++= Seq(http4sClient.value) ++ Seq(
      scalatest.value % "provided",
      specs2.value % "provided"))
  .dependsOn(core)

lazy val examples = crossProject(JVMPlatform)
  .withoutSuffixFor(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("examples"))
  .settings(baseSettings *)
  .settings(noPublishSettings *)
  .settings(
    name := "http4s-stir-examples",
    libraryDependencies ++= Seq(http4sDsl.value, http4sEmber.value) ++ Seq(circeCore.value, circeGeneric.value,
      circeParser.value, http4sCirce.value) ++ Seq(
      specs2.value % Test,
      scalatest.value % Test))
  .dependsOn(core, testkit % Test)

lazy val http4sStir = tlCrossRootProject.aggregate(core, testkit, examples, coreTests)
  .settings(baseSettings *)
