package pl.iterators.stir.server.directives

import org.http4s.Uri
import org.http4s.headers.Host
import pl.iterators.stir.server.{ Directive0, Directive1 }

import scala.util.matching.Regex

/**
 * @groupname host Host directives
 * @groupprio host 110
 */
trait HostDirectives {
  import BasicDirectives._
  import RouteDirectives._

  /**
   * Extracts the hostname part of the Host request header value.
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
  // request, or HTTP/2's :authority, which brings no Host header), else the Host header (RFC 7230 5.4); an IPv6
  // address without brackets
  private val _extractHost: Directive1[String] = extract { ctx =>
    ctx.request.uri.authority.map(authority => address(authority.host))
      .orElse(ctx.request.headers.get[Host].map(header => unbracketed(header.host)))
      .getOrElse("")
  }

  private def address(host: Uri.Host): String = host match {
    case Uri.Ipv6Address(ipv6) => ipv6.toString
    case other                 => other.value
  }

  private def unbracketed(host: String): String =
    if (host.startsWith("[") && host.endsWith("]")) host.substring(1, host.length - 1) else host
}
