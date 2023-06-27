package pl.iterators.stir.server.directives

import com.comcast.ip4s.IpAddress
import org.http4s.LanguageTag
import org.http4s.headers._
import pl.iterators.stir.server.{ directives, _ }

trait MiscDirectives {
  import RouteDirectives._

  /**
   * Checks the given condition before running its inner route.
   * If the condition fails the route is rejected with a [[ValidationRejection]].
   *
   * @group misc
   */
  def validate(check: => Boolean, errorMsg: String): Directive0 =
    Directive { inner => if (check) inner(()) else reject(ValidationRejection(errorMsg, None)) }

  /**
   * Extracts the client's IP from either the X-Forwarded-For, Remote-Address, X-Real-IP header
   * or the TCP connection the request was received from
   * (in that order of priority).
   *
   * @group misc
   */
  def extractClientIP: Directive1[Option[IpAddress]] = MiscDirectives._extractClientIP

//  /**
//   * Rejects if the request entity is non-empty.
//   *
//   * @group misc
//   */
//  def requestEntityEmpty: Directive0 = MiscDirectives._requestEntityEmpty
//
//  /**
//   * Rejects with a [[RequestEntityExpectedRejection]] if the request entity is empty.
//   * Non-empty requests are passed on unchanged to the inner route.
//   *
//   * @group misc
//   */
//  def requestEntityPresent: Directive0 = MiscDirectives._requestEntityPresent
//
//  /**
//   * Converts responses with an empty entity into (empty) rejections.
//   * This way you can, for example, have the marshalling of a ''None'' option
//   * be treated as if the request could not be matched.
//   *
//   * @group misc
//   */
//  def rejectEmptyResponse: Directive0 = MiscDirectives._rejectEmptyResponse

  /**
   * Inspects the request's `Accept-Language` header and determines,
   * which of the given language alternatives is preferred by the client.
   * (See http://tools.ietf.org/html/rfc7231#section-5.3.5 for more details on the
   * negotiation logic.)
   * If there are several best language alternatives that the client
   * has equal preference for (even if this preference is zero!)
   * the order of the arguments is used as a tie breaker (First one wins).
   *
   * @group misc
   */
  def selectPreferredLanguage(first: LanguageTag, more: LanguageTag*): Directive1[LanguageTag] = {
    import HeaderDirectives._
    import BasicDirectives._
    optionalHeaderValueByType[`Accept-Language`].flatMap {
      case Some(acceptLanguage) =>
        val availableLanguages = first +: more
        val preferredLanguage =
          availableLanguages.map(l => (l, acceptLanguage.qValue(l))).filter(_._2.isAcceptable).maxByOption(_._2)
        provide(preferredLanguage.map(_._1).getOrElse(first))
      case None => provide(first)
    }
  }

//  /**
//   * Fails the stream with [[akka.http.scaladsl.model.EntityStreamSizeException]] if its request entity size exceeds
//   * given limit. Limit given as parameter overrides limit configured with `akka.http.parsing.max-content-length`.
//   *
//   * Beware that request entity size check is executed when entity is consumed.
//   *
//   * @group misc
//   */
//  def withSizeLimit(maxBytes: Long): Directive0 =
//    mapRequestContext(_.mapRequest(_.mapEntity(_.withSizeLimit(maxBytes))))
//
//  /**
//   *
//   * Disables the size limit (configured by `akka.http.parsing.max-content-length` by default) checking on the incoming
//   * [[HttpRequest]] entity.
//   * Can be useful when handling arbitrarily large data uploads in specific parts of your routes.
//   *
//   * @note  Usage of `withoutSizeLimit` is not recommended as it turns off the too large payload protection. Therefore,
//   *        we highly encourage using `withSizeLimit` instead, providing it with a value high enough to successfully
//   *        handle the route in need of big entities.
//   *
//   * @group misc
//   */
//  def withoutSizeLimit: Directive0 = MiscDirectives._withoutSizeLimit
}

object MiscDirectives extends MiscDirectives {
  import BasicDirectives._
  import HeaderDirectives._
  import RouteDirectives._
  import RouteResult._

  private val _extractClientIP: Directive1[Option[IpAddress]] =
    optionalHeaderValueByType[`X-Forwarded-For`].flatMap { xForwardedFor =>
      optionalHeaderValueByName("X-Real-Ip").flatMap { xRealIp =>
        optionalHeaderValueByName("Remote-Address").flatMap { remoteAddress =>
          extractRequest.map { request =>
            xForwardedFor.flatMap(_.values.head)
              .orElse(xRealIp.flatMap(IpAddress.fromString))
              .orElse(remoteAddress.flatMap(IpAddress.fromString))
              .orElse(request.remote.map(_.host))
          }
        }
      }
    }

//  private val _requestEntityEmpty: Directive0 =
//    extract(_.request.entity.isKnownEmpty).flatMap(if (_) pass else reject)
//
//  private val _requestEntityPresent: Directive0 =
//    extract(_.request.entity.isKnownEmpty).flatMap(if (_) reject(RequestEntityExpectedRejection) else pass)
//
//  private val _rejectEmptyResponse: Directive0 =
//    mapRouteResult {
//      case Complete(response) if response.entity.isKnownEmpty => Rejected(Nil)
//      case x => x
//    }
//
//  private val _withoutSizeLimit: Directive0 =
//    mapRequestContext(_.mapRequest(_.mapEntity(_.withoutSizeLimit)))
}
