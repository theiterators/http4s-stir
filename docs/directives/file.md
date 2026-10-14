---
id: directives-file
title: File and Upload Directives
sidebar_position: 17
---

# File and Upload Directives

File and upload directives serve static files from the filesystem or classpath, and handle multipart file uploads. These directives wrap http4s `StaticFile` utilities and multipart parsing into the stir DSL.

Note: directory listing is not supported. Requests to directory paths are rejected.

## File Serving

### getFromFile

Completes GET requests with the content of the given file. If the file cannot be found or read, the request is rejected.

```scala
val route: Route = path("download") {
  getFromFile("/var/data/report.pdf")
}
```

Both overloads accept a second `Content-Type` argument that replaces the content type derived from the file name, for example `getFromFile("logo.svgz", `Content-Type`(MediaType.image.`svg+xml`))`.

The directive also accepts a `java.io.File` instance:

```scala
import java.io.File

val route: Route = path("download") {
  getFromFile(new File("/var/data/report.pdf"))
}
```

### getFromResource

Completes GET requests with the content of the given classpath resource. If the resource cannot be found or read, the request is rejected, so a later route alternative can handle it. Requests for resource "directories" (paths ending with `/`) are rejected.

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

Same as `getFromDirectory` except that files are served from the classpath rather than the filesystem.

```scala
val route: Route = pathPrefix("assets") {
  getFromResourceDirectory("public")
}
// GET /assets/js/app.js -> serves classpath resource public/js/app.js
```

## File Uploads

File upload directives process multipart form data. They use `FileInfo` to carry metadata about uploaded parts:

```scala
// FileInfo(fieldName: String, fileName: String, contentType: `Content-Type`)
```

### fileUpload

Extracts a single multipart file part as a tuple of `FileInfo` and `Stream[IO, Byte]`. The request is rejected with `MissingFormFieldRejection` if no part with the given field name exists, or if the matched part lacks a `filename`. A part without `Content-Type` is reported as `application/octet-stream`. If multiple parts share the same name, only the first is used. The whole multipart body is buffered in memory, because the part's stream outlives the decoder; prefer `storeUploadedFile` or `fileUploadAll` for large uploads.

```scala
val route: Route = (post & path("upload")) {
  fileUpload("document") { case (metadata, byteStream) =>
    complete(s"Received file: ${metadata.fileName}")
  }
}
```

### fileUploadAll

Extracts all multipart file parts with the given field name as a sequence of `(FileInfo, Stream[IO, Byte])` tuples. Files are buffered to temporary files on disk and cleaned up after the stream is consumed. Parts above 50 MiB are spooled to a temporary file while the multipart body is decoded, so memory use stays bounded; parts without `Content-Type` are stored as `application/octet-stream`. http4s' multipart decoder keeps at most 50 parts and 1 KiB of headers per part and silently drops the rest.

```scala
val route: Route = (post & path("upload-many")) {
  fileUploadAll("documents") { files =>
    complete(s"Received ${files.size} files")
  }
}
```

### storeUploadedFile

Streams the bytes of a single multipart file part to disk. The destination file is determined by the provided function. Returns a tuple of `FileInfo` and the destination `File`. If multiple parts share the same name, only the first is used. On write failure, the destination file is deleted. Parts above 50 MiB are spooled to a temporary file while the multipart body is decoded, so memory use stays bounded; parts without `Content-Type` are stored as `application/octet-stream`. http4s' multipart decoder keeps at most 50 parts and 1 KiB of headers per part and silently drops the rest.

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

Streams all multipart file parts with the given field name to disk. Returns a sequence of `(FileInfo, File)` tuples. Parts above 50 MiB are spooled to a temporary file while the multipart body is decoded, so memory use stays bounded; parts without `Content-Type` are stored as `application/octet-stream`. http4s' multipart decoder keeps at most 50 parts and 1 KiB of headers per part and silently drops the rest.

```scala
import java.io.File

val route: Route = (post & path("upload-batch")) {
  storeUploadedFiles("files", info => new File(s"/tmp/${info.fileName}")) { files =>
    complete(s"Stored ${files.size} files")
  }
}
```
