package pl.iterators.stir.server.directives

import org.http4s.Header
import org.http4s.headers.Origin
import org.typelevel.ci.CIString
import pl.iterators.stir.impl.util._
import pl.iterators.stir.server._

import scala.util.control.NonFatal

trait HeaderDirectives {
  import BasicDirectives._
  import RouteDirectives._

  /**
   * Checks that the request comes from one of the given origins. Extracts the [[Origin]] header value and verifies
   * that it is in the allowed list. If the header is absent rejects with a [[MissingHeaderRejection]]; if the
   * origin is not allowed rejects with an [[InvalidOriginRejection]] (`403 Forbidden` when sealed).
   *
   * @group header
   */
  def checkSameOrigin(first: Origin.Host, more: Origin.Host*): Directive0 = {
    val allowed = first +: more
    headerValueByType[Origin].flatMap {
      case Origin.HostList(hosts) if hosts.exists(allowed.contains) => pass
      case _                                                        => reject(InvalidOriginRejection(allowed))
    }
  }

  /**
   * Extracts an HTTP header value using the given function. If the function result is undefined for all headers the
   * request is rejected with an empty rejection set. If the given function throws an exception the request is rejected
   * with a [[pl.iterators.stir.server.MalformedHeaderRejection]].
   *
   * @group header
   */
  def headerValue[T](f: Header.Raw => Option[T]): Directive1[T] = {
    val protectedF: Header.Raw => Option[Either[Rejection, T]] = header =>
      try f(header).map(Right.apply)
      catch {
        case NonFatal(e) =>
          Some(Left(MalformedHeaderRejection(header.name.toString, e.getMessage.nullAsEmpty, Some(e))))
      }

    extract(_.request.headers.headers.collectFirst(Function.unlift(protectedF))).flatMap {
      case Some(Right(a))        => provide(a)
      case Some(Left(rejection)) => reject(rejection)
      case None                  => reject
    }
  }

  /**
   * Extracts an HTTP header value using the given partial function. If the function is undefined for all headers the
   * request is rejected with an empty rejection set.
   *
   * @group header
   */
  def headerValuePF[T](pf: PartialFunction[Header.Raw, T]): Directive1[T] = headerValue(pf.lift)

  /**
   * Extracts the value of the HTTP request header with the given name.
   * If no header with a matching name is found the request is rejected with a [[pl.iterators.stir.server.MissingHeaderRejection]].
   *
   * @group header
   */
  def headerValueByName(headerName: String): Directive1[String] =
    headerValue(optionalValue(headerName.toRootLowerCase)) | reject(MissingHeaderRejection(headerName))

  /**
   * Extracts the first HTTP request header of the given type.
   * If no header with a matching type is found the request is rejected with a [[MissingHeaderRejection]].
   *
   * Custom headers will only be matched by this directive if an http4s `Header[T, _]` instance is in scope; the
   * rejection names the header as that instance does (`X-Custom-Header`), as Pekko's names a modeled custom header.
   *
   * @group header
   */
  def headerValueByType[T](implicit ev: Header.Select[T], header: Header[T, _]): Directive1[ev.F[T]] =
    extract(_.request.headers.get(ev)).flatMap {
      case Some(value) => provide(value)
      case None        => reject(MissingHeaderRejection(header.name.toString))
    }

  /**
   * Extracts an optional HTTP header value using the given function.
   * If the given function throws an exception the request is rejected
   * with a [[pl.iterators.stir.server.MalformedHeaderRejection]].
   *
   * @group header
   */
  // #optionalHeaderValue
  def optionalHeaderValue[T](f: Header.Raw => Option[T]): Directive1[Option[T]] =
    headerValue(f).map(Some(_): Option[T]).recoverPF {
      case Nil => provide(None)
    }
  // #optionalHeaderValue

  /**
   * Extracts an optional HTTP header value using the given partial function.
   * If the given function throws an exception the request is rejected
   * with a [[pl.iterators.stir.server.MalformedHeaderRejection]].
   *
   * @group header
   */
  def optionalHeaderValuePF[T](pf: PartialFunction[Header.Raw, T]): Directive1[Option[T]] =
    optionalHeaderValue(pf.lift)

  /**
   * Extracts the value of the optional HTTP request header with the given name.
   *
   * @group header
   */
  def optionalHeaderValueByName(headerName: String): Directive1[Option[String]] = {
    extract(_.request.headers.headers.collectFirst {
      case h: Header.Raw if h.name.equals(CIString(headerName)) => h.value
    })
  }

  /**
   * Extract the header value of the optional HTTP request header with the given type.
   *
   * Custom headers will only be matched by this directive if an http4s `Header[T, _]` instance is in scope; the
   * rejection names the header as that instance does (`X-Custom-Header`), as Pekko's names a modeled custom header.
   *
   * @group header
   */
  def optionalHeaderValueByType[T](implicit ev: Header.Select[T]): Directive1[Option[ev.F[T]]] =
    extract(_.request.headers.get(ev))

  private def optionalValue(lowerCaseName: String): Header.Raw => Option[String] = {
    case h: Header.Raw if h.name.equals(CIString(lowerCaseName)) => Some(h.value)
    case _                                                       => None
  }
}

object HeaderDirectives extends HeaderDirectives
