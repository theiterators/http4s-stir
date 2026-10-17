package pl.iterators.stir.server.directives

import cats.data.OptionT
import cats.effect.{ IO, SyncIO }
import cats.syntax.all._
import fs2.io.file.Files
import fs2.io.file.Path
import cats.data.NonEmptyList
import org.http4s.{
  Charset,
  ContentCoding,
  EntityTag,
  HttpDate,
  MediaType,
  Method,
  RangeUnit,
  Request,
  Response,
  StaticFile,
  Status,
  Uri
}
import org.http4s.headers.{ `Content-Encoding`, `Content-Type`, `Last-Modified`, Range }
import org.typelevel.vault.Key
import pl.iterators.stir.impl.util._
import pl.iterators.stir.server.{
  Directive0,
  Rejection,
  Route,
  RouteConcatenation,
  RouteResult,
  TooManyRangesRejection,
  UnsatisfiableRangeRejection
}

import java.io.{ File, IOException }

/**
 * @groupname fileandresource File and resource directives
 * @groupprio fileandresource 70
 */
trait FileAndResourceDirectives {
  import MethodDirectives._
  import FileAndResourceDirectives._
  import IODirectives._
  import RouteDirectives._
  import BasicDirectives._
  import CacheConditionDirectives._
  import RangeDirectives._

  /**
   * Completes GET requests with the content of the given file, typed by the in-scope [[ContentTypeResolver]]
   * ([[ContentTypeResolver.Default]], Pekko's rules). If the file cannot be found or read the request is rejected.
   *
   * @group fileandresource
   */
  def getFromFile(fileName: String)(implicit resolver: ContentTypeResolver): Route = getFromFile(new File(fileName))

  /**
   * Completes GET requests with the content of the given file, served with the given content type instead of the
   * one derived from the file name. If the file cannot be found or read the request is rejected.
   *
   * @group fileandresource
   */
  def getFromFile(fileName: String, contentType: `Content-Type`): Route = getFromFile(new File(fileName), contentType)

  /**
   * Completes GET requests with the content of the given file.
   * If the file cannot be found or read the request is rejected.
   *
   * @group fileandresource
   */
  def getFromFile(file: File)(implicit resolver: ContentTypeResolver): Route = {
    val contentType = resolver(file.getName)
    serveFile(file, contentType, gzipped = isPrecompressed(file.getName, contentType))
  }

  /**
   * Completes GET requests with the content of the given file, served with the given content type instead of the
   * one derived from the file name. If the file cannot be found or read the request is rejected.
   *
   * @group fileandresource
   */
  def getFromFile(file: File, contentType: `Content-Type`): Route = serveFile(file, Some(contentType), gzipped = false)

  private def serveFile(file: File, contentType: Option[`Content-Type`], gzipped: Boolean): Route =
    get {
      if (file.isFile && file.canRead) {
        val length = file.length
        val lastModified = file.lastModified
        val tag = entityTag(length, lastModified)
        val path = Path(file.getAbsolutePath)
        val etagCalculator: Path => IO[String] = _ => IO.pure(tag.tag)
        // `conditional` adds the clamped Last-Modified and the ETag itself
        def typed(response: Response[IO]): Response[IO] =
          contentType.fold(response)(response.withContentType).removeHeader[`Last-Modified`]
        def whole = StaticFile.fromPath(path, StaticFile.DefaultBufferSize, None, etagCalculator).map(typed)
        // conditional outside, ranges inside, as Pekko's getFromFile; http4s is given no request so that it does
        // not run its own If-None-Match / If-Modified-Since check with a different ETag
        conditionalFor(tag, lastModified) {
          rangeSupportFor(length) {
            extractRequest { request =>
              singleSatisfiableRange(request, length) match {
                case Some(range) => // one range: seek instead of reading and discarding the bytes before it
                  serve(file.getName, gzipped,
                    StaticFile.fromPath(path, range.start, range.end, StaticFile.DefaultBufferSize, None,
                      etagCalculator).flatMap { response =>
                      // the file may have shrunk since its length was read; then let withRangeSupport decide
                      if (response.contentLength.contains(range.length))
                        OptionT.pure[IO](
                          typed(response).withStatus(Status.PartialContent).putHeaders(range.contentRange(length)))
                      else whole
                    })
                case None => serve(file.getName, gzipped, whole)
              }
            }
          }
        }
      } else reject
    }

  /**
   * Completes GET requests with the content of the given class-path resource.
   * If the resource cannot be found or read the Route rejects the request, so a later alternative can handle it.
   *
   * @group fileandresource
   */
  def getFromResource(resourceName: String)(implicit resolver: ContentTypeResolver): Route =
    getFromResource(resourceName, None)(resolver)

  private def getFromResource(resourceName: String, classLoader: Option[ClassLoader])(
      resolver: ContentTypeResolver): Route = {
    val contentType = resolver(resourceName)
    serveResource(resourceName, contentType, gzipped = isPrecompressed(resourceName, contentType), classLoader)
  }

  /**
   * Completes GET requests with the content of the given class-path resource, served with the given content type
   * instead of the one derived from the resource name. If the resource cannot be found or read the Route rejects
   * the request.
   *
   * @group fileandresource
   */
  def getFromResource(resourceName: String, contentType: `Content-Type`): Route =
    serveResource(resourceName, Some(contentType), gzipped = false, classLoader = None)

  /**
   * Completes GET requests with the content of the given class-path resource looked up through the given class
   * loader (http4s' own loader otherwise), served with the given content type. Useful where the application's
   * resources are not visible from the library's class loader.
   *
   * @group fileandresource
   */
  def getFromResource(resourceName: String, contentType: `Content-Type`, classLoader: ClassLoader): Route =
    serveResource(resourceName, Some(contentType), gzipped = false, classLoader = Some(classLoader))

  /**
   * Completes GET requests with the content of the given class-path resource looked up through the given class
   * loader and typed by the in-scope [[ContentTypeResolver]].
   *
   * @group fileandresource
   */
  def getFromResource(resourceName: String, classLoader: ClassLoader)(implicit resolver: ContentTypeResolver): Route =
    getFromResource(resourceName, Some(classLoader))(resolver)
  private def serveResource(resourceName: String, contentType: Option[`Content-Type`], gzipped: Boolean,
      classLoader: Option[ClassLoader]): Route =
    if (!resourceName.endsWith("/"))
      get { ctx =>
        // http4s opens the resource's stream as soon as it builds the response, so the first response is only a
        // probe for the validators and is released at once; the content is opened again inside the inner route,
        // after `conditional` and the range pre-checks have decided that a body will be sent. http4s is given no
        // request so that it does not run its own If-None-Match / If-Modified-Since check.
        def open = StaticFile.fromResource[IO](resourceName, None, classloader = classLoader)
        open.value.flatMap {
          case None        => IO.pure(RouteResult.Rejected(Nil))
          case Some(probe) =>
            probe.body.take(1).compile.drain *> {
              val length = probe.contentLength
              val lastModifiedMillis = probe.headers.get[`Last-Modified`].map(_.date.epochSecond * 1000)
              val tag = length.map(l => entityTag(l, lastModifiedMillis.getOrElse(0L)))
              val content =
                open.map(found => contentType.fold(found)(found.withContentType).removeHeader[`Last-Modified`])
              // the pre-check reads the request `conditional` hands down: an If-Range mismatch has removed Range
              val ranged: Route = length.filter(_ > 0) match {
                case Some(l) => extractRequest { request =>
                    unservableRanges(request, l).fold(withRangeSupport(serve(resourceName, gzipped, content)))(
                      reject(_))
                  }
                case None => serve(resourceName, gzipped, content)
              }
              // the clamped Last-Modified, as for files (Pekko's conditionalFor)
              val clamped = lastModifiedMillis.map(millis =>
                HttpDate.unsafeFromEpochSecond(math.min(millis, System.currentTimeMillis()) / 1000))
              conditional(tag, clamped)(ranged)(ctx)
            }
        }
      }
    else reject // don't serve the content of resource "directories"

  /**
   * Completes GET requests to a directory below one of the given directories with a listing of its contents,
   * rendered by the in-scope [[DirectoryRenderer]] (Pekko's HTML index page by default). The listings of several
   * directories are merged. Requests to files, to missing or unreadable directories, and path traversal attempts
   * are rejected.
   *
   * @group fileandresource
   */
  def listDirectoryContents(directories: String*)(implicit renderer: DirectoryRenderer): Route =
    get {
      extractRequestContext { ctx =>
        extractMatchedPath { matched =>
          val prefixPath = matched.toString
          val remainingPath = ctx.unmatchedPath
          val pathString = withTrailingSlash(safeJoinPaths("/", remainingPath, '/'))
          val candidates = directories.toList.flatMap { dir =>
            safeDirectoryChildPath(withTrailingSlash(dir), remainingPath) match {
              case ""       => None
              case fileName => Some(Path(fileName))
            }
          }
          onSuccess(readableDirectories(candidates)) { dirs =>
            if (dirs.isEmpty) reject
            else
              onSuccess(directoryEntries(dirs)) { entries =>
                complete(renderer.render(DirectoryListing(prefixPath + pathString, isRoot = pathString == "/",
                  entries)))
              }
          }
        }
      }
    }

  /**
   * Same as `getFromBrowseableDirectories` with only one directory.
   *
   * @group fileandresource
   */
  def getFromBrowseableDirectory(directory: String)(implicit renderer: DirectoryRenderer,
      resolver: ContentTypeResolver): Route =
    getFromBrowseableDirectories(directory)

  /**
   * Serves the content of the given directories as a file system browser, i.e. files are sent and directories
   * served as browseable listings.
   *
   * @group fileandresource
   */
  def getFromBrowseableDirectories(directories: String*)(implicit renderer: DirectoryRenderer,
      resolver: ContentTypeResolver): Route = {
    import RouteConcatenation._
    directories.map(getFromDirectory(_)).reduceLeft(_ ~ _) ~ listDirectoryContents(directories: _*)
  }

  /**
   * Completes GET requests with the content of a file underneath the given directory.
   * If the file cannot be read the Route rejects the request.
   *
   * @group fileandresource
   */
  def getFromDirectory(directoryName: String)(implicit resolver: ContentTypeResolver): Route =
    extractUnmatchedPath { unmatchedPath =>
      safeDirectoryChildPath(withTrailingSlash(directoryName), unmatchedPath) match {
        case ""       => reject
        case fileName =>
          getFromFile(fileName)
      }
    }

  /**
   * Same as "getFromDirectory" except that the file is not fetched from the file system but rather from a
   * "resource directory".
   * If the requested resource is itself a directory or cannot be found or read the Route rejects the request.
   *
   * @group fileandresource
   */
  def getFromResourceDirectory(directoryName: String)(implicit resolver: ContentTypeResolver): Route =
    getFromResourceDirectory(directoryName, None)(resolver)

  /**
   * Same as `getFromResourceDirectory` with the resources looked up through the given class loader.
   *
   * @group fileandresource
   */
  def getFromResourceDirectory(directoryName: String, classLoader: ClassLoader)(
      implicit resolver: ContentTypeResolver): Route =
    getFromResourceDirectory(directoryName, Some(classLoader))(resolver)

  private def getFromResourceDirectory(directoryName: String, classLoader: Option[ClassLoader])(
      resolver: ContentTypeResolver): Route = {
    val base = if (directoryName.isEmpty) "" else withTrailingSlash(directoryName)
    extractUnmatchedPath { path =>
      safeJoinPaths(base, path, separator = '/') match {
        case ""           => reject
        case resourceName => getFromResource(resourceName, classLoader)(resolver)
      }
    }
  }
}

object FileAndResourceDirectives extends FileAndResourceDirectives {
  import BasicDirectives._
  import CacheConditionDirectives._
  import RangeDirectives._

  private def withTrailingSlash(path: String): String = if (path.endsWith("/")) path else path + '/'

  private def readableDirectories(paths: List[Path]): IO[List[Path]] =
    paths.filterA(path =>
      Files[IO].isDirectory(path).flatMap(dir => if (dir) Files[IO].isReadable(path) else IO.pure(false)))

  private def directoryEntries(directories: List[Path]): IO[List[DirectoryListing.Entry]] =
    directories.flatTraverse { directory =>
      Files[IO].list(directory).evalMap { child =>
        Files[IO].getBasicFileAttributes(child, followLinks = true)
          .handleErrorWith(_ => Files[IO].getBasicFileAttributes(child, followLinks = false)).map(attributes =>
            DirectoryListing.Entry(child.fileName.toString, attributes.isDirectory, attributes.size,
              attributes.lastModifiedTime.toMillis))
      }.compile.toList
    }

  /** The file or resource name a response was served from, for `withPrecompressedMediaTypeSupport`. */
  private[stir] val ServedNameKey: Key[String] = Key.newKey[SyncIO, String].unsafeRunSync()

  /** Pekko's `conditionalFor`: an ETag from length and modification time, and a `Last-Modified` not in the future. */
  private def conditionalFor(tag: EntityTag, lastModified: Long): Directive0 =
    conditional(tag, HttpDate.unsafeFromEpochSecond(math.min(lastModified, System.currentTimeMillis()) / 1000))

  /** http4s' ETag format for a file (`lastModifiedHex-sizeHex`), so both layers agree on the validator. */
  private def entityTag(length: Long, lastModified: Long): EntityTag =
    EntityTag(s"${lastModified.toHexString}-${length.toHexString}")

  /** Pekko serves an empty file without range support; a `Range` on it would otherwise be unsatisfiable. */
  private def rangeSupportFor(length: Long): Directive0 = if (length > 0) withRangeSupport else pass

  /**
   * The rejection `withRangeSupport` would produce for the request without looking at the response: too many ranges,
   * or no satisfiable one. Decided before a resource stream is opened, so a rejected request never leaks one.
   */
  private def unservableRanges(request: Request[IO], length: Long): Option[Rejection] =
    if (request.method != Method.GET) None
    else request.headers.get[Range].flatMap {
      case Range(unit, ranges) if unit == RangeUnit.Bytes && ranges.forall(isValid) =>
        if (ranges.size > DefaultRangeCountLimit) Some(TooManyRangesRejection(DefaultRangeCountLimit))
        else if (ranges.exists(isSatisfiable(length))) None
        else Some(UnsatisfiableRangeRejection(ranges.toList, length))
      case _ => None
    }

  /** The one byte range of a GET that can be served by seeking, when the header holds exactly one valid satisfiable range. */
  private def singleSatisfiableRange(request: Request[IO], length: Long): Option[IndexRange] =
    if (request.method != Method.GET) None
    else request.headers.get[Range].collect {
      case Range(unit, NonEmptyList(single, Nil))
          if unit == RangeUnit.Bytes && isValid(single) && isSatisfiable(length)(single) =>
        indexRange(length)(single)
    }

  /**
   * Completes with the static content when http4s found it and rejects (with no rejections) otherwise. A
   * precompressed file (`.gz`, `.svgz`) is marked `Content-Encoding: gzip` unless an encoding is already set, as
   * Pekko's `withPrecompressedMediaTypeSupport` does for its `Gzipped` media types.
   */
  private def serve(name: String, gzipped: Boolean, content: OptionT[IO, Response[IO]]): Route = _ =>
    content.value.map {
      case Some(response) =>
        val named = response.withAttributes(response.attributes.insert(ServedNameKey, name))
        val encoded =
          if (gzipped && named.headers.get[`Content-Encoding`].isEmpty)
            named.putHeaders(`Content-Encoding`(ContentCoding.gzip))
          else named
        RouteResult.Complete(encoded)
      case None => RouteResult.Rejected(Nil)
    }

  /**
   * Whether a file is gzipped content of its resolved type, as Pekko's `Gzipped` media types: a `.svgz`, or a
   * `name.ext.gz` with an inner extension (`app.js.gz` is JavaScript). A bare `data.gz` or a file typed as an archive
   * (`application/gzip`, `x-gzip`) is served as it is, without `Content-Encoding`.
   */
  private[stir] def isPrecompressed(name: String, contentType: Option[`Content-Type`]): Boolean = {
    val lower = name.toRootLowerCase
    val gzippedName = lower.endsWith(".svgz") || (lower.endsWith(".gz") && lower.dropRight(3).contains('.'))
    gzippedName && contentType.exists(ct => !ArchiveSubtypes.contains(ct.mediaType.subType))
  }

  /** Media subtypes that are compressed archives in their own right and must never be marked `Content-Encoding`. */
  private val ArchiveSubtypes: Set[String] = Set("gzip", "x-gzip", "x-compressed", "x-gtar", "gnutar", "zip")

  /**
   * Given a base directory and a (Uri) path, returns a path to a location contained in the base directory,
   * while checking that no path traversal is possible. Path traversal is prevented by two individual measures:
   *  - A path segment must not be ".." and must not contain slashes or backslashes that may carry special meaning in
   *    file-system paths. This logic is intentionally a bit conservative as it might also prevent legitimate access
   *    to files containing one of those characters on a file-system that allows those characters in file names
   *    (e.g. backslash on posix).
   *  - Resulting paths are checked to be "contained" in the base directory. "Contained" means that the canonical location
   *    of the file (according to File.getCanonicalPath) has the canonical version of the basePath as a prefix. The exact
   *    semantics depend on the implementation of `File.getCanonicalPath` that may or may not resolve symbolic links and
   *    similar structures depending on the OS and the JDK implementation of file system accesses.
   */
  private def safeDirectoryChildPath(basePath: String, path: Uri.Path, separator: Char = File.separatorChar): String =
    safeJoinPaths(basePath, path, separator) match {
      case ""   => ""
      case path => checkIsSafeDescendant(basePath, path)
    }

  /**
   * Joins the base path and the (Uri) path segments. Returns "" when any segment could change the meaning of the
   * file-system path: a `/` or `\\` inside a segment, a NUL byte, or a `..` segment.
   */
  private def safeJoinPaths(base: String, path: Uri.Path, separator: Char): String = {
    val segments = path.segments.map(_.decoded())
    if (segments.exists(isSuspicious)) "" else base + segments.mkString(separator.toString)
  }

  private def isSuspicious(segment: String): Boolean =
    segment.contains('/') || segment.contains('\\') || segment.contains('\u0000') || segment == ".."

  /**
   * Check against directory traversal attempts by making sure that the final path is a "true child" of the given
   * base directory: the canonical path of the file must start with the canonical path of the base directory
   * segment by segment (so `/srv/base-private` is not inside `/srv/base`). Symbolic links are resolved by
   * `File.getCanonicalPath`, so a link pointing outside the base is rejected.
   *
   * Returns "" if the finalPath is suspicious or cannot be canonicalised and the canonical path otherwise.
   */
  private def checkIsSafeDescendant(basePath: String, finalPath: String): String =
    try {
      val base = canonicalSegments(new File(basePath))
      val canonicalFinalPath = new File(finalPath).getCanonicalPath
      if (canonicalSegments(new File(canonicalFinalPath)).startsWith(base)) canonicalFinalPath else ""
    } catch {
      case _: IOException => ""
    }

  private def canonicalSegments(file: File): Seq[String] =
    file.getCanonicalPath.split(File.separatorChar).toSeq.filter(_.nonEmpty)
}

/**
 * Resolves the `Content-Type` of a served file or resource from its name. `None` leaves the choice to http4s,
 * which looks the extension up in its media type registry and sends no `Content-Type` for an unknown extension.
 */
trait ContentTypeResolver {
  def apply(fileName: String): Option[`Content-Type`]
}

object ContentTypeResolver {

  /**
   * Pekko's default: the media type registered for the extension, text types with the `UTF-8` charset, unknown or
   * missing extensions `application/octet-stream`, and `name.ext.gz` the type of `ext` (served with
   * `Content-Encoding: gzip`).
   */
  implicit val Default: ContentTypeResolver = withDefaultCharset(Charset.`UTF-8`)

  /** Like [[Default]] with the given charset for text types. */
  def withDefaultCharset(charset: Charset): ContentTypeResolver = fileName => {
    def forExtension(ext: String): MediaType =
      MediaType.forExtension(ext.toRootLowerCase).getOrElse(MediaType.application.`octet-stream`)
    val lastDot = fileName.lastIndexOf('.')
    val mediaType =
      if (lastDot < 0) MediaType.application.`octet-stream`
      else fileName.substring(lastDot + 1).toRootLowerCase match {
        case "gz" => fileName.lastIndexOf('.', lastDot - 1) match {
            case -1 => MediaType.application.`octet-stream`
            case x  => forExtension(fileName.substring(x + 1, lastDot))
          }
        case ext => forExtension(ext)
      }
    Some(if (mediaType.binary) `Content-Type`(mediaType) else `Content-Type`(mediaType, charset))
  }
}
