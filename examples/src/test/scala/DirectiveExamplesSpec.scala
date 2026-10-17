import cats.effect.{ IO, SyncIO }
import cats.effect.unsafe.IORuntime
import fs2.Stream
import io.circe.Codec
import io.circe.generic.semiauto._
import io.circe.parser.decode
import org.http4s.{ ContentCoding, EntityEncoder, EntityTag, HttpDate, MediaType, Status, Uri }
import org.http4s.headers._
import org.http4s.headers.Range.SubRange
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.typelevel.ci._
import org.typelevel.vault.Key
import pl.iterators.stir.common.EntityStreamingSupport
import pl.iterators.stir.server._
import pl.iterators.stir.server.Directives._
import pl.iterators.stir.testkit.ScalatestRouteTest
import pl.iterators.stir.unmarshalling.Unmarshaller

import scala.concurrent.duration._

/** Compiles and runs the examples of the directive pages added in 0.7, so the docs cannot drift from the code. */
class DirectiveExamplesSpec extends AnyWordSpec with Matchers with ScalatestRouteTest {
  override implicit val runtime: IORuntime = IORuntime.global

  case class Measurement(id: String, value: Double)
  implicit val measurementCodec: Codec[Measurement] = deriveCodec

  "the cache condition example" should {
    val tag = EntityTag("v42")
    val modified = HttpDate.unsafeFromEpochSecond(1700000000L)
    val route: Route = path("resource") {
      conditional(tag, modified) {
        complete("the resource")
      }
    }
    "answer 304 to a matching If-None-Match" in {
      Get("/resource") ~> addHeader(`If-None-Match`(tag)) ~> route ~> check { status shouldEqual Status.NotModified }
    }
  }

  "the coding example" should {
    val route: Route = encodeResponse { complete("a large text body " * 10) }
    "gzip when asked" in {
      Get() ~> addHeader(`Accept-Encoding`(ContentCoding.gzip)) ~> route ~> check {
        header[`Content-Encoding`] shouldEqual Some(`Content-Encoding`(ContentCoding.gzip))
      }
    }
  }

  "the size limit example" should {
    val route: Route = withSizeLimit(16) { entity(as[String]) { body => complete(s"${body.length} chars") } }
    "answer 413 for a body over the limit" in {
      Post("/", "0" * 17) ~> Route.seal(route) ~> check { status shouldEqual Status.PayloadTooLarge }
    }
  }

  "the attribute example" should {
    val userKey: Key[String] = Key.newKey[SyncIO, String].unsafeRunSync()
    val route: Route =
      mapRequest(request => request.withAttributes(request.attributes.insert(userKey, "alice"))) {
        attribute(userKey) { user => complete(s"Hello, $user") }
      }
    "read the attribute" in {
      Get() ~> route ~> check { responseAs[String] shouldEqual "Hello, alice" }
    }
  }

  "the entity streaming example" should {
    implicit val measurementUnmarshaller: Unmarshaller[String, Measurement] =
      Unmarshaller.strict(json => decode[Measurement](json).fold(throw _, identity))
    implicit val jsonStreaming: EntityStreamingSupport = EntityStreamingSupport.json()
    implicit val measurementEncoder: EntityEncoder[IO, Measurement] = org.http4s.circe.jsonEncoderOf[IO, Measurement]

    val sum: Route = post {
      entity(asSourceOf[Measurement]) { measurements =>
        onSuccess(measurements.map(_.value).compile.foldMonoid) { total => complete(s"Total: $total") }
      }
    }
    val list: Route = get {
      complete(Stream.emits(List(Measurement("a", 1.0), Measurement("b", 2.5))).covary[IO])
    }
    "sum a streamed array" in {
      Post("/", """[{"id":"a","value":1.0},{"id":"b","value":2.5}]""") ~>
      addHeader(`Content-Type`(MediaType.application.json)) ~> sum ~> check {
        responseAs[String] shouldEqual "Total: 3.5"
      }
    }
    "render a stream as a JSON array" in {
      Get() ~> list ~> check { responseAs[String] shouldEqual """[{"id":"a","value":1.0},{"id":"b","value":2.5}]""" }
    }
  }

  "the range example" should {
    val route: Route = path("data") { withRangeSupport { complete(Array.tabulate(10)(_.toByte)) } }
    "answer a partial response" in {
      Get("/data") ~> addHeader(Range(SubRange(2L, 4L))) ~> route ~> check {
        status shouldEqual Status.PartialContent
        responseAs[Array[Byte]] shouldEqual Array[Byte](2, 3, 4)
      }
    }
  }

  "the same origin example" should {
    val allowed = Origin.Host(Uri.Scheme.https, Uri.RegName("example.com"), None)
    val route: Route = checkSameOrigin(allowed) { complete("same origin") }
    "pass the allowed origin" in {
      Get() ~> addHeader(Origin.HostList(cats.data.NonEmptyList.one(allowed)): Origin) ~> route ~> check {
        responseAs[String] shouldEqual "same origin"
      }
    }
  }

  "the timeout example" should {
    val route: Route = withRequestTimeout(5.seconds) {
      extractRequestTimeout { timeout => complete(s"This route must answer within $timeout") }
    }
    "report the timeout" in {
      Get() ~> route ~> check { responseAs[String] shouldEqual "This route must answer within 5 seconds" }
    }
  }

  "the query example" should {
    val route: Route = path("search") { (query & entity(as[String])) { text => complete(s"Results for $text") } }
    "answer a QUERY request" in {
      Query("/search", "beer") ~> route ~> check { responseAs[String] shouldEqual "Results for beer" }
    }
  }

  "the websocket testing example" should {
    "recognise the headers the docs tell you to send" in {
      val upgrade = addHeaders(
        Connection(ci"Upgrade"),
        org.http4s.headers.Upgrade(org.http4s.Protocol(ci"websocket", None)),
        org.http4s.Header.Raw(ci"Sec-WebSocket-Version", "13"),
        org.http4s.Header.Raw(ci"Sec-WebSocket-Key", "dGhlIHNhbXBsZSBub25jZQ=="))
      Get("/ws") ~> upgrade ~> extractOfferedWsProtocols { offered => complete(offered.size.toString) } ~> check {
        responseAs[String] shouldEqual "0"
      }
    }
  }
}
