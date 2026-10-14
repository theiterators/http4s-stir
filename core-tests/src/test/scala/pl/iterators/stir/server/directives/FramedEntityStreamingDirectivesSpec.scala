package pl.iterators.stir.server.directives

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import fs2.Stream
import org.http4s.{ MediaType, Status }
import org.http4s.headers.`Content-Type`
import pl.iterators.stir.common.EntityStreamingSupport
import pl.iterators.stir.server._
import pl.iterators.stir.unmarshalling.Unmarshaller

class FramedEntityStreamingDirectivesSpec extends RoutingSpec {
  override implicit def runtime: IORuntime = IORuntime.global

  /** Reads the `a` field of a JSON object such as `{"a":1}`. */
  implicit val aField: Unmarshaller[String, Int] = Unmarshaller.strict[String, Int] { s =>
    io.circe.parser.parse(s).flatMap(_.hcursor.get[Int]("a")).fold(throw _, identity)
  }

  val json = `Content-Type`(MediaType.application.json)
  val csv = `Content-Type`(MediaType.text.csv)

  def sumRoute(implicit support: EntityStreamingSupport): Route =
    entity(asSourceOf[Int]) { ints => onSuccess(ints.compile.toList) { list => complete(list.mkString(",")) } }

  "asSourceOf with JSON streaming support" should {
    implicit val support: EntityStreamingSupport = EntityStreamingSupport.json()

    "decode the objects of a JSON array" in {
      Post("/", """[{"a":1},{"a":2}]""") ~> addHeader(json) ~> sumRoute ~> check {
        responseAs[String] shouldEqual "1,2"
      }
    }
    "decode newline separated objects" in {
      Post("/", "{\"a\":1}\n{\"a\":2}\n") ~> addHeader(json) ~> sumRoute ~> check {
        responseAs[String] shouldEqual "1,2"
      }
    }
    "keep braces inside strings and nested objects together" in {
      Post("/", """[{"a":1,"s":"}{","n":{"x":[1,2]}}, {"a":3}]""") ~> addHeader(json) ~> sumRoute ~> check {
        responseAs[String] shouldEqual "1,3"
      }
    }
    "reject a request whose Content-Type is not supported" in {
      Post("/", """[{"a":1}]""") ~> addHeader(`Content-Type`(MediaType.text.plain)) ~> sumRoute ~> check {
        rejection shouldEqual UnsupportedRequestContentTypeRejection(Set(MediaType.application.json),
          Some(MediaType.text.plain))
      }
    }
    "complete with 400 when an object exceeds the maximum length" in {
      val small = EntityStreamingSupport.json(maxObjectLength = 8)
      Post("/", """[{"a":1234567}]""") ~> addHeader(json) ~> Route.seal(sumRoute(small)) ~> check {
        status shouldEqual Status.BadRequest
      }
    }
    "reject a top-level array of scalars with 400" in {
      Post("/", "[1,2,3]") ~> addHeader(json) ~> Route.seal(sumRoute) ~> check { status shouldEqual Status.BadRequest }
    }
    "reject trailing garbage after the last object with 400" in {
      Post("/", """[{"a":1}] nonsense""") ~> addHeader(json) ~> Route.seal(sumRoute) ~> check {
        status shouldEqual Status.BadRequest
      }
    }
    "reject a body cut off inside an object with 400" in {
      Post("/", """[{"a":1""") ~> addHeader(json) ~> Route.seal(sumRoute) ~> check {
        status shouldEqual Status.BadRequest
      }
    }
    "decode a streamed array over a real server" in {
      Post("/", """[{"a":1},{"a":2},{"a":3}]""") ~> addHeader(json) ~!> sumRoute ~> check {
        responseAs[String] shouldEqual "1,2,3"
      }
    }
    "render a stream as a JSON array" in {
      Get() ~> complete(Stream("\"a\"", "\"b\"").covary[IO]) ~> check {
        mediaType shouldEqual MediaType.application.json
        responseAs[String] shouldEqual """["a","b"]"""
      }
    }
  }

  "asSourceOf with CSV streaming support" should {
    implicit val support: EntityStreamingSupport = EntityStreamingSupport.csv()

    "decode one element per line" in {
      Post("/", "a,b\r\nc,d\n") ~> addHeader(csv) ~> entity(asSourceOf[String]) { lines =>
        onSuccess(lines.compile.toList) { list => complete(list.mkString("|")) }
      } ~> check { responseAs[String] shouldEqual "a,b|c,d" }
    }
    "fail a line over the limit before the line ends" in {
      val small = EntityStreamingSupport.csv(maxLineLength = 4)
      val readPastTheLimit = new java.util.concurrent.atomic.AtomicBoolean(false)
      val body: Stream[IO, Byte] =
        Stream.emits("abcdefgh".getBytes("UTF-8")).covary[IO] ++
        Stream.exec(IO(readPastTheLimit.set(true))) ++
        Stream.emits("ijkl\n".getBytes("UTF-8"))
      Post("/", body) ~> addHeader(csv) ~> Route.seal(entity(asSourceOf[String](small)) { lines =>
        onSuccess(lines.compile.toList) { list => complete(list.mkString("|")) }
      }) ~> check {
        status shouldEqual Status.BadRequest
        readPastTheLimit.get shouldEqual false
      }
    }
    "render a stream as lines" in {
      Get() ~> complete(Stream("a,b", "c,d").covary[IO]) ~> check {
        mediaType shouldEqual MediaType.text.csv
        responseAs[String] shouldEqual "a,b\nc,d\n"
      }
    }
  }
}
