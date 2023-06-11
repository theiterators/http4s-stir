import cats.effect._
import com.comcast.ip4s._
import org.http4s._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.ember.server._
import pl.iterators.stir.server.{ ExceptionHandler, RejectionHandler, Route }
import pl.iterators.stir.server.Directives._
import pl.iterators.kebs.Http4s
import pl.iterators.kebs.circe.KebsCirce

import scala.jdk.CollectionConverters._
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

case class Beer(id: UUID, name: String, style: String, abv: Double)

object Main extends IOApp.Simple with KebsCirce with Http4s {
  val beers = new ConcurrentHashMap[UUID, Beer]()

  val routes: Route = {
    handleExceptions(ExceptionHandler.default()) {
      handleRejections(RejectionHandler.default) {
        logRequestResult() {
          pathPrefix("api" / "beers") {
            (get & parameters("pageSize".as[Int].?, "pageNumber".as[Int].?) & pathEndOrSingleSlash) {
              (pageSize, pageNumber) =>
                complete {
                  Status.Ok -> beers.values().asScala.toList.slice(pageNumber.getOrElse(0) * pageSize.getOrElse(25),
                    pageNumber.getOrElse(0) * pageSize.getOrElse(25) + pageSize.getOrElse(25))
                }
            } ~
            (post & pathEndOrSingleSlash & entityAs[Beer] & optionalHeaderValueByName("Authorization")) {
              (beer, token) =>
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
          } ~ path("oops") {
            complete {
              throw new IllegalArgumentException("Oops")
            }
          } ~ path("file") {
            getFromFile("../scala-3-http4s-doobie/Main.scala")
          } ~ pathPrefix("dir") {
            getFromDirectory("src/main")
          }
        }
      }
    }
  }

  val run = {
    EmberServerBuilder
      .default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(port"8080")
      .withHttpApp(routes.toHttpRoutes.orNotFound)
      .build
      .use(_ => IO.never)
  }
}
