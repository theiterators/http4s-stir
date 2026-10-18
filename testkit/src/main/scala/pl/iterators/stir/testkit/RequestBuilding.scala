package pl.iterators.stir.testkit

import cats.effect.IO
import org.http4s.Method._
import org.http4s.headers.Authorization
import org.http4s.{ Credentials, EmptyBody, EntityBody, EntityEncoder, Header, Headers, Method, Request, Uri }
import org.typelevel.ci.CIString
import org.typelevel.vault.Key
import pl.iterators.stir.server.directives.MethodDirectives.QUERY

trait RequestBuilding {
  type RequestTransformer = Request[IO] => Request[IO]

  class RequestBuilder(val method: Method) {
    def apply(): Request[IO] =
      apply("/")

    def apply(uri: String): Request[IO] =
      apply(uri, EmptyBody)

    def apply[T](uri: String, content: T)(implicit m: EntityEncoder[IO, T]): Request[IO] =
      apply(uri, Some(content))

    def apply[T](uri: String, content: Option[T])(implicit m: EntityEncoder[IO, T]): Request[IO] =
      apply(Uri.unsafeFromString(uri), content)

    def apply(uri: String, entity: EntityBody[IO]): Request[IO] =
      apply(Uri.unsafeFromString(uri), entity)

    def apply(uri: Uri): Request[IO] =
      apply(uri, EmptyBody)

    def apply[T](uri: Uri, content: T)(implicit m: EntityEncoder[IO, T]): Request[IO] =
      apply(uri, Some(content))

    def apply[T](uri: Uri, content: Option[T])(implicit m: EntityEncoder[IO, T]): Request[IO] =
      content match {
        case None        => apply(uri, EmptyBody)
        case Some(value) => Request[IO](method, uri).withEntity(value)
      }

    def apply(uri: Uri, entity: EntityBody[IO]): Request[IO] =
      Request[IO](method, uri, body = entity)
  }

  val Get = new RequestBuilder(GET)
  val Post = new RequestBuilder(POST)
  val Put = new RequestBuilder(PUT)
  val Patch = new RequestBuilder(PATCH)
  val Delete = new RequestBuilder(DELETE)
  val Options = new RequestBuilder(OPTIONS)
  val Head = new RequestBuilder(HEAD)
  val Query = new RequestBuilder(QUERY)

  /**
   * Adds the header before the request's headers, as Pekko's `addHeader`, so it is the one a typed lookup of a
   * single-value header finds (a later `addHeader` overrides an earlier one) while a header of the same name stays.
   * The entity headers (`Content-Type`, `Content-Length`, `Transfer-Encoding`), which Pekko keeps in the entity rather
   * than among the headers, replace the request's own instead.
   */
  def addHeader(header: Header.ToRaw): RequestTransformer = request => {
    val added = header.values
    request.withHeaders(Headers(added ++ RequestBuilding.withoutReplaced(request.headers.headers, added)))
  }

  def addHeader(headerName: String, headerValue: String): RequestTransformer =
    addHeader(Header.Raw(CIString(headerName), headerValue))

  /** Adds the headers after the request's headers, as Pekko's `addHeaders`; entity headers replace, as in `addHeader`. */
  def addHeaders(first: Header.ToRaw, more: Header.ToRaw*): RequestTransformer = request => {
    val added = (first +: more).flatMap(_.values)
    request.withHeaders(Headers(RequestBuilding.withoutReplaced(request.headers.headers, added) ++ added))
  }

  def mapHeaders(f: Headers => Headers): RequestTransformer = _.transformHeaders(f)

  def removeHeader(headerName: String): RequestTransformer = _.removeHeader(CIString(headerName))

  def removeHeader[T](implicit h: Header[T, _]): RequestTransformer = _.removeHeader[T]

//  def removeHeader(clazz: Class[_]): RequestTransformer =
//    _ mapHeaders (_ filterNot clazz.isInstance)

  def removeHeaders(names: String*): RequestTransformer =
    _.filterHeaders(header => !names.map(CIString(_)).contains(header.name))

  def addCredentials(credentials: Credentials) = addHeader(Authorization(credentials))

  /** Sets a request attribute, as Pekko's `addAttribute`; read it in a route with `attribute(key)`. */
  def addAttribute[T](key: Key[T], value: T): RequestTransformer = _.withAttribute(key, value)
//
//  def logRequest(log: LoggingAdapter, level: Logging.LogLevel = Logging.DebugLevel) = logValue[HttpRequest](log, level)
//
//  def logRequest(logFun: HttpRequest => Unit) = logValue[HttpRequest](logFun)

  implicit def header2AddHeader(header: Header.ToRaw): RequestTransformer = addHeader(header)
}

object RequestBuilding extends RequestBuilding {
  private val EntityHeaders: Set[CIString] =
    Set(CIString("Content-Type"), CIString("Content-Length"), CIString("Transfer-Encoding"))

  /** The request's headers without the entity headers that `added` replaces. */
  private def withoutReplaced(headers: List[Header.Raw], added: Seq[Header.Raw]): List[Header.Raw] = {
    val replaced = added.map(_.name).filter(EntityHeaders).toSet
    headers.filterNot(h => replaced(h.name))
  }
}
