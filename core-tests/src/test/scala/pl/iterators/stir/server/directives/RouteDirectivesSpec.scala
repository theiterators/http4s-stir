package pl.iterators.stir.server.directives

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.http4s.{ Header, Headers, Method, Request, Response, Status, Uri }
import org.scalatest.wordspec.AnyWordSpec
import org.typelevel.ci.CIString
import pl.iterators.stir.marshalling.ToResponseMarshallable
import pl.iterators.stir.server._

import java.util.concurrent.atomic.AtomicReference

class RouteDirectivesSpec extends AnyWordSpec with GenericRoutingSpec {
  override implicit def runtime: IORuntime = IORuntime.global

  "ValidationRejection" should {
    "take its cause as optional, as Pekko's does" in {
      Get() ~> reject(ValidationRejection("Invalid input")) ~> check {
        rejection shouldEqual ValidationRejection("Invalid input", None)
      }
    }
  }

  "The `complete` directive" should {
    "send no entity for a status that allows none, with or without headers, as Pekko" in {
      val custom = Headers(Header.Raw(CIString("X-A"), "a"))
      for (status <- List(Status.NoContent, Status.ResetContent, Status.NotModified)) {
        Get() ~> complete(status, "x") ~> check {
          response.status shouldEqual status
          responseAs[String] shouldEqual ""
          response.headers.get(CIString("Content-Type")) shouldEqual None
          response.headers.get(CIString("Content-Length")) shouldEqual None
        }
        Get() ~> complete(status, custom, "x") ~> check {
          responseAs[String] shouldEqual ""
          response.headers.get(CIString("X-A")).map(_.head.value) shouldEqual Some("a")
          response.headers.get(CIString("Content-Type")) shouldEqual None
        }
      }
    }
    "be chainable with the `&` operator" in {
      Get() ~> (get & complete("yeah")) ~> check { responseAs[String] shouldEqual "yeah" }
    }
    "be lazy in its argument evaluation, independently of application style" in {
      var i = 0
      Put() ~> {
        get { complete { i += 1; "get" } } ~
        put {
          complete { i += 1; "put" }
        } ~
        (post & complete { i += 1; "post" })
      } ~> check {
        responseAs[String] shouldEqual "put"
        i shouldEqual 1
      }
    }
    "be lazy in its argument evaluation even when passing in a status code" in {
      var i = 0
      Put() ~> {
        get { complete(Status.Ok, { i += 1; "get" }) } ~
        put {
          complete(Status.Ok, { i += 1; "put" })
        } ~
        (post & complete(Status.Ok, { i += 1; "post" }))
      } ~> check {
        responseAs[String] shouldEqual "put"
        i shouldEqual 1
      }
    }
    "support completion from response futures" should {
      "simple case without marshaller" in {
        Get() ~> {
          get & complete(IO.pure(Response[IO](Status.Ok).withEntity("yup")))
        } ~> check { responseAs[String] shouldEqual "yup" }
      }
      "for successful futures and marshalling" in {
        Get() ~> complete(IO.pure("yes")) ~> check { responseAs[String] shouldEqual "yes" }
      }
      object TestException extends RuntimeException("Boom")
      "for failed futures and marshalling" in {
        val lines = new AtomicReference[List[String]](Nil)
        Get() ~> handleExceptions(recordingDefaultHandler(lines))(complete(IO.raiseError[String](TestException))) ~>
        check {
          status shouldEqual Status.InternalServerError
          responseAs[String] shouldEqual "" // Pekko: "There was an internal server error."
          lines.get shouldEqual List(logged500("Boom"))
        }
      }
    }

    "allow easy handling of futured ToResponseMarshallers" in {
      trait RegistrationStatus
      case class Registered(name: String) extends RegistrationStatus
      case object AlreadyRegistered extends RegistrationStatus

      val route =
        get {
          path("register" / Segment) { name =>
            def registerUser(name: String): IO[RegistrationStatus] = IO {
              name match {
                case "otto" => AlreadyRegistered
                case _      => Registered(name)
              }
            }
            complete {

              registerUser(name).map[ToResponseMarshallable] {
                case Registered(_)     => ""
                case AlreadyRegistered =>
                  import org.http4s.circe.CirceEntityEncoder._

                  Status.BadRequest -> Map[String, String]("error" -> "User already Registered")
              }
            }
          }
        }

      Get("/register/otto") ~> route ~> check {
        status shouldEqual Status.BadRequest
      }
      Get("/register/karl") ~> route ~> check {
        status shouldEqual Status.Ok
        responseAs[String] shouldEqual ""
      }
    }
  }

  "the redirect directive" should {
    "produce proper 'Found' redirections" in {
      Get() ~> {
        redirect(Uri.unsafeFromString("/foo"), Status.Found)
      } ~> check {
        status shouldEqual Status.Found
        headers.headers.head shouldEqual Header.Raw(CIString("Location"), "/foo")
      }
    }

    "produce proper 'NotModified' redirections" in {
      Get() ~> {
        redirect(Uri.unsafeFromString("/foo"), Status.NotModified)
      } ~> check {
        status shouldEqual Status.NotModified
        headers.headers.head shouldEqual Header.Raw(CIString("Location"), "/foo")
      }
    }
  }

  "the handle directive" should {
    "use a function to complete a request" in {
      Get(Uri.unsafeFromString("https://pekko.apache.org/foo")) ~> {
        handle(req => IO.pure(Response[IO](Status.Ok).withEntity(req.uri.toString)))
      } ~> check {
        response.status shouldEqual Status.Ok
        responseAs[String] shouldEqual "https://pekko.apache.org/foo"
      }
    }
    "fail the request when the future fails" in {
      Get(Uri.unsafeFromString("https://pekko.apache.org/foo")) ~> {
        concat(
          handle(_ => IO.raiseError(new IllegalStateException("Some error"))),
          complete(Status.ImATeapot))
      } ~> check { response.status shouldEqual Status.InternalServerError }
    }
    "fail the request when the function throws" in {
      Get(Uri.unsafeFromString("https://pekko.apache.org/foo")) ~> {
        concat(
          handle(_ => throw new IllegalStateException("Some error")),
          complete(Status.ImATeapot))
      } ~> check { response.status shouldEqual Status.InternalServerError }
    }
  }

  "the handle directive with PartialFunction" should {
    val handler: PartialFunction[Request[IO], IO[Response[IO]]] = {
      case Request(Method.GET, uri, _, _, _, _) if uri == Uri.unsafeFromString("/value") =>
        IO.pure(Response[IO]().withEntity("23"))
      case Request(Method.GET, uri, _, _, _, _) if uri == Uri.unsafeFromString("/fail") =>
        IO.raiseError(new RuntimeException("failure!"))
      case Request(Method.GET, uri, _, _, _, _) if uri == Uri.unsafeFromString("/throw") =>
        throw new RuntimeException("oops")
    }
    val theRejection = MethodRejection(Method.POST)
    val route = handle(handler, theRejection :: Nil)

    "use a PartialFunction to complete a request" in {
      Get("/value") ~> route ~> check {
        status shouldEqual Status.Ok
        responseAs[String] shouldEqual "23"
      }
    }
    "reject if the function is not defined for the request" in {
      Get("/other") ~> route ~> check {
        rejection shouldEqual theRejection
      }
    }

    "fail if the function returns a failure" in {
      val lines = new AtomicReference[List[String]](Nil)
      Get("/fail") ~> handleExceptions(recordingDefaultHandler(lines))(route) ~> check {
        status shouldBe Status.InternalServerError
        lines.get shouldEqual List(logged500("failure!"))
      }
    }

    "fail if the function throws" in {
      val lines = new AtomicReference[List[String]](Nil)
      Get("/throw") ~> handleExceptions(recordingDefaultHandler(lines))(route) ~> check {
        status shouldBe Status.InternalServerError
        lines.get shouldEqual List(logged500("oops"))
      }
    }
  }

  "the handleSync directive with PartialFunction" should {
    val handler: PartialFunction[Request[IO], Response[IO]] = {
      case Request(Method.GET, uri, _, _, _, _) if uri == Uri.unsafeFromString("/value") =>
        Response[IO]().withEntity("23")
      case Request(Method.GET, uri, _, _, _, _) if uri == Uri.unsafeFromString("/throw") =>
        throw new RuntimeException("oops")
    }
    val theRejection = MethodRejection(Method.POST)
    val route = handleSync(handler, theRejection :: Nil)

    "use a PartialFunction to complete a request" in {
      Get("/value") ~> route ~> check {
        status shouldEqual Status.Ok
        responseAs[String] shouldEqual "23"
      }
    }
    "reject if the function is not defined for the request" in {
      Get("/other") ~> route ~> check {
        rejection shouldEqual theRejection
      }
    }

    "fail if the function throws" in {
      val lines = new AtomicReference[List[String]](Nil)
      Get("/throw") ~> handleExceptions(recordingDefaultHandler(lines))(route) ~> check {
        status shouldBe Status.InternalServerError
        lines.get shouldEqual List(logged500("oops"))
      }
    }
  }
}
