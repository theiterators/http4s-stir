import cats.effect._
import cats.implicits._
import com.comcast.ip4s._
import io.circe.generic.semiauto.deriveCodec
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax._
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.http4s._
import org.http4s.implicits._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe.jsonOf
import org.http4s.server.middleware.{Logger => Http4sLogger}
import org.http4s.ember.server._
import pdsl.server.Route
import pdsl.server.directives.RouteDirectives._
import pdsl.server.directives.MethodDirectives._
import pdsl.server.directives.RouteConcatenation._
import pdsl.server.directives.PathDirectives._
import pdsl.server.directives.MarshallingDirectives._
import pl.iterators.kebs.Http4s
import pl.iterators.kebs.circe.KebsCirce

import scala.jdk.CollectionConverters._
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

case class Beer(id: UUID, name: String, style: String, abv: Double)

object Main extends IOApp.Simple with KebsCirce with Http4s {
  val beers = new ConcurrentHashMap[UUID, Beer]()

  val routes: Route = {
    (get & path("beers")) {
      complete {
        Status.Ok -> beers.values().asScala.toList
      }
    } ~
      (post & path("beers") & entityAs[Beer]) { (beer) =>
        complete {
          Option(beers.get(beer.id)) match { // yes, race condition here :-D
            case Some(_) => Status.Conflict -> "Beer already exists"
            case None =>
              beers.put(beer.id, beer)
              Status.Created -> beer
          }
        }
      } ~
      (delete & path("beers" / JavaUUID)) { (id) =>
        complete {
          IO.delay(beers.remove(id)).map(_ => Status.NoContent -> "Yes, content")
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