package pl.iterators.stir.server.directives

import org.http4s.Uri
import org.http4s.headers.Host
import pl.iterators.stir.server.{ Directive0, Directive1 }

import java.util.Locale
import scala.util.matching.Regex

/**
 * @groupname host Host directives
 * @groupprio host 110
 */
trait HostDirectives {
  import BasicDirectives._
  import RouteDirectives._

  /**
   * Extracts the hostname of the request: the authority of the request URI, else the Host header, lowercased (see
   * `HostDirectives` for the rules).
   *
   * @group host
   */
  def extractHost: Directive1[String] = HostDirectives._extractHost

  /**
   * Rejects all requests with a host name different from the given ones.
   *
   * @group host
   */
  def host(hostNames: String*): Directive0 = host(hostNames.contains(_))

  // #require-host
  /**
   * Rejects all requests for whose host name the given predicate function returns false.
   *
   * @group host
   */
  def host(predicate: String => Boolean): Directive0 = extractHost.require(predicate)
  // #require-host

  /**
   * Rejects all requests with a host name that doesn't have a prefix matching the given regular expression.
   * For all matching requests the prefix string matching the regex is extracted and passed to the inner route.
   * If the regex contains a capturing group only the string matched by this group is extracted.
   * If the regex contains more than one capturing group an IllegalArgumentException is thrown.
   *
   * @group host
   */
  def host(regex: Regex): Directive1[String] = {
    def forFunc(regexMatch: String => Option[String]): Directive1[String] = {
      extractHost.flatMap { name =>
        regexMatch(name) match {
          case Some(matched) => provide(matched)
          case None          => reject
        }
      }
    }

    regex.pattern.matcher("").groupCount() match {
      case 0 => forFunc(regex.findPrefixOf(_))
      case 1 => forFunc(regex.findPrefixMatchOf(_).map(_.group(1)))
      case _ => throw new IllegalArgumentException("Path regex '" + regex.pattern.pattern +
          "' must not contain more than one capturing group")
    }
  }

}

object HostDirectives extends HostDirectives {
  import BasicDirectives._

  // the host of the request's effective URI, as Pekko reads it: the authority of the request URI (an absolute-form
  // request, or HTTP/2's :authority), else the Host header (RFC 7230 5.4); a host name lowercased as Pekko's URI parser
  // lowercases it, an IPv6 address without brackets. When the URI authority and the Host header disagree (host or
  // port) Pekko answers 400; no host is named then, so that `host(...)` rejects instead of trusting either one (a
  // proxy routing on Host must not be bypassed by an absolute-form target naming another host).
  private val _extractHost: Directive1[String] = extract { ctx =>
    val request = ctx.request
    val defaultPort = request.uri.scheme.flatMap(scheme => if (scheme == Uri.Scheme.https) Some(443) else Some(80))
    def port(p: Option[Int]): Option[Int] = p.filterNot(defaultPort.contains)
    val fromUri = request.uri.authority.filter(_.host.value.nonEmpty).map(a => (address(a.host), port(a.port)))
    val fromHeader = request.headers.get[Host].map(h => (unbracketed(h.host).toLowerCase(Locale.ROOT), port(h.port)))
    (fromUri, fromHeader) match {
      case (Some(uri), Some(header)) => if (uri == header) uri._1 else ""
      case (Some(uri), None)         => uri._1
      case (None, Some(header))      => header._1
      case (None, None)              => ""
    }
  }

  private def address(host: Uri.Host): String = host match {
    case Uri.Ipv6Address(ipv6) => ipv6.toString
    case other                 => other.value.toLowerCase(Locale.ROOT)
  }

  private def unbracketed(host: String): String =
    if (host.startsWith("[") && host.endsWith("]")) host.substring(1, host.length - 1) else host
}
