package pl.iterators.stir.server.directives

import org.http4s.{ Header, Headers }
import pl.iterators.stir.server.Directive0
import scala.collection.immutable

trait RespondWithDirectives {
  import BasicDirectives._

  /**
   * Unconditionally adds the given response header to all HTTP responses of its inner Route.
   *
   * @group response
   */
  def respondWithHeader(responseHeader: Header.Raw): Directive0 =
    respondWithHeaders(Seq(responseHeader))

  /**
   * Adds the given response header to all HTTP responses of its inner Route,
   * if the response from the inner Route doesn't already contain a header with the same name.
   *
   * @group response
   */
  def respondWithDefaultHeader(responseHeader: Header.Raw): Directive0 =
    respondWithDefaultHeaders(immutable.Seq(responseHeader))

  /**
   * Unconditionally adds the given response headers to all HTTP responses of its inner Route.
   *
   * @group response
   */
  def respondWithHeaders[A](responseHeaders: Seq[Header.Raw]): Directive0 =
    // prepend; http4s' `Headers.++` would drop the added headers whose names the response already carries
    mapResponseHeaders(headers => Headers(responseHeaders.toList ++ headers.headers))

  def respondWithHeaders(firstHeader: Header.Raw, otherHeaders: Header.Raw*): Directive0 =
    respondWithHeaders(firstHeader +: otherHeaders.toList)

  /**
   * Adds the given response headers to all HTTP responses of its inner Route,
   * if a header already exists it is not added again.
   *
   * @group response
   */
  def respondWithDefaultHeaders(responseHeaders: immutable.Seq[Header.Raw]): Directive0 =
    mapResponse { response =>
      // the missing defaults go first (in declared order; Pekko's withDefaultHeaders also prepends them, reversed)
      val missing = responseHeaders.filterNot(header => response.headers.get(header.name).isDefined)
      response.withHeaders(Headers(missing.toList ++ response.headers.headers))
    }

  /**
   * Adds the given response headers to all HTTP responses of its inner Route,
   * if a header already exists it is not added again.
   *
   * @group response
   */
  def respondWithDefaultHeaders(firstHeader: Header.Raw, otherHeaders: Header.Raw*): Directive0 =
    respondWithDefaultHeaders(firstHeader +: otherHeaders.toList)

}

object RespondWithDirectives extends RespondWithDirectives
