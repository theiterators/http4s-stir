package pl.iterators.stir.server.directives

import org.typelevel.ci.CIString
import pl.iterators.stir.impl.util._

/**
 * The name predicate shared by every redaction channel: header names, query parameter names, JSON keys, form
 * field names and the field names carried by rejections. See the design spec, section 5.3.
 */
object SensitiveNames {

  /** Words that make a name sensitive, derived from the Rails, Django, Sentry, http4s and OpenTelemetry lists. */
  val words: Set[String] = Set(
    "auth", "authorization", "apikey", "certificate", "cookie", "credential", "csrf", "cvc", "cvv", "key", "otp",
    "pass", "passphrase", "passwd", "password", "privatekey", "pwd", "salt", "secret", "session", "sessionid", "sig",
    "signature", "ssn", "token", "xsrf")

  /** A word ending with one of these is sensitive (`accesstoken`, `clientsecret`, `userpassword`). */
  val suffixes: Set[String] = Set("token", "secret", "password", "passwd")

  /**
   * Builds a predicate from entries (normalised like names) and suffix rules (lowercased like names). An entry or a
   * suffix without a letter or digit is rejected here, at construction, never at log time.
   */
  def matching(entries: Set[String], suffixes: Set[String] = SensitiveNames.suffixes): String => Boolean = {
    val phrases: Set[List[String]] = entries.map { entry =>
      val ws = fineWords(entry)
      if (ws.isEmpty)
        throw new IllegalArgumentException(s"sensitive name entry '$entry' contains no letter or digit")
      ws
    }
    val singles: Set[String] = phrases.collect { case w :: Nil => w }
    val multis: List[List[String]] = phrases.filter(_.lengthCompare(1) > 0).toList
    val concatenations: Set[String] = multis.map(_.mkString).toSet
    val sfx: List[String] = suffixes.toList.map { suffix =>
      if (!suffix.exists(isAsciiAlnum))
        throw new IllegalArgumentException(s"sensitive name suffix '$suffix' contains no letter or digit")
      suffix.toRootLowerCase
    }

    def wordMatches(w: String): Boolean =
      singles.contains(w) || concatenations.contains(w) ||
      (w.endsWith("s") && (singles.contains(w.dropRight(1)) || concatenations.contains(w.dropRight(1)))) ||
      sfx.exists(w.endsWith)

    def phraseMatches(seq: List[String]): Boolean = multis.exists(phrase => containsPhrase(seq, phrase))

    name => {
      val coarse = coarseWords(name)
      val fine = fineWords(name)
      coarse.exists(wordMatches) || fine.exists(wordMatches) || phraseMatches(coarse) || phraseMatches(fine)
    }
  }

  /** The default predicate: `matching(words)` with the default suffix rules. */
  val default: String => Boolean = matching(words)

  /** Header names are matched lowercased: HTTP/2 lowercases them on the wire. */
  def headerFrom(p: String => Boolean): CIString => Boolean = name => p(name.toString.toRootLowerCase)

  val header: CIString => Boolean = headerFrom(default)

  private def isAsciiAlnum(c: Char): Boolean =
    (c >= 'a' && c <= 'z') ||
    (c >= 'A' && c <= 'Z') ||
    (c >= '0' && c <= '9')

  /** Lowercase (`Locale.ROOT`), then cut on runs of characters other than ASCII letters and digits. */
  private def coarseWords(name: String): List[String] = splitAlnum(name.toRootLowerCase)

  /** Also cut at each boundary between an ASCII lowercase letter or digit and an ASCII uppercase letter. */
  private def fineWords(name: String): List[String] = {
    val sb = new StringBuilder
    var i = 0
    while (i < name.length) {
      val c = name.charAt(i)
      if (i > 0 && c >= 'A' && c <= 'Z') {
        val prev = name.charAt(i - 1)
        if ((prev >= 'a' && prev <= 'z') || (prev >= '0' && prev <= '9')) sb.append(' ')
      }
      sb.append(c)
      i += 1
    }
    splitAlnum(sb.toString.toRootLowerCase)
  }

  private def splitAlnum(s: String): List[String] = {
    val words = List.newBuilder[String]
    val current = new StringBuilder
    var i = 0
    while (i < s.length) {
      val c = s.charAt(i)
      if (isAsciiAlnum(c)) current.append(c)
      else if (current.nonEmpty) { words += current.toString; current.clear() }
      i += 1
    }
    if (current.nonEmpty) words += current.toString
    words.result()
  }

  /** `phrase` occurs consecutively in `seq`, its last word optionally with a trailing `s`. */
  private def containsPhrase(seq: List[String], phrase: List[String]): Boolean = {
    val last = phrase.last
    val init = phrase.init
    def lastMatches(w: String): Boolean = w == last || (w.endsWith("s") && w.dropRight(1) == last)
    seq.tails.exists { tail =>
      tail.lengthCompare(phrase.length) >= 0 &&
      tail.zip(init).forall { case (w, p) => w == p } &&
      lastMatches(tail(init.length))
    }
  }
}
