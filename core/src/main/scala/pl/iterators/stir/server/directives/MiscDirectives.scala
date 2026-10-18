package pl.iterators.stir.server.directives

import cats.syntax.all._
import cats.effect.{ Deferred, IO, Outcome, Ref, SyncIO }
import com.comcast.ip4s.IpAddress
import fs2.{ Pull, Stream }
import org.http4s.{ EmptyBody, EntityBody, LanguageTag, Request, Response }
import org.http4s.headers._
import org.typelevel.ci.CIString
import org.typelevel.vault.Key
import pl.iterators.stir.server._

import java.util.concurrent.CancellationException

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
   * Extracts the client's IP from the X-Forwarded-For or X-Real-Ip header, or the TCP connection the request was
   * received from (in that order of priority). A `Remote-Address` header is ignored, as in Pekko: clients can send one,
   * and only the server knows the connection's address. The forwarding headers are only trustworthy behind a proxy
   * that sets or strips them.
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
   * Rejects if the request entity is non-empty. An HTTP/1 request is empty when it declares `Content-Length: 0` or
   * declares neither `Content-Length` nor `Transfer-Encoding`; an HTTP/2 request without `Content-Length` when its
   * body has no bytes, which is found out by reading the first chunk (and putting it back).
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
   * q-values are read as leniently as Pekko does (`q=.5`, more than three decimals, spaces around `=`); a header
   * that still cannot be parsed is ignored.
   *
   * @group misc
   */
  def selectPreferredLanguage(first: LanguageTag, more: LanguageTag*): Directive1[LanguageTag] = {
    extract(ctx => MiscDirectives.acceptLanguage(ctx.request)).flatMap {
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
        extractRequest.map { request =>
          xForwardedFor.flatMap(_.values.head)
            .orElse(xRealIp.flatMap(IpAddress.fromString))
            .orElse(request.remote.map(_.host))
        }
      }
    }

  private val _extractDirectClientIP: Directive1[Option[IpAddress]] = extract(_.request.remote.map(_.host))

  /**
   * Whether the request is known to be empty. Over HTTP/1 the headers tell. An HTTP/2 body carries no
   * Content-Length and whether its stream ended with the headers only shows in the body, so the [[SharedBody]] that
   * routing starts with reads its first chunk. That answer holds only while the request still carries that body
   * (possibly under `withSizeLimit`); a replaced length-less body, or one under `toHttpRoutes` or a hand-built
   * `RequestContext`, is taken as present.
   */
  private val knownEmpty: Directive1[Boolean] =
    Directive[Tuple1[Boolean]] { inner => ctx =>
      val request = ctx.request
      sharedBodyOf(request) match {
        case Some(shared)                     => shared.isEmpty.flatMap(empty => inner(Tuple1(empty))(ctx))
        case None if lengthlessHttp2(request) => inner(Tuple1(false))(ctx)
        case None                             => inner(Tuple1(isKnownEmpty(request)))(ctx)
      }
    }

  /** The request's [[SharedBody]] while the request still carries it, directly or limited by `withSizeLimit`. */
  private def sharedBodyOf(request: Request[IO]): Option[SharedBody] =
    request.attributes.lookup(SharedBodyKey).filter { shared =>
      (request.body eq shared.body) ||
      request.attributes.lookup(LimitedBodyKey).exists(l => (l.limited eq request.body) && (l.original eq shared.body))
    }

  private def lengthlessHttp2(request: Request[IO]): Boolean =
    request.httpVersion.major >= 2 && request.contentLength.isEmpty

  /**
   * The body of a length-less HTTP/2 request, read through a cell so that its first chunk can be looked at before
   * the route reads it: the network stream can be read only once, so the body can be read once, whole, after the
   * look (a second read fails, see [[body]]). The look runs in a scope of its own, released once the rest of the body
   * has been read, or when the route rejects or fails or its response body ends ([[RequestContext.run]]); a read in
   * the background after that may fail for a body that holds resources.
   */
  private[stir] final class SharedBody private (original: EntityBody[IO],
      cell: Ref[IO, Option[Deferred[IO, Either[Throwable, EntityBody[IO]]]]], releaseCell: Ref[IO, Option[IO[Unit]]],
      guard: RereadGuard) {

    /** The original body with its first chunk already read, or `EmptyBody` when it has no bytes. */
    private val peeked: IO[EntityBody[IO]] =
      Deferred[IO, Either[Throwable, EntityBody[IO]]].flatMap { result =>
        IO.uncancelable { poll =>
          cell.modify {
            case existing @ Some(done) => existing -> poll(done.get).rethrow
            case None                  =>
              Some(result) -> poll(peek).guaranteeCase {
                case Outcome.Succeeded(body) => body.flatMap(b => result.complete(Right(b))).void
                case Outcome.Errored(error)  => result.complete(Left(error)).void
                case Outcome.Canceled()      =>
                  cell.set(None) *> result.complete(Left(new CancellationException("The body was not read"))).void
              }
          }.flatten
        }
      }

    /** Reads up to the first non-empty chunk in a scope that [[release]] closes. */
    private def peek: IO[EntityBody[IO]] = {
      def go(stream: EntityBody[IO]): Pull[IO, EntityBody[IO], Unit] =
        stream.pull.uncons.flatMap {
          case None                                 => Pull.output1(EmptyBody)
          case Some((chunk, rest)) if chunk.isEmpty => go(rest)
          case Some((chunk, rest))                  => Pull.output1(Stream.chunk(chunk) ++ rest)
        }
      IO.uncancelable { poll =>
        poll(go(original).stream.compile.resource.lastOrError.allocated).flatMap { case (peeked, close) =>
          if (peeked eq EmptyBody) close.as(EmptyBody)
          else releaseCell.set(Some(close)).as(peeked.onFinalize(release))
        }
      }
    }

    val isEmpty: IO[Boolean] = peeked.map(_ eq EmptyBody)

    /**
     * The body every reader gets: the first chunk and the rest of the stream, which a network body hands out once,
     * so it can be read once ([[guardRereads]]); a second read (an alternative after a rejection) fails at once
     * instead of getting the first chunk and whatever the drained stream still has.
     */
    val body: EntityBody[IO] =
      // an empty body loses nothing on a second read (every HTTP/2 GET comes here)
      Stream.eval(peeked).flatMap(peeked => if (peeked eq EmptyBody) Stream.empty else guard(peeked))

    /** Closes the scope of the first look, once; nothing to do when the body was not looked at or was empty. */
    val release: IO[Unit] = releaseCell.getAndSet(None).flatMap(_.getOrElse(IO.unit))
  }

  private[stir] object SharedBody {
    def apply(original: EntityBody[IO]): IO[SharedBody] =
      (Ref.of[IO, Option[Deferred[IO, Either[Throwable, EntityBody[IO]]]]](None), Ref.of[IO, Option[IO[Unit]]](None),
        RereadGuard(singleRead = true)).mapN(new SharedBody(original, _, _, _))
  }

  private[stir] val SharedBodyKey: Key[SharedBody] = Key.newKey[SyncIO, SharedBody].unsafeRunSync()

  /**
   * Prepares a request for routing: a length-less HTTP/2 request gets its body through a [[SharedBody]], any other
   * body through [[guardRereads]].
   */
  private[stir] def prepare(request: Request[IO]): IO[Request[IO]] =
    if (lengthlessHttp2(request))
      SharedBody(request.body).map(shared => request.withAttribute(SharedBodyKey, shared).withBodyStream(shared.body))
    else if (request.body eq EmptyBody) IO.pure(request)
    else
      guardRereads(request.body, singleRead = request.isChunked || request.contentLength.isEmpty)
        .map(request.withBodyStream)

  /**
   * The body, failing at once when it is read again where the server cannot hand it out again: a server hands a
   * network body out once, and read again Ember's HTTP/1 body re-emits what it buffered and then waits on the socket
   * for bytes that were already taken, so an alternative re-reading it would hang until the idle timeout instead of
   * being answered (Pekko fails the second materialization of a streamed entity the same way).
   *
   *  - A chunked or length-less body (`singleRead`) can be read once: any later read fails.
   *  - A body of known length fails a later read after a read that stopped partway (an error, a cancellation, a
   *    reader that stopped before asking for the end); one read to its end stays readable as before.
   */
  private[stir] def guardRereads(body: EntityBody[IO], singleRead: Boolean): IO[EntityBody[IO]] =
    RereadGuard(singleRead).map(_(body))

  /** The state of [[guardRereads]] for one request body, shared by every read of it. */
  private[stir] final class RereadGuard private (started: Ref[IO, Boolean], readPartly: Ref[IO, Boolean],
      singleRead: Boolean) {
    def apply(body: EntityBody[IO]): EntityBody[IO] =
      Stream.eval((started.getAndSet(true), readPartly.get, Ref.of[IO, Boolean](false)).tupled).flatMap {
        case (wasStarted, partly, ended) =>
          if (partly || (singleRead && wasStarted)) Stream.raiseError[IO](new IllegalStateException(RereadMessage))
          // a reader stopping early closes the stream normally, so whether the body reached its end is recorded
          // here; a reader that stops after the last byte without asking for more counts as stopping early
          else (body ++ Stream.exec(ended.set(true))).onFinalize(ended.get.flatMap(readPartly.set(true).unlessA(_)))
      }
  }

  private[stir] object RereadGuard {
    def apply(singleRead: Boolean): IO[RereadGuard] =
      (Ref.of[IO, Boolean](false), Ref.of[IO, Boolean](false)).mapN(new RereadGuard(_, _, singleRead))
  }

  private[stir] val RereadMessage: String =
    "The request entity was read before (by an earlier route) and cannot be read again"

  private val _requestEntityEmpty: Directive0 =
    knownEmpty.flatMap(if (_) pass else reject)

  private val _requestEntityPresent: Directive0 =
    knownEmpty.flatMap(if (_) reject(RequestEntityExpectedRejection) else pass)

  /**
   * The `Accept-Language` header with its q-values read as Pekko reads them: http4s rejects `.5` and spaces around
   * `;` and `=`, and rounds `0.4999` up to `0.5`, so each q-value is rewritten to three truncated decimals before
   * http4s parses the header. Each header line is parsed on its own and the lines it can parse are kept, as Pekko
   * keeps the `Accept-Language` headers it parsed.
   */
  private def acceptLanguage(request: Request[IO]): Option[`Accept-Language`] =
    request.headers.get(CIString("Accept-Language")).flatMap { raw =>
      raw.toList.flatMap(header => `Accept-Language`.parse(lenientQValues(header.value)).toOption)
        .reduceOption((a, b) => `Accept-Language`(a.values.concatNel(b.values)))
    }

  // written without regular expressions: Scala Native's engine has no lookaround, and a backtracking pattern over
  // a long run of whitespace takes quadratic time
  private def lenientQValues(value: String): String =
    value.split(",", -1).map { element =>
      val parameters = element.split(";", -1)
      if (parameters.length < 2) element
      else (trimEnd(parameters.head) +: parameters.tail.map(lenientQParameter)).mkString(";")
    }.mkString(",")

  private def lenientQParameter(parameter: String): String = {
    val eq = parameter.indexOf('=')
    if (eq < 0 || trimOws(parameter.substring(0, eq)) != "q") parameter
    else canonicalQValue(trimOws(parameter.substring(eq + 1))).fold(parameter)("q=" + _)
  }

  /**
   * Pekko's `CommonRules.qvalue`, `"0" ["." *DIGIT] / "." 1*DIGIT / "1" ["." *"0"]`, as the three decimals http4s
   * takes, truncated: that never inverts the order against another q-value (it may make two of them equal), and a
   * positive q-value stays positive, as in Pekko.
   */
  private def canonicalQValue(text: String): Option[String] = {
    val dot = text.indexOf('.')
    val (whole, fraction) = if (dot < 0) (text, "") else (text.substring(0, dot), text.substring(dot + 1))
    val valid = whole match {
      case "0" => fraction.forall(isDigit)
      case ""  => dot == 0 && fraction.nonEmpty && fraction.forall(isDigit)
      case "1" => fraction.forall(_ == '0')
      case _   => false
    }
    if (!valid) None
    else if (whole == "1") Some("1.000")
    else {
      val decimals = (fraction + "000").substring(0, 3)
      Some(if (decimals == "000" && fraction.exists(_ != '0')) "0.001" else "0." + decimals)
    }
  }

  private def isDigit(c: Char): Boolean = c >= '0' && c <= '9'

  /** Optional whitespace (RFC 7230 OWS) is spaces and tabs only. */
  private def isOws(c: Char): Boolean = c == ' ' || c == '\t'

  private def trimEnd(s: String): String = {
    var end = s.length
    while (end > 0 && isOws(s.charAt(end - 1))) end -= 1
    s.substring(0, end)
  }

  private def trimOws(s: String): String = {
    var start = 0
    while (start < s.length && isOws(s.charAt(start))) start += 1
    trimEnd(s.substring(start))
  }

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
