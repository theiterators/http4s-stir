import cats.effect.IO
import cats.effect.unsafe.IORuntime
import io.circe.Json
import org.http4s.Status
import org.http4s.server.websocket.WebSocketBuilder2
import org.specs2.mutable.Specification
import pl.iterators.stir.testkit.Specs2RouteTest

import java.util.UUID

class ServiceRoutesSpec extends Specification with Specs2RouteTest {
  override implicit val runtime: IORuntime = IORuntime.global
  import Main._
  import org.http4s.circe.CirceEntityEncoder._
  import org.http4s.circe.CirceEntityDecoder._

  val routes = Main.routes(WebSocketBuilder2[IO].unsafeRunSync())

  sequential
  "The service" should {

    "return a list of beers for GET requests to /api/beers" in {
      val request = Get(uri = "/api/beers")

      request ~> routes ~> check {
        status === Status.Ok
        responseAs[List[Beer]] shouldEqual List.empty[Beer]
      }
    }

    "create a new beer for POST requests to /api/beers" in {
      // Create a Beer
      val beer = Beer(UUID.randomUUID(), "Test Beer", "Test Style", 5.5)

      // Create request
      val request = Post(uri = "/api/beers").withEntity(
        io.circe.parser.parse(
          s"""{"id": "${beer.id}", "name": "${beer.name}", "style": "${beer.style}", "abv": ${beer.abv}}""").getOrElse(
          Json.Null))

      // Test
      request ~> addHeader("Authorization", "Bearer someToken") ~> routes ~> check {
        status === Status.Created
        entityAs[Beer] shouldEqual beer
      }
    }

    "throw an IllegalArgumentException for GET requests to /oops" in {
      // Create request
      val request = Get(uri = "/oops")

      // Test
      request ~> routes ~> check {
        status === Status.InternalServerError
      }
    }
  }
}
