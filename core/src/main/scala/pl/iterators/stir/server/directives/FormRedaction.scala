package pl.iterators.stir.server.directives

import java.nio.charset.{ Charset => JCharset, StandardCharsets }
import org.http4s.Uri

/**
 * The `application/x-www-form-urlencoded` splitter behind `BodyRedactor.formFields` and the form branch of the
 * value rules. Parts are separated by `&` or `;`, the two separators http4s accepts, and split on their first `=`;
 * only values are replaced, except that a part without `=` at the end of a truncated body is dropped.
 */
private[directives] object FormRedaction {

  /** Masks the value of every part whose decoded name satisfies `isSensitive`; everything else is verbatim. */
  def maskFields(text: String, truncated: Boolean, isSensitive: String => Boolean): String =
    rewrite(text, truncated) { (name, value) =>
      if (isSensitive(Uri.decode(name, StandardCharsets.UTF_8, plusIsSpace = true))) BodyRedactor.Mask else value
    }

  /** Applies `f` to every decoded value; a changed result is re-encoded the way `UrlForm` encodes values. */
  def transformValues(text: String, charset: JCharset, f: String => String): String =
    rewrite(text, truncated = false) { (_, value) =>
      val decoded = Uri.decode(value, charset, plusIsSpace = true)
      val result = f(decoded)
      if (result == decoded) value else Uri.encode(result, charset, spaceIsPlus = true, toSkip = unreserved)
    }

  private def unreserved(c: Char): Boolean =
    (c >= 'a' && c <= 'z') ||
    (c >= 'A' && c <= 'Z') ||
    (c >= '0' && c <= '9') ||
    c == '-' || c == '.' || c == '_' || c == '~'

  private def isSeparator(c: Char): Boolean = c == '&' || c == ';'

  /**
   * Re-emits `text` part by part; `value(name, rawValue)` chooses the text that replaces a part's raw value.
   * A part without `=` is re-emitted verbatim, except the last part of a truncated body, which is dropped with
   * its separator.
   */
  private def rewrite(text: String, truncated: Boolean)(value: (String, String) => String): String = {
    val out = new StringBuilder(text.length)
    var start = 0
    while (start <= text.length) {
      var end = start
      while (end < text.length && !isSeparator(text.charAt(end))) end += 1
      val part = text.substring(start, end)
      val isLast = end == text.length
      val eq = part.indexOf('=')
      if (eq < 0) {
        if (isLast && truncated) {
          // drop the dangling part and the separator before it
          if (start > 0) out.setLength(out.length - 1)
        } else out.append(part)
      } else {
        out.append(part.substring(0, eq + 1))
        out.append(value(part.substring(0, eq), part.substring(eq + 1)))
      }
      if (!isLast) out.append(text.charAt(end))
      start = end + 1
    }
    out.toString
  }
}
