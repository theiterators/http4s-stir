package pl.iterators.stir.server.directives

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import pl.iterators.stir.server.directives.DirectoryListing.Entry

import java.time.{ Instant, ZoneOffset }
import java.util.Locale
import java.time.format.DateTimeFormatter
import scala.util.Random

class DirectoryRendererSpec extends AnyWordSpec with Matchers {

  "isoLikeDateTime" should {
    val reference = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)

    "render the epoch, leap days, year ends and far years in UTC" in {
      val cases = List(
        0L -> "1970-01-01 00:00:00",
        -1L -> "1969-12-31 23:59:59",
        -2208988800000L -> "1900-01-01 00:00:00",
        951782400000L -> "2000-02-29 00:00:00",
        1709164800000L -> "2024-02-29 00:00:00",
        1704067199999L -> "2023-12-31 23:59:59",
        2147483647000L -> "2038-01-19 03:14:07",
        4102444800000L -> "2100-01-01 00:00:00",
        253402300799000L -> "9999-12-31 23:59:59")
      cases.foreach { case (millis, expected) =>
        withClue(s"$millis: ") { DirectoryRenderer.isoLikeDateTime(millis) shouldEqual expected }
      }
    }
    "agree with java.time on random instants between 1811 and 2286" in {
      val random = new Random(42)
      val samples = List.fill(20000)(random.between(-5000000000000L, 10000000000000L))
      samples.foreach { millis =>
        withClue(s"$millis: ") {
          DirectoryRenderer.isoLikeDateTime(millis) shouldEqual reference.format(Instant.ofEpochMilli(millis))
        }
      }
    }
  }

  "humanReadableByteCount" should {
    "render sizes the way Pekko does, SI units with one decimal" in {
      val cases = List(
        0L -> "0  B", 3L -> "3  B", 999L -> "999  B",
        1000L -> "1.0 kB", 1024L -> "1.0 kB", 1234L -> "1.2 kB", 999949L -> "999.9 kB",
        // Pekko's rounding quirk: just under a unit boundary rounds up to 1000.0 of the smaller unit
        999950L -> "1000.0 kB", 999999L -> "1000.0 kB",
        1000000L -> "1.0 MB", 1500000L -> "1.5 MB", 123456789012L -> "123.5 GB",
        1000000000000000000L -> "1.0 EB", Long.MaxValue -> "9.2 EB")
      cases.foreach { case (bytes, expected) =>
        withClue(s"$bytes: ") { DirectoryRenderer.humanReadableByteCount(bytes) shouldEqual expected }
      }
    }
  }

  "the formatters under a foreign default locale" should {
    def withDefaultLocale[A](tag: String)(body: => A): A = {
      val previous = Locale.getDefault
      Locale.setDefault(Locale.forLanguageTag(tag))
      try body
      finally Locale.setDefault(previous)
    }
    "still use a decimal point for sizes under tr-TR" in withDefaultLocale("tr-TR") {
      DirectoryRenderer.humanReadableByteCount(1500000L) shouldEqual "1.5 MB"
      DirectoryRenderer.humanReadableByteCount(1234L) shouldEqual "1.2 kB"
    }
    "still use ASCII digits for dates under ar-EG" in withDefaultLocale("ar-EG") {
      DirectoryRenderer.isoLikeDateTime(0L) shouldEqual "1970-01-01 00:00:00"
      DirectoryRenderer.isoLikeDateTime(1709164800000L) shouldEqual "2024-02-29 00:00:00"
    }
  }

  "renderHtml" should {
    "escape the path and the entry names in both the link and the text" in {
      val html = DirectoryRenderer.renderHtml(
        DirectoryListing("/x&y/", isRoot = false, List(Entry("""a&b<c>"d'.txt""", isDirectory = false, 3, 0L))),
        renderVanityFooter = false)
      html should include("<title>Index of /x&amp;y/</title>")
      html should
      include("""<a href="/x&amp;y/a&amp;b&lt;c&gt;&quot;d&#39;.txt">a&amp;b&lt;c&gt;&quot;d&#39;.txt</a>""")
      (html should not).include("<c>")
    }
    "keep the first of several entries with the same name" in {
      val html = DirectoryRenderer.renderHtml(
        DirectoryListing("/", isRoot = true,
          List(
            Entry("same.txt", isDirectory = false, 3, 0L), Entry("same.txt", isDirectory = false, 1000, 0L))),
        renderVanityFooter = false)
      html.split("same.txt").length - 1 shouldEqual 2 // once in the href, once as the text
      html should include("3  B")
      (html should not).include("1.0 kB")
    }
    "list directories before files, each group sorted by name, and pad the names to one column" in {
      val html = DirectoryRenderer.renderHtml(
        DirectoryListing("/", isRoot = true,
          List(
            Entry("zeta.txt", isDirectory = false, 0, 0L), Entry("alpha", isDirectory = true, 0, 0L),
            Entry("beta.txt", isDirectory = false, 0, 0L), Entry("omega", isDirectory = true, 0, 0L))),
        renderVanityFooter = false)
      val lines = html.linesIterator.filter(_.startsWith("<a href")).toList
      lines.map(_.takeWhile(_ != '>').drop("<a href=\"".length).init) shouldEqual
      List("/alpha/", "/omega/", "/beta.txt", "/zeta.txt")
      // the dates line up in the rendered text, where the link markup is invisible
      lines.map(_.replaceAll("<[^>]*>", "").indexOf("1970-")).distinct shouldEqual List(16)
    }
  }
}
