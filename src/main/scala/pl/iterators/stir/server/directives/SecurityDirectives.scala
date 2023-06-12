package pl.iterators.stir.server.directives

import cats.effect.IO
import org.http4s.headers.Authorization
import org.http4s.{BasicCredentials, Challenge, Credentials}
import org.typelevel.ci.CIString
import pl.iterators.stir.server.{AuthenticationFailedRejection, AuthorizationFailedRejection, Directive0, Directive1, RequestContext, Route}
import pl.iterators.stir.impl.util._
import AuthenticationFailedRejection._

import scala.reflect.ClassTag
import scala.util._

/**
 * Provides directives for securing an inner route using the standard Http authentication headers [[`WWW-Authenticate`]]
 * and [[Authorization]]. Most prominently, HTTP Basic authentication and OAuth 2.0 Authorization Framework
 * as defined in RFC 2617 and RFC 6750 respectively.
 *
 * See: <a href="https://www.ietf.org/rfc/rfc2617.txt">RFC 2617</a>.
 * See: <a href="https://www.ietf.org/rfc/rfc6750.txt">RFC 6750</a>.
 *
 * @groupname security Security directives
 * @groupprio security 220
 */
trait SecurityDirectives {
  import BasicDirectives._
  import HeaderDirectives._
  import IODirectives._
  import RouteDirectives._

  //#authentication-result
  /**
   * The result of an HTTP authentication attempt is either the user object or
   * an HttpChallenge to present to the browser.
   *
   * @group security
   */
  type AuthenticationResult[+T] = Either[Challenge, T]
  //#authentication-result

  //#authenticator
  /**
   * @group security
   */
  type Authenticator[T] = CredentialsHelper => Option[T]
  //#authenticator
  //#async-authenticator
  /**
   * @group security
   */
  type AsyncAuthenticator[T] = CredentialsHelper => IO[Option[T]]
  //#async-authenticator
  //#authenticator-pf
  /**
   * @group security
   */
  type AuthenticatorPF[T] = PartialFunction[CredentialsHelper, T]
  //#authenticator-pf
  //#async-authenticator-pf
  /**
   * @group security
   */
  type AsyncAuthenticatorPF[T] = PartialFunction[CredentialsHelper, IO[T]]
  //#async-authenticator-pf

  /**
   * Extracts the potentially present [[Credentials]] provided with the request's [[Authorization]] header.
   *
   * @group security
   */
  def extractCredentials: Directive1[Option[Credentials]] =
    optionalHeaderValueByType[Authorization].map(_.map(_.credentials))

  /**
   * Wraps the inner route with Http Basic authentication support using a given `Authenticator[T]`.
   * The given authenticator determines whether the credentials in the request are valid
   * and, if so, which user object to supply to the inner route.
   *
   * @group security
   */
  def authenticateBasic[T](realm: String, authenticator: Authenticator[T]): AuthenticationDirective[T] =
    authenticateBasicAsync(realm, cred => IO.pure(authenticator(cred)))

  /**
   * Wraps the inner route with Http Basic authentication support.
   * The given authenticator determines whether the credentials in the request are valid
   * and, if so, which user object to supply to the inner route.
   *
   * @group security
   */
  def authenticateBasicAsync[T](realm: String, authenticator: AsyncAuthenticator[T]): AuthenticationDirective[T] =
      authenticateOrRejectWithChallenge[Credentials, T] { credAll =>
        val cred = credAll match {
          case b @ Some(BasicCredentials(_, _)) => b
          case _ => None
        }
        authenticator(CredentialsHelper(cred)).map {
          case Some(t) => AuthenticationResult.success(t)
          case None    => AuthenticationResult.failWithChallenge(Challenge("Basic", realm, Map("charset" -> "UTF-8")))
        }
    }

  /**
   * A directive that wraps the inner route with Http Basic authentication support.
   * The given authenticator determines whether the credentials in the request are valid
   * and, if so, which user object to supply to the inner route.
   *
   * @group security
   */
  def authenticateBasicPF[T](realm: String, authenticator: AuthenticatorPF[T]): AuthenticationDirective[T] =
    authenticateBasic(realm, authenticator.lift)

  /**
   * A directive that wraps the inner route with Http Basic authentication support.
   * The given authenticator determines whether the credentials in the request are valid
   * and, if so, which user object to supply to the inner route.
   *
   * @group security
   */
  def authenticateBasicPFAsync[T](realm: String, authenticator: AsyncAuthenticatorPF[T]): AuthenticationDirective[T] = {
      authenticateBasicAsync(realm, credentials =>
        if (authenticator isDefinedAt credentials) authenticator(credentials).map(Some(_))
        else IO.pure(None))
    }

  /**
   * A directive that wraps the inner route with OAuth2 Bearer Token authentication support.
   * The given authenticator determines whether the credentials in the request are valid
   * and, if so, which user object to supply to the inner route.
   *
   * @group security
   */
  def authenticateOAuth2[T](realm: String, authenticator: Authenticator[T]): AuthenticationDirective[T] =
    authenticateOAuth2Async(realm, cred => IO.pure(authenticator(cred)))

  /**
   * A directive that wraps the inner route with OAuth2 Bearer Token authentication support.
   * The given authenticator determines whether the credentials in the request are valid
   * and, if so, which user object to supply to the inner route.
   *
   * @group security
   */
  def authenticateOAuth2Async[T](realm: String, authenticator: AsyncAuthenticator[T]): AuthenticationDirective[T] = {
      def extractAccessTokenParameterAsBearerToken = {
        import ParameterDirectives._
        parameter("access_token".optional).map(_.map(t => Credentials.Token(CIString("Bearer"), t)))
      }
      val extractCreds: Directive1[Option[Credentials.Token]] =
        extractCredentials.flatMap {
          case Some(c: Credentials.Token) => provide(Some(c))
          case _                          => extractAccessTokenParameterAsBearerToken
        }

      extractCredentialsAndAuthenticateOrRejectWithChallenge[Credentials.Token, T](extractCreds, { cred =>
        authenticator(CredentialsHelper(cred)).map {
          case Some(t) => AuthenticationResult.success(t)
          case None    => AuthenticationResult.failWithChallenge(Challenge("Bearer", realm))
        }
      })
    }

  /**
   * A directive that wraps the inner route with OAuth2 Bearer Token authentication support.
   * The given authenticator determines whether the credentials in the request are valid
   * and, if so, which user object to supply to the inner route.
   *
   * @group security
   */
  def authenticateOAuth2PF[T](realm: String, authenticator: AuthenticatorPF[T]): AuthenticationDirective[T] =
    authenticateOAuth2(realm, authenticator.lift)

  /**
   * A directive that wraps the inner route with OAuth2 Bearer Token authentication support.
   * The given authenticator determines whether the credentials in the request are valid
   * and, if so, which user object to supply to the inner route.
   *
   * @group security
   */
  def authenticateOAuth2PFAsync[T](realm: String, authenticator: AsyncAuthenticatorPF[T]): AuthenticationDirective[T] =
      authenticateOAuth2Async(realm, credentials =>
        if (authenticator isDefinedAt credentials) authenticator(credentials).map(Some(_))
        else IO.pure(None))

  /**
   * Lifts an authenticator function into a directive. The authenticator function gets passed in credentials from the
   * [[Authorization]] header of the request. If the function returns `Right(user)` the user object is provided
   * to the inner route. If the function returns `Left(challenge)` the request is rejected with an
   * [[AuthenticationFailedRejection]] that contains this challenge to be added to the response.
   *
   * You can supply a directive to extract the credentials (to support alternative ways of providing credentials).
   *
   * @group security
   */
  private def extractCredentialsAndAuthenticateOrRejectWithChallenge[C <: Credentials, T](
                                                                                               extractCredentials: Directive1[Option[C]],
                                                                                               authenticator:      Option[C] => IO[AuthenticationResult[T]]): AuthenticationDirective[T] =
    extractCredentials.flatMap { cred =>
      onSuccess(authenticator(cred)).flatMap {
        case Right(user) => provide(user)
        case Left(challenge) =>
          val cause = if (cred.isEmpty) CredentialsMissing else CredentialsRejected
          reject(AuthenticationFailedRejection(cause, challenge)): Directive1[T]
      }
    }

  /**
   * Lifts an authenticator function into a directive. The authenticator function gets passed in credentials from the
   * [[Authorization]] header of the request. If the function returns `Right(user)` the user object is provided
   * to the inner route. If the function returns `Left(challenge)` the request is rejected with an
   * [[AuthenticationFailedRejection]] that contains this challenge to be added to the response.
   *
   * @group security
   */
  def authenticateOrRejectWithChallenge[T](authenticator: Option[Credentials] => IO[AuthenticationResult[T]]): AuthenticationDirective[T] =
    extractCredentialsAndAuthenticateOrRejectWithChallenge(extractCredentials, authenticator)

  /**
   * Lifts an authenticator function into a directive. Same as `authenticateOrRejectWithChallenge`
   * but only applies the authenticator function with a certain type of credentials.
   *
   * @group security
   */
  def authenticateOrRejectWithChallenge[C <: Credentials: ClassTag, T](
                                                                            authenticator: Option[C] => IO[AuthenticationResult[T]]): AuthenticationDirective[T] =
    extractCredentialsAndAuthenticateOrRejectWithChallenge(extractCredentials.map(_ collect { case c: C => c }), authenticator)

  /**
   * Applies the given authorization check to the request.
   * If the check fails the route is rejected with an [[AuthorizationFailedRejection]].
   *
   * @group security
   */
  def authorize(check: => Boolean): Directive0 = authorize(_ => check)

  /**
   * Applies the given authorization check to the request.
   * If the check fails the route is rejected with an [[AuthorizationFailedRejection]].
   *
   * @group security
   */
  def authorize(check: RequestContext => Boolean): Directive0 =
    authorizeAsync(ctx => IO.pure(check(ctx)))

  /**
   * Asynchronous version of [[authorize]].
   * If the [[IO]] fails or is completed with `false`
   * authorization fails and the route is rejected with an [[AuthorizationFailedRejection]].
   *
   * @group security
   */
  def authorizeAsync(check: => IO[Boolean]): Directive0 =
    authorizeAsync(ctx => check)

  /**
   * Asynchronous version of [[authorize]].
   * If the [[IO]] fails or is completed with `false`
   * authorization fails and the route is rejected with an [[AuthorizationFailedRejection]].
   *
   * @group security
   */
  def authorizeAsync(check: RequestContext => IO[Boolean]): Directive0 =
    extract(check).flatMap[Unit] { fa =>
      onComplete(fa).flatMap {
        case Success(true) => pass
        case _             => reject(AuthorizationFailedRejection)
      }
    }
}

object SecurityDirectives extends SecurityDirectives

/**
 * Represents authentication credentials supplied with a request. Credentials can either be
 * [[CredentialsHelper.Missing]] or can be [[CredentialsHelper.Provided]] in which case an identifier is
 * supplied and a function to check the known secret against the provided one in a secure fashion.
 */
sealed trait CredentialsHelper
object CredentialsHelper {
  case object Missing extends CredentialsHelper
  abstract case class Provided(identifier: String) extends CredentialsHelper {

    /**
     * First applies the passed in `hasher` function to the received secret part of the Credentials
     * and then safely compares the passed in `secret` with the hashed received secret.
     * This method can be used if the secret is not stored in plain text.
     * Use of this method instead of manual String equality testing is recommended in order to guard against timing attacks.
     *
     * See also [[EnhancedString#secure_==]], for more information.
     */
    def verify(secret: String, hasher: String => String): Boolean

    /**
     * Safely compares the passed in `secret` with the received secret part of the Credentials.
     * Use of this method instead of manual String equality testing is recommended in order to guard against timing attacks.
     *
     * See also [[EnhancedString#secure_==]], for more information.
     */
    def verify(secret: String): Boolean = verify(secret, x => x)

    /**
     * Compares with custom 'verifier' the received secret part of the Credentials.
     * Use of this method only if custom String equality testing is required, not recommended.
     */
    def provideVerify(verifier: String => Boolean): Boolean

    /**
     * Compares with custom 'verifier' and the passed secret with the received secret part of the Credentials.
     * Use of this method only if custom String equality testing is required, not recommended.
     */
    def provideVerify(secret: String, verifier: (String, String) => Boolean): Boolean = provideVerify(verifier.curried(secret))
  }

  def apply(cred: Option[Credentials]): CredentialsHelper = {
    cred match {
      case Some(BasicCredentials(username, receivedSecret)) =>
        new CredentialsHelper.Provided(username) {
          def verify(secret: String, hasher: String => String): Boolean = secret secure_== hasher(receivedSecret)
          def provideVerify(verifier: String => Boolean): Boolean = verifier(receivedSecret)
        }
      case Some(Credentials.Token(authScheme, token)) if authScheme == CIString("Bearer") =>
        new CredentialsHelper.Provided(token) {
          def verify(secret: String, hasher: String => String): Boolean = secret secure_== hasher(token)
          def provideVerify(verifier: String => Boolean): Boolean = verifier(token)
        }
      case Some(c) =>
        throw new UnsupportedOperationException(s"Credentials does not support scheme '${c.authScheme}'.")
      case None => CredentialsHelper.Missing
    }
  }
}

import SecurityDirectives._

object AuthenticationResult {
  def success[T](user: T): AuthenticationResult[T] = Right(user)
  def failWithChallenge(challenge: Challenge): AuthenticationResult[Nothing] = Left(challenge)
}

trait AuthenticationDirective[T] extends Directive1[T] {
  import BasicDirectives._
  import RouteDirectives._

  /**
   * Returns a copy of this [[AuthenticationDirective]] that will provide `Some(user)` if credentials
   * were supplied and otherwise `None`.
   */
  def optional: Directive1[Option[T]] =
    this.map(Some(_): Option[T]) recover {
      case AuthenticationFailedRejection(CredentialsMissing, _) +: _ => provide(None)
      case rejs => reject(rejs: _*)
    }

  /**
   * Returns a copy of this [[AuthenticationDirective]] that uses the given object as the
   * anonymous user which will be used if no credentials were supplied in the request.
   */
  def withAnonymousUser(anonymous: T): Directive1[T] = optional map (_ getOrElse anonymous)
}
object AuthenticationDirective {
  implicit def apply[T](other: Directive1[T]): AuthenticationDirective[T] =
    new AuthenticationDirective[T] { def tapply(inner: Tuple1[T] => Route) = other.tapply(inner) }
}
