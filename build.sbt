scalaVersion := "2.13.10"

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.4.7" ,
  "io.circe" % "circe-core_2.13" % "0.14.5" ,
  "io.circe" % "circe-parser_2.13" % "0.14.5" ,
  "io.circe" % "circe-generic_2.13" % "0.14.5" ,
  "org.http4s" % "http4s-dsl_2.13" % "0.23.19-RC3" ,
  "org.http4s" % "http4s-circe_2.13" % "0.23.19-RC3" ,
  "org.http4s" % "http4s-ember-server_2.13" % "0.23.19-RC3",
  "pl.iterators" %% "kebs-http4s" % "1.9.5",
  "pl.iterators" %% "kebs-circe" % "1.9.5",
  "com.beachape" %% "enumeratum" % "1.7.2"
)
