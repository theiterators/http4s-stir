package pl.iterators.stir.testkit

import cats.effect.IO
import org.http4s.Method._
import org.http4s.headers.Authorization
import org.http4s.{ Credentials, EmptyBody, EntityBody, EntityEncoder, Header, Headers, Method, Request, Uri }
import org.typelevel.ci.CIString

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
        case Some(value) => apply(uri, m.toEntity(value).body)
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

  def addHeader(header: Header.ToRaw): RequestTransformer = _.putHeaders(header)

  def addHeader(headerName: String, headerValue: String): RequestTransformer =
    _.putHeaders(Header.Raw(CIString(headerName), headerValue))

  def addHeaders(first: Header.ToRaw, more: Header.ToRaw*): RequestTransformer = _.putHeaders(Seq(first) ++ more: _*)

  def mapHeaders(f: Headers => Headers): RequestTransformer = _.transformHeaders(f)

  def removeHeader(headerName: String): RequestTransformer = _.removeHeader(CIString(headerName))

  def removeHeader[T](implicit h: Header[T, _]): RequestTransformer = _.removeHeader[T]

//  def removeHeader(clazz: Class[_]): RequestTransformer =
//    _ mapHeaders (_ filterNot clazz.isInstance)

  def removeHeaders(names: String*): RequestTransformer =
    _.filterHeaders(header => !names.map(CIString(_)).contains(header.name))

  def addCredentials(credentials: Credentials) = addHeader(Authorization(credentials))
//
//  def logRequest(log: LoggingAdapter, level: Logging.LogLevel = Logging.DebugLevel) = logValue[HttpRequest](log, level)
//
//  def logRequest(logFun: HttpRequest => Unit) = logValue[HttpRequest](logFun)

  implicit def header2AddHeader(header: Header.ToRaw): RequestTransformer = addHeader(header)
}

object RequestBuilding extends RequestBuilding
