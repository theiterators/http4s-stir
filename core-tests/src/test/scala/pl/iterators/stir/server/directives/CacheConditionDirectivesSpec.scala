package pl.iterators.stir.server.directives

import cats.effect.unsafe.IORuntime
import org.http4s.{ EntityTag, HttpDate, Status }
import org.http4s.headers._
import pl.iterators.stir.server._

class CacheConditionDirectivesSpec extends RoutingSpec {
  override implicit def runtime: IORuntime = IORuntime.global

  val timestamp = HttpDate.unsafeFromEpochSecond(System.currentTimeMillis() / 1000 - 3600)
  val ifUnmodifiedSince = `If-Unmodified-Since`(timestamp)
  val ifModifiedSince = `If-Modified-Since`(timestamp)
  val tag = EntityTag("fresh")

  def taggedAndTimestamped: Route = conditional(tag, timestamp) { completeOk }

  "the conditional directive" should {
    "return OK and the expected headers for an unconditional GET" in {
      Get() ~> taggedAndTimestamped ~> check {
        status shouldEqual Status.Ok
        header[ETag] shouldEqual Some(ETag(tag))
        header[`Last-Modified`] shouldEqual Some(`Last-Modified`(timestamp))
      }
    }
    "return 304 for a GET whose If-None-Match matches" in {
      Get() ~> addHeader(`If-None-Match`(tag)) ~> taggedAndTimestamped ~> check {
        status shouldEqual Status.NotModified
        header[ETag] shouldEqual Some(ETag(tag))
      }
    }
    "return 304 for a GET whose If-None-Match matches weakly" in {
      Get() ~> addHeader(`If-None-Match`(EntityTag("fresh", EntityTag.Weak))) ~> taggedAndTimestamped ~> check {
        status shouldEqual Status.NotModified
      }
    }
    "return 304 for a GET with If-None-Match: *" in {
      Get() ~> addHeader(`If-None-Match`.`*`) ~> taggedAndTimestamped ~> check { status shouldEqual Status.NotModified }
    }
    "return 412 for a PUT whose If-None-Match matches" in {
      Put() ~> addHeader(`If-None-Match`(tag)) ~> taggedAndTimestamped ~> check {
        status shouldEqual Status.PreconditionFailed
      }
    }
    "return OK when If-None-Match does not match" in {
      Get() ~> addHeader(`If-None-Match`(EntityTag("stale"))) ~> taggedAndTimestamped ~> check {
        status shouldEqual Status.Ok
      }
    }
    "return 304 for a GET whose If-Modified-Since is not older than Last-Modified" in {
      Get() ~> addHeader(ifModifiedSince) ~> taggedAndTimestamped ~> check { status shouldEqual Status.NotModified }
    }
    "return OK when the resource changed after If-Modified-Since" in {
      val earlier = HttpDate.unsafeFromEpochSecond(timestamp.epochSecond - 60)
      Get() ~> addHeader(`If-Modified-Since`(earlier)) ~> taggedAndTimestamped ~> check { status shouldEqual Status.Ok }
    }
    "treat an If-Modified-Since of the current second as valid" in {
      val now = HttpDate.unsafeFromEpochSecond(System.currentTimeMillis() / 1000)
      Get() ~> addHeader(`If-Modified-Since`(now)) ~> taggedAndTimestamped ~> check {
        status shouldEqual Status.NotModified
      }
    }
    "ignore If-Modified-Since on a PUT" in {
      Put() ~> addHeader(ifModifiedSince) ~> taggedAndTimestamped ~> check { status shouldEqual Status.Ok }
    }
    "return 412 when If-Match does not match" in {
      Get() ~> addHeader(`If-Match`(EntityTag("stale"))) ~> taggedAndTimestamped ~> check {
        status shouldEqual Status.PreconditionFailed
      }
    }
    "return 412 when If-Match matches only weakly" in {
      Get() ~> addHeader(`If-Match`(EntityTag("fresh", EntityTag.Weak))) ~> taggedAndTimestamped ~> check {
        status shouldEqual Status.PreconditionFailed
      }
    }
    "return OK when If-Match matches strongly" in {
      Get() ~> addHeader(`If-Match`(tag)) ~> taggedAndTimestamped ~> check { status shouldEqual Status.Ok }
    }
    "return 412 when the resource changed after If-Unmodified-Since" in {
      val earlier = HttpDate.unsafeFromEpochSecond(timestamp.epochSecond - 60)
      Get() ~> addHeader(`If-Unmodified-Since`(earlier)) ~> taggedAndTimestamped ~> check {
        status shouldEqual Status.PreconditionFailed
      }
    }
    "return OK when the resource is unchanged since If-Unmodified-Since" in {
      Get() ~> addHeader(ifUnmodifiedSince) ~> taggedAndTimestamped ~> check { status shouldEqual Status.Ok }
    }
    "not override headers the inner route already set" in {
      val other = EntityTag("inner")
      Get() ~> conditional(tag) { mapResponse(_.putHeaders(ETag(other))) { completeOk } } ~> check {
        header[ETag] shouldEqual Some(ETag(other))
      }
    }
    "work with only an ETag" in {
      Get() ~> addHeader(`If-None-Match`(tag)) ~> conditional(tag) { completeOk } ~> check {
        status shouldEqual Status.NotModified
        header[`Last-Modified`] shouldEqual None
      }
    }
    "work with only a Last-Modified date" in {
      Get() ~> addHeader(ifModifiedSince) ~> conditional(timestamp) { completeOk } ~> check {
        status shouldEqual Status.NotModified
        header[ETag] shouldEqual None
      }
    }
    // the cases below are Pekko's CacheConditionDirectivesSpec ones that had no counterpart yet
    val earlier = HttpDate.unsafeFromEpochSecond(timestamp.epochSecond - 60)
    val later = HttpDate.unsafeFromEpochSecond(timestamp.epochSecond + 60)
    def weak: Route = conditional(EntityTag("fresh", EntityTag.Weak), timestamp) { completeOk }
    def timestampedOnly: Route = conditional(timestamp) { completeOk }
    def rangeHeaderSeen: Route = (conditional(tag, timestamp) & optionalHeaderValueByType[Range]) { range =>
      complete(range.isDefined.toString)
    }

    "ignore If-Modified-Since if If-None-Match is defined" in {
      Get() ~> addHeaders(`If-None-Match`(tag), `If-Modified-Since`(earlier)) ~> taggedAndTimestamped ~> check {
        status shouldEqual Status.NotModified
      }
      Get() ~> addHeaders(`If-None-Match`(EntityTag("old")), ifModifiedSince) ~> taggedAndTimestamped ~> check {
        status shouldEqual Status.Ok
      }
    }
    "return NotModified for matching resources whatever else the If-None-Match lists" in {
      Get()                ~> addHeaders(`If-None-Match`(tag, EntityTag("some"), EntityTag("other")), ifModifiedSince) ~>
      taggedAndTimestamped ~> check {
        status shouldEqual Status.NotModified
        header[ETag] shouldEqual Some(ETag(tag))
        header[`Last-Modified`] shouldEqual Some(`Last-Modified`(timestamp))
      }
      Get() ~> addHeaders(`If-None-Match`(tag), `If-Modified-Since`(later)) ~> taggedAndTimestamped ~> check {
        status shouldEqual Status.NotModified
      }
    }
    "return NotModified for matching weak resources" in {
      val weakTag = EntityTag("fresh", EntityTag.Weak)
      Get() ~> addHeader(`If-None-Match`(tag)) ~> weak ~> check {
        status shouldEqual Status.NotModified
        header[ETag] shouldEqual Some(ETag(weakTag))
      }
      Get() ~> addHeader(`If-None-Match`(weakTag)) ~> weak ~> check {
        status shouldEqual Status.NotModified
        header[ETag] shouldEqual Some(ETag(weakTag))
      }
    }
    "return normally for If-Match: * and a matching If-Unmodified-Since on an unsafe method" in {
      Put() ~> addHeader(`If-Match`.`*`) ~> taggedAndTimestamped ~> check {
        status shouldEqual Status.Ok
        header[ETag] shouldEqual Some(ETag(tag))
      }
      Put() ~> addHeader(`If-Match`(tag))   ~> taggedAndTimestamped ~> check { status shouldEqual Status.Ok }
      Put() ~> addHeader(ifUnmodifiedSince) ~> taggedAndTimestamped ~> check { status shouldEqual Status.Ok }
    }
    "return PreconditionFailed without validators for non-matching If-Match/If-Unmodified-Since on a PUT" in {
      Put() ~> addHeader(`If-Match`(EntityTag("old"))) ~> taggedAndTimestamped ~> check {
        status shouldEqual Status.PreconditionFailed
        header[ETag] shouldEqual None
        header[`Last-Modified`] shouldEqual None
      }
      Put() ~> addHeader(`If-Unmodified-Since`(earlier)) ~> taggedAndTimestamped ~> check {
        status shouldEqual Status.PreconditionFailed
      }
    }
    "ignore If-Unmodified-Since if If-Match is defined" in {
      Put() ~> addHeaders(`If-Match`(tag), `If-Unmodified-Since`(earlier)) ~> taggedAndTimestamped ~> check {
        status shouldEqual Status.Ok
      }
      Put() ~> addHeaders(`If-Match`(EntityTag("old")), ifModifiedSince) ~> taggedAndTimestamped ~> check {
        status shouldEqual Status.PreconditionFailed
      }
    }
    "not filter out a Range header if If-Range does match the timestamp" in {
      Get() ~> addHeaders(`If-Range`.LastModified(timestamp): `If-Range`, Range(0, 10)) ~> rangeHeaderSeen ~> check {
        status shouldEqual Status.Ok
        responseAs[String] shouldEqual "true"
      }
    }
    "filter out a Range header if If-Range doesn't match the timestamp" in {
      Get() ~> addHeaders(`If-Range`.LastModified(earlier): `If-Range`, Range(0, 10)) ~> rangeHeaderSeen ~> check {
        status shouldEqual Status.Ok
        responseAs[String] shouldEqual "false"
      }
    }
    "not filter out a Range header if If-Range does match the ETag" in {
      Get() ~> addHeaders(`If-Range`.ETag(tag): `If-Range`, Range(0, 10)) ~> rangeHeaderSeen ~> check {
        status shouldEqual Status.Ok
        responseAs[String] shouldEqual "true"
      }
    }
    "filter out a Range header if If-Range doesn't match the ETag" in {
      Get() ~> addHeaders(`If-Range`.ETag(EntityTag("other")): `If-Range`, Range(0, 10)) ~> rangeHeaderSeen ~> check {
        status shouldEqual Status.Ok
        responseAs[String] shouldEqual "false"
      }
    }
    "ignore If-Match if the ETag is omitted" in {
      Get() ~> addHeader(`If-Match`(EntityTag("old"))) ~> timestampedOnly ~> check {
        status shouldEqual Status.Ok
        header[`Last-Modified`] shouldEqual Some(`Last-Modified`(timestamp))
      }
    }
    "ignore If-None-Match if the ETag is omitted" in {
      Get() ~> addHeader(`If-None-Match`(EntityTag("old"))) ~> timestampedOnly ~> check {
        status shouldEqual Status.Ok
        header[`Last-Modified`] shouldEqual Some(`Last-Modified`(timestamp))
      }
    }
  }
}
