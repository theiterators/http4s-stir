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
(`416 Range Not Satisfiable` with `Content-Range: bytes */<length>` when sealed); a response without
`Content-Length` cannot be sliced and rejects the same way. More than 16 ranges reject with a
`TooManyRangesRejection` (416). Combine with `conditional` by putting `conditional` on the outside.

```scala
val route: Route = path("video.mp4") {
  withRangeSupport {
    getFromFile("/var/media/video.mp4")
  }
}
```

`withRangeSupportLimits(rangeCountLimit, rangeCoalescingThreshold)` sets the limit on the number of ranges and the
distance under which neighbouring ranges are merged.
