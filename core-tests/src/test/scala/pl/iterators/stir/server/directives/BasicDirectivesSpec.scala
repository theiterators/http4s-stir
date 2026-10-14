package pl.iterators.stir.server.directives

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.http4s.{ Response, Status }
import fs2.{ text, Stream }
import pl.iterators.stir.server.{ Route, RouteResult }

import scala.concurrent.duration._

class BasicDirectivesSpec extends RoutingSpec {
  override implicit def runtime: IORuntime = IORuntime.global

  "The `mapUnmatchedPath` directive" should {
    "map the unmatched path" in {
      Get("/abc") ~> {
        mapUnmatchedPath(_ / "def") {
          path("abc" / "def") { completeOk }
        }
      } ~> check { response.status shouldEqual Status.Ok }
    }
  }

  "The `extract` directive" should {
    "extract from the RequestContext" in {
      Get("/abc") ~> {
        extract(_.request.method.name) {
          echoComplete
        }
      } ~> check { responseAs[String] shouldEqual "GET" }
    }
  }

  "The `extractDataBytes` directive" should {
    "extract stream of ByteString from the RequestContext" in {
      val dataBytes = Stream.range[IO, Byte](1, 10)
      Post("/abc", dataBytes) ~> {
        extractDataBytes { data =>
          val sum = data.compile.fold(0L)(_ + _)
          onSuccess(sum) { s =>
            complete(Response[IO](Status.Ok).withEntity(s.toString))
          }
        }
      } ~> check { responseAs[String] shouldEqual "45" }
    }
  }

  "The `extractRequestEntity` directive" should {
    "extract entity from the RequestContext" in {
      val httpEntity = "abc"
      Post("/abc", httpEntity) ~> {
        extractRequestEntity { complete(_) }
      } ~> check { responseEntity.through(text.utf8.decode).compile.string.unsafeRunSync() shouldEqual httpEntity }
    }
  }

  "The `extractMatchedPath` directive" should {
    "extract bar if /foo has been matched for /foo/bar" in {
      Get("/foo") ~> {
        pathPrefix("foo") {
          extractMatchedPath { matched =>
            complete(matched.toString)
          }
        }
      } ~> check { responseAs[String] shouldEqual "/foo" }
    }

    "extract bar with slash if /foo/ with slash has been matched for /foo/bar" in {
      Get("/foo/") ~> {
        pathPrefix("foo"./) {
          extractMatchedPath { matched =>
            complete(matched.toString)
          }
        }
      } ~> check { responseAs[String] shouldEqual "/foo/" }
    }

    "extract bar with slash if /foo/ with slash has been matched for /foo/bar if nested directives used" in {
      Get("/foo/bar/car") ~> {
        pathPrefix("foo") {
          pathPrefix("bar") {
            extractMatchedPath { matched =>
              complete(matched.toString)
            }
          }
        }
      } ~> check { responseAs[String] shouldEqual "/foo/bar" }
    }

    "extract all if fully matched" in {
      Get("/foo/bar") ~> {
        pathPrefix("foo") {
          pathPrefix("bar") {
            extractMatchedPath { matched =>
              complete(matched.toString)
            }
          }
        }
      } ~> check { responseAs[String] shouldEqual "/foo/bar" }
    }

    "extract nothing if root path" in {
      Get("/foo/bar") ~> {
        extractMatchedPath { matched =>
          complete(matched.toString)
        }
      } ~> check { responseAs[String] shouldEqual "" }
    }
  }

  "The mapRouteResultIO directive" should {
    val echoResponse = mapRouteResultIO { res =>
      def response(msg: String): RouteResult =
        RouteResult.Complete(Response[IO](Status.Ok).withEntity(msg))

      res.map {
        case RouteResult.Complete(res) =>
          response(s"Completed with status ${res.status.code}")
        case RouteResult.Rejected(rejections) =>
          response(s"Rejected with [${rejections.mkString(", ")}]")
      }.recover {
        case e =>
          response(s"Failed with exception [${e.getMessage}]")
      }
    }

    "be able to handle completed results" in {
      Get() ~> echoResponse { complete("Hello World") } ~> check {
        responseAs[String] shouldEqual "Completed with status 200"
      }
    }
    "be able to handle rejections" in {
      Get() ~> echoResponse { reject } ~> check {
        responseAs[String] shouldEqual "Rejected with []"
      }
    }
    "be able to handle failures" in {
      Get() ~> echoResponse { failWith(new IllegalStateException("errrorr")) } ~> check {
        responseAs[String] shouldEqual "Failed with exception [errrorr]"
      }
    }
    "be able to handle failures created by inner exceptions" in {
      Get() ~> echoResponse { get { throw new IllegalStateException("errrorr") } } ~> check {
        status shouldEqual Status.Ok // RouteTest has sealing for exceptions so it'll return a 500 if the exception gets through
        responseAs[String] shouldEqual "Failed with exception [errrorr]"
      }
    }
  }
//
//  "The `extractStrictEntity` directive" should {
//    "change request to contain strict entity for inner routes" in {
//      val chunks = () => List("Akka", "HTTP").map(HttpEntity.Chunk(_)).iterator
//      val entity = HttpEntity.Chunked(ContentTypes.`text/plain(UTF-8)`, Source.fromIterator(chunks))
//
//      Post("/abc", entity) ~> {
//        extractRequestEntity { before =>
//          extractStrictEntity(200.millis) { _ =>
//            extractRequestEntity { after =>
//              complete(Seq(before, after).map(_.isStrict).mkString(" => "))
//            }
//          }
//        }
//      } ~> check { responseAs[String] shouldEqual "false => true" }
//    }
//
//    "only consume data once when nested" in {
//      val randomStream = Iterator.continually(ThreadLocalRandom.current.nextInt(0, 2).toString).take(100)
//      val chunks = Source.fromIterator(() => randomStream.map(HttpEntity.Chunk(_)))
//      val entity = HttpEntity.Chunked(ContentTypes.`text/plain(UTF-8)`, chunks)
//
//      Post("/abc", entity) ~> {
//        extractStrictEntity(200.millis) { outer =>
//          extractStrictEntity(200.millis) { inner =>
//            /* Check that the string representations of the outer and inner
//             * random number sequences are identical. */
//            complete(Seq(outer, inner).map(_.data.utf8String).distinct.size.toString)
//          }
//        }
//      } ~> check { responseAs[String] shouldEqual "1" }
//    }
//
//    "return 408 Request Timeout status on timeout" in {
//      val neverEndingEntity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, Source.maybe[ByteString])
//      val timeout = 10.milliseconds
//
//      Post("/abc", neverEndingEntity) ~> {
//        extractStrictEntity(timeout) { _ =>
//          complete("content")
//        }
//      } ~> check {
//        entityAs[String] shouldEqual s"Request timed out after $timeout while waiting for entity data"
//        status shouldEqual StatusCodes.RequestTimeout
//      }
//    }
//
//    "return 400 Bad Request if max size is exceeded" in {
//      val request = HttpEntity(ContentTypes.`text/plain(UTF-8)`, Source.single(ByteString("abcd")))
//      val timeout = 10.milliseconds
//
//      Post("/abc", request) ~> {
//        extractStrictEntity(timeout, 2) { _ =>
//          complete("content")
//        }
//      } ~> check {
//        entityAs[String] shouldEqual "Request too large"
//        status shouldEqual StatusCodes.BadRequest
//      }
//    }
//
//    "return 400 Bad request on EntityStreamException" in {
//      val errorMessage = "An EntityStreamException error"
//      val errorDetail = "The internal details of the error"
//      val entity = Source.failed(EntityStreamException(errorMessage, errorDetail))
//      val request = HttpEntity(ContentTypes.`text/plain(UTF-8)`, entity)
//      val timeout = 10.milliseconds
//
//      Post("/abc", request) ~> {
//        extractStrictEntity(timeout) { _ =>
//          complete("content")
//        }
//      } ~> check {
//        entityAs[String] shouldEqual errorMessage
//        status shouldEqual StatusCodes.BadRequest
//      }
//    }
//
//    "return 500 InternalServerError status if response generation timed out" in {
//      Get("/abc") ~> {
//        extractStrictEntity(1.second) { _ =>
//          throw new TimeoutException()
//        }
//      } ~> check {
//        status shouldEqual StatusCodes.InternalServerError
//      }
//    }
//  }

  "The `toStrictEntity` directive" should {
    "let the inner route consume the entity more than once" in {
      Post("/", "abc") ~!> {
        toStrictEntity(1.second) {
          entity(as[String]) { first =>
            entity(as[String]) { second => complete(first + second) }
          }
        }
      } ~> check { responseAs[String] shouldEqual "abcabc" }
    }
    "complete with 413 when the entity exceeds maxBytes" in {
      Post("/", "0123456789") ~> Route.seal(toStrictEntity(1.second, maxBytes = 5) { completeOk }) ~> check {
        status shouldEqual Status.PayloadTooLarge
        responseAs[String] should include("exceeded size limit")
      }
    }
    "complete with 408 when the entity does not arrive in time" in {
      Post("/", Stream.never[IO]) ~> Route.seal(toStrictEntity(100.millis) { completeOk }) ~> check {
        status shouldEqual Status.RequestTimeout
      }
    }
  }

  "The `extractStrictEntity` directive" should {
    "provide the buffered bytes and keep the body readable" in {
      Post("/", "abc") ~> extractStrictEntity(1.second) { bytes =>
        entity(as[String]) { body => complete(new String(bytes.toArray, "UTF-8") + body) }
      } ~> check { responseAs[String] shouldEqual "abcabc" }
    }
  }
}
