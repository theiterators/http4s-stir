package pl.iterators.stir.server

import cats.effect.unsafe.IORuntime
import org.http4s.{ Method, Status }
import pl.iterators.stir.server.directives.RoutingSpec

/** Pekko's `BasicRouteSpecs`: the core combinators, `Directive.as`, inner-route re-execution and sealing. */
class BasicRouteSpecs extends RoutingSpec {
  override implicit def runtime: IORuntime = IORuntime.global

  "routes created by the concatenation operator '~'" should {
    "yield the first sub route if it succeeded" in {
      Get() ~> {
        get { complete("first") } ~ get { complete("second") }
      } ~> check { responseAs[String] shouldEqual "first" }
    }
    "yield the second sub route if the first did not succeed" in {
      Get() ~> {
        post { complete("first") } ~ get { complete("second") }
      } ~> check { responseAs[String] shouldEqual "second" }
    }
    "collect rejections from both sub routes" in {
      Delete() ~> {
        get { completeOk } ~ put { completeOk }
      } ~> check { rejections shouldEqual Seq(MethodRejection(Method.GET), MethodRejection(Method.PUT)) }
    }
    "clear rejections that have already been 'overcome' by previous directives" in {
      Put() ~> {
        put { parameter("yeah") { echoComplete } } ~
        get { completeOk }
      } ~> check { rejection shouldEqual MissingQueryParamRejection("yeah") }
    }
  }

  "routes created by the 'concat' directive" should {
    "reject with zero arguments, since no routes were matched" in {
      Get() ~> concat() ~> check { rejections shouldEqual Seq() }
    }
    "yield the first sub route if it succeeded" in {
      Get() ~> concat(get { complete("first") }, get { complete("second") }) ~> check {
        responseAs[String] shouldEqual "first"
      }
    }
    "yield the second sub route if the first did not succeed" in {
      Get() ~> concat(post { complete("first") }, get { complete("second") }) ~> check {
        responseAs[String] shouldEqual "second"
      }
    }
    "collect rejections from both sub routes" in {
      Delete() ~> concat(get { completeOk }, put { completeOk }) ~> check {
        rejections shouldEqual Seq(MethodRejection(Method.GET), MethodRejection(Method.PUT))
      }
    }
    "clear rejections that have already been 'overcome' by previous directives" in {
      Put() ~> concat(put { parameter("yeah") { echoComplete } }, get { completeOk }) ~> check {
        rejection shouldEqual MissingQueryParamRejection("yeah")
      }
    }
  }

  "Route conjunction" should {
    val stringDirective = provide("The cat")
    val intDirective = provide(42)
    val doubleDirective = provide(23.0)

    val dirStringInt = stringDirective & intDirective
    val dirStringIntDouble = dirStringInt & doubleDirective
    val dirDoubleStringInt = doubleDirective & dirStringInt
    val dirStringIntStringInt = dirStringInt & dirStringInt

    "work for two elements" in {
      Get("/abc") ~> dirStringInt { (str, i) => complete(s"$str ${i + 1}") } ~> check {
        responseAs[String] shouldEqual "The cat 43"
      }
    }
    "work for 2 + 1" in {
      Get("/abc") ~> dirStringIntDouble { (str, i, d) => complete(s"$str ${i + 1} ${d + 0.1}") } ~> check {
        responseAs[String] shouldEqual "The cat 43 23.1"
      }
    }
    "work for 1 + 2" in {
      Get("/abc") ~> dirDoubleStringInt { (d, str, i) => complete(s"$str ${i + 1} ${d + 0.1}") } ~> check {
        responseAs[String] shouldEqual "The cat 43 23.1"
      }
    }
    "work for 2 + 2" in {
      Get("/abc") ~> dirStringIntStringInt { (str, i, str2, i2) => complete(s"$str ${i + i2} $str2") } ~> check {
        responseAs[String] shouldEqual "The cat 84 The cat"
      }
    }
  }

  "Route disjunction" should {
    "work in the happy case" in {
      val route = Route.seal((path("abc") | path("def")) { completeOk })
      Get("/abc") ~> route ~> check { status shouldEqual Status.Ok }
      Get("/def") ~> route ~> check { status shouldEqual Status.Ok }
      Get("/ghi") ~> route ~> check { status shouldEqual Status.NotFound }
    }
    "don't apply alternative if inner route rejects" in {
      object MyRejection extends Rejection
      val route = (path("abc") | post) { reject(MyRejection) }
      Get("/abc") ~> route ~> check { rejection shouldEqual MyRejection }
    }
  }

  "Case class extraction with Directive.as" should {
    "extract one argument" in {
      case class MyNumber(i: Int)
      val abcPath = path("abc" / IntNumber).as(MyNumber.apply _)(echoComplete)
      Get("/abc/5") ~> abcPath ~> check { responseAs[String] shouldEqual "MyNumber(5)" }
    }
    "extract two arguments" in {
      case class Person(name: String, age: Int)
      val personPath = path("person" / Segment / IntNumber).as(Person.apply _)(echoComplete)
      Get("/person/john/38") ~> personPath ~> check { responseAs[String] shouldEqual "Person(john,38)" }
    }
    "reject if case class requirements fail" in {
      case class MyValidNumber(i: Int) {
        require(i > 10)
      }
      val abcPath = path("abc" / IntNumber).as(MyValidNumber.apply _)(echoComplete)
      Get("/abc/5") ~> abcPath ~> check { rejection shouldBe a[ValidationRejection] }
    }
  }

  "Dynamic execution of inner routes of Directive0" should {
    "re-execute inner routes every time" in {
      var a = ""
      val dynamicRoute = get { a += "x"; complete(a) }
      def expect(route: Route, s: String) = Get() ~> route ~> check { responseAs[String] shouldEqual s }

      expect(dynamicRoute, "x")
      expect(dynamicRoute, "xx")
      expect(dynamicRoute, "xxx")
      expect(dynamicRoute, "xxxx")
    }
  }

  case object MyException extends RuntimeException("Boom")

  "Route sealing" should {
    "catch route execution exceptions" in {
      Get("/abc") ~> Route.seal(get { _ => throw MyException }) ~> check {
        status shouldEqual Status.InternalServerError
      }
    }
    "catch route building exceptions" in {
      Get("/abc") ~> Route.seal(get { throw MyException }) ~> check {
        status shouldEqual Status.InternalServerError
      }
    }
    "convert all rejections to responses" in {
      object MyRejection extends Rejection
      Get("/abc") ~> Route.seal(get { reject(MyRejection) }) ~> check {
        status shouldEqual Status.InternalServerError
      }
    }
    "always prioritize MethodRejections over AuthorizationFailedRejections" in {
      Get("/abc") ~> Route.seal(post { completeOk } ~ authorize(false) { completeOk }) ~> check {
        status shouldEqual Status.MethodNotAllowed
        responseAs[String] shouldEqual "HTTP method not allowed, supported methods: POST"
      }
      Get("/abc") ~> Route.seal(authorize(false) { completeOk } ~ post { completeOk }) ~> check {
        status shouldEqual Status.MethodNotAllowed
      }
    }
  }
}
