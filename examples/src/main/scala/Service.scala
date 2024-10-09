import cats.effect._
import cats.effect.std.Random
import com.comcast.ip4s._
import fs2.{ Pipe, Stream }
import io.circe.Codec
import io.circe.generic.semiauto._
import org.http4s._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.ember.server._
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame
import pl.iterators.stir.server.{ ExceptionHandler, RejectionHandler, Route }
import pl.iterators.stir.server.Directives._
import pl.iterators.stir.server.directives.CredentialsHelper

import java.io.File
import scala.jdk.CollectionConverters._
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import scala.annotation.nowarn
import scala.concurrent.duration._

case class Beer(id: UUID, name: String, style: String, abv: Double) {
  require(abv >= 0 && abv <= 100, "ABV must be between 0 and 100")
}

object Main extends IOApp.Simple {
  @nowarn
  implicit val beerCodec: Codec[Beer] = deriveCodec[Beer]
  val beers = new ConcurrentHashMap[UUID, Beer]()

  val authenticator = new Authenticator[String] {
    override def apply(v1: CredentialsHelper): Option[String] = v1 match {
      case c @ CredentialsHelper.Provided(identifier) if c.verify("password") => Some(identifier)
      case _                                                                  => None
    }
  }

  def routes(ws: WebSocketBuilder2[IO]): Route = {
    handleExceptions(ExceptionHandler.default()) {
      handleRejections(RejectionHandler.default) {
        logRequestResult() {
          extractClientIP { _ =>
            pathPrefix("api" / "beers") {
              (get & parameters("pageSize".as[Int].?, "pageNumber".as[Int].?) & pathEndOrSingleSlash) {
                (pageSize, pageNumber) =>
                  complete {
                    Status.Ok -> beers.values().asScala.toList.slice(pageNumber.getOrElse(0) * pageSize.getOrElse(25),
                      pageNumber.getOrElse(0) * pageSize.getOrElse(25) + pageSize.getOrElse(25))
                  }
              } ~
              (post & pathEndOrSingleSlash & entity(as[Beer]) & optionalHeaderValueByName("Authorization")) {
                (beer, _) =>
                  complete {
                    Option(beers.get(beer.id)) match { // yes, race condition here :-D
                      case Some(_) => Status.Conflict -> "Beer already exists"
                      case None =>
                        beers.put(beer.id, beer)
                        Status.Created -> beer
                    }
                  }
              } ~
              (get & path(JavaUUID) & pathEndOrSingleSlash) { id =>
                complete {
                  Option(beers.get(id)) match {
                    case Some(beer) => Status.Ok -> beer
                    case None       => Status.NotFound -> "Beer not found"
                  }
                }
              } ~
              (delete & path(JavaUUID) & pathEndOrSingleSlash) { id =>
                complete {
                  IO.delay(beers.remove(id)).map(_ => Status.NoContent -> "Yes, content")
                }
              } ~ path("legacy") {
                (post & pathEndOrSingleSlash & formFields("id".as[UUID], "name", "style", "abv".as[Double])) {
                  (id, name, style, abv) =>
                    validate(abv >= 0 && abv <= 100, "ABV must be between 0 and 100") {
                      complete {
                        val beer = Beer(id, name, style, abv)
                        beers.put(beer.id, beer)
                        Status.Created -> beer
                      }
                    }
                }
              }
            } ~ path("oops") {
              complete {
                throw new IllegalArgumentException("Oops")
              }
            } ~ path("timeout-maybe") {
              withRequestTimeout(3.second) {
                complete {
                  Random.scalaUtilRandom[IO].flatMap {
                    _.betweenInt(0, 6).flatMap { int =>
                      IO.delay(println(s"Got: $int")).flatMap { _ =>
                        IO.sleep(int.seconds).map(_ => Status.Ok)
                      }
                    }
                  }
                }
              }
            } ~ (path("file-upload") & storeUploadedFiles("file", fi => new File("/tmp/" + fi.fileName))) { files =>
              complete {
                Status.Ok -> s"File $files uploaded"
              }
            } ~ authenticateBasic("d-and-d-realm", authenticator) { _ =>
              path("file") {
                getFromFile("project/plugins.sbt")
              } ~ pathPrefix("dir") {
                getFromDirectory("core/src/main")
              }
            }
          } ~ path("ws") {
            val send: Stream[IO, WebSocketFrame] =
              Stream.awakeEvery[IO](1.second).map(_ => WebSocketFrame.Text("Hello"))
            val receive: Pipe[IO, WebSocketFrame, Unit] = _.evalMap(frame => IO(println(frame)))
            handleWebSocketMessages(ws, send, receive)
          } ~ http4sRoutes()
        }
      }
    }
  }

  def http4sRoutes(): Route = {
    import org.http4s.dsl.io._
    httpRoutesOf {
      case GET -> Root / "hello" =>
        Ok("Hello")
      case GET -> Root / "hello" / name =>
        Ok(s"Hello $name")
    }
  }

  val run = {
    EmberServerBuilder
      .default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(port"8080")
      .withHttpWebSocketApp(ws => routes(ws).toHttpRoutes.orNotFound)
      .build
      .use(_ => IO.never)
  }
}
