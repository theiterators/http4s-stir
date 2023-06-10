package pl.iterators.stir.server.directives

import org.http4s.Header
import org.typelevel.ci.CIString
import pl.iterators.stir.server._

import scala.reflect.ClassTag
import scala.util.control.NonFatal

trait HeaderDirectives {
  import BasicDirectives._
  import RouteDirectives._

//  /**
//   * Checks that request comes from the same origin. Extracts the [[Origin]] header value and verifies that
//   * allowed range contains the obtained value. In the case of absent of the [[Origin]] header rejects
//   * with [[MissingHeaderRejection]]. If the origin value is not in the allowed range
//   * rejects with an [[InvalidOriginRejection]] and [[StatusCodes.Forbidden]] status.
//   *
//   * @group header
//   */
//  def checkSameOrigin(allowed: HttpOriginRange.Default): Directive0 = {
//    headerValueByType(Origin).flatMap { origin =>
//      if (origin.origins.exists(allowed.matches)) pass
//      else reject(InvalidOriginRejection(allowed.origins))
//    }
//  }

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
        case NonFatal(e) => Some(Left(MalformedHeaderRejection(header.name.toString, e.getMessage, Some(e))))
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
   * If no header with a matching name is found the request is rejected with a [[pl.iterators.stir.server..MissingHeaderRejection]].
   *
   * @group header
   */
  def headerValueByName(headerName: String): Directive1[String] =
    headerValue(optionalValue(headerName.toLowerCase)) | reject(MissingHeaderRejection(headerName))

  /**
   * Extracts the first HTTP request header of the given type.
   * If no header with a matching type is found the request is rejected with a [[MissingHeaderRejection]].
   *
   * Custom headers will only be matched by this directive if ev [[Header.Select[T]]] is provided.
   *
   * @group header
   */
  def headerValueByType[T](implicit ev: Header.Select[T], cls: ClassTag[T]): Directive1[ev.F[T]] =
    extract(_.request.headers.get(ev)).flatMap {
      case Some(header) => provide(header)
      case None         => reject(MissingHeaderRejection(cls.runtimeClass.getSimpleName))
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
   * Custom headers will only be matched by this directive if ev [[Header.Select[T]]] is provided.
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
