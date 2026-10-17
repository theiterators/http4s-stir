package pl.iterators.stir.server.directives

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import fs2.{ Chunk, Stream }
import org.http4s.{ EntityTag, Header, HttpDate, MediaType, RangeUnit, Response, Status }
import org.http4s.headers.{
  `Accept-Ranges`,
  `Content-Length`,
  `Content-Range`,
  `Content-Type`,
  `If-None-Match`,
  ETag,
  Range
}
import org.http4s.headers.Range.SubRange
import org.typelevel.ci._
import pl.iterators.stir.server._

class RangeDirectivesSpec extends RoutingSpec {
  override implicit def runtime: IORuntime = IORuntime.global

  def bytes(length: Int): Array[Byte] = Array.tabulate(length)(_.toByte)
  def completeWithRangedBytes(length: Int): Route = withRangeSupport { complete(bytes(length)) }

  /** A `200` of `length` bytes with a `Content-Length` but no `Content-Type`, streamed in `chunkSize`-byte chunks. */
  def chunkedEntity(length: Int, chunkSize: Int): Route = complete(Response[IO]()
    .withBodyStream(Stream.chunk(Chunk.array(bytes(length))).covary[IO].chunkN(chunkSize).unchunks)
    .putHeaders(`Content-Length`.unsafeFromLong(length.toLong)))

  /** The response body as ISO-8859-1 text with the multipart boundary replaced by `B`. */
  def multipartBody(): String = {
    val boundary = header[`Content-Type`].flatMap(_.mediaType.extensions.get("boundary")).getOrElse(fail("no boundary"))
    new String(responseAs[Array[Byte]], "ISO-8859-1").replace(boundary, "B")
  }
  def part(range: String, payload: Int*): String =
    s"--B\r\nContent-Range: bytes $range\r\n\r\n" + payload.map(_.toChar).mkString + "\r\n"

  "The `withRangeSupport` directive" should {
    "return an Accept-Ranges(bytes) header for GET requests" in {
      Get() ~> withRangeSupport { complete("any") } ~> check {
        header[`Accept-Ranges`] shouldEqual Some(`Accept-Ranges`.bytes)
      }
    }
    "not return an Accept-Ranges(bytes) header for non-GET requests" in {
      Put() ~> withRangeSupport { complete("any") } ~> check { header[`Accept-Ranges`] shouldEqual None }
    }
    "return a Content-Range header for a ranged request with a single range" in {
      Get() ~> addHeader(Range(0, 1)) ~> completeWithRangedBytes(10) ~> check {
        status shouldEqual Status.PartialContent
        header[`Content-Range`] shouldEqual Some(`Content-Range`(RangeUnit.Bytes, SubRange(0L, 1L), Some(10L)))
        header("Content-Length").map(_.value) shouldEqual Some("2")
        responseAs[Array[Byte]] shouldEqual bytes(2)
      }
    }
    "return a partial response for a ranged request with a single range with undefined lastBytePosition" in {
      Get() ~> addHeader(Range(5)) ~> completeWithRangedBytes(10) ~> check {
        responseAs[Array[Byte]] shouldEqual Array[Byte](5, 6, 7, 8, 9)
      }
    }
    "return a partial response for a ranged request with a single suffix range" in {
      Get() ~> addHeader(Range(-1)) ~> completeWithRangedBytes(10) ~> check {
        responseAs[Array[Byte]] shouldEqual Array[Byte](9)
      }
    }
    "return a partial response for a ranged request with an overlapping suffix range" in {
      Get() ~> addHeader(Range(-100)) ~> completeWithRangedBytes(10) ~> check {
        responseAs[Array[Byte]] shouldEqual bytes(10)
      }
    }
    "be transparent to non-GET requests" in {
      Post() ~> addHeader(Range(1, 2)) ~> completeWithRangedBytes(5) ~> check {
        responseAs[Array[Byte]] shouldEqual bytes(5)
      }
    }
    "be transparent to non-200 responses" in {
      Get() ~> addHeader(Range(1, 2)) ~> Route.seal(withRangeSupport(reject)) ~> check {
        status shouldEqual Status.NotFound
        header[`Content-Range`] shouldEqual None
      }
    }
    "reject an unsatisfiable single range" in {
      Get() ~> addHeader(Range(100, 200)) ~> completeWithRangedBytes(10) ~> check {
        rejection shouldEqual UnsatisfiableRangeRejection(SubRange(100L, 200L) :: Nil, 10)
      }
    }
    "complete an unsatisfiable range with 416 and the entity length when sealed" in {
      Get() ~> addHeader(Range(100, 200)) ~> Route.seal(completeWithRangedBytes(10)) ~> check {
        status shouldEqual Status.RangeNotSatisfiable
        header("Content-Range").map(_.value) shouldEqual Some("bytes */10")
      }
    }
    "return a mediaType of 'multipart/byteranges' for a ranged request with multiple ranges" in {
      Get() ~> addHeader(Range(SubRange(0L, 10L), SubRange(0L, 10L))) ~> completeWithRangedBytes(10) ~> check {
        status shouldEqual Status.PartialContent
        mediaType.withExtensions(Map.empty) shouldEqual MediaType.multipart.byteranges
        header("Content-Length") shouldEqual None
      }
    }
    "merge ranges closer than Pekko's default threshold of 80 bytes" in {
      Get() ~> addHeader(Range(SubRange(0L, 1L), SubRange(8L, 9L))) ~> completeWithRangedBytes(10) ~> check {
        status shouldEqual Status.PartialContent
        val body = responseAs[String]
        body should include("Content-Range: bytes 0-9/10")
        body.indexOf("bytes 8-9/10") shouldEqual -1
      }
    }
    "reject a ranged request when the response length is unknown, as Pekko does" in {
      val streamed = complete(Response[IO]().withBodyStream(Stream.emits(bytes(10)).covary[IO]))
      Get() ~> addHeader(Range(0, 1)) ~> withRangeSupport(streamed) ~> check {
        rejection shouldEqual UnsatisfiableRangeRejection(SubRange(0L, 1L) :: Nil, -1)
      }
    }
    "omit Content-Range from the 416 when the entity length is unknown" in {
      val streamed = complete(Response[IO]().withBodyStream(Stream.emits(bytes(10)).covary[IO]))
      Get() ~> addHeader(Range(0, 1)) ~> Route.seal(withRangeSupport(streamed)) ~> check {
        status shouldEqual Status.RangeNotSatisfiable
        header("Content-Range") shouldEqual None
      }
    }
    "ignore a Range header whose byte range ends before it starts" in {
      Get() ~> addHeader(Header.Raw(ci"Range", "bytes=5-2")) ~> Route.seal(completeWithRangedBytes(10)) ~> check {
        status shouldEqual Status.Ok
        header("Content-Range") shouldEqual None
        header[`Accept-Ranges`] shouldEqual Some(`Accept-Ranges`.bytes)
        responseAs[Array[Byte]] shouldEqual bytes(10)
      }
    }
    "ignore a Range header when any of its byte ranges is invalid" in {
      Get() ~> addHeader(Header.Raw(ci"Range", "bytes=0-1,5-2")) ~> Route.seal(completeWithRangedBytes(10)) ~> check {
        status shouldEqual Status.Ok
        responseAs[Array[Byte]] shouldEqual bytes(10)
      }
    }
    "return coalesced parts in ascending order for multiple ranges" in {
      val route = withRangeSupportLimits(rangeCountLimit = 16, rangeCoalescingThreshold = 1) { complete(bytes(10)) }
      Get() ~> addHeader(Range(SubRange(8L, 9L), SubRange(0L, 1L), SubRange(2L, 3L))) ~> route ~> check {
        val body = responseAs[String]
        body.indexOf("Content-Range: bytes 0-3/10") should be > 0
        body.indexOf("Content-Range: bytes 8-9/10") should be > body.indexOf("Content-Range: bytes 0-3/10")
        body should include("Content-Type: application/octet-stream")
        body.indexOf("bytes 2-3/10") shouldEqual -1
      }
    }
    "reject a request with too many requested ranges" in {
      val route = withRangeSupportLimits(rangeCountLimit = 2, rangeCoalescingThreshold = 1) { complete(bytes(10)) }
      Get() ~> addHeader(Range(SubRange(0L, 1L), SubRange(3L, 4L), SubRange(6L, 7L))) ~> route ~> check {
        rejection shouldEqual TooManyRangesRejection(2)
      }
    }
    "keep the other response headers on the 206" in {
      val route: Route = withRangeSupport { respondWithHeader(Header.Raw(ci"ETag", "\"v1\"")) { complete(bytes(10)) } }
      Get() ~> addHeader(Range(0, 1)) ~> route ~> check {
        status shouldEqual Status.PartialContent
        header("ETag").map(_.value) shouldEqual Some("\"v1\"")
        mediaType shouldEqual MediaType.application.`octet-stream`
      }
    }
    "slice a single range that spans several chunks" in {
      Get() ~> addHeader(Range(3, 12)) ~> withRangeSupport(chunkedEntity(20, 4)) ~> check {
        header("Content-Length").map(_.value) shouldEqual Some("10")
        responseAs[Array[Byte]].toList shouldEqual (3 to 12).map(_.toByte).toList
      }
    }
    "render the parts of a multipart response with their payloads" in {
      val ranges = Range(SubRange(0L, 1L), SubRange(5L, 7L), SubRange(-2L, None))
      Get() ~> addHeader(ranges) ~> withRangeSupportLimits(16, 1)(chunkedEntity(20, Int.MaxValue)) ~> check {
        status shouldEqual Status.PartialContent
        multipartBody() shouldEqual
        part("0-1/20", 0, 1) + part("5-7/20", 5, 6, 7) + part("18-19/20", 18, 19) + "--B--\r\n"
      }
    }
    "render the same multipart response however the entity is chunked" in {
      val ranges = Range(SubRange(0L, 1L), SubRange(5L, 7L), SubRange(-2L, None), SubRange(10L, 10L))
      val expected = part("0-1/20", 0, 1) + part("5-7/20", 5, 6, 7) + part("10-10/20", 10) + part("18-19/20", 18, 19) +
        "--B--\r\n"
      (1 to 20).foreach { chunkSize =>
        Get() ~> addHeader(ranges) ~> withRangeSupportLimits(16, 1)(chunkedEntity(20, chunkSize)) ~> check {
          withClue(s"chunk size $chunkSize: ") { multipartBody() shouldEqual expected }
        }
      }
    }
    "merge ranges exactly at the coalescing threshold and keep ranges one byte further apart" in {
      val route = withRangeSupportLimits(16, rangeCoalescingThreshold = 1)(chunkedEntity(10, Int.MaxValue))
      Get() ~> addHeader(Range(SubRange(0L, 1L), SubRange(3L, 4L))) ~> route ~> check {
        multipartBody() shouldEqual part("0-4/10", 0, 1, 2, 3, 4) + "--B--\r\n"
      }
      Get() ~> addHeader(Range(SubRange(0L, 1L), SubRange(4L, 5L))) ~> route ~> check {
        multipartBody() shouldEqual part("0-1/10", 0, 1) + part("4-5/10", 4, 5) + "--B--\r\n"
      }
    }
    "drop the unsatisfiable ranges of a multipart request and keep the rest" in {
      Get() ~> addHeader(Range(SubRange(0L, 1L), SubRange(50L, 60L))) ~> withRangeSupport(chunkedEntity(10, 3)) ~>
      check {
        status shouldEqual Status.PartialContent
        header[`Content-Range`] shouldEqual Some(`Content-Range`(RangeUnit.Bytes, SubRange(0L, 1L), Some(10L)))
        responseAs[Array[Byte]].toList shouldEqual List[Byte](0, 1)
      }
    }
    "reject a range that starts at the entity length" in {
      Get() ~> addHeader(Range(10)) ~> Route.seal(completeWithRangedBytes(10)) ~> check {
        status shouldEqual Status.RangeNotSatisfiable
        header("Content-Range").map(_.value) shouldEqual Some("bytes */10")
      }
    }
    "reject any range on an empty entity" in {
      Get() ~> addHeader(Range(0)) ~> Route.seal(completeWithRangedBytes(0)) ~> check {
        status shouldEqual Status.RangeNotSatisfiable
        header("Content-Range").map(_.value) shouldEqual Some("bytes */0")
      }
    }
    "serve the whole entity for a zero-length suffix range, which http4s parses as 0- (Pekko answers 416)" in {
      Get() ~> addHeader(Header.Raw(ci"Range", "bytes=-0")) ~> completeWithRangedBytes(10) ~> check {
        status shouldEqual Status.PartialContent
        responseAs[Array[Byte]] shouldEqual bytes(10)
      }
    }
    "pass a request with a range unit other than bytes through, with Accept-Ranges" in {
      Get() ~> addHeader(Header.Raw(ci"Range", "items=0-1")) ~> completeWithRangedBytes(10) ~> check {
        status shouldEqual Status.Ok
        header[`Accept-Ranges`] shouldEqual Some(`Accept-Ranges`.bytes)
        responseAs[Array[Byte]] shouldEqual bytes(10)
      }
    }
    "work inside conditional" in {
      val tag = EntityTag("v1")
      val route = conditional(tag, HttpDate.unsafeFromEpochSecond(1700000000L)) { completeWithRangedBytes(10) }
      Get() ~> addHeader(Range(0, 1)) ~> addHeader(`If-None-Match`(tag)) ~> route ~> check {
        status shouldEqual Status.NotModified
      }
      Get() ~> addHeader(Range(0, 1)) ~> route ~> check {
        status shouldEqual Status.PartialContent
        header[ETag] shouldEqual Some(ETag(tag))
        responseAs[Array[Byte]] shouldEqual bytes(2)
      }
    }
    "serve a single range over a real server" in {
      Get() ~> addHeader(Range(2, 4)) ~!> completeWithRangedBytes(10) ~> check {
        status shouldEqual Status.PartialContent
        responseAs[Array[Byte]] shouldEqual Array[Byte](2, 3, 4)
      }
    }
  }
}
