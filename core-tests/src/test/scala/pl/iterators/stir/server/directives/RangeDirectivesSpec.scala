package pl.iterators.stir.server.directives

import cats.effect.unsafe.IORuntime
import org.http4s.{ MediaType, RangeUnit, Status }
import org.http4s.headers.{ `Accept-Ranges`, `Content-Range`, Range }
import org.http4s.headers.Range.SubRange
import pl.iterators.stir.server._

class RangeDirectivesSpec extends RoutingSpec {
  override implicit def runtime: IORuntime = IORuntime.global

  def bytes(length: Int): Array[Byte] = Array.tabulate(length)(_.toByte)
  def completeWithRangedBytes(length: Int): Route = withRangeSupport { complete(bytes(length)) }

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
    "serve a single range over a real server" in {
      Get() ~> addHeader(Range(2, 4)) ~!> completeWithRangedBytes(10) ~> check {
        status shouldEqual Status.PartialContent
        responseAs[Array[Byte]] shouldEqual Array[Byte](2, 3, 4)
      }
    }
  }
}
