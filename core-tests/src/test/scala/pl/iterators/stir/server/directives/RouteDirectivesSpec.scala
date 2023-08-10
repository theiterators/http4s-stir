package pl.iterators.stir.server.directives

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.http4s.{ Header, Method, Request, Response, Status, Uri }
import org.scalatest.wordspec.AnyWordSpec
import org.typelevel.ci.CIString
import pl.iterators.stir.marshalling.ToResponseMarshallable
import pl.iterators.stir.server._

class RouteDirectivesSpec extends AnyWordSpec with GenericRoutingSpec {
  override implicit def runtime: IORuntime = IORuntime.global

  "The `complete` directive" should {
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
      "for failed futures and marshalling" in // EventFilter[TestException.type](
//        occurrences = 1,
//        message = BasicRouteSpecs.defaultExnHandler500Error("Boom")).intercept {
      Get() ~> complete(IO.raiseError[String](TestException)) ~>
      check {
        status shouldEqual Status.InternalServerError
        responseAs[String] shouldEqual ""
      }
//      }
//      "for futures failed with a RejectionError" in {
//        Get() ~> complete(IO.raiseError[String](RejectionError(AuthorizationFailedRejection)).future) ~>
//          check {
//            rejection shouldEqual AuthorizationFailedRejection
//          }
//      }
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
                case Registered(_) => ""
                case AlreadyRegistered =>
                  import org.http4s.circe.CirceEntityEncoder._

                  //                  import SprayJsonSupport._
                  //
                  //                  // FIXME: Scala 3 workaround, which cannot figure out the implicit itself
                  //                  // Needs to avoid importing more implicits accidentally from DefaultJsonProtocol to avoid ambiguity in
                  //                  // Scala 2
                  //                  implicit val mapFormat: spray.json.RootJsonFormat[Map[String, String]] = {
                  //                    import spray.json.DefaultJsonProtocol
                  //                    import DefaultJsonProtocol._
                  //                    DefaultJsonProtocol.mapFormat
                  //                  }

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
//    "do Content-Type negotiation for multi-marshallers" in {
//      val route = get & complete(Data("Ida", 83))
//
//      import pekko.http.scaladsl.model.headers.Accept
//      Get().withHeaders(Accept(MediaTypes.`application/json`)) ~> route ~> check {
//        responseAs[String] shouldEqual
//          """{"age":83,"name":"Ida"}"""
//      }
//      Get().withHeaders(Accept(MediaTypes.`text/xml`)) ~> route ~> check {
//        responseAs[xml.NodeSeq] shouldEqual <data><name>Ida</name><age>83</age></data>
//      }
//      Get().withHeaders(Accept(MediaTypes.`text/plain`)) ~> Route.seal(route) ~> check {
//        status shouldEqual StatusCodes.NotAcceptable
//      }
//    }
//    "avoid marshalling too eagerly for multi-marshallers" in {
//      case class MyClass(value: String)
//
//      implicit val superMarshaller = {
//        val jsonMarshaller =
//          Marshaller.stringMarshaller(MediaTypes.`application/json`)
//            .compose[MyClass] { mc =>
//              println(s"jsonMarshaller marshall $mc")
//              mc.value
//            }
//        val textMarshaller = Marshaller.stringMarshaller(MediaTypes.`text/html`)
//          .compose[MyClass] { mc =>
//            println(s"textMarshaller marshall $mc")
//            throw new IllegalArgumentException(s"Unexpected value $mc")
//          }
//
//        Marshaller.oneOf(jsonMarshaller, textMarshaller)
//      }
//      val request =
//        HttpRequest(uri = "/test")
//          .withHeaders(Accept(MediaTypes.`application/json`))
//      val response = Await.result(Marshal(MyClass("test")).toResponseFor(request), 1.second)
//      response.status shouldEqual StatusCodes.OK
//    }
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

    "fail if the function returns a failure" in // EventFilter[RuntimeException](occurrences = 1).intercept {
    Get("/fail") ~> route ~> check {
      status shouldBe Status.InternalServerError
    }
//    }

    "fail if the function throws" in // EventFilter[RuntimeException](occurrences = 1).intercept {
    Get("/throw") ~> route ~> check {
      status shouldBe Status.InternalServerError
    }
//    }
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

    "fail if the function throws" in // EventFilter[RuntimeException](occurrences = 1).intercept {
    Get("/throw") ~> route ~> check {
      status shouldEqual Status.InternalServerError
    }
//    }
  }

//  case class Data(name: String, age: Int)
//  object Data {
//    import spray.json.DefaultJsonProtocol._
//    import SprayJsonSupport._
//    import ScalaXmlSupport._
//
//    val jsonMarshaller: ToEntityMarshaller[Data] = jsonFormat2(Data.apply)
//
//    val xmlMarshaller: ToEntityMarshaller[Data] = Marshaller.combined { (data: Data) =>
//      <data><name>{data.name}</name><age>{data.age}</age></data>
//    }
//
//    implicit val dataMarshaller: ToResponseMarshaller[Data] =
//      Marshaller.oneOf(jsonMarshaller, xmlMarshaller)
//  }
}
