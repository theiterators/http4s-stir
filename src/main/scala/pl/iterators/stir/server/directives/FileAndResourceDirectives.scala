package pl.iterators.stir.server.directives

import cats.effect.IO
import fs2.io.file.Path
import org.http4s.Status.{ InternalServerError, NotFound }
import org.http4s.{ Response, StaticFile, Uri }
import pl.iterators.stir.server.Route

import java.io.File

/**
 * @groupname fileandresource File and resource directives
 * @groupprio fileandresource 70
 */
trait FileAndResourceDirectives {
  import MethodDirectives._
  import FileAndResourceDirectives._
  import RouteDirectives._
  import BasicDirectives._

  /**
   * Completes GET requests with the content of the given file.
   * If the file cannot be found or read the request is rejected.
   *
   * @group fileandresource
   */
  def getFromFile(fileName: String): Route =
    get {
      val file = new File(fileName)
      if (file.isFile && file.canRead) {
        extractRequest { request =>
          complete {
            StaticFile.fromPath(Path.fromNioPath(file.toPath), Some(request)).getOrElse(
              Response[IO](InternalServerError))
          }
        }
      } else reject
    }

  /**
   * Completes GET requests with the content of the given class-path resource.
   * If the resource cannot be found or read the Route rejects the request.
   *
   * @group fileandresource
   */
  def getFromResource(resourceName: String): Route =
    if (!resourceName.endsWith("/"))
      get {
        extractRequest { request =>
          complete {
            StaticFile.fromResource(resourceName, Some(request)).getOrElse(Response[IO](NotFound))
          }
        }
      }
    else reject // don't serve the content of resource "directories"

  /**
   * Completes GET requests with the content of a file underneath the given directory.
   * If the file cannot be read the Route rejects the request.
   *
   * @group fileandresource
   */
  def getFromDirectory(directoryName: String): Route =
    extractUnmatchedPath { unmatchedPath =>
      safeDirectoryChildPath(withTrailingSlash(directoryName), unmatchedPath) match {
        case "" => reject
        case fileName =>
          println(fileName)
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
  def getFromResourceDirectory(directoryName: String): Route = {
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

  private def safeJoinPaths(base: String, path: Uri.Path, separator: Char): String = {
    base + path.segments.map(_.decoded()).filterNot(s => s.contains('/') || s.contains('\\') || s == "..").mkString(
      separator.toString)
  }

  /**
   * Check against directory traversal attempts by making sure that the final is a "true child"
   * of the given base directory.
   *
   * Returns "" if the finalPath is suspicious and the canonical path otherwise.
   */
  private def checkIsSafeDescendant(basePath: String, finalPath: String): String = {
    val baseFile = new File(basePath)
    val finalFile = new File(finalPath)
    val canonicalFinalPath = finalFile.getCanonicalPath

    if (!canonicalFinalPath.startsWith(baseFile.getCanonicalPath)) {
      ""
    } else canonicalFinalPath
  }
}
