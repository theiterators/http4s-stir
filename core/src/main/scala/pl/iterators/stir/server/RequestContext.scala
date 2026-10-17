package pl.iterators.stir.server

import cats.effect.{ IO, SyncIO }
import org.http4s.{ EmptyBody, Request, Response, Status, Uri }
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
    IO.pure(RouteResult.Complete(Response[IO](status = redirectionType).putHeaders(org.http4s.headers.Location(uri))))

  /**
   * Bubbles the given error up the response chain where it is dealt with by the closest `handleExceptions`
   * directive and its `ExceptionHandler`, unless the error is a `RejectionError`. In this case the
   * wrapped rejection is unpacked and "executed".
   */
  def fail(error: Throwable): IO[RouteResult] = IO.raiseError(error)
}

object RequestContext {
  def apply(request: Request[IO]): RequestContext =
    RequestContext(request, request.pathInfo)

  /**
   * Runs a route the way a server or the testkit starts routing: on a request prepared by `MiscDirectives.prepare`,
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
