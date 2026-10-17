package pl.iterators.stir.server

import cats.effect.IO
import org.http4s.{ Request, Response, Status, Uri }
import org.http4s.Uri.Path
import org.http4s.headers.Accept
import pl.iterators.stir.marshalling.{ ContentNegotiation, ToResponseMarshallable }

case class RequestContext(request: Request[IO], unmatchedPath: Path) {

  /**
   * Completes the request with the given ToResponseMarshallable.
   */
  def complete(obj: ToResponseMarshallable): IO[RouteResult] =
    obj.marshaller.toResponse(obj.value).map { response =>
      response.contentType match {
        case Some(contentType) if obj.marshaller.negotiable && !ContentNegotiation.isAcceptable(request, contentType) =>
          RouteResult.Rejected(UnacceptedResponseContentTypeRejection(Set(contentType)) :: Nil)
        case _ => RouteResult.Complete(response)
      }
    }

  /**
   * Returns a copy of this context without the request's `Accept` header, so that whatever is completed with it is
   * acceptable to the client: Pekko's `withAcceptAll`, used for the responses of exception and rejection handlers.
   */
  def withAcceptAll: RequestContext = copy(request = request.removeHeader[Accept])

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
}
