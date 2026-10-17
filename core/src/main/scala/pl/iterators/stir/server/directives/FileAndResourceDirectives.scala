package pl.iterators.stir.server.directives

import cats.data.OptionT
import cats.effect.{ IO, SyncIO }
import cats.syntax.all._
import fs2.io.file.Files
import fs2.io.file.Path
import org.http4s.{ Charset, ContentCoding, MediaType, Response, StaticFile, Uri }
import org.http4s.headers.{ `Content-Encoding`, `Content-Type` }
import org.typelevel.vault.Key
import pl.iterators.stir.impl.util._
import pl.iterators.stir.server.{ Route, RouteConcatenation, RouteResult }

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

  /**
   * Completes GET requests with the content of the given file, typed by the in-scope [[ContentTypeResolver]]
   * (http4s' extension lookup by default). If the file cannot be found or read the request is rejected.
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
      if (file.isFile && file.canRead)
        extractRequest { request =>
          serve(file.getName, gzipped,
            StaticFile.fromPath(Path(file.getAbsolutePath), Some(request)).map(response =>
              contentType.fold(response)(response.withContentType)))
        }
      else reject
    }

  /**
   * Completes GET requests with the content of the given class-path resource.
   * If the resource cannot be found or read the Route rejects the request, so a later alternative can handle it.
   *
   * @group fileandresource
   */
  def getFromResource(resourceName: String)(implicit resolver: ContentTypeResolver): Route = {
    val contentType = resolver(resourceName)
    serveResource(resourceName, contentType, gzipped = isPrecompressed(resourceName, contentType))
  }

  /**
   * Completes GET requests with the content of the given class-path resource, served with the given content type
   * instead of the one derived from the resource name. If the resource cannot be found or read the Route rejects
   * the request.
   *
   * @group fileandresource
   */
  def getFromResource(resourceName: String, contentType: `Content-Type`): Route =
    serveResource(resourceName, Some(contentType), gzipped = false)

  private def serveResource(resourceName: String, contentType: Option[`Content-Type`], gzipped: Boolean): Route =
    if (!resourceName.endsWith("/"))
      get {
        extractRequest { request =>
          serve(resourceName, gzipped,
            StaticFile.fromResource(resourceName, Some(request)).map(response =>
              contentType.fold(response)(response.withContentType)))
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
  def getFromResourceDirectory(directoryName: String)(implicit resolver: ContentTypeResolver): Route = {
    val base = if (directoryName.isEmpty) "" else withTrailingSlash(directoryName)
    extractUnmatchedPath { path =>
      safeJoinPaths(base, path, separator = '/') match {
        case ""           => reject
        case resourceName => getFromResource(resourceName)
      }
    }
  }
}

object FileAndResourceDirectives extends FileAndResourceDirectives {
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
   * A `.gz` or `.svgz` file is gzipped content of the resolved type (`app.js.gz` is JavaScript), unless the resolver
   * typed it as an archive (`application/gzip`), in which case the bytes are served as they are.
   */
  private[stir] def isPrecompressed(name: String, contentType: Option[`Content-Type`]): Boolean = {
    val lower = name.toRootLowerCase
    (lower.endsWith(".gz") || lower.endsWith(".svgz")) &&
    contentType.exists(ct => ct.mediaType.subType != "gzip" && ct.mediaType.subType != "x-gzip")
  }

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
