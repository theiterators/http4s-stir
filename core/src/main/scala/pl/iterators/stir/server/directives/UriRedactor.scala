package pl.iterators.stir.server.directives

import java.nio.charset.StandardCharsets
import org.http4s.{ Query, Uri }

/**
 * Building blocks for redacting a `Uri` before it is logged: the request-line URI and, while the URI-header stage
 * is on, the `Referer` and `Location` values. `LogRedaction` composes `query(names) andThen userInfo andThen
 * fragment(names)` itself; `redactPath` wraps `pathTemplate`.
 */
object UriRedactor {

  /**
   * Every pair `(k, Some(v))` with `isSensitive(k)` becomes `(k, Some("REDACTED"))`; keys, order and flags stay. A
   * query without a masked value is kept as received; one with a masked value is re-rendered by http4s, so `;`
   * becomes `&` and percent-encoding is normalised.
   */
  def query(isSensitive: String => Boolean): Uri => Uri = { uri =>
    val pairs = uri.query.pairs
    val masked = pairs.map {
      case (k, Some(_)) if isSensitive(k) => (k, Some(Mask))
      case pair                           => pair
    }
    if (masked == pairs) uri else uri.copy(query = Query.fromVector(masked))
  }

  /** `user:pass@host` becomes `REDACTED:REDACTED@host`; `user@host` becomes `REDACTED@host`. */
  val userInfo: Uri => Uri = { uri =>
    uri.copy(authority = uri.authority.map { authority =>
      authority.copy(userInfo = authority.userInfo.map(info => Uri.UserInfo(Mask, info.password.map(_ => Mask))))
    })
  }

  /**
   * A query-shaped fragment (one containing `=`, as the OAuth 2.0 implicit grant writes `access_token=…` into the
   * `Location` fragment) has the value of every pair whose decoded name satisfies `isSensitive` replaced by
   * `REDACTED`; other fragments and a URI without one are untouched. Pairs are separated by `&` alone, as
   * `URLSearchParams` reads a fragment, so a sensitive value is masked whole even when it contains `;`; inside
   * another value, a `;`-separated sensitive pair (the legacy separator) is masked as well. In a hash route
   * (`#/login?id_token=…`) the text up to the first `?` is a route prefix and names start after it. The fragment is
   * rewritten as text, never re-encoded, and `LogRendering` logs it as received.
   */
  def fragment(isSensitive: String => Boolean): Uri => Uri = { uri =>
    uri.fragment match {
      case Some(f) if f.contains('=') =>
        val q = f.indexOf('?')
        val prefixLength = if (q >= 0 && q < f.indexOf('=')) q + 1 else 0
        val masked = f.substring(0, prefixLength) + maskPairs(f.substring(prefixLength), isSensitive)
        if (masked == f) uri else uri.copy(fragment = Some(masked))
      case _ => uri
    }
  }

  private def maskPairs(text: String, isSensitive: String => Boolean): String = {
    def sensitive(name: String) = isSensitive(Uri.decode(name, StandardCharsets.UTF_8, plusIsSpace = true))
    FormRedaction.rewrite(text, truncated = false, separators = "&") { (name, value) =>
      if (sensitive(name)) Mask
      else FormRedaction.rewrite(value, truncated = false, separators = ";") { (n, v) => if (sensitive(n)) Mask else v }
    }
  }

  /** Segment `i` (i >= 1) becomes `REDACTED` when `isSensitive` holds for the decoded segment `i - 1`. */
  def segmentAfter(isSensitive: String => Boolean): Uri => Uri = { uri =>
    val segments = uri.path.segments
    if (segments.size < 2) uri
    else {
      val decoded = segments.map(_.decoded())
      val updated = segments.indices.map { i =>
        if (i >= 1 && isSensitive(decoded(i - 1))) Uri.Path.Segment(Mask) else segments(i)
      }.toVector
      withSegments(uri, updated)
    }
  }

  /**
   * `template` is an absolute path such as `/users/{id}/tokens/{token}`: a literal must equal the decoded path
   * segment, `{name}` matches any one segment and masks it, and a segment that is a single asterisk matches any one
   * segment and keeps it. Applies when the path has at least as many segments as the template and every template
   * segment matches; extra path segments are kept; otherwise the URI is returned unchanged.
   */
  def pathTemplate(template: String): Uri => Uri = {
    val parts: Vector[String] = template.split('/').toVector.filter(_.nonEmpty)
    def isPlaceholder(part: String) = part.length >= 2 && part.startsWith("{") && part.endsWith("}")
    uri => {
      val segments = uri.path.segments
      if (segments.size < parts.size) uri
      else {
        val decoded = segments.map(_.decoded())
        val matches = parts.indices.forall { i =>
          val part = parts(i)
          part == "*" || isPlaceholder(part) || part == decoded(i)
        }
        if (!matches) uri
        else {
          val updated = segments.indices.map { i =>
            if (i < parts.size && isPlaceholder(parts(i))) Uri.Path.Segment(Mask) else segments(i)
          }.toVector
          withSegments(uri, updated)
        }
      }
    }
  }

  private def Mask: String = BodyRedactor.Mask

  private def withSegments(uri: Uri, segments: Vector[Uri.Path.Segment]): Uri =
    uri.copy(path = Uri.Path(segments, uri.path.absolute, uri.path.endsWithSlash))
}
