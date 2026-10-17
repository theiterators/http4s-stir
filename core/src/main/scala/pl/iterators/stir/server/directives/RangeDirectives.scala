package pl.iterators.stir.server.directives

import cats.effect.IO
import fs2.{ Chunk, Stream }
import org.http4s.{ MediaType, Method, RangeUnit, Response, Status }
import org.http4s.headers.{ `Accept-Ranges`, `Content-Length`, `Content-Range`, `Content-Type`, Range }
import org.http4s.headers.Range.SubRange
import org.http4s.multipart.Multiparts
import pl.iterators.stir.server._

/**
 * @groupname range Range directives
 * @groupprio range 190
 */
trait RangeDirectives {
  import BasicDirectives._
  import RangeDirectives._
  import RouteDirectives._

  /**
   * Answers GET requests with a `Range` header by a `206 Partial Content` response as defined by
   * http://tools.ietf.org/html/rfc7233 and adds an `Accept-Ranges: bytes` header to every GET response.
   * Several ranges are coalesced when they are at most one byte apart and answered as `multipart/byteranges`;
   * more than 16 ranges reject with a [[TooManyRangesRejection]]. Ranges beyond the entity reject with an
   * [[UnsatisfiableRangeRejection]] (`416` when sealed), as do responses without a `Content-Length`.
   * Only `200` responses are sliced; everything else passes through unchanged.
   *
   * @group range
   */
  def withRangeSupport: Directive0 = withRangeSupportLimits(DefaultRangeCountLimit, DefaultRangeCoalescingThreshold)

  /**
   * Like [[withRangeSupport]] with an explicit limit on the number of ranges and the distance (in bytes) under
   * which neighbouring ranges are merged into one part.
   *
   * @group range
   */
  def withRangeSupportLimits(rangeCountLimit: Int, rangeCoalescingThreshold: Long): Directive0 =
    extractRequest.flatMap { request =>
      val ranges = if (request.method == Method.GET) request.headers.get[Range] else None
      ranges match {
        case Some(Range(unit, subRanges)) if unit == RangeUnit.Bytes =>
          if (subRanges.size <= rangeCountLimit)
            applyRanges(subRanges.toList, rangeCoalescingThreshold) & acceptByteRanges
          else reject(TooManyRangesRejection(rangeCountLimit))
        case _ => (MethodDirectives.get & acceptByteRanges) | pass
      }
    }

  private def applyRanges(ranges: List[SubRange], coalescingThreshold: Long): Directive0 =
    mapRouteResultWithPF {
      case RouteResult.Complete(response) if response.status == Status.Ok =>
        response.contentLength match {
          case Some(length) =>
            ranges.filter(isSatisfiable(length)) match {
              case Nil           => IO.pure(RouteResult.Rejected(UnsatisfiableRangeRejection(ranges, length) :: Nil))
              case single :: Nil =>
                IO.pure(RouteResult.Complete(rangeResponse(response, indexRange(length)(single), length)))
              case several =>
                val parts = coalesce(several.map(indexRange(length)), coalescingThreshold).sortBy(_.start)
                multipartResponse(response, parts, length).map(RouteResult.Complete(_))
            }
          case None =>
            // ranges are not supported for responses of unknown length (chunked)
            IO.pure(RouteResult.Rejected(UnsatisfiableRangeRejection(ranges, -1) :: Nil))
        }
    }
}

object RangeDirectives extends RangeDirectives {
  val DefaultRangeCountLimit: Int = 16
  val DefaultRangeCoalescingThreshold: Long = 1

  private val acceptByteRanges: Directive0 = BasicDirectives.mapResponse(_.putHeaders(`Accept-Ranges`.bytes))

  /** A byte range of the entity, `end` exclusive. */
  private[stir] final case class IndexRange(start: Long, end: Long) {
    def length: Long = end - start
    def distance(other: IndexRange): Long =
      math.max(end, other.end) - math.min(start, other.start) - (length + other.length)
    def mergeWith(other: IndexRange): IndexRange = IndexRange(math.min(start, other.start), math.max(end, other.end))
    def contentRange(entityLength: Long): `Content-Range` =
      `Content-Range`(RangeUnit.Bytes, SubRange(start, Some(end - 1)), Some(entityLength))
  }

  private[stir] def indexRange(entityLength: Long)(range: SubRange): IndexRange = range match {
    case SubRange(first, Some(last)) if first >= 0 => IndexRange(first, math.min(last + 1, entityLength))
    case SubRange(first, None) if first >= 0       => IndexRange(first, entityLength)
    case SubRange(negativeSuffix, _)               => IndexRange(math.max(0, entityLength + negativeSuffix), entityLength)
  }

  private[stir] def isSatisfiable(entityLength: Long)(range: SubRange): Boolean =
    if (range.first >= 0) range.first < entityLength else true

  /** Merges ranges that are at most `threshold` bytes apart (Pekko's `range-coalescing-threshold`). */
  private[stir] def coalesce(ranges: List[IndexRange], threshold: Long): List[IndexRange] =
    ranges.foldLeft(List.empty[IndexRange]) { (acc, range) =>
      val (mergeCandidates, others) = acc.partition(_.distance(range) <= threshold)
      others :+ mergeCandidates.foldLeft(range)(_.mergeWith(_))
    }

  private def rangeResponse(response: Response[IO], range: IndexRange, entityLength: Long): Response[IO] =
    response.withStatus(Status.PartialContent)
      .withBodyStream(response.body.drop(range.start).take(range.length))
      .putHeaders(range.contentRange(entityLength), `Content-Length`.unsafeFromLong(range.length))

  private val CRLF: Chunk[Byte] = Chunk.array("\r\n".getBytes("UTF-8"))

  /** Streams the sliced parts in one pass over the body; `ranges` must be sorted and non-overlapping. */
  private def multipartResponse(response: Response[IO], ranges: List[IndexRange],
      entityLength: Long): IO[Response[IO]] =
    Multiparts.forSync[IO].flatMap(_.boundary).map { boundary =>
      val partContentType = response.contentType.map(ct => `Content-Type`.headerInstance.value(ct))
      def partHeader(range: IndexRange): Chunk[Byte] = Chunk.array((
        s"--${boundary.value}\r\n" +
        partContentType.fold("")(ct => s"Content-Type: $ct\r\n") +
        s"Content-Range: bytes ${range.start}-${range.end - 1}/$entityLength\r\n\r\n").getBytes("UTF-8"))
      val closing = Chunk.array(s"--${boundary.value}--\r\n".getBytes("UTF-8"))

      val parts = response.body.scanChunks((0L, ranges)) { case ((position, remaining), chunk) =>
        val chunkEnd = position + chunk.size
        val out = List.newBuilder[Chunk[Byte]]
        remaining.foreach { range =>
          if (range.end > position && range.start < chunkEnd) {
            if (range.start >= position) out += partHeader(range)
            val from = math.max(range.start - position, 0L).toInt
            val until = math.min(range.end - position, chunk.size.toLong).toInt
            out += chunk.drop(from).take(until - from)
            if (range.end <= chunkEnd) out += CRLF
          }
        }
        ((chunkEnd, remaining.filter(_.end > chunkEnd)), Chunk.concat(out.result()))
      }

      response.withStatus(Status.PartialContent)
        .withBodyStream(parts ++ Stream.chunk(closing))
        .removeHeader[`Content-Length`]
        .putHeaders(`Content-Type`(MediaType.multipart.byteranges.withExtensions(Map("boundary" -> boundary.value))))
    }
}
