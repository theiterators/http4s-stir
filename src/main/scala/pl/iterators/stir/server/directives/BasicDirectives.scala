package pl.iterators.stir.server.directives

import cats.effect.IO
import org.http4s.Uri.Path
import org.http4s.{EntityBody, Headers, Request, Response, Uri}
import pl.iterators.stir.server.{Directive, Directive0, Directive1, Rejection, RequestContext, Route, RouteResult}
import pl.iterators.stir.util.Tuple

object BasicDirectives {
  /**
   * @group basic
   */
  def mapInnerRoute(f: Route => Route): Directive0 =
    Directive { inner => f(inner(())) }

  def mapRequestContext(f: RequestContext => RequestContext): Directive0 =
    mapInnerRoute { inner => ctx => inner(f(ctx)) }

  /**
   * @group basic
   */
  def mapRequest(f: Request[IO] => Request[IO]): Directive0 =
    mapRequestContext { ctx => ctx.copy(request = f(ctx.request)) }

  /**
   * @group basic
   */
  def mapRouteResult(f: RouteResult => RouteResult): Directive0 =
    Directive { inner => ctx => inner(())(ctx)map(f) }

  /**
   * @group basic
   */
  def mapRouteResultPF(f: PartialFunction[RouteResult, RouteResult]): Directive0 =
    mapRouteResult(f.applyOrElse(_, identity[RouteResult]))

  /**
   * @group basic
   */
  def recoverRejections(f: Seq[Rejection] => RouteResult): Directive0 =
    mapRouteResultPF { case RouteResult.Rejected(rejections) => f(rejections) }

  /**
   * @group basic
   */
  def mapRejections(f: Seq[Rejection] => Seq[Rejection]): Directive0 =
    recoverRejections(rejections => RouteResult.Rejected(f(rejections)))

  /**
   * @group basic
   */
  def mapResponse(f: Response[IO] => Response[IO]): Directive0 =
    mapRouteResultPF { case RouteResult.Complete(response) => RouteResult.Complete(f(response)) }

  /**
   * @group basic
   */
  def mapResponseEntity(f: EntityBody[IO] => EntityBody[IO]): Directive0 =
    mapResponse(r => r.copy(body = f(r.body)))

  /**
   * @group basic
   */
  def mapResponseHeaders(f: Headers => Headers): Directive0 =
    mapResponse(r => r.copy(headers = f(r.headers)))

  /**
   * A Directive0 that always passes the request on to its inner route
   * (i.e. does nothing with the request or the response).
   *
   * @group basic
   */
  def pass: Directive0 = Directive.Empty

  /**
   * Injects the given value into a directive.
   *
   * @group basic
   */
  def provide[T](value: T): Directive1[T] = tprovide(Tuple1(value))

  /**
   * Injects the given values into a directive.
   *
   * @group basic
   */
  def tprovide[L: Tuple](values: L): Directive[L] =
    Directive {
      _(values)
    }

  /**
   * Extracts a single value using the given function.
   *
   * @group basic
   */
  def extract[T](f: RequestContext => T): Directive1[T] =
    textract(ctx => Tuple1(f(ctx)))

  /**
   * Extracts a number of values using the given function.
   *
   * @group basic
   */
  def textract[L: Tuple](f: RequestContext => L): Directive[L] =
    Directive { inner => ctx => inner(f(ctx))(ctx) }

  /**
   * Adds a TransformationRejection cancelling all rejections equal to the given one
   * to the list of rejections potentially coming back from the inner route.
   *
   * @group basic
   */
  def cancelRejection(rejection: Rejection): Directive0 =
    cancelRejections(_ == rejection)

  /**
   * Adds a TransformationRejection cancelling all rejections of one of the given classes
   * to the list of rejections potentially coming back from the inner route.
   *
   * @group basic
   */
  def cancelRejections(classes: Class[_]*): Directive0 =
    cancelRejections(r => classes.exists(_ isInstance r))

  /**
   * Adds a TransformationRejection cancelling all rejections for which the given filter function returns true
   * to the list of rejections potentially coming back from the inner route.
   *
   * @group basic
   */
  def cancelRejections(cancelFilter: Rejection => Boolean): Directive0 =
    mapRejections(_.filterNot(cancelFilter))

  /**
   * Transforms the unmatchedPath of the RequestContext using the given function.
   *
   * @group basic
   */
  def mapUnmatchedPath(f: Path => Path): Directive0 =
    mapRequestContext(ctx => ctx.copy(unmatchedPath = f(ctx.unmatchedPath)))

  /**
   * Extracts the yet unmatched path from the RequestContext.
   *
   * @group basic
   */
  def extractUnmatchedPath: Directive1[Path] = extract(_.unmatchedPath)

  /**
   * Extracts the current [[Request]] instance.
   *
   * @group basic
   */
  def extractRequest: Directive1[Request[IO]] = extract(_.request)

  /**
   * Extracts the complete request URI.
   *
   * @group basic
   */
  def extractUri: Directive1[Uri] = extract(_.request.uri)

  /**
   * Extracts the [[RequestContext]] itself.
   *
   * @group basic
   */
  def extractRequestContext: Directive1[RequestContext] = extract(identity)

  /**
   * Extracts the [[EntityBody]] from the [[RequestContext]].
   *
   * @group basic
   */
  def extractRequestEntity: Directive1[EntityBody[IO]] = extract(_.request.body)
}