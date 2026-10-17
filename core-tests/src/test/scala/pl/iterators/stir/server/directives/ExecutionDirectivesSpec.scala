package pl.iterators.stir.server.directives

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.http4s.{ ContentCoding, MediaRange, MediaType, QValue, Response, Status }
import org.http4s.headers.{ `Content-Encoding`, Accept, MediaRangeAndQValue }
import pl.iterators.stir.server._

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ExecutionDirectivesSpec extends RoutingSpec {
  override implicit def runtime: IORuntime = IORuntime.global

  object MyException extends RuntimeException("Boom")
  val handler =
    ExceptionHandler {
      case MyException => complete(Status.InternalServerError, "Pling! Plong! Something went wrong!!!")
    }

  "The `handleExceptions` directive" should {
    "handle an exception strictly thrown in the inner route with the supplied exception handler" in {
      exceptionShouldBeHandled {
        handleExceptions(handler) { _ =>
          throw MyException
        }
      }
    }
    "handle an Future.failed RouteResult with the supplied exception handler" in {
      exceptionShouldBeHandled {
        handleExceptions(handler) { _ =>
          IO.raiseError(MyException)
        }
      }
    }
    "handle an eventually failed Future[RouteResult] with the supplied exception handler" in {
      exceptionShouldBeHandled {
        handleExceptions(handler) { _ =>
          IO.fromFuture(IO(Future {
            Thread.sleep(100)
            throw MyException
          }))
        }
      }
    }
    "handle an exception happening during route building" in {
      exceptionShouldBeHandled {
        get {
          handleExceptions(handler) {
            throw MyException
          }
        }
      }
    }
    "not interfere with alternative routes" in {
      val lines = new AtomicReference[List[String]](Nil)
      Get("/abc") ~> handleExceptions(recordingDefaultHandler(lines)) {
        get {
          handleExceptions(handler)(reject) ~ { _ => throw MyException }
        }
      } ~> check {
        status shouldEqual Status.InternalServerError
        responseAs[String] shouldEqual "" // Pekko: "There was an internal server error."
        lines.get shouldEqual List(logged500("Boom"))
      }
    }
    "not handle other exceptions" in {
      val lines = new AtomicReference[List[String]](Nil)
      Get("/abc") ~> handleExceptions(recordingDefaultHandler(lines)) {
        get {
          handleExceptions(handler) { throw new RuntimeException("buh") }
        }
      } ~> check {
        status shouldEqual Status.InternalServerError
        lines.get shouldEqual List(logged500("buh"))
      }
    }
    "always fall back to a default content type" in {
      val lines = new AtomicReference[List[String]](Nil)
      val route = handleExceptions(recordingDefaultHandler(lines)) {
        get {
          handleExceptions(handler) { throw new RuntimeException("buh2") }
        }
      }
      Get("/abc") ~> addHeader(Accept(MediaType.application.json)) ~> route ~> check {
        status shouldEqual Status.InternalServerError
      }
      Get("/abc")                                            ~> addHeader(Accept(MediaRangeAndQValue(MediaType.text.xml),
        MediaRangeAndQValue(MediaRange.`*/*`, QValue.Zero))) ~> route ~> check {
        status shouldEqual Status.InternalServerError
      }
      lines.get shouldEqual List(logged500("buh2"), logged500("buh2"))
    }
  }

  "ExceptionHandler.seal(handler)(logAction)" should {
    "log through the given logAction when the custom handler does not match" in {
      val logged = new AtomicReference[Option[String]](None)
      val sealedHandler = ExceptionHandler.seal(handler)(Some((_, message) => IO(logged.set(Some(message)))))
      Get() ~> handleExceptions(sealedHandler) {
        complete(IO.raiseError[String](new RuntimeException("unexpected boom")))
      } ~> check {
        status shouldEqual Status.InternalServerError
        logged.get.getOrElse("") should include("unexpected boom")
      }
    }
    "still use the custom handler when it matches" in {
      val logged = new AtomicReference[Option[String]](None)
      val sealedHandler = ExceptionHandler.seal(handler)(Some((_, message) => IO(logged.set(Some(message)))))
      Get() ~> handleExceptions(sealedHandler) { throw MyException } ~> check {
        responseAs[String] shouldEqual "Pling! Plong! Something went wrong!!!"
        logged.get shouldEqual None
      }
    }
  }

  "The `handleRejections` directive" should {
    "handle encodeResponse inside RejectionHandler for non-success responses" in {
      val rejectionHandler: RejectionHandler = RejectionHandler.newBuilder()
        .handleNotFound {
          encodeResponseWith(ContentCoding.gzip) {
            complete(Status.NotFound, "Not here!")
          }
        }.result()

      Get("/hell0") ~>
      get {
        handleRejections(rejectionHandler) {
          encodeResponseWith(ContentCoding.gzip) {
            path("hello") {
              get {
                complete(Response[IO](status = Status.Ok).withEntity("world"))
              }
            }
          }
        }
      } ~> check {
        header[`Content-Encoding`].map(_.contentCoding) shouldEqual Some(ContentCoding.gzip)
        status shouldEqual Status.NotFound
      }
    }
  }

  "Default handler" should {
    "handle `IllegalRequestException` with its status and message" in {
      val lines = new AtomicReference[List[String]](Nil)
      Get("/abc") ~> handleExceptions(recordingDefaultHandler(lines)) {
        get { throw IllegalRequestException(Status.Conflict, "Some summary.") }
      } ~> check {
        status shouldEqual Status.Conflict
        responseAs[String] shouldEqual "Some summary."
        lines.get shouldEqual List(ExceptionHandler.errorMessageTemplate("Some summary.", Status.Conflict.toString()))
      }
    }
    "handle exceptions other than `IllegalRequestException`" in {
      val lines = new AtomicReference[List[String]](Nil)
      Get("/abc") ~> handleExceptions(recordingDefaultHandler(lines)) { get { throw new RuntimeException("re") } } ~>
      check {
        status shouldEqual Status.InternalServerError
        lines.get shouldEqual List(logged500("re"))
      }
    }
    "show exception class if .getMessage == null" in {
      val lines = new AtomicReference[List[String]](Nil)
      Get("/abc") ~> handleExceptions(recordingDefaultHandler(lines)) { get { throw new NullPointerException } } ~>
      check {
        status shouldEqual Status.InternalServerError
        lines.get shouldEqual List(logged500(s"${classOf[NullPointerException].getName} (No error message supplied)"))
      }
    }
  }

  def exceptionShouldBeHandled(route: Route) =
    Get("/abc") ~> route ~> check {
      status shouldEqual Status.InternalServerError
      responseAs[String] shouldEqual "Pling! Plong! Something went wrong!!!"
    }
}
