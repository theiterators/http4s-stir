package pl.iterators.stir.server.directives

import cats.effect.IO
import fs2.{ Chunk, Stream }
import org.http4s.{ Charset, MediaType, Message, Request }

/**
 * The bytes a logging directive captured for one message, at most `maxBodyBytes` of them. The directives pass the
 * message with these bytes as its body, so reading `message` never touches the stream being logged.
 *
 * @param truncated
 *   whether the message body is longer than `bytes`: `Content-Length > maxBodyBytes` when the length is known,
 *   `bytes.size == maxBodyBytes` otherwise
 */
final case class LoggedBody(message: Message[IO], bytes: Chunk[Byte], truncated: Boolean) {

  /** The message charset, UTF-8 when absent. */
  def charset: Charset = message.charset.getOrElse(Charset.`UTF-8`)

  /** `bytes` decoded with `charset`. */
  lazy val text: String = new String(bytes.toArray, charset.nioCharset)

  /** The message when it is a request, for route-conditional rules; `None` for a response. */
  def request: Option[Request[IO]] = message match {
    case r: Request[IO @unchecked] => Some(r)
    case _                         => None
  }

  private def mediaType: Option[MediaType] = message.contentType.map(_.mediaType)

  /**
   * `application/json` or any `*&#47;*+json`. Like `isForm` and `isPlainText` it compares the type and subtype only:
   * http4s keeps every parameter but `charset` on the media type and its `equals` compares them, so `==` would
   * reject `application/json; version=2`.
   */
  def isJson: Boolean =
    mediaType.exists(mt => (mt.mainType == "application" && mt.subType == "json") || mt.subType.endsWith("+json"))

  def isForm: Boolean = mediaType.exists(mt => mt.mainType == "application" && mt.subType == "x-www-form-urlencoded")

  def isPlainText: Boolean = mediaType.exists(mt => mt.mainType == "text" && mt.subType == "plain")

  /** The http4s rule: a binary media type that is not JSON. */
  def isBinary: Boolean = mediaType.exists(_.binary) && !isJson
}

/**
 * Decides what a logging directive prints for a body. Built-ins are in the companion; `eval` is the general
 * constructor and `orElse` chains redactors through `Skip`. See "Redacting sensitive data" in
 * `docs/directives/misc.md`.
 */
sealed abstract class BodyRedactor {

  /**
   * Never throws: an exception from user code, thrown or raised, is a failed `IO`, which the directives log as
   * `<redaction failed: ExceptionClass>` with the fully qualified class name.
   */
  def redact(body: LoggedBody): IO[BodyRedactor.Outcome]

  /** `that` is consulted when this redactor yields `Skip`. */
  final def orElse(that: BodyRedactor): BodyRedactor = BodyRedactor.unguarded { body =>
    redact(body).flatMap {
      case BodyRedactor.Skip => that.redact(body)
      case outcome           => IO.pure(outcome)
    }
  }
}

object BodyRedactor {

  sealed trait Outcome

  /** Logged as `body="<value>"`; `note` is appended in parentheses; `incomplete` says the value came from a truncated or repaired body. */
  final case class Text(value: String, note: Option[String] = None, incomplete: Boolean = false) extends Outcome

  /** Logged as `body=<hidden>` with the size; `note` is appended in parentheses. */
  final case class Hidden(note: Option[String] = None) extends Outcome

  /** "Not applicable": lets `orElse` try the next redactor. A chain ending in `Skip` is logged as hidden. */
  case object Skip extends Outcome

  /** The word that replaces a masked value. Unreserved in URLs, valid inside JSON strings. */
  val Mask: String = "REDACTED"

  private final class Impl(run: LoggedBody => IO[Outcome]) extends BodyRedactor {
    def redact(body: LoggedBody): IO[Outcome] = IO.defer(run(body))
  }

  private[directives] def unguarded(run: LoggedBody => IO[Outcome]): BodyRedactor = new Impl(run)

  private def guarded(prefixSafe: Boolean)(run: LoggedBody => IO[Outcome]): BodyRedactor = unguarded { body =>
    if (body.truncated && !prefixSafe) IO.pure(Hidden()) else run(body)
  }

  /** Every body is logged as `<hidden>` with its size. */
  val hidden: BodyRedactor = unguarded(_ => IO.pure(Hidden()))

  /** The 0.5.0 rendering: decoded text, or hex for a binary body; `incomplete` follows `truncated`. */
  val passThrough: BodyRedactor = unguarded { body =>
    IO(Text(if (body.isBinary) body.bytes.toByteVector.toHex else body.text, None, body.truncated))
  }

  /** JSON bodies: the value under any key satisfying `isSensitive` becomes `"REDACTED"`, at any depth. */
  def jsonKeys(isSensitive: String => Boolean): BodyRedactor = when(_.isJson)(unguarded { body =>
    IO {
      val r = JsonRedaction.scan(body.text, body.truncated, JsonRedaction.DenyList(isSensitive))
      Text(r.text, r.note, r.incomplete)
    }
  })

  /**
   * JSON bodies: a key satisfying `isSensitive` is masked whole; otherwise a scalar is kept only under a key in
   * `keys`; objects and arrays are traversed.
   */
  def jsonKeepOnly(keys: Set[String], isSensitive: String => Boolean = SensitiveNames.default): BodyRedactor =
    when(_.isJson)(unguarded { body =>
      IO {
        val r = JsonRedaction.scan(body.text, body.truncated, JsonRedaction.KeepOnly(keys, isSensitive))
        Text(r.text, r.note, r.incomplete)
      }
    })

  /** Form bodies: the value of every field whose name satisfies `isSensitive` becomes `REDACTED`. */
  def formFields(isSensitive: String => Boolean): BodyRedactor = when(_.isForm)(unguarded { body =>
    IO(Text(FormRedaction.maskFields(body.text, body.truncated, isSensitive), None, body.truncated))
  })

  /**
   * JSON bodies: `f` receives the compacted, closed JSON text. Unless `prefixSafe`, a truncated or repaired body is
   * hidden and `f` is not called.
   */
  def json(f: String => String, prefixSafe: Boolean = false): BodyRedactor = when(_.isJson)(unguarded { body =>
    IO {
      val r = JsonRedaction.scan(body.text, body.truncated, JsonRedaction.Keep)
      if (r.incomplete && !prefixSafe) Hidden(r.note)
      else if (r.text.isEmpty) Text("", r.note, r.incomplete)
      else Text(f(r.text), r.note, r.incomplete)
    }
  })

  /** `r` for bodies satisfying `p`, `Skip` for the others. */
  def when(p: LoggedBody => Boolean)(r: BodyRedactor): BodyRedactor = unguarded { body =>
    if (p(body)) r.redact(body) else IO.pure(Skip)
  }

  /** A pure text redactor; hidden on truncation unless `prefixSafe`. */
  def apply(f: LoggedBody => String, prefixSafe: Boolean = false): BodyRedactor =
    guarded(prefixSafe)(body => IO(Text(f(body))))

  /** The general constructor; hidden on truncation unless `prefixSafe`. */
  def eval(f: LoggedBody => IO[Outcome], prefixSafe: Boolean = false): BodyRedactor = guarded(prefixSafe)(f)

  /** Adapts an http4s `logBody`-style function; a truncated body is hidden since such a function expects it whole. */
  def fromLogBodyText(f: Stream[IO, Byte] => Option[IO[String]]): BodyRedactor = unguarded { body =>
    if (body.truncated) IO.pure(Hidden())
    else f(Stream.chunk(body.bytes)) match {
      case None     => IO.pure(Hidden())
      case Some(io) => io.map(Text(_))
    }
  }
}
