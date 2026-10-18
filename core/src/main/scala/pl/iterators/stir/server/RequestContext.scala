package pl.iterators.stir.server

import cats.effect.{ IO, SyncIO }
import org.http4s.{ Charset, EmptyBody, MediaType, Request, Response, Status, Uri }
import org.http4s.headers.{ `Content-Type`, Location }
import org.http4s.Uri.Path
import org.typelevel.vault.Key
import pl.iterators.stir.marshalling.{ ContentNegotiation, ToResponseMarshallable, ToResponseMarshaller }

case class RequestContext(request: Request[IO], unmatchedPath: Path) {
  import RequestContext.AcceptAllKey
  import ToResponseMarshaller.NegotiatedKey

  /**
   * Completes the request with the given ToResponseMarshallable. A response the marshaller marked as negotiated
   * (`ToResponseMarshaller.negotiated`) is rejected with an `UnacceptedResponseContentTypeRejection` when the
   * request's `Accept` header does not accept its `Content-Type`, unless the context accepts all (`withAcceptAll`).
   */
  def complete(obj: ToResponseMarshallable): IO[RouteResult] =
    obj.marshaller.toResponse(obj.value).map { response =>
      if (response.attributes.lookup(NegotiatedKey).isEmpty) RouteResult.Complete(response)
      else {
        val plain = response.withoutAttribute(NegotiatedKey)
        plain.contentType match {
          case Some(contentType) if !acceptsAll && !ContentNegotiation.isAcceptable(request, contentType) =>
            RouteResult.Rejected(UnacceptedResponseContentTypeRejection(Set(contentType)) :: Nil)
          case _ => RouteResult.Complete(plain)
        }
      }
    }

  /**
   * Returns a copy of this context in which every response is acceptable to the client, whatever the request's
   * `Accept` header says: Pekko's `withAcceptAll`, used for the responses of exception and rejection handlers. The
   * header itself stays on the request, so a handler can still choose a representation by it (Pekko appends
   * a wildcard range with the smallest quality instead, which lets a `q=0` range still reject the handler's response).
   */
  def withAcceptAll: RequestContext = copy(request = request.withAttribute(AcceptAllKey, ()))

  private def acceptsAll: Boolean = request.attributes.lookup(AcceptAllKey).isDefined

  /**
   * Rejects the request with the given rejections.
   */
  def reject(rejections: Rejection*): IO[RouteResult] = IO.pure(RouteResult.Rejected(rejections))

  /**
   * Completes the request with redirection response of the given type to the given URI.
   */
  def redirect(uri: Uri, redirectionType: Status): IO[RouteResult] =
    IO(RequestContext.redirectResponse(uri, redirectionType)).map(RouteResult.Complete(_))

  /**
   * Bubbles the given error up the response chain where it is dealt with by the closest `handleExceptions`
   * directive and its `ExceptionHandler` (stir has no `RejectionError`: to reject, use `reject`).
   */
  def fail(error: Throwable): IO[RouteResult] = IO.raiseError(error)
}

object RequestContext {

  // Pekko's `htmlTemplate` of each redirection; 304 and redirections without one get no body
  private val RedirectTemplates: Map[Int, String] = Map(
    300 ->
    "There are multiple options for the resource that the client may follow. The preferred one is <a href=\"%s\">this URI</a>.",
    301 -> "This and all future requests should be directed to <a href=\"%s\">this URI</a>.",
    302 -> "The requested resource temporarily resides under <a href=\"%s\">this URI</a>.",
    303 -> "The response to the request can be found under <a href=\"%s\">this URI</a> using a GET method.",
    305 -> "This single request is to be repeated via the proxy under <a href=\"%s\">this URI</a>.",
    307 ->
    "The request should be repeated with <a href=\"%s\">this URI</a>, but future requests can still use the original URI.",
    308 -> "The request, and all future requests should be repeated using <a href=\"%s\">this URI</a>.")

  /**
   * The response `redirect` completes with: the status, a `Location` header and, as Pekko sends it, a short
   * `text/html` body linking to the URI (HTML-escaped). Throws an `IllegalArgumentException` for a status that is
   * not a redirection (3xx), which Pekko's `Redirection` type rules out at compile time.
   */
  private[stir] def redirectResponse(uri: Uri, redirectionType: Status): Response[IO] = {
    requireRedirection(redirectionType)
    val response = Response[IO](status = redirectionType).putHeaders(Location(uri))
    RedirectTemplates.get(redirectionType.code) match {
      case Some(template) =>
        response.withEntity(template.replace("%s", escapeHtml(uri.renderString)))
          .withContentType(`Content-Type`(MediaType.text.html, Charset.`UTF-8`))
      case None => response
    }
  }

  private[stir] def requireRedirection(status: Status): Unit =
    require(status.responseClass == Status.Redirection, s"$status is not a redirection (3xx) status")

  private def escapeHtml(s: String): String =
    s.flatMap {
      case '&'  => "&amp;"
      case '<'  => "&lt;"
      case '>'  => "&gt;"
      case '"'  => "&quot;"
      case '\'' => "&#39;"
      case c    => c.toString
    }
  def apply(request: Request[IO]): RequestContext =
    RequestContext(request, request.pathInfo)

  /**
   * Runs a route the way `toHttpApp` and the testkit start routing: on a request prepared by `MiscDirectives.prepare`,
   * releasing what its shared body holds when the route rejects or fails, or when the response body ends.
   */
  private[stir] def run(request: Request[IO])(route: RequestContext => IO[RouteResult]): IO[RouteResult] =
    directives.MiscDirectives.prepare(request).flatMap { prepared =>
      prepared.attributes.lookup(directives.MiscDirectives.SharedBodyKey) match {
        case None         => route(RequestContext(prepared))
        case Some(shared) =>
          route(RequestContext(prepared)).onError(_ => shared.release).flatMap {
            case RouteResult.Complete(response) if !(response.body eq EmptyBody) =>
              IO.pure(RouteResult.Complete(response.withBodyStream(response.body.onFinalize(shared.release))))
            case other => shared.release.as(other)
          }.onCancel(shared.release)
      }
    }

  /** Set on the request by `withAcceptAll`: responses completed with it are not negotiated. */
  private[stir] val AcceptAllKey: Key[Unit] = Key.newKey[SyncIO, Unit].unsafeRunSync()
}
