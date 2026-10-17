package pl.iterators.stir.marshalling

import cats.effect.IO
import org.http4s.{ MediaRange, MediaType, Request }
import org.http4s.headers.{ `Content-Type`, Accept }

/** Pekko's content negotiation for a single representation: the request's `Accept` ranges decide. */
object ContentNegotiation {

  /**
   * Whether the client accepts the given content type: no `Accept` header accepts everything; otherwise the most
   * specific matching range decides (RFC 7231 5.3.2), a quality of 0 meaning "not acceptable", and a type that no
   * range matches is not acceptable.
   */
  def isAcceptable(request: Request[IO], contentType: `Content-Type`): Boolean =
    request.headers.get[Accept].forall { accept =>
      val matching = accept.values.toList.filter(_.mediaRange.satisfiedBy(contentType.mediaType))
      matching.maxByOption(v => (specificity(v.mediaRange), v.qValue.thousandths)).exists(_.qValue.thousandths > 0)
    }

  private def specificity(range: MediaRange): Int = range match {
    case mediaType: MediaType => 2 + mediaType.extensions.size
    case _ if range.mainType == "*" => 0
    case _ => 1
  }
}
