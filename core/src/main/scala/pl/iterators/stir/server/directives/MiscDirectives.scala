package pl.iterators.stir.server.directives

import cats.effect.{ IO, SyncIO }
import cats.effect.std.Console
import com.comcast.ip4s.IpAddress
import fs2.{ Pull, Stream }
import org.http4s.{ EmptyBody, EntityBody, LanguageTag, Request, Response }
import org.http4s.headers._
import org.typelevel.vault.Key
import pl.iterators.stir.server._

import java.util.concurrent.atomic.AtomicLong

trait MiscDirectives {
  import BasicDirectives._
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

  /**
   * Extracts the client's IP from the TCP connection the request was received from, ignoring any headers.
   * `None` when the server did not record the connection (for example in the in-memory testkit).
   *
   * @group misc
   */
  def extractDirectClientIP: Directive1[Option[IpAddress]] = MiscDirectives._extractDirectClientIP

  /**
   * Rejects if the request entity is non-empty. A request is empty when it declares `Content-Length: 0` or
   * declares neither `Content-Length` nor `Transfer-Encoding`.
   *
   * @group misc
   */
  def requestEntityEmpty: Directive0 = MiscDirectives._requestEntityEmpty

  /**
   * Rejects with a [[RequestEntityExpectedRejection]] if the request entity is empty.
   * Non-empty requests are passed on unchanged to the inner route.
   *
   * @group misc
   */
  def requestEntityPresent: Directive0 = MiscDirectives._requestEntityPresent

  /**
   * Converts responses with an empty entity into (empty) rejections.
   * This way you can, for example, have the marshalling of an empty result be treated as if the request could not
   * be matched.
   *
   * @group misc
   */
  def rejectEmptyResponse: Directive0 = MiscDirectives._rejectEmptyResponse

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
    optionalHeaderValueByType[`Accept-Language`].flatMap {
      case Some(acceptLanguage) =>
        val availableLanguages = first +: more
        val preferredLanguage =
          availableLanguages.map(l => (l, acceptLanguage.qValue(l))).filter(_._2.isAcceptable).maxByOption(_._2)
        provide(preferredLanguage.map(_._1).getOrElse(first))
      case None => provide(first)
    }
  }

  /**
   * Fails the request entity stream with an [[EntityStreamSizeException]] (completed as `413 Payload Too Large`
   * by the default handlers) if its size exceeds the given limit. A request whose `Content-Length` already exceeds
   * the limit fails as soon as the entity is consumed. Nested `withSizeLimit` directives apply the innermost limit
   * to the original entity.
   *
   * Beware that the request entity size check is executed when the entity is consumed.
   *
   * @group misc
   */
  def withSizeLimit(maxBytes: Long): Directive0 = mapRequest { request =>
    val original = MiscDirectives.originalBody(request)
    val limited = request.contentLength match {
      case Some(length) if length > maxBytes =>
        Stream.raiseError[IO](EntityStreamSizeException(maxBytes, Some(length)))
      case _ => MiscDirectives.limitBytes(original, maxBytes)
    }
    request.withAttributes(request.attributes.insert(MiscDirectives.LimitedBodyKey,
      MiscDirectives.LimitedBody(original, limited))).withBodyStream(limited)
  }

  /**
   * Disables the size limit set by an enclosing `withSizeLimit` directive on the incoming request entity.
   * Can be useful when handling arbitrarily large data uploads in specific parts of your routes. A body that was
   * replaced in between (for example buffered by `toStrictEntity` or decoded by `decodeRequest`) is left alone.
   *
   * @note  Usage of `withoutSizeLimit` is not recommended as it turns off the too large payload protection.
   *        Prefer `withSizeLimit` with a value high enough for the route in need of big entities.
   *
   * @group misc
   */
  def withoutSizeLimit: Directive0 = MiscDirectives._withoutSizeLimit
}

object MiscDirectives extends MiscDirectives {
  import BasicDirectives._
  import HeaderDirectives._
  import RouteDirectives._

  /** The body `withSizeLimit` found and the limited stream it installed, so a later directive can tell them apart. */
  private[stir] final case class LimitedBody(original: EntityBody[IO], limited: EntityBody[IO])

  private[stir] val LimitedBodyKey: Key[LimitedBody] = Key.newKey[SyncIO, LimitedBody].unsafeRunSync()

  /** The unlimited body: the stored original while the request still carries the limited stream, else the body. */
  private[stir] def originalBody(request: Request[IO]): EntityBody[IO] =
    request.attributes.lookup(LimitedBodyKey).filter(_.limited eq request.body).map(_.original).getOrElse(request.body)

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

  private val _extractDirectClientIP: Directive1[Option[IpAddress]] = extract(_.request.remote.map(_.host))

  /** How often the HTTP/2 length-less body heuristic was applied; the first time prints a warning. */
  private[stir] val lengthlessHttp2Warnings = new AtomicLong(0L)

  /** Whether the request is known to be empty, warning once per process about length-less HTTP/2 bodies. */
  private val knownEmpty: Directive1[Boolean] =
    Directive[Tuple1[Boolean]] { inner => ctx =>
      val request = ctx.request
      val lengthless = request.httpVersion.major >= 2 && request.contentLength.isEmpty && !request.isChunked
      val warn =
        if (!lengthless) IO.unit
        else IO(lengthlessHttp2Warnings.incrementAndGet()).flatMap { count =>
          if (count == 1)
            Console[IO].errorln(
              "requestEntityEmpty / requestEntityPresent met an HTTP/2 request without Content-Length and treats " +
              "it as empty; HTTP/2 bodies carry no length, so prefer Content-Length: 0 or a body check there " +
              "(this warning is printed once).")
          else IO.unit
        }
      warn *> inner(Tuple1(isKnownEmpty(request)))(ctx)
    }

  private val _requestEntityEmpty: Directive0 =
    knownEmpty.flatMap(if (_) pass else reject)

  private val _requestEntityPresent: Directive0 =
    knownEmpty.flatMap(if (_) reject(RequestEntityExpectedRejection) else pass)

  private val _rejectEmptyResponse: Directive0 =
    mapRouteResultPF {
      case RouteResult.Complete(response) if isKnownEmpty(response) => RouteResult.Rejected(Nil)
    }

  private val _withoutSizeLimit: Directive0 =
    mapRequest { request =>
      request.attributes.lookup(LimitedBodyKey).filter(_.limited eq request.body)
        .fold(request)(stored => request.withBodyStream(stored.original))
    }

  /** A request is known to be empty when it declares `Content-Length: 0` or has neither a length nor chunking. */
  private[stir] def isKnownEmpty(request: Request[IO]): Boolean =
    request.contentLength.contains(0L) || (request.contentLength.isEmpty && !request.isChunked)

  /** A response is known to be empty when it declares `Content-Length: 0` or still carries the empty body. */
  private[stir] def isKnownEmpty(response: Response[IO]): Boolean =
    response.contentLength.contains(0L) || (response.body eq EmptyBody)

  private[stir] def limitBytes(body: EntityBody[IO], maxBytes: Long): EntityBody[IO] = {
    def go(stream: Stream[IO, Byte], seen: Long): Pull[IO, Byte, Unit] =
      stream.pull.uncons.flatMap {
        case None                => Pull.done
        case Some((chunk, rest)) =>
          val total = seen + chunk.size
          if (total > maxBytes) Pull.raiseError[IO](EntityStreamSizeException(maxBytes, None))
          else Pull.output(chunk) >> go(rest, total)
      }
    go(body, 0L).stream
  }
}
