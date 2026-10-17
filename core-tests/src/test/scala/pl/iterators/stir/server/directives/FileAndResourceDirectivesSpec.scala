package pl.iterators.stir.server.directives

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.http4s.{ Charset, ContentCoding, Header, HttpDate, MediaType, Response, Status, Uri }
import org.http4s.headers.{ `Content-Encoding`, `Content-Type`, `Last-Modified` }
import org.typelevel.ci._
import org.scalatest.{ Inside, Inspectors }
import pl.iterators.stir.impl.util._

import java.io.File
import java.nio.file.{ Files => NioFiles, Paths }
import java.time.{ LocalDate, ZoneOffset }
import scala.annotation.unused
import scala.util.Properties

class FileAndResourceDirectivesSpec extends RoutingSpec with Inspectors with Inside {
  override implicit def runtime: IORuntime = IORuntime.global

  // operations touch files, can be randomly hit by slowness
//  implicit val routeTestTimeout: RouteTestTimeout = RouteTestTimeout(3.seconds.dilated)

  // need to serve from the src directory, when sbt copies the resource directory over to the
  // target directory it will resolve symlinks in the process
  val testRoot = new File("core-tests/src/test/resources")
  require(testRoot.exists(), s"testRoot was not found at ${testRoot.getAbsolutePath}")

//  override def testConfigSource = super.testConfigSource ++ """
//    pekko.http.routing.range-coalescing-threshold = 1
//  """

  def writeAllText(text: String, file: File) =
    NioFiles.write(file.toPath, text.getBytes("UTF-8"))

  "getFromFile" should {
    "reject non-GET requests" in {
      Put() ~> getFromFile("some") ~> check { handled shouldEqual false }
    }
    "reject requests to non-existing files" in {
      Get() ~> getFromFile("nonExistentFile") ~> check { handled shouldEqual false }
    }
    "reject requests to directories" in {
      Get() ~> getFromFile(Properties.javaHome) ~> check { handled shouldEqual false }
    }
    "return the file content with the MediaType matching the file extension" in {
      val file = File.createTempFile("pekko Http Test", ".PDF")
      try {
        writeAllText("This is PDF", file)
        Get() ~> getFromFile(file.getPath) ~> check {
          mediaType shouldEqual MediaType.parse("application/pdf").getOrElse(fail("Media type could not be parsed"))
          charsetOption shouldEqual None
          responseAs[String] shouldEqual "This is PDF"
          headers.get[`Last-Modified`] should contain(
            `Last-Modified`(HttpDate.unsafeFromEpochSecond(file.lastModified / 1000)))
        }
      } finally {
        file.delete
        ()
      }
    }
    "return the file content with MediaType 'application/octet-stream' on unknown file extensions" in {
      val file = File.createTempFile("pekkoHttpTest", null)
      try {
        writeAllText("Some content", file)
        Get() ~> getFromFile(file) ~> check {
          mediaType shouldEqual MediaType.application.`octet-stream`
          responseAs[String] shouldEqual "Some content"
        }
      } finally {
        file.delete
        ()
      }
    }

//    "return a single range from a file" in {
//      val file = File.createTempFile("pekkoHttpTest", null)
//      try {
//        writeAllText("ABCDEFGHIJKLMNOPQRSTUVWXYZ", file)
//        Get() ~> addHeader(Range(ByteRange(0, 10))) ~> getFromFile(file) ~> check {
//          status shouldEqual StatusCodes.PartialContent
//          headers should contain(`Content-Range`(ContentRange(0, 10, 26)))
//          responseAs[String] shouldEqual "ABCDEFGHIJK"
//        }
//      } finally file.delete
//    }
//
//    "return multiple ranges from a file at once" in {
//      val file = File.createTempFile("pekkoHttpTest", null)
//      try {
//        writeAllText("ABCDEFGHIJKLMNOPQRSTUVWXYZ", file)
//        val rangeHeader = Range(ByteRange(1, 10), ByteRange.suffix(10))
//        Get() ~> addHeader(rangeHeader) ~> getFromFile(file, ContentTypes.`text/plain(UTF-8)`) ~> check {
//          status shouldEqual StatusCodes.PartialContent
//          header[`Content-Range`] shouldEqual None
//          mediaType.withParams(Map.empty) shouldEqual `multipart/byteranges`
//
//          val parts =
//            responseAs[Multipart.ByteRanges].toStrict(1.second.dilated).awaitResult(3.seconds.dilated).strictParts
//          parts.map(_.entity.data.utf8String) should contain theSameElementsAs List("BCDEFGHIJK", "QRSTUVWXYZ")
//        }
//      } finally file.delete
//    }
//
    "use the given content type instead of the one derived from the file name" in {
      val file = File.createTempFile("stirHttpTest", ".svgz")
      try {
        writeAllText("<svg/>", file)
        Get() ~> getFromFile(file, `Content-Type`(MediaType.application.xml)) ~> check {
          mediaType shouldEqual MediaType.application.xml
          responseAs[String] shouldEqual "<svg/>"
        }
      } finally {
        file.delete
        ()
      }
    }
    "properly handle zero-byte files" in {
      val file = File.createTempFile("pekkoHttpTest", null)
      try {
        Get() ~> getFromFile(file) ~> check {
          responseAs[String] shouldEqual ""
        }
      } finally {
        file.delete
        ()
      }
    }

    "support precompressed files with registered MediaType" in {
      val file = File.createTempFile("pekkoHttpTest", ".svgz")
      try {
        writeAllText("123", file)
        Get() ~> withPrecompressedMediaTypeSupport(getFromFile(file)) ~> check {
          mediaType shouldEqual MediaType.parse("image/svg+xml").getOrElse(fail("Media type could not be parsed"))
          header[`Content-Encoding`] shouldEqual Some(`Content-Encoding`(ContentCoding.gzip))
          responseAs[String] shouldEqual "123"
        }
      } finally {
        file.delete
        ()
      }
    }
    "not mark a plain svg as gzip-encoded" in {
      val file = File.createTempFile("pekkoHttpTest", ".svg")
      try {
        writeAllText("<svg/>", file)
        Get() ~> withPrecompressedMediaTypeSupport(getFromFile(file)) ~> check {
          header[`Content-Encoding`] shouldEqual None
        }
      } finally {
        file.delete
        ()
      }
    }
    "keep an existing Content-Encoding on a precompressed file" in {
      val file = File.createTempFile("pekkoHttpTest", ".svgz")
      try {
        writeAllText("123", file)
        val route = respondWithHeader(Header.Raw(ci"Content-Encoding", "br")) { getFromFile(file) }
        Get() ~> withPrecompressedMediaTypeSupport(route) ~> check {
          header[`Content-Encoding`].map(_.contentCoding.coding) shouldEqual Some("br")
        }
      } finally {
        file.delete
        ()
      }
    }

    "mark a .svgz file gzip-encoded without withPrecompressedMediaTypeSupport, like Pekko" in {
      val file = File.createTempFile("pekkoHttpTest", ".svgz")
      try {
        writeAllText("123", file)
        Get() ~> getFromFile(file) ~> check {
          mediaType shouldEqual MediaType.image.`svg+xml`
          header[`Content-Encoding`] shouldEqual Some(`Content-Encoding`(ContentCoding.gzip))
        }
      } finally {
        file.delete
        ()
      }
    }
    "serve a .gz file without an inner extension as an unmarked octet-stream, like Pekko" in {
      val file = File.createTempFile("pekkoHttpTest", ".gz")
      try {
        writeAllText("123", file)
        Get() ~> getFromFile(file) ~> check {
          mediaType shouldEqual MediaType.application.`octet-stream`
          header[`Content-Encoding`] shouldEqual None
        }
      } finally {
        file.delete
        ()
      }
    }
    "serve archive.tar.gz as a gzip-encoded tar" in {
      val file = File.createTempFile("pekkoHttpTest", ".tar.gz")
      try {
        writeAllText("123", file)
        Get() ~> getFromFile(file) ~> check {
          mediaType shouldEqual MediaType.application.`x-tar`
          header[`Content-Encoding`] shouldEqual Some(`Content-Encoding`(ContentCoding.gzip))
        }
      } finally {
        file.delete
        ()
      }
    }
    "never mark a file served explicitly as application/gzip, even under withPrecompressedMediaTypeSupport" in {
      val file = File.createTempFile("pekkoHttpTest", ".js.gz")
      try {
        writeAllText("123", file)
        val archive = `Content-Type`(MediaType.application.gzip)
        Get() ~> withPrecompressedMediaTypeSupport(getFromFile(file, archive)) ~> check {
          mediaType shouldEqual MediaType.application.gzip
          header[`Content-Encoding`] shouldEqual None
        }
      } finally {
        file.delete
        ()
      }
    }
    "support files with registered MediaType and .gz suffix" in {
      val file = File.createTempFile("pekkoHttpTest", ".js.gz")
      try {
        writeAllText("456", file)
        Get() ~> getFromFile(file) ~> check {
          mediaType shouldEqual MediaType.application.javascript
          header[`Content-Encoding`] shouldEqual Some(`Content-Encoding`(ContentCoding.gzip))
          responseAs[String] shouldEqual "456"
        }
      } finally {
        file.delete
        ()
      }
    }
  }

  "ContentTypeResolver.Default and withDefaultCharset" should {
    "give text files the UTF-8 charset and binary files none" in {
      ContentTypeResolver.Default("a.txt") shouldEqual Some(`Content-Type`(MediaType.text.plain, Charset.`UTF-8`))
      ContentTypeResolver.Default("a.png") shouldEqual Some(`Content-Type`(MediaType.image.png))
    }
    "fall back to application/octet-stream for unknown or missing extensions" in {
      ContentTypeResolver.Default("a.unknownext") shouldEqual Some(`Content-Type`(MediaType.application.`octet-stream`))
      ContentTypeResolver.Default("README") shouldEqual Some(`Content-Type`(MediaType.application.`octet-stream`))
    }
    "resolve a .gz file to the type of the inner extension" in {
      ContentTypeResolver.Default("app.js.gz") shouldEqual
      Some(`Content-Type`(MediaType.application.javascript, Charset.`UTF-8`))
      ContentTypeResolver.Default("data.gz") shouldEqual Some(`Content-Type`(MediaType.application.`octet-stream`))
      FileAndResourceDirectives.isPrecompressed("data.gz", ContentTypeResolver.Default("data.gz")) shouldEqual false
      FileAndResourceDirectives.isPrecompressed("app.js.gz", ContentTypeResolver.Default("app.js.gz")) shouldEqual true
    }
    "use the given charset for text files with withDefaultCharset" in {
      ContentTypeResolver.withDefaultCharset(Charset.`ISO-8859-1`)("a.txt") shouldEqual
      Some(`Content-Type`(MediaType.text.plain, Charset.`ISO-8859-1`))
      Get("fileA.txt")                                                ~> getFromDirectory(testRoot.getCanonicalPath + "/someDir")(
        ContentTypeResolver.withDefaultCharset(Charset.`ISO-8859-1`)) ~> check {
        charsetOption shouldEqual Some(Charset.`ISO-8859-1`)
      }
    }
  }

  "getFromDirectory" should {
    def _getFromDirectory(directory: String) = getFromDirectory(new File(testRoot, directory).getCanonicalPath)

    "reject non-GET requests" in {
      Put() ~> _getFromDirectory("someDir") ~> check { handled shouldEqual false }
    }
    "reject requests to non-existing files" in {
      Get("nonExistentFile") ~> _getFromDirectory("subDirectory") ~> check { handled shouldEqual false }
    }
    "reject requests to directories" in {
      Get("sub") ~> _getFromDirectory("someDir") ~> check { handled shouldEqual false }
    }
    "reject path traversal attempts" in {
      def route(uri: String) =
        mapRequestContext(_.copy(unmatchedPath = Uri.Path.unsafeFromString("/" + uri))) {
          _getFromDirectory("someDir/sub")
        }

      Get() ~> route("file.html") ~> check { handled shouldEqual true }

      def shouldReject(prefix: String, @unused warnings: Int = 1) =
        try {
//          EventFilter.warning(
//            start = "File-system path for base",
//            occurrences = warnings).intercept {
          Get() ~> route(prefix + "fileA.txt") ~> check {
            handled shouldEqual false
          }
//          }
        } catch {
          case err: AssertionError => throw new AssertionError(s"Failure for prefix $prefix", err)
        }

      shouldReject("../")
      shouldReject("%5c../")
      shouldReject("%2e%2e%2f")
      shouldReject("%2e%2e/") // resolved
      shouldReject("..%2f")
      shouldReject("%2e%2e%5c")
      shouldReject("%2e%2e\\")
      shouldReject("..\\")
      shouldReject("\\")
      shouldReject("%5c")
      shouldReject("..%5c")

      // FIXME these don't cause log warnings for some reason
      shouldReject("..%255c", warnings = 0)
      shouldReject("..%c0%af", warnings = 0)
      shouldReject("..%c1%9c", warnings = 0)
    }
    "reject the whole request when a segment is suspicious instead of dropping the segment" in {
      // "..\\" is dropped today, leaving someDir/sub/file.html, which exists and is served
      Get("..%5C/file.html") ~> _getFromDirectory("someDir/sub") ~> check { handled shouldEqual false }
    }
    "reject a path containing a NUL byte" in {
      Get("na%00ive.txt") ~> _getFromDirectory("someDir") ~> check { handled shouldEqual false }
    }
    "not serve files from a sibling directory whose name starts with the base directory's name" in {
      val root = NioFiles.createTempDirectory("stir-traversal")
      val base = NioFiles.createDirectory(root.resolve("base"))
      val sibling = NioFiles.createDirectory(root.resolve("base-private"))
      val secret = NioFiles.write(sibling.resolve("secret.txt"), "secret".getBytes("UTF-8"))
      val link = NioFiles.createSymbolicLink(base.resolve("link"), sibling)
      try {
        Get("link/secret.txt") ~> getFromDirectory(base.toString) ~> check { handled shouldEqual false }
      } finally {
        List(link, secret, sibling, base, root).foreach(p => NioFiles.deleteIfExists(p))
      }
    }
    "return the file content with the MediaType matching the file extension" in {
      Get("fileA.txt") ~> _getFromDirectory("someDir") ~> check {
        mediaType shouldEqual MediaType.parse("text/plain").getOrElse(fail("Media type could not be parsed"))
        charsetOption shouldEqual Some(Charset.`UTF-8`)
        responseAs[String] shouldEqual "123"
        val lastModified = new File(testRoot, "someDir/fileA.txt").lastModified()
        headers.get[`Last-Modified`] should contain(
          `Last-Modified`(HttpDate.unsafeFromEpochSecond(lastModified / 1000)))
      }
    }
    "return the file content with the MediaType matching the file extension (unicode chars in filename)" in {
      Get("sample%20sp%c3%a4ce.PDF") ~> _getFromDirectory("sübdir") ~> check {
        mediaType shouldEqual MediaType.parse("application/pdf").getOrElse(fail("Media type could not be parsed"))
        charsetOption shouldEqual None
        responseAs[String] shouldEqual "This is PDF"
        val lastModified = new File(testRoot, "sübdir/sample späce.PDF").lastModified()
        headers.get[`Last-Modified`] should contain(
          `Last-Modified`(HttpDate.unsafeFromEpochSecond(lastModified / 1000)))
      }
    }
    "not follow symbolic links to find a file" in {
//      EventFilter.warning(pattern = ".* points to a location that is not part of .*", occurrences = 1).intercept {
      Get("linked-dir/empty.pdf") ~> _getFromDirectory("dirWithLink") ~> check {
        handled shouldBe false
        /* TODO: resurrect following links under an option
          responseAs[String] shouldEqual "123"
          mediaType shouldEqual `application/pdf`*/
      }
//      }
    }
  }

  "an implicit ContentTypeResolver" should {
    implicit val xyzIsText: ContentTypeResolver =
      fileName => if (fileName.endsWith(".xyz")) Some(`Content-Type`(MediaType.text.plain)) else None

    "override the content type derived from the file name in getFromDirectory" in {
      Get("sample.xyz") ~> getFromDirectory(testRoot.getCanonicalPath) ~> check {
        mediaType shouldEqual MediaType.text.plain
        responseAs[String] shouldEqual "XyZ"
      }
    }
    "override the content type in getFromResource" in {
      Get() ~> getFromResource("sample.xyz") ~> check { mediaType shouldEqual MediaType.text.plain }
    }
    "leave other files to http4s" in {
      Get() ~> getFromResource("sample.html") ~> check {
        mediaType shouldEqual MediaType.parse("text/html").getOrElse(fail("Media type could not be parsed"))
      }
    }
    "lose against an explicit content type" in {
      Get() ~> getFromResource("sample.xyz", `Content-Type`(MediaType.application.xml)) ~> check {
        mediaType shouldEqual MediaType.application.xml
      }
    }
  }

  "getFromResource" should {
    "reject non-GET requests" in {
      Put() ~> getFromResource("some") ~> check { handled shouldEqual false }
    }
    "reject requests to non-existing resources" in {
      Get() ~> getFromResource("nonExistingResource") ~> check { handled shouldEqual false }
    }
    "reject requests to directory resources" in {
      Get() ~> getFromResource("someDir") ~> check { handled shouldEqual false }
    }
    "reject requests to directory resources with trailing slash" in {
      Get() ~> getFromResource("someDir/") ~> check { handled shouldEqual false }
    }
    "let the next alternative handle the request when the resource is missing" in {
      Get() ~> (getFromResource("nonExistingResource") ~ complete("fallback")) ~> check {
        responseAs[String] shouldEqual "fallback"
      }
    }
    "reject requests to directory resources from an archive " in {
      Get() ~> getFromResource("com/typesafe/config") ~> check { handled shouldEqual false }
    }
    "reject requests to directory resources from an archive with trailing slash" in {
      Get() ~> getFromResource("com/typesafe/config/") ~> check { handled shouldEqual false }
    }
//    "return the resource from an archive with spaces and umlauts" in {
//      // contained within lib/jar with späces.jar
//      Get() ~> getFromResource("test-resource.txt") ~> check {
////        mediaType shouldEqual MediaType.parse("text/plain").getOrElse(fail("Media type could not be parsed"))
//        responseAs[String] shouldEqual "I have spaces, too!"
//      }
//    }
    "return the resource content with the MediaType matching the file extension" in {
      val route = getFromResource("sample.html")

      def runCheck() =
        Get() ~> route ~> check {
          mediaType shouldEqual MediaType.parse("text/html").getOrElse(fail("Media type could not be parsed"))
          forAtLeast(1, headers.get[`Last-Modified`].toList) { h =>
            inside(h) {
              case `Last-Modified`(dt) =>
                HttpDate.unsafeFromInstant(LocalDate.of(2011, 7, 1).atTime(0, 0, 0).toInstant(
                  ZoneOffset.UTC)) should be < dt
                dt.epochSecond should be < System.currentTimeMillis() / 1000
            }
          }
          responseAs[String] shouldEqual "<p>Lorem ipsum!</p>"
        }

      runCheck()
      runCheck() // additional test to check that no internal state is kept
    }
//    "return the resource content from an archive" in {
//      Get() ~> getFromResource("com/typesafe/config/Config.class") ~> check {
//        mediaType shouldEqual `application/octet-stream`
//        responseEntity.toStrict(1.second.dilated).awaitResult(
//          1.second.dilated).data.asByteBuffer.getInt shouldEqual 0xCAFEBABE
//      }
//    }
    "use the given content type instead of the one derived from the resource name" in {
      Get() ~> getFromResource("sample.xyz", `Content-Type`(MediaType.text.plain)) ~> check {
        mediaType shouldEqual MediaType.text.plain
        responseAs[String] shouldEqual "XyZ"
      }
    }
    "return the file content with MediaType 'application/octet-stream' on unknown file extensions" in {
      Get() ~> getFromResource("sample.unknownext") ~> check {
        mediaType shouldEqual MediaType.application.`octet-stream`
        responseAs[String] shouldEqual "XyZ"
      }
    }
    "properly handle zero-byte files" in {
      Get() ~> getFromResource("subDirectory/fileA.txt") ~> check {
//        mediaType shouldEqual NoMediaType
        responseAs[String] shouldEqual ""
      }
    }
  }

  "getFromResourceDirectory" should {
    "reject requests to non-existing resources" in {
      Get("not/found") ~> getFromResourceDirectory("subDirectory") ~> check { handled shouldEqual false }
    }
    val verify = check {
      mediaType shouldEqual MediaType.parse("application/pdf").getOrElse(fail("Media type could not be parsed"))
      responseAs[String] shouldEqual "123"
    }
    "return the resource content with the MediaType matching the file extension - example 1" in {
      Get("empty.pdf") ~> getFromResourceDirectory("subDirectory") ~> verify
    }
    "return the resource content with the MediaType matching the file extension - example 2" in {
      Get("empty.pdf") ~> getFromResourceDirectory("subDirectory/") ~> verify
    }
    "return the resource content with the MediaType matching the file extension - example 3" in {
      Get("subDirectory/empty.pdf") ~> getFromResourceDirectory("") ~> verify
    }
//    "return the resource content from an archive" in {
//      Get("Config.class") ~> getFromResourceDirectory("com/typesafe/config") ~> check {
//        mediaType shouldEqual `application/octet-stream`
//        responseEntity.toStrict(1.second.dilated).awaitResult(
//          1.second.dilated).data.asByteBuffer.getInt shouldEqual 0xCAFEBABE
//      }
//    }
    "reject requests to directory resources" in {
      Get() ~> getFromResourceDirectory("subDirectory") ~> check { handled shouldEqual false }
    }
    "reject requests to directory resources with trailing slash" in {
      Get() ~> getFromResourceDirectory("subDirectory/") ~> check { handled shouldEqual false }
    }
    "reject requests to sub directory resources" in {
      Get("sub") ~> getFromResourceDirectory("someDir") ~> check { handled shouldEqual false }
    }
    "reject requests to sub directory resources with trailing slash" in {
      Get("sub/") ~> getFromResourceDirectory("someDir") ~> check { handled shouldEqual false }
    }
    "reject requests to directory resources from an archive" in {
      Get() ~> getFromResourceDirectory("com/typesafe/config") ~> check { handled shouldEqual false }
    }
    "reject requests to directory resources from an archive with trailing slash" in {
      Get() ~> getFromResourceDirectory("com/typesafe/config/") ~> check { handled shouldEqual false }
    }
  }

  "listDirectoryContents" should {
    val base = testRoot.getCanonicalPath
    NioFiles.createDirectories(Paths.get(base, "subDirectory", "emptySub"))
    def eraseDateTime(s: String) = s.replaceAll("""\d\d\d\d-\d\d-\d\d \d\d:\d\d:\d\d""", "xxxx-xx-xx xx:xx:xx")

    "properly render a simple directory" in {
      Get() ~> listDirectoryContents(base + "/someDir") ~> check {
        eraseDateTime(responseAs[String]) shouldEqual prep {
          """<html>
            |<head><title>Index of /</title></head>
            |<body>
            |<h1>Index of /</h1>
            |<hr>
            |<pre>
            |<a href="/sub/">sub/</a>             xxxx-xx-xx xx:xx:xx
            |<a href="/fileA.txt">fileA.txt</a>        xxxx-xx-xx xx:xx:xx            3  B
            |<a href="/fileB.xml">fileB.xml</a>        xxxx-xx-xx xx:xx:xx            0  B
            |</pre>
            |<hr>
            |</body>
            |</html>
            |"""
        }
      }
    }
    "properly render a sub directory" in {
      Get("/sub/") ~> listDirectoryContents(base + "/someDir") ~> check {
        eraseDateTime(responseAs[String]) shouldEqual prep {
          """<html>
            |<head><title>Index of /sub/</title></head>
            |<body>
            |<h1>Index of /sub/</h1>
            |<hr>
            |<pre>
            |<a href="/">../</a>
            |<a href="/sub/file.html">file.html</a>        xxxx-xx-xx xx:xx:xx            0  B
            |</pre>
            |<hr>
            |</body>
            |</html>
            |"""
        }
      }
    }
    "properly render the union of several directories" in {
      Get() ~> listDirectoryContents(base + "/someDir", base + "/subDirectory") ~> check {
        eraseDateTime(responseAs[String]) shouldEqual prep {
          """<html>
            |<head><title>Index of /</title></head>
            |<body>
            |<h1>Index of /</h1>
            |<hr>
            |<pre>
            |<a href="/emptySub/">emptySub/</a>        xxxx-xx-xx xx:xx:xx
            |<a href="/sub/">sub/</a>             xxxx-xx-xx xx:xx:xx
            |<a href="/empty.pdf">empty.pdf</a>        xxxx-xx-xx xx:xx:xx            3  B
            |<a href="/fileA.txt">fileA.txt</a>        xxxx-xx-xx xx:xx:xx            3  B
            |<a href="/fileB.xml">fileB.xml</a>        xxxx-xx-xx xx:xx:xx            0  B
            |</pre>
            |<hr>
            |</body>
            |</html>
            |"""
        }
      }
    }
    "properly render an empty sub directory with vanity footer" in {
      Get("/emptySub/") ~> listDirectoryContents(base + "/subDirectory")(DirectoryRenderer.withVanityFooter) ~> check {
        eraseDateTime(responseAs[String]) shouldEqual prep {
          """<html>
            |<head><title>Index of /emptySub/</title></head>
            |<body>
            |<h1>Index of /emptySub/</h1>
            |<hr>
            |<pre>
            |<a href="/">../</a>
            |</pre>
            |<hr>
            |<div style="width:100%;text-align:right;color:gray">
            |<small>rendered by <a href="https://github.com/theiterators/http4s-stir">http4s-stir</a> on xxxx-xx-xx xx:xx:xx</small>
            |</div>
            |</body>
            |</html>
            |"""
        }
      }
    }
    "properly render an empty top-level directory" in {
      Get() ~> listDirectoryContents(base + "/subDirectory/emptySub") ~> check {
        eraseDateTime(responseAs[String]) shouldEqual prep {
          """<html>
            |<head><title>Index of /</title></head>
            |<body>
            |<h1>Index of /</h1>
            |<hr>
            |<pre>
            |(no files)
            |</pre>
            |<hr>
            |</body>
            |</html>
            |"""
        }
      }
    }
    "properly render a simple directory with a path prefix" in {
      Get("/files/") ~> pathPrefix("files")(listDirectoryContents(base + "/someDir")) ~> check {
        eraseDateTime(responseAs[String]) shouldEqual prep {
          """<html>
            |<head><title>Index of /files/</title></head>
            |<body>
            |<h1>Index of /files/</h1>
            |<hr>
            |<pre>
            |<a href="/files/sub/">sub/</a>             xxxx-xx-xx xx:xx:xx
            |<a href="/files/fileA.txt">fileA.txt</a>        xxxx-xx-xx xx:xx:xx            3  B
            |<a href="/files/fileB.xml">fileB.xml</a>        xxxx-xx-xx xx:xx:xx            0  B
            |</pre>
            |<hr>
            |</body>
            |</html>
            |"""
        }
      }
    }
    "properly render a sub directory with a path prefix" in {
      Get("/files/sub/") ~> pathPrefix("files")(listDirectoryContents(base + "/someDir")) ~> check {
        eraseDateTime(responseAs[String]) shouldEqual prep {
          """<html>
            |<head><title>Index of /files/sub/</title></head>
            |<body>
            |<h1>Index of /files/sub/</h1>
            |<hr>
            |<pre>
            |<a href="/files/">../</a>
            |<a href="/files/sub/file.html">file.html</a>        xxxx-xx-xx xx:xx:xx            0  B
            |</pre>
            |<hr>
            |</body>
            |</html>
            |"""
        }
      }
    }
    "reject requests to file resources" in {
      Get() ~> listDirectoryContents(base + "/subDirectory/empty.pdf") ~> check { handled shouldEqual false }
    }
    "render a symlinked directory as a directory" in {
      Get("/") ~> listDirectoryContents(base + "/dirWithLink") ~> check {
        responseAs[String] should include("""<a href="/linked-dir/">linked-dir/</a>""")
      }
    }
    "reject non-GET requests" in {
      Put() ~> listDirectoryContents(base + "/someDir") ~> check { handled shouldEqual false }
    }
    "reject path traversal attempts" in {
      Get("/../") ~> listDirectoryContents(base + "/someDir/sub") ~> check { handled shouldEqual false }
    }
    "serve a listing over a real server" in {
      Get("/") ~!> listDirectoryContents(base + "/someDir") ~> check {
        mediaType shouldEqual MediaType.text.html
        responseAs[String] should include("""<a href="/fileA.txt">fileA.txt</a>""")
      }
    }
    "list a directory requested without a trailing slash as if it had one" in {
      Get("/sub") ~> listDirectoryContents(base + "/someDir") ~> check {
        val body = responseAs[String]
        body should include("<title>Index of /sub/</title>")
        body should include("""<a href="/">../</a>""")
        body should include("""<a href="/sub/file.html">file.html</a>""")
      }
    }
    "reject requests to a missing directory" in {
      Get() ~> listDirectoryContents(base + "/doesNotExist") ~> check { handled shouldEqual false }
    }
    "render non-ASCII names and declare the UTF-8 charset" in {
      Get("/") ~> listDirectoryContents(base) ~> check {
        header[`Content-Type`].flatMap(_.charset) shouldEqual Some(Charset.`UTF-8`)
        responseAs[String] should include("""<a href="/sübdir/">sübdir/</a>""")
      }
      Get("/s%C3%BCbdir/") ~> listDirectoryContents(base) ~> check {
        responseAs[String] should include("""<a href="/sübdir/sample späce.PDF">sample späce.PDF</a>""")
      }
    }
    "escape file names in the links and the text" in {
      val dir = NioFiles.createTempDirectory("stirListing")
      NioFiles.write(dir.resolve("""a&b<c>"d.txt"""), "123".getBytes("UTF-8"))
      Get("/") ~> listDirectoryContents(dir.toString) ~> check {
        responseAs[String] should include(
          """<a href="/a&amp;b&lt;c&gt;&quot;d.txt">a&amp;b&lt;c&gt;&quot;d.txt</a>""")
      }
    }
    "list a dangling symlink as a file instead of failing" in {
      val dir = NioFiles.createTempDirectory("stirListing")
      NioFiles.createSymbolicLink(dir.resolve("dangling"), dir.resolve("nowhere"))
      NioFiles.write(dir.resolve("real.txt"), "123".getBytes("UTF-8"))
      Get("/") ~> listDirectoryContents(dir.toString) ~> check {
        status shouldEqual Status.Ok
        val body = responseAs[String]
        body should include("""<a href="/dangling">dangling</a>""")
        body should include("""<a href="/real.txt">real.txt</a>""")
      }
    }
    "render through a custom renderer" in {
      implicit val plainText: DirectoryRenderer = listing =>
        Response[IO]().withEntity(listing.files.sortBy(_.name).map { entry =>
          if (entry.isDirectory) entry.name + "/" else s"${entry.name}:${entry.size}"
        }.mkString(listing.path + "\n", "\n", ""))
      Get("/sub/") ~> listDirectoryContents(base + "/someDir") ~> check {
        mediaType shouldEqual MediaType.text.plain
        responseAs[String] shouldEqual "/sub/\nfile.html:0"
      }
      Get() ~> listDirectoryContents(base + "/someDir") ~> check {
        responseAs[String] shouldEqual "/\nfileA.txt:3\nfileB.xml:0\nsub/"
      }
    }
  }

  "getFromBrowseableDirectory" should {
    val base = testRoot.getCanonicalPath
    "serve files" in {
      Get("/fileA.txt") ~> getFromBrowseableDirectory(base + "/someDir") ~> check {
        responseAs[String] shouldEqual "123"
      }
    }
    "list directories" in {
      Get("/sub/") ~> getFromBrowseableDirectory(base + "/someDir") ~> check {
        responseAs[String] should include("""<a href="/sub/file.html">file.html</a>""")
      }
    }
    "serve files and merge listings from several directories" in {
      val route = getFromBrowseableDirectories(base + "/someDir", base + "/subDirectory")
      Get("/fileB.xml") ~> route ~> check { status shouldEqual Status.Ok }
      Get("/empty.pdf") ~> route ~> check { responseAs[Array[Byte]].length shouldEqual 3 }
      Get("/")          ~> route ~> check {
        val body = responseAs[String]
        body should include("""<a href="/sub/">sub/</a>""")
        body should include("""<a href="/emptySub/">emptySub/</a>""")
        body should include("""<a href="/empty.pdf">empty.pdf</a>""")
      }
      Get("/missing/") ~> route ~> check { handled shouldEqual false }
    }
  }

  def prep(s: String) = s.stripMarginWithNewline("\n")
}
