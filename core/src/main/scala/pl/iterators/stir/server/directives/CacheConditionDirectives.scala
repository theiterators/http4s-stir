package pl.iterators.stir.server.directives

import cats.data.NonEmptyList
import cats.effect.IO
import org.http4s.{ EntityTag, HttpDate, Method, Response, Status }
import org.http4s.headers._
import pl.iterators.stir.server._

/**
 * @groupname cachecondition Cache condition directives
 * @groupprio cachecondition 20
 */
trait CacheConditionDirectives {
  import BasicDirectives._
  import CacheConditionDirectives._
  import RouteDirectives._

  /**
   * Wraps its inner route with support for Conditional Requests as defined by
   * http://tools.ietf.org/html/rfc7232: the algorithm of section 6 decides between `304 Not Modified`,
   * `412 Precondition Failed` and running the inner route, and the `ETag` / `Last-Modified` headers are added to
   * the response unless the inner route set them itself.
   *
   * @group cachecondition
   */
  def conditional(eTag: EntityTag): Directive0 = conditional(Some(eTag), None)

  /**
   * @group cachecondition
   */
  def conditional(lastModified: HttpDate): Directive0 = conditional(None, Some(lastModified))

  /**
   * @group cachecondition
   */
  def conditional(eTag: EntityTag, lastModified: HttpDate): Directive0 = conditional(Some(eTag), Some(lastModified))

  /**
   * @group cachecondition
   */
  def conditional(eTag: Option[EntityTag], lastModified: Option[HttpDate]): Directive0 = {
    def addResponseHeaders: Directive0 = mapResponse(withDefaultHeaders(_, eTag, lastModified))
    def complete304: Route = addResponseHeaders(complete(Status.NotModified))
    def complete412: Route = complete(Status.PreconditionFailed)

    extractRequest.flatMap { request =>
      mapInnerRoute { route =>
        val headers = request.headers
        def isGetOrHead = request.method == Method.GET || request.method == Method.HEAD
        def unmodified(since: HttpDate): Boolean = lastModified.exists(lm => lm <= since && since <= now())
        def innerRouteWithRangeHeaderFilteredOut: Route = (mapRequest(_.removeHeader[Range]) & addResponseHeaders)(
          route)

        def step1: Route = headers.get[`If-Match`] match {
          case Some(`If-Match`(tags)) if eTag.isDefined =>
            if (matchesRange(eTag.get, tags, weakComparison = false)) step3 else complete412
          case _ => step2
        }
        def step2: Route = headers.get[`If-Unmodified-Since`] match {
          case Some(`If-Unmodified-Since`(ius)) if lastModified.isDefined && !unmodified(ius) => complete412
          case _                                                                              => step3
        }
        def step3: Route = headers.get[`If-None-Match`] match {
          case Some(`If-None-Match`(tags)) if eTag.isDefined =>
            if (!matchesRange(eTag.get, tags, weakComparison = true)) step5
            else if (isGetOrHead) complete304
            else complete412
          case _ => step4
        }
        def step4: Route =
          if (isGetOrHead) headers.get[`If-Modified-Since`] match {
            case Some(`If-Modified-Since`(ims)) if lastModified.isDefined && unmodified(ims) => complete304
            case _                                                                           => step5
          }
          else step5
        def step5: Route =
          if (request.method == Method.GET && headers.get[Range].isDefined) headers.get[`If-Range`] match {
            case Some(`If-Range`.ETag(tag)) if eTag.isDefined && !matches(eTag.get, tag, weakComparison = false) =>
              innerRouteWithRangeHeaderFilteredOut
            case Some(`If-Range`.LastModified(ims)) if lastModified.isDefined && !unmodified(ims) =>
              innerRouteWithRangeHeaderFilteredOut
            case _ => step6
          }
          else step6
        def step6: Route = addResponseHeaders(route)

        step1
      }
    }
  }
}

object CacheConditionDirectives extends CacheConditionDirectives {
  private def now(): HttpDate = HttpDate.unsafeFromEpochSecond(System.currentTimeMillis() / 1000)

  private def withDefaultHeaders(response: Response[IO], eTag: Option[EntityTag],
      lastModified: Option[HttpDate]): Response[IO] = {
    val withETag =
      eTag.filter(_ => response.headers.get[ETag].isEmpty).fold(response)(tag => response.putHeaders(ETag(tag)))
    lastModified.filter(_ => withETag.headers.get[`Last-Modified`].isEmpty)
      .fold(withETag)(date => withETag.putHeaders(`Last-Modified`(date)))
  }

  private def matches(a: EntityTag, b: EntityTag, weakComparison: Boolean): Boolean =
    if (weakComparison) a.tag == b.tag
    else a.tag == b.tag && a.weakness == EntityTag.Strong && b.weakness == EntityTag.Strong

  /** `None` tags stand for `*`, which matches any entity tag. */
  private def matchesRange(tag: EntityTag, tags: Option[NonEmptyList[EntityTag]], weakComparison: Boolean): Boolean =
    tags.fold(true)(_.exists(matches(tag, _, weakComparison)))
}
