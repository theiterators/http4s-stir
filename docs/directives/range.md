---
id: directives-range
title: Range Directives
sidebar_position: 26
---

# Range Directives

## withRangeSupport

Adds support for [RFC 7233](https://tools.ietf.org/html/rfc7233) byte ranges to the inner route: every GET
response carries `Accept-Ranges: bytes`, and a GET request with a `Range: bytes=...` header receives a
`206 Partial Content` slice of a `200` response, with the `Content-Range` header set. Several ranges are answered
as `multipart/byteranges`; ranges that are at most 80 bytes apart (Pekko's `range-coalescing-threshold`) are
merged into one part, and the parts are sent in ascending order in one pass over the body. Responses other than `200`, and non-GET requests, pass through
untouched.

A request whose ranges all lie outside the entity is rejected with an `UnsatisfiableRangeRejection`
(`416 Range Not Satisfiable` with `Content-Range: bytes */<length>` when sealed). More than 16 ranges reject with a
`TooManyRangesRejection` (416). A response without `Content-Length` cannot be sliced and is served in full, as are
requests whose `Range` header is invalid (a last position before the first; RFC 7233 requires the header to be
ignored) or uses a unit other than `bytes`. Combine with `conditional` by putting `conditional` on the outside.

Two differences from Pekko: the parts of a `multipart/byteranges` response are sent in ascending order rather than in
the order requested, so the body is read only once; and `bytes=-0`, which http4s parses as `bytes=0-`, serves the whole
entity as a `206` where Pekko answers `416`.

```scala
val route: Route = path("video.mp4") {
  withRangeSupport {
    getFromFile("/var/media/video.mp4")
  }
}
```

`withRangeSupportLimits(rangeCountLimit, rangeCoalescingThreshold)` sets the limit on the number of ranges and the
distance under which neighbouring ranges are merged.
