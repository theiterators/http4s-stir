package pl.iterators.stir.server.directives

import cats.effect.IO
import fs2.Chunk
import org.http4s.{ Header, Message, Request, Response, Uri }
import org.typelevel.ci._
import pl.iterators.stir.server.{
  InvalidRequiredValueForQueryParamRejection,
  MalformedFormFieldRejection,
  MalformedHeaderRejection,
  MalformedQueryParamRejection,
  Rejection
}
import scala.util.control.NonFatal

/**
 * Renders the log lines of the debugging directives in the http4s format (design spec, sections 6 and 9), so that
 * placeholders can be put anywhere and user code is guarded.
 */
private[directives] object LogRendering {

  final case class Config(logHeaders: Boolean, redactHeadersWhen: CIString => Boolean, redaction: LogRedaction)

  private val Redacted = "<REDACTED>"

  def failed(e: Throwable): String = s"<redaction failed: ${e.getClass.getName}>"

  def requestLine(request: Request[IO], config: Config): String = {
    val uri =
      try config.redaction.uri(request.uri).renderString
      catch { case NonFatal(e) => failed(e) }
    s"${request.httpVersion} ${request.method} $uri" + headerSection(request, config)
  }

  def responseLine(response: Response[IO], config: Config): String =
    s"${response.httpVersion} ${response.status}" + headerSection(response, config)

  private def headerSection(message: Message[IO], config: Config): String =
    if (!config.logHeaders) ""
    else
      " " +
      (try {
        val headers =
          if (config.redaction.transformUriHeaders) message.headers.transform(_.map(transformUriHeader(_, config)))
          else message.headers
        headers.redactSensitive(name => config.redactHeadersWhen(name) || config.redaction.headers(name)).toString
      } catch { case NonFatal(e) => s"Headers(${failed(e)})" })

  private def transformUriHeader(header: Header.Raw, config: Config): Header.Raw =
    if (header.name == ci"Referer" || header.name == ci"Location")
      Header.Raw(header.name,
        Uri.fromString(header.value) match {
          case Right(uri) =>
            try config.redaction.uri(uri).renderString
            catch { case NonFatal(e) => failed(e) }
          case Left(_) => Redacted
        })
    else header

  /** `truncated` as `LoggedBody` defines it. */
  def isTruncated(contentLength: Option[Long], bytes: Chunk[Byte], maxBodyBytes: Int): Boolean =
    contentLength match {
      case Some(length) => length > maxBodyBytes
      case None         => bytes.size == maxBodyBytes
    }

  /** Runs the policy's body redactor on the captured bytes, guarding user code, and renders the body part. */
  def renderBody(message: Message[IO], bytes: Chunk[Byte], maxBodyBytes: Int, config: Config): IO[String] = {
    val body = LoggedBody(message, bytes, isTruncated(message.contentLength, bytes, maxBodyBytes))
    IO.defer(config.redaction.body.redact(body)).attempt.map(bodyPart(_, message.contentLength, maxBodyBytes))
  }

  def bodyPart(outcome: Either[Throwable, BodyRedactor.Outcome], contentLength: Option[Long],
      maxBodyBytes: Int): String = {
    val total = contentLength.fold("???")(_.toString)
    val truncation = contentLength match {
      case Some(length) if length > maxBodyBytes => s" ... ($length bytes total)"
      case None                                  => " ... (??? bytes total)"
      case _                                     => ""
    }
    def noteText(note: Option[String]) = note.fold("")(n => s" ($n)")
    outcome match {
      case Right(BodyRedactor.Text(value, note, _)) => "body=\"" + escapeControl(value) + "\"" + noteText(note) +
        truncation
      case Right(BodyRedactor.Hidden(note)) => "body=<hidden>" + noteText(note) + s" ($total bytes total)"
      case Right(BodyRedactor.Skip)         => s"body=<hidden> ($total bytes total)"
      case Left(e)                          => s"body=${failed(e)} ($total bytes total)"
    }
  }

  def notConsumed(contentLength: Option[Long]): String =
    s"body=<not consumed> (${contentLength.fold("???")(_.toString)} bytes total)"

  def rejectionLine(rejections: Seq[Rejection], config: Config): String =
    "Request was rejected with rejections: " +
    rejections.map(r =>
      try renderRejection(r, config)
      catch { case NonFatal(e) => failed(e) }).mkString(", ")

  private def renderRejection(rejection: Rejection, config: Config): String = {
    def sensitive(name: String, isHeader: Boolean): Boolean = config.redaction.rejectionValues match {
      case LogRedaction.RejectionValues.Shown  => false
      case LogRedaction.RejectionValues.Hidden => true
      case LogRedaction.RejectionValues.ByName =>
        try
          if (isHeader) {
            val ci = CIString(name)
            ci == ci"Referer" || ci == ci"Location" || config.redactHeadersWhen(ci) || config.redaction.headers(ci)
          } else config.redaction.names(name)
        catch { case NonFatal(_) => true }
    }
    rejection match {
      case MalformedQueryParamRejection(name, _, _) if sensitive(name, isHeader = false) =>
        s"MalformedQueryParamRejection($name,$Redacted)"
      case MalformedFormFieldRejection(name, _, _) if sensitive(name, isHeader = false) =>
        s"MalformedFormFieldRejection($name,$Redacted)"
      case InvalidRequiredValueForQueryParamRejection(name, _, _) if sensitive(name, isHeader = false) =>
        s"InvalidRequiredValueForQueryParamRejection($name,$Redacted,$Redacted)"
      case MalformedHeaderRejection(name, _, _) if sensitive(name, isHeader = true) =>
        s"MalformedHeaderRejection($name,$Redacted)"
      case other => other.toString
    }
  }

  /** Control characters (U+0000–U+001F, U+007F) rendered as `\n`, `\r`, `\t` or `\u00XX`: one entry is one line. */
  def escapeControl(s: String): String = {
    def isControl(c: Char) = c < 0x20 || c == 0x7F
    if (!s.exists(isControl)) s
    else {
      val sb = new StringBuilder(s.length + 8)
      var i = 0
      while (i < s.length) {
        val c = s.charAt(i)
        c match {
          case '\n'              => sb.append("\\n")
          case '\r'              => sb.append("\\r")
          case '\t'              => sb.append("\\t")
          case c if isControl(c) =>
            sb.append("\\u00")
            sb.append(Character.forDigit((c >> 4) & 0xF, 16))
            sb.append(Character.forDigit(c & 0xF, 16))
          case c => sb.append(c)
        }
        i += 1
      }
      sb.toString
    }
  }
}
