package pl.iterators.stir.server.directives

import cats.effect.{ IO, SyncIO }
import cats.effect.unsafe.IORuntime
import org.http4s.{ Request, Status }
import org.typelevel.vault.Key
import pl.iterators.stir.server._

class AttributeDirectivesSpec extends RoutingSpec {
  override implicit def runtime: IORuntime = IORuntime.global

  val userKey: Key[String] = Key.newKey[SyncIO, String].unsafeRunSync()

  def withUser(user: String): Request[IO] => Request[IO] =
    request => request.withAttributes(request.attributes.insert(userKey, user))

  "The attribute directive" should {
    "extract the attribute when it is present" in {
      Get() ~> withUser("alice") ~> attribute(userKey) { echoComplete } ~> check {
        responseAs[String] shouldEqual "alice"
      }
    }
    "extract an attribute added with the testkit's addAttribute" in {
      Get() ~> addAttribute(userKey, "bob") ~> attribute(userKey) { echoComplete } ~> check {
        responseAs[String] shouldEqual "bob"
      }
    }
    "reject with MissingAttributeRejection when it is absent" in {
      Get() ~> attribute(userKey) { echoComplete } ~> check {
        rejection shouldEqual MissingAttributeRejection(userKey)
      }
    }
    "complete with 500 when sealed and the attribute is absent" in {
      Get() ~> Route.seal(attribute(userKey) { echoComplete }) ~> check {
        status shouldEqual Status.InternalServerError
      }
    }
  }

  "The optionalAttribute directive" should {
    "extract Some when the attribute is present" in {
      Get() ~> withUser("alice") ~> optionalAttribute(userKey) { echoComplete } ~> check {
        responseAs[String] shouldEqual "Some(alice)"
      }
    }
    "extract None when the attribute is absent" in {
      Get() ~> optionalAttribute(userKey) { echoComplete } ~> check { responseAs[String] shouldEqual "None" }
    }
  }
}
