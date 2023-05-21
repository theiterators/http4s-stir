scalaVersion := "2.13.10"

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.4.7" ,
  "io.circe" % "circe-core_2.13" % "0.14.5" ,
  "io.circe" % "circe-parser_2.13" % "0.14.5" ,
  "io.circe" % "circe-generic_2.13" % "0.14.5" ,
  "org.http4s" % "http4s-dsl_2.13" % "1.0.0-M39" ,
  "org.http4s" % "http4s-circe_2.13" % "1.0.0-M39" ,
  "org.http4s" % "http4s-ember-server_2.13" % "1.0.0-M39"
)
