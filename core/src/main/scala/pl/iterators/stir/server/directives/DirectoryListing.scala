package pl.iterators.stir.server.directives

import cats.effect.IO
import org.http4s.{ Charset, MediaType, Response }
import org.http4s.headers.`Content-Type`

/**
 * The contents of one or more directories, to be rendered by a [[DirectoryRenderer]].
 *
 * @param path   the request path of the listing, always ending with a slash (used for the links)
 * @param isRoot whether the listing is the root of the served tree (no `../` link)
 * @param files  the entries of the listed directories
 */
final case class DirectoryListing(path: String, isRoot: Boolean, files: Seq[DirectoryListing.Entry])

object DirectoryListing {

  /** One directory entry; `lastModifiedMillis` is the modification time as epoch milliseconds. */
  final case class Entry(name: String, isDirectory: Boolean, size: Long, lastModifiedMillis: Long)
}

/** Renders a [[DirectoryListing]] into a response; the default renders Pekko's HTML index page. */
trait DirectoryRenderer {
  def render(listing: DirectoryListing): Response[IO]
}

object DirectoryRenderer {

  /** Pekko's HTML index page without the vanity footer. */
  implicit val default: DirectoryRenderer = html(renderVanityFooter = false)

  /** Pekko's HTML index page with the "rendered by ... on ..." footer. */
  val withVanityFooter: DirectoryRenderer = html(renderVanityFooter = true)

  def html(renderVanityFooter: Boolean): DirectoryRenderer = listing =>
    Response[IO]().withEntity(renderHtml(listing, renderVanityFooter))
      .withContentType(`Content-Type`(MediaType.text.html, Charset.`UTF-8`))

  private val template =
    """<html>
      |<head><title>Index of $</title></head>
      |<body>
      |<h1>Index of $</h1>
      |<hr>
      |<pre>
      |$</pre>
      |<hr>$
      |<div style="width:100%;text-align:right;color:gray">
      |<small>rendered by <a href="https://github.com/theiterators/http4s-stir">http4s-stir</a> on $</small>
      |</div>$
      |</body>
      |</html>
      |""".stripMargin.split('$')

  private def escapeHtml(s: String): String = {
    val sb = new java.lang.StringBuilder(s.length + 16)
    s.foreach {
      case '&'  => sb.append("&amp;")
      case '<'  => sb.append("&lt;")
      case '>'  => sb.append("&gt;")
      case '"'  => sb.append("&quot;")
      case '\'' => sb.append("&#39;")
      case c    => sb.append(c)
    }
    sb.toString
  }

  private[stir] def renderHtml(listing: DirectoryListing, renderVanityFooter: Boolean): String = {
    val DirectoryListing(path, isRoot, files) = listing
    val sorted = files.sortBy(_.name)
    val deduped = sorted.zipWithIndex.collect {
      case (entry, ix) if ix == 0 || sorted(ix - 1).name != entry.name => entry
    }
    val (directories, regularFiles) = deduped.partition(_.isDirectory)
    def maxNameLength(entries: Seq[DirectoryListing.Entry]) = if (entries.isEmpty) 0 else entries.map(_.name.length).max
    val maxNameLen = math.max(maxNameLength(directories) + 1, maxNameLength(regularFiles))
    val sb = new java.lang.StringBuilder
    val escapedPath = escapeHtml(path)
    sb.append(template(0)).append(escapedPath).append(template(1)).append(escapedPath).append(template(2))
    if (!isRoot) {
      val secondToLastSlash = path.lastIndexOf('/', path.lastIndexOf('/', path.length - 1) - 1)
      sb.append("<a href=\"").append(escapeHtml(path.substring(0, secondToLastSlash))).append("/\">../</a>\n")
    }
    def start(name: String): java.lang.StringBuilder = {
      val escapedName = escapeHtml(name)
      sb.append("<a href=\"").append(escapedPath).append(escapedName).append("\">").append(escapedName).append("</a>")
      var padding = maxNameLen - name.length
      while (padding > 0) {
        sb.append(' ')
        padding -= 1
      }
      sb
    }
    directories.foreach { entry =>
      start(entry.name + '/').append("        ").append(isoLikeDateTime(entry.lastModifiedMillis)).append('\n')
    }
    regularFiles.foreach { entry =>
      val size = humanReadableByteCount(entry.size)
      start(entry.name).append("        ").append(isoLikeDateTime(entry.lastModifiedMillis))
      sb.append("                ".substring(size.length)).append(size).append('\n')
    }
    if (isRoot && files.isEmpty) sb.append("(no files)\n")
    sb.append(template(3))
    if (renderVanityFooter)
      sb.append(template(4)).append(isoLikeDateTime(System.currentTimeMillis())).append(template(5))
    sb.append(template(6)).toString
  }

  /** `1.2 kB` style, SI units, as Pekko renders file sizes. */
  private[stir] def humanReadableByteCount(bytes: Long): String =
    if (bytes >= 1000) {
      val exp = (math.log(bytes.toDouble) / math.log(1000)).toInt
      val prefix = "kMGTPE".charAt(exp - 1)
      f"${bytes / math.pow(1000, exp.toDouble)}%.1f ${prefix}B"
    } else s"$bytes  B"

  /** `yyyy-MM-dd HH:mm:ss` in UTC, computed without java.time so it works on every platform. */
  private[stir] def isoLikeDateTime(millis: Long): String = {
    val totalSeconds = Math.floorDiv(millis, 1000L)
    val days = Math.floorDiv(totalSeconds, 86400L)
    val seconds = Math.floorMod(totalSeconds, 86400L)
    // civil date from days since 1970-01-01 (Howard Hinnant's algorithm)
    val z = days + 719468
    val era = Math.floorDiv(z, 146097L)
    val dayOfEra = z - era * 146097
    val yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36524 - dayOfEra / 146096) / 365
    val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
    val mp = (5 * dayOfYear + 2) / 153
    val day = dayOfYear - (153 * mp + 2) / 5 + 1
    val month = if (mp < 10) mp + 3 else mp - 9
    val year = yearOfEra + era * 400 + (if (month <= 2) 1 else 0)
    f"$year%04d-$month%02d-$day%02d ${seconds / 3600}%02d:${seconds % 3600 / 60}%02d:${seconds % 60}%02d"
  }
}
