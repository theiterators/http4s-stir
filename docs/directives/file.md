---
id: directives-file
title: File and Upload Directives
sidebar_position: 17
---

# File and Upload Directives

File and upload directives serve static files from the filesystem or classpath, and handle multipart file uploads. These directives wrap http4s `StaticFile` utilities and multipart parsing into the stir DSL.

## File Serving

### getFromFile

Completes GET requests with the content of the given file. If the file cannot be found or read, the request is rejected.

```scala
val route: Route = path("download") {
  getFromFile("/var/data/report.pdf")
}
```

Both overloads accept a second `Content-Type` argument that replaces the content type derived from the file name, for example `getFromFile("logo.svgz", `Content-Type`(MediaType.image.`svg+xml`))`. The explicit overloads never add `Content-Encoding`; wrap them in `withPrecompressedMediaTypeSupport` for a precompressed file served under an explicit type.

The content type is otherwise chosen by an implicit `ContentTypeResolver` (a `String => Option[Content-Type]` over the file name). The default, `ContentTypeResolver.Default`, behaves like Pekko's: the media type registered for the extension, text types with `charset=UTF-8`, `application/octet-stream` for unknown or missing extensions, and `name.ext.gz` typed as `ext` and served with `Content-Encoding: gzip` (as is `.svgz`); a bare `data.gz` is an unmarked `application/octet-stream`, as in Pekko. `ContentTypeResolver.withDefaultCharset(charset)` changes the charset; a resolver returning `None` leaves the choice to http4s' registry, which sends no `Content-Type` for unknown extensions and types `.gz` as `application/gzip`. `getFromResource`, `getFromDirectory`, `getFromResourceDirectory` and the browseable directory directives use the same resolver.

```scala
import pl.iterators.stir.server.directives.ContentTypeResolver

implicit val resolver: ContentTypeResolver =
  name => if (name.endsWith(".map")) Some(`Content-Type`(MediaType.application.json)) else None
```

The directive also accepts a `java.io.File` instance:

```scala
import java.io.File

val route: Route = path("download") {
  getFromFile(new File("/var/data/report.pdf"))
}
```

Like Pekko's, the file and resource directives handle conditional and ranged requests themselves. A file, and a
resource whose length is known, carries an `ETag` (from its size and modification time) and `Last-Modified`;
`If-None-Match` and
`If-Modified-Since` answer `304 Not Modified` with those validators, `If-Match` and `If-Unmodified-Since` answer
`412 Precondition Failed`, and a GET with a `Range` header gets a `206 Partial Content`: a single range on a file is
read by seeking to it, several ranges are served as `multipart/byteranges` in one pass (see `withRangeSupport` for the
coalescing and limits). An empty file is served as is, without range support.

### getFromResource

Completes GET requests with the content of the given classpath resource. If the resource cannot be found or read, the request is rejected, so a later route alternative can handle it. A second `Content-Type` argument replaces the content type derived from the resource name, as for `getFromFile`. Requests for resource "directories" (paths ending with `/`) are rejected. `getFromResource(name, classLoader)` and `getFromResource(name, contentType, classLoader)` look the resource up through the given class loader instead of http4s' own, for deployments where the application's resources are not visible from the library's loader (Pekko's `classLoader` argument).

```scala
val route: Route = path("static" / "logo") {
  getFromResource("assets/logo.png")
}
```

### getFromDirectory

Completes GET requests with the content of a file underneath the given directory. The unmatched path from the request URI is appended to the directory path. Path traversal is prevented twice: a request whose path has a segment containing `/`, `\` or a NUL byte, or a
`..` segment, is rejected outright; and the canonical path of the resolved file (symbolic links followed) must
lie inside the canonical base directory, compared segment by segment, so a link to a sibling directory such as
`static-private` next to `static` is rejected as well.

```scala
val route: Route = pathPrefix("files") {
  getFromDirectory("/var/www/static/")
}
// GET /files/css/style.css -> serves /var/www/static/css/style.css
```

### getFromResourceDirectory

Same as `getFromDirectory` except that files are served from the classpath rather than the filesystem. `getFromResourceDirectory(directoryName, classLoader)` uses the given class loader.

```scala
val route: Route = pathPrefix("assets") {
  getFromResourceDirectory("public")
}
// GET /assets/js/app.js -> serves classpath resource public/js/app.js
```

### listDirectoryContents

Completes GET requests to a directory below one of the given directories with an HTML index of its contents
(Pekko's layout: directories first, then files with modification time and size), rendered by the in-scope
`DirectoryRenderer`. The default renderer has no footer; `DirectoryRenderer.withVanityFooter` adds Pekko's
"rendered by ... on ..." line, and any `DirectoryListing => Response[IO]` can replace the HTML. Requests to
files, missing or unreadable directories, and path traversal attempts are rejected.

```scala
val route: Route = pathPrefix("browse") {
  listDirectoryContents("/var/www/static")
}
```

### getFromBrowseableDirectory

Serves files from the directory like `getFromDirectory` and lists directories like `listDirectoryContents`.
`getFromBrowseableDirectories(dirs: _*)` merges several directories.

```scala
val route: Route = pathPrefix("files") {
  getFromBrowseableDirectory("/var/www/static")
}
```

## File Uploads

File upload directives process multipart form data. They use `FileInfo` to carry metadata about uploaded parts:

```scala
// FileInfo(fieldName: String, fileName: String, contentType: `Content-Type`)
```

### fileUpload

Extracts a single multipart file part as a tuple of `FileInfo` and `Stream[IO, Byte]`. The request is rejected with `MissingFormFieldRejection` if no part with the given field name exists, or if the matched part lacks a `filename`. A part without `Content-Type` is reported as `application/octet-stream`. If multiple parts share the same name, only the first is used. The whole multipart body is buffered in memory, because the part's stream outlives the decoder; prefer `fileUploadSpooled`, `storeUploadedFile` or `fileUploadAll` for large uploads.

```scala
val route: Route = (post & path("upload")) {
  fileUpload("document") { case (metadata, byteStream) =>
    complete(s"Received file: ${metadata.fileName}")
  }
}
```

### fileUploadSpooled

Like `fileUpload`, but decodes the body with http4s' mixed multipart decoder, which spools parts above 50 MiB (`fileUploadSpooled(field, maxSizeBeforeWrite)` changes the threshold) to a temporary file, and keeps that decoder and its files alive until the inner route has completed and the response body has been consumed. The part's stream can be read inside the route or streamed back as the response. A body with more than 1000 parts (`FileUploadDirectives.MaxParts`) is rejected with a 400 ("Parts limit exceeded"). Up to `maxSizeBeforeWrite` bytes of every part stay on the heap while the body is decoded, so put `withSizeLimit` around upload routes.

```scala
import fs2.io.file.{ Files, Path }
import java.io.File

val route: Route = (post & path("upload")) {
  fileUploadSpooled("document") { case (metadata, byteStream) =>
    // never build the destination from the client-supplied file name
    val destination = Path(File.createTempFile("upload-", ".bin").getAbsolutePath)
    onSuccess(byteStream.through(Files[IO].writeAll(destination)).compile.drain) {
      complete(s"Stored ${metadata.fileName} as $destination")
    }
  }
}
```

### fileUploadAll

Extracts all multipart file parts with the given field name as a sequence of `(FileInfo, Stream[IO, Byte])` tuples. Files are buffered to temporary files on disk, which are deleted once the response body has been consumed (so a part can be streamed back as the response) or as soon as the route rejects, fails or is cancelled; nothing is left for `deleteOnExit`. The part streams are only valid while the request is handled, so copy a part elsewhere before completing if it is needed later. Parts above 64 KiB (`FileUploadDirectives.DiskSpoolThreshold`) are spooled to a temporary file while the multipart body is decoded, so at most 64 KiB of each part is held on the heap (put `withSizeLimit` around upload routes to bound the body as a whole); parts without `Content-Type` are stored as `application/octet-stream`. A body with more than 1000 parts (`FileUploadDirectives.MaxParts`) is rejected with a 400 ("Parts limit exceeded") rather than silently truncated, and each part may carry at most 1 KiB of headers.

```scala
val route: Route = (post & path("upload-many")) {
  fileUploadAll("documents") { files =>
    complete(s"Received ${files.size} files")
  }
}
```

### storeUploadedFile

Streams the bytes of a single multipart file part to disk. The destination file is determined by the provided function. Returns a tuple of `FileInfo` and the destination `File`. If multiple parts share the same name, only the first is used. On write failure, the destination file is deleted. Parts above 64 KiB (`FileUploadDirectives.DiskSpoolThreshold`) are spooled to a temporary file while the multipart body is decoded, so at most 64 KiB of each part is held on the heap (put `withSizeLimit` around upload routes to bound the body as a whole); parts without `Content-Type` are stored as `application/octet-stream`. A body with more than 1000 parts (`FileUploadDirectives.MaxParts`) is rejected with a 400 ("Parts limit exceeded") rather than silently truncated, and each part may carry at most 1 KiB of headers.

```scala
import java.io.File

val route: Route = (post & path("upload")) {
  storeUploadedFile("file", info => new File(s"/tmp/${info.fileName}")) {
    case (metadata, file) =>
      complete(s"Stored ${metadata.fileName} at ${file.getAbsolutePath}")
  }
}
```

### storeUploadedFiles

Streams all multipart file parts with the given field name to disk. Returns a sequence of `(FileInfo, File)` tuples. Parts above 64 KiB (`FileUploadDirectives.DiskSpoolThreshold`) are spooled to a temporary file while the multipart body is decoded, so at most 64 KiB of each part is held on the heap (put `withSizeLimit` around upload routes to bound the body as a whole); parts without `Content-Type` are stored as `application/octet-stream`. A body with more than 1000 parts (`FileUploadDirectives.MaxParts`) is rejected with a 400 ("Parts limit exceeded") rather than silently truncated, and each part may carry at most 1 KiB of headers.

```scala
import java.io.File

val route: Route = (post & path("upload-batch")) {
  storeUploadedFiles("files", info => new File(s"/tmp/${info.fileName}")) { files =>
    complete(s"Stored ${files.size} files")
  }
}
```
