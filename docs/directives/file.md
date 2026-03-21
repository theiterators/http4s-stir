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

The directive also accepts a `java.io.File` instance:

```scala
import java.io.File

val route: Route = path("download") {
  getFromFile(new File("/var/data/report.pdf"))
}
```

### getFromResource

Completes GET requests with the content of the given classpath resource. If the resource cannot be found, a 404 response is returned. Requests for resource "directories" (paths ending with `/`) are rejected.

```scala
val route: Route = path("static" / "logo") {
  getFromResource("assets/logo.png")
}
```

### getFromDirectory

Completes GET requests with the content of a file underneath the given directory. The unmatched path from the request URI is appended to the directory path. Path traversal attacks are prevented by validating that the resolved path is a descendant of the base directory.

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

Extracts a single multipart file part as a tuple of `FileInfo` and `Stream[IO, Byte]`. The request is rejected with `MissingFormFieldRejection` if no part with the given field name exists, or if the matched part lacks a `filename` or `content-type`. If multiple parts share the same name, only the first is used.

```scala
val route: Route = (post & path("upload")) {
  fileUpload("document") { case (metadata, byteStream) =>
    complete(s"Received file: ${metadata.fileName}")
  }
}
```

### fileUploadAll

Extracts all multipart file parts with the given field name as a sequence of `(FileInfo, Stream[IO, Byte])` tuples. Files are buffered to temporary files on disk and cleaned up after the stream is consumed.

```scala
val route: Route = (post & path("upload-many")) {
  fileUploadAll("documents") { files =>
    complete(s"Received ${files.size} files")
  }
}
```

### storeUploadedFile

Streams the bytes of a single multipart file part to disk. The destination file is determined by the provided function. Returns a tuple of `FileInfo` and the destination `File`. If multiple parts share the same name, only the first is used. On write failure, the destination file is deleted.

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

Streams all multipart file parts with the given field name to disk. Returns a sequence of `(FileInfo, File)` tuples.

```scala
import java.io.File

val route: Route = (post & path("upload-batch")) {
  storeUploadedFiles("files", info => new File(s"/tmp/${info.fileName}")) { files =>
    complete(s"Stored ${files.size} files")
  }
}
```
