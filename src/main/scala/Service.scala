import cats.effect._
import com.comcast.ip4s._
import org.http4s._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.ember.server._
import pl.iterators.stir.server.Route
import pl.iterators.stir.server.directives.RouteDirectives._
import pl.iterators.stir.server.directives.MethodDirectives._
import pl.iterators.stir.server.directives.RouteConcatenation._
import pl.iterators.stir.server.directives.PathDirectives._
import pl.iterators.stir.server.directives.MarshallingDirectives._
import pl.iterators.stir.server.directives.ParameterDirectives._
import pl.iterators.kebs.Http4s
import pl.iterators.kebs.circe.KebsCirce

import scala.jdk.CollectionConverters._
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

case class Beer(id: UUID, name: String, style: String, abv: Double)

object Main extends IOApp.Simple with KebsCirce with Http4s {
  val beers = new ConcurrentHashMap[UUID, Beer]()

  val routes: Route = {
    pathPrefix("api" / "beers") {
      (get & parameters("pageSize".as[Int].?, "pageNumber".as[Int].?) & pathEndOrSingleSlash) { (pageSize, pageNumber) =>
        complete {
          Status.Ok -> beers.values().asScala.toList.slice(pageNumber.getOrElse(0) * pageSize.getOrElse(25), pageNumber.getOrElse(0) * pageSize.getOrElse(25) + pageSize.getOrElse(25))
        }
      } ~
        (post & pathEndOrSingleSlash & entityAs[Beer]) { beer =>
          complete {
            Option(beers.get(beer.id)) match { // yes, race condition here :-D
              case Some(_) => Status.Conflict -> "Beer already exists"
              case None =>
                beers.put(beer.id, beer)
                Status.Created -> beer
            }
          }
        } ~
        (delete & pathEndOrSingleSlash & path(JavaUUID)) { id =>
          complete {
            IO.delay(beers.remove(id)).map(_ => Status.NoContent -> "Yes, content")
          }
        }
    }
  }

  val run = {
    EmberServerBuilder
      .default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(port"8080")
      .withHttpApp(routes.toHttpRoutes)
      .build
      .use(_ => IO.never)
  }
}