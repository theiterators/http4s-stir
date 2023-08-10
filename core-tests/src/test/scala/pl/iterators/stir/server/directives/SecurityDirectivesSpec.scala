package pl.iterators.stir.server.directives

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.http4s.{ AuthScheme, BasicCredentials, Challenge, Credentials, Header, Status }
import org.http4s.headers.{ `WWW-Authenticate`, Authorization }
import org.typelevel.ci.CIString
import pl.iterators.stir.server.AuthenticationFailedRejection.{ CredentialsMissing, CredentialsRejected }
import pl.iterators.stir.server._

class SecurityDirectivesSpec extends RoutingSpec {
  override implicit def runtime: IORuntime = IORuntime.global

  val dontBasicAuth = authenticateBasicAsync[String]("MyRealm", _ => IO.pure(None))
  val dontOAuth2Auth = authenticateOAuth2Async[String]("MyRealm", _ => IO.pure(None))
  val doBasicAuth = authenticateBasicPF("MyRealm", { case CredentialsHelper.Provided(identifier) => identifier })
  val doOAuth2Auth = authenticateOAuth2PF("MyRealm", { case CredentialsHelper.Provided(identifier) => identifier })
  val authWithAnonymous = doBasicAuth.withAnonymousUser("We are Legion")

  val basicChallenge = Challenge("Basic", "MyRealm", Map("charset" -> "UTF-8"))
  val oAuth2Challenge = Challenge("Bearer", "MyRealm")

  def OAuth2BearerToken(token: String): Credentials = Credentials.Token(AuthScheme.Bearer, token)

  def doBasicAuthVerify(secret: String) =
    authenticateBasicPF("MyRealm",
      { case p @ CredentialsHelper.Provided(identifier) if p.verify(secret) => identifier })
  def doBasicAuthProvideVerify(secret: String) =
    authenticateBasicPF("MyRealm",
      {
        case p @ CredentialsHelper.Provided(identifier) if p.provideVerify(password => secret == password) => identifier
      })

  "basic authentication" should {
    "reject requests without Authorization header with an AuthenticationFailedRejection" in {
      Get() ~> {
        dontBasicAuth { echoComplete }
      } ~> check { rejection shouldEqual AuthenticationFailedRejection(CredentialsMissing, basicChallenge) }
    }
    "reject unauthenticated requests with Authorization header with an AuthenticationFailedRejection" in {
      Get() ~> addHeader(Authorization(BasicCredentials("Bob", ""))) ~> {
        dontBasicAuth { echoComplete }
      } ~> check { rejection shouldEqual AuthenticationFailedRejection(CredentialsRejected, basicChallenge) }
    }
    "reject requests with an OAuth2 Bearer Token Authorization header with 401" in {
      Get() ~> addHeader(Authorization(OAuth2BearerToken("myToken"))) ~> Route.seal {
        dontOAuth2Auth { echoComplete }
      } ~> check {
        status shouldEqual Status.Unauthorized
        responseAs[String] shouldEqual "The supplied authentication is invalid"
        header[`WWW-Authenticate`] shouldEqual Some(`WWW-Authenticate`(oAuth2Challenge))
      }
    }
    "reject requests with illegal Authorization header with 401" in {
      Get() ~> addHeader(Header.Raw(CIString("Authorization"), "bob alice")) ~> Route.seal {
        dontBasicAuth { echoComplete }
      } ~> check {
        status shouldEqual Status.Unauthorized
        responseAs[String] shouldEqual "The supplied authentication is invalid"
        header[`WWW-Authenticate`] shouldEqual Some(`WWW-Authenticate`(basicChallenge))
      }
    }
    "extract the object representing the user identity created by successful authentication" in {
      Get() ~> addHeader(Authorization(BasicCredentials("Alice", ""))) ~> {
        doBasicAuth { echoComplete }
      } ~> check { responseAs[String] shouldEqual "Alice" }
    }
    "extract the object representing the user identity created by verifying user password" in {
      val secret = "secret"
      Get() ~> addHeader(Authorization(BasicCredentials("Alice", secret))) ~> {
        doBasicAuthVerify(secret) { echoComplete }
      } ~> check { responseAs[String] shouldEqual "Alice" }
    }
    "extract the object representing the user identity created by providing a custom verifier to test user password" in {
      val secret = "secret"
      Get() ~> addHeader(Authorization(BasicCredentials("Alice", secret))) ~> {
        doBasicAuthProvideVerify(secret) { echoComplete }
      } ~> check { responseAs[String] shouldEqual "Alice" }
    }
    "extract the object representing the user identity created for the anonymous user" in {
      Get() ~> {
        authWithAnonymous { echoComplete }
      } ~> check { responseAs[String] shouldEqual "We are Legion" }
    }
    "properly handle exceptions thrown in its inner route" in {
      object TestException extends RuntimeException("Boom")
//      EventFilter[TestException.type](
//        occurrences = 1,
//        start =
//          "Error during processing of request: 'Boom'. Completing with 500 Internal Server Error response.").intercept {
      Get() ~> addHeader(Authorization(BasicCredentials("Alice", ""))) ~> {
        Route.seal {
          doBasicAuth { _ => throw TestException }
        }
      } ~> check { status shouldEqual Status.InternalServerError }
//      }
    }
  }
  "bearer token authentication" should {
    "reject requests without Authorization header with an AuthenticationFailedRejection" in {
      Get() ~> {
        dontOAuth2Auth { echoComplete }
      } ~> check { rejection shouldEqual AuthenticationFailedRejection(CredentialsMissing, oAuth2Challenge) }
    }
    "reject unauthenticated requests with Authorization header with an AuthenticationFailedRejection" in {
      Get() ~> addHeader(Authorization(OAuth2BearerToken("myToken"))) ~> {
        dontOAuth2Auth { echoComplete }
      } ~> check { rejection shouldEqual AuthenticationFailedRejection(CredentialsRejected, oAuth2Challenge) }
    }
    "reject unauthenticated requests without Authorization header but with access_token URI parameter with an AuthenticationFailedRejection" in {
      Get("?access_token=myToken") ~> {
        dontOAuth2Auth { echoComplete }
      } ~> check { rejection shouldEqual AuthenticationFailedRejection(CredentialsRejected, oAuth2Challenge) }
    }
    "reject requests with a Basic Authorization header with 401" in {
      Get() ~> addHeader(Authorization(BasicCredentials("Alice", ""))) ~> Route.seal {
        dontBasicAuth { echoComplete }
      } ~> check {
        status shouldEqual Status.Unauthorized
        responseAs[String] shouldEqual "The supplied authentication is invalid"
        header[`WWW-Authenticate`] shouldEqual Some(`WWW-Authenticate`(basicChallenge))
      }
    }
    "reject requests with illegal Authorization header with 401" in {
      Get() ~> addHeader(Header.Raw(CIString("Authorization"), "bob alice")) ~> Route.seal {
        dontOAuth2Auth { echoComplete }
      } ~> check {
        status shouldEqual Status.Unauthorized
        responseAs[String] shouldEqual "The supplied authentication is invalid"
        header[`WWW-Authenticate`] shouldEqual Some(`WWW-Authenticate`(oAuth2Challenge))
      }
    }
    "extract the object representing the user identity created by successful authentication with Authorization header" in {
      Get() ~> addHeader(Authorization(OAuth2BearerToken("myToken"))) ~> {
        doOAuth2Auth { echoComplete }
      } ~> check { responseAs[String] shouldEqual "myToken" }
    }
    "extract the object representing the user identity created by successful authentication with access_token URI parameter" in {
      Get("?access_token=myToken") ~> {
        doOAuth2Auth { echoComplete }
      } ~> check { responseAs[String] shouldEqual "myToken" }
    }
    "extract the object representing the user identity created for the anonymous user" in {
      Get() ~> {
        authWithAnonymous { echoComplete }
      } ~> check { responseAs[String] shouldEqual "We are Legion" }
    }
    "properly handle exceptions thrown in its inner route" in {
      object TestException extends RuntimeException("Boom")
//      EventFilter[TestException.type](
//        occurrences = 1,
//        start =
//          "Error during processing of request: 'Boom'. Completing with 500 Internal Server Error response.").intercept {
      Get() ~> addHeader(Authorization(OAuth2BearerToken("myToken"))) ~> {
        Route.seal {
          doOAuth2Auth { _ => throw TestException }
        }
      } ~> check { status shouldEqual Status.InternalServerError }
//      }
    }
  }
  "authentication directives" should {
    "properly stack" in {
      val otherChallenge = Challenge("MyAuth", "MyRealm2")
      val otherAuth: Directive1[String] = authenticateOrRejectWithChallenge { (_: Option[Credentials]) =>
        IO.pure(Left(otherChallenge))
      }
      val bothAuth = dontBasicAuth | otherAuth

      Get() ~> Route.seal(bothAuth { echoComplete }) ~> check {
        status shouldEqual Status.Unauthorized
        header[`WWW-Authenticate`].get.values.toList shouldEqual Seq(basicChallenge, otherChallenge)
      }
    }
  }

  "authorization directives" should {
    "authorize" in {
      Get() ~> {
        authorize(_ => true) { complete("OK") }
      } ~> check { responseAs[String] shouldEqual "OK" }
    }
    "not authorize" in {
      Get() ~> {
        authorize(_ => false) { complete("OK") }
      } ~> check { rejection shouldEqual AuthorizationFailedRejection }
    }

    "authorizeAsync" in {
      Get() ~> {
        authorizeAsync(_ => IO.pure(true)) { complete("OK") }
      } ~> check { responseAs[String] shouldEqual "OK" }
    }
    "not authorizeAsync" in {
      Get() ~> {
        authorizeAsync(_ => IO.pure(false)) { complete("OK") }
      } ~> check { rejection shouldEqual AuthorizationFailedRejection }
    }
    "not authorizeAsync when future fails" in {
      Get() ~> {
        authorizeAsync(_ => IO.raiseError(new Exception("Boom!"))) { complete("OK") }
      } ~> check { rejection shouldEqual AuthorizationFailedRejection }
    }
  }
}
