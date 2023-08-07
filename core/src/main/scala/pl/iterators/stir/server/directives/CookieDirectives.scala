package pl.iterators.stir.server.directives

import org.http4s.Header.ToRaw
import org.http4s.headers.{ `Set-Cookie`, Cookie }
import org.http4s.{ HttpDate, RequestCookie, ResponseCookie }
import pl.iterators.stir.server._
import pl.iterators.stir.impl.util._

/**
 * @groupname cookie Cookie directives
 * @groupprio cookie 30
 */
trait CookieDirectives {
  import HeaderDirectives._
  import BasicDirectives._
  import RespondWithDirectives._
  import RouteDirectives._

  /**
   * Extracts the [[RequestCookie]] with the given name. If the cookie is not present the
   * request is rejected with a respective [[MissingCookieRejection]].
   *
   * @group cookie
   */
  def cookie(name: String): Directive1[RequestCookie] =
    optionalHeaderValueByType[Cookie].flatMap {
      case Some(cookie) => findCookie(name)(cookie).map(provide).getOrElse(reject(MissingCookieRejection(name)))
      case None         => reject(MissingCookieRejection(name))
    }

  /**
   * Extracts the [[RequestCookie]] with the given name as an `Option[HttpCookiePair]`.
   * If the cookie is not present a value of `None` is extracted.
   *
   * @group cookie
   */
  def optionalCookie(name: String): Directive1[Option[RequestCookie]] =
    optionalHeaderValueByType[Cookie].map(_.flatMap(findCookie(name)))

  private def findCookie(name: String): Cookie => Option[RequestCookie] = _.values.find(_.name == name)

  /**
   * Adds a [[`Set-Cookie`]] response header with the given cookies.
   *
   * @group cookie
   */
  def setCookie(first: ResponseCookie, more: ResponseCookie*): Directive0 =
    respondWithHeaders((first :: more.toList).map(`Set-Cookie`(_)).flatMap(c => ToRaw.modelledHeadersToRaw(c).values))

  /**
   * Adds a [[Set-Cookie]] response header expiring the given cookies.
   *
   * @group cookie
   */
  def deleteCookie(first: ResponseCookie, more: ResponseCookie*): Directive0 =
    respondWithHeaders((first :: more.toList).map { c =>
      `Set-Cookie`(c.copy(content = "deleted", expires = Some(HttpDate.MinValue)))
    }.flatMap(c => ToRaw.modelledHeadersToRaw(c).values))

  /**
   * Adds a [[Set-Cookie]] response header expiring the cookie with the given properties.
   *
   * @group cookie
   */
  def deleteCookie(name: String, domain: String = "", path: String = ""): Directive0 =
    deleteCookie(ResponseCookie(name, "", domain = domain.toOption, path = path.toOption))
}

object CookieDirectives extends CookieDirectives
