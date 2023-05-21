import cats.effect._
import cats.implicits._
import com.comcast.ip4s._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax._
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.http4s._
import org.http4s.implicits._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.server.middleware.{Logger => Http4sLogger}
import org.http4s.ember.server._
import pdsl.server.Route
import pdsl.server.directives.RouteDirectives._
import pdsl.server.directives.MethodDirectives._
import pdsl.server.directives.RouteConcatenation._
import pdsl.server.directives.PathDirectives._


object Main extends IOApp.Simple {
  val routes: Route = {
    (get & path("beers")) {
      complete {
        IO.delay(println(s"beers")).map(_ => Status.Ok)
      }
    } ~
      (post & path("beers" / JavaUUID)) { (id) =>
        complete {
          IO.delay(println(s"beers/$id")).map(_ => Status.Created)
        }
      } ~
      (delete & path("beers" / JavaUUID)) { (id) =>
        complete {
          IO.delay(println(s"beers/$id")).map(_ => Status.NoContent)
        }
      } ~
      (get & path(Segment)) { (resource) =>
        complete {
          IO.delay(println(s"GET /$resource")).map(_ => Status.Ok)
        }
      } ~
      (post & path(Segment / JavaUUID)) { (resource, id) =>
        complete {
          IO.delay(println(s"POST /$resource/$id")).map(_ => Status.Created)
        }
      } ~
      (delete & path(Segment / JavaUUID)) { (resource, id) =>
        complete {
          IO.delay(println(s"DELETE /$resource/$id")).map(_ => Status.NoContent)
        }
      }
  }

  val run = {
    val logger = Slf4jLogger.getLogger[IO]
    EmberServerBuilder
      .default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(port"8080")
      .withHttpApp(routes.toHttpRoutes)
      .build
      .use(_ => IO.never)
  }
}