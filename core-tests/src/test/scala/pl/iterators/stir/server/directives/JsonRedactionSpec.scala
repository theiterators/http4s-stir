package pl.iterators.stir.server.directives

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import pl.iterators.stir.server.directives.JsonRedaction._

class JsonRedactionSpec extends AnyWordSpec with Matchers {
  val secret: String => Boolean = Set("password", "secret", "token")
  def deny(input: String, truncated: Boolean = false): Result = scan(input, truncated, DenyList(secret))
  def keep(input: String, truncated: Boolean = false): Result = scan(input, truncated, Keep)

  "the scanner in DenyList mode" should {
    "mask values under sensitive keys whatever their type" in {
      deny("""{"user":"alice","password":"x","n":{"token":12,"ok":true},"secret":[1,{"a":2}],"t":null}""") shouldEqual
      Result(
        """{"user":"alice","password":"REDACTED","n":{"token":"REDACTED","ok":true},"secret":"REDACTED","t":null}""",
        None, incomplete = false)
    }
    "check keys at every depth, inside arrays too" in {
      deny("""[{"password":"x"},[{"token":1}]]""").text shouldEqual
      """[{"password":"REDACTED"},[{"token":"REDACTED"}]]"""
    }
    "compare keys after unescaping and emit keys verbatim" in {
      deny("{\"pass\\u0077ord\":\"x\",\"a\\\"b\":\"y\"}").text shouldEqual
      "{\"pass\\u0077ord\":\"REDACTED\",\"a\\\"b\":\"y\"}"
    }
    "drop insignificant whitespace" in {
      deny("{\n  \"user\" : \"alice\" ,\n  \"password\" : \"x\"\n}\n") shouldEqual
      Result("""{"user":"alice","password":"REDACTED"}""", None, incomplete = false)
    }
    "keep string content verbatim, escapes and raw control characters included" in {
      deny("{\"note\":\"a\\\"q\\u00e9\\n\tb\"}").text shouldEqual "{\"note\":\"a\\\"q\\u00e9\\n\tb\"}"
    }
    "accept unicode escapes spelled with ASCII hex digits of either case" in {
      deny("{\"a\":\"\\u0041\\u00e9\\uABCD\"}") shouldEqual
      Result("{\"a\":\"\\u0041\\u00e9\\uABCD\"}", None, incomplete = false)
      deny("{\"t\\u006fke\\u006E\":\"x\"}") shouldEqual
      Result("{\"t\\u006fke\\u006E\":\"REDACTED\"}", None, incomplete = false)
    }
    "pass a top-level scalar through" in {
      deny("42") shouldEqual Result("42", None, incomplete = false)
      deny("\"string\"") shouldEqual Result("\"string\"", None, incomplete = false)
      deny("true").text shouldEqual "true"
    }
    "not inspect keys inside a masked container" in {
      var seen = List.empty[String]
      scan("""{"secret":{"inner":1},"b":2}""", truncated = false,
        DenyList { k => seen ::= k; k == "secret" }).text shouldEqual
      """{"secret":"REDACTED","b":2}"""
      seen.reverse shouldEqual List("secret", "b")
    }
  }

  "the scanner at the end of truncated input" should {
    // A regular string literal: triple quotes would leave the `\u0041` escape ambiguous across Scala versions.
    val body = "{\"user\":\"alice\",\"password\":\"ZZZ\",\"tags\":[\"x\",\"y\"],\"n\":12.5,\"note\":\"a\\\"q \\u0041\"}"
    def cut(n: Int): String = deny(body.take(n), truncated = true).text

    "drop a member cut inside its key, with its comma" in {
      cut(body.indexOf("\"pass") + 5) shouldEqual """{"user":"alice"}"""
    }
    "drop a member whose value has not started" in {
      cut(body.indexOf(":\"ZZZ") + 1) shouldEqual """{"user":"alice"}"""
    }
    "emit the mask for a masked string cut in the middle" in {
      cut(body.indexOf("ZZZ") + 2) shouldEqual """{"user":"alice","password":"REDACTED"}"""
    }
    "close a kept string cut in the middle" in {
      cut(body.indexOf("alice") + 2) shouldEqual """{"user":"al"}"""
    }
    "drop a dangling number and close the containers" in {
      cut(body.indexOf("12.") + 3) shouldEqual """{"user":"alice","password":"REDACTED","tags":["x","y"]}"""
    }
    "drop a dangling literal" in {
      deny("""{"a":tru""", truncated = true).text shouldEqual "{}"
    }
    "drop a trailing comma" in {
      cut(body.indexOf("\"tags\"")) shouldEqual """{"user":"alice","password":"REDACTED"}"""
    }
    "drop an incomplete escape before closing a string" in {
      cut(body.indexOf("\\u0041") + 3) shouldEqual
      "{\"user\":\"alice\",\"password\":\"REDACTED\",\"tags\":[\"x\",\"y\"],\"n\":12.5,\"note\":\"a\\\"q \"}"
      cut(body.indexOf("\\\"q") + 1) shouldEqual
      "{\"user\":\"alice\",\"password\":\"REDACTED\",\"tags\":[\"x\",\"y\"],\"n\":12.5,\"note\":\"a\"}"
    }
    "close an array cut after a comma" in {
      cut(body.indexOf("\"y\"")) shouldEqual """{"user":"alice","password":"REDACTED","tags":["x"]}"""
    }
    "emit the mask once for a masked container cut inside" in {
      deny("""{"secret":{"b":[1,"ZZZ",{"c""", truncated = true).text shouldEqual """{"secret":"REDACTED"}"""
    }
    "drop a dangling root number and mark everything incomplete" in {
      deny("1234", truncated = true) shouldEqual Result("", None, incomplete = true)
      deny("", truncated = true) shouldEqual Result("", None, incomplete = true)
    }
    "carry no note and set incomplete for every cut" in {
      for (n <- 0 until body.length) withClue(n) {
        val r = deny(body.take(n), truncated = true)
        r.note shouldBe None
        r.incomplete shouldBe true
      }
      deny(body, truncated = true).incomplete shouldBe true
    }
  }

  "the scanner on malformed or non-truncated incomplete input" should {
    "cut at the first invalid character with a note" in {
      deny("""{"a":1,}""") shouldEqual Result("""{"a":1}""", Some("unparseable from position 7"), incomplete = true)
      deny("""{"password":"ZZZ" "x":1}""") shouldEqual
      Result("""{"password":"REDACTED"}""", Some("unparseable from position 18"), incomplete = true)
      deny("hello") shouldEqual Result("", Some("unparseable from position 0"), incomplete = true)
      deny("""{a:1}""") shouldEqual Result("{}", Some("unparseable from position 1"), incomplete = true)
      deny("""{"a":1}{"b":2}""") shouldEqual
      Result("""{"a":1}""", Some("unparseable from position 7"), incomplete = true)
      deny("""{"a":1}""" + "\n" + """{"b":2}""").note shouldEqual Some("unparseable from position 8")
      deny("""{"a":tru,"b":1}""") shouldEqual Result("{}", Some("unparseable from position 8"), incomplete = true)
      deny("""{"a":12x}""") shouldEqual Result("""{"a":12}""", Some("unparseable from position 7"), incomplete = true)
      deny("""{"a":"\x"}""") shouldEqual Result("""{"a":""}""", Some("unparseable from position 7"), incomplete = true)
      deny("""{"a":01}""") shouldEqual Result("{}", Some("unparseable from position 5"), incomplete = true)
      deny("""[1,]""") shouldEqual Result("[1]", Some("unparseable from position 3"), incomplete = true)
      deny("""{"a":[1}""") shouldEqual Result("""{"a":[1]}""", Some("unparseable from position 7"), incomplete = true)
    }
    "cut a unicode escape at its first non-ASCII hex digit, with a note" in {
      deny("{\"a\":\"\\u\uFF10\uFF10\uFF14\uFF11\"}") shouldEqual
      Result("{\"a\":\"\"}", Some("unparseable from position 8"), incomplete = true)
      deny("{\"a\":\"\\u004\uFF11\"}") shouldEqual
      Result("{\"a\":\"\"}", Some("unparseable from position 11"), incomplete = true)
      deny("{\"a\":\"\\u00\uFF21F\"}") shouldEqual
      Result("{\"a\":\"\"}", Some("unparseable from position 10"), incomplete = true)
    }
    "treat an unterminated structure as malformed at the end when not truncated" in {
      deny("""{"card":"411111111111""") shouldEqual
      Result("""{"card":"411111111111"}""", Some("unparseable from position 21"), incomplete = true)
      deny("""{"a":1""") shouldEqual Result("""{"a":1}""", Some("unparseable from position 6"), incomplete = true)
      deny("") shouldEqual Result("", Some("unparseable from position 0"), incomplete = true)
      deny("   ") shouldEqual Result("", Some("unparseable from position 3"), incomplete = true)
    }
    "accept a number or literal at the very end when not truncated" in {
      deny("42") shouldEqual Result("42", None, incomplete = false)
      deny("-1.5e3 ") shouldEqual Result("-1.5e3", None, incomplete = false)
      deny("null") shouldEqual Result("null", None, incomplete = false)
    }
  }

  "the scanner in Keep mode" should {
    "compact without masking" in {
      keep("{ \"password\" : \"x\" , \"a\" : [ 1 , 2 ] }") shouldEqual
      Result("""{"password":"x","a":[1,2]}""", None, incomplete = false)
    }
    "report repair and truncation the same way as DenyList" in {
      keep("""{"card":"411111111111""").incomplete shouldBe true
      keep("""{"card":"4111"}""", truncated = true).incomplete shouldBe true
      keep("""{"card":"4111"}""").incomplete shouldBe false
    }
  }

  "the scanner" should {
    "handle 1000 nesting levels without stack growth" in {
      val deep = "[" * 1000 + "1" + "]" * 1000
      keep(deep) shouldEqual Result(deep, None, incomplete = false)
      deny("{\"a\":" * 1000 + "1" + "}" * 1000).incomplete shouldBe false
      keep("[" * 1000, truncated = true).text shouldEqual "[" * 1000 + "]" * 1000
    }
    "validate number tokens" in {
      for (n <- Seq("0", "-0", "1", "-1", "1.5", "1e5", "1E+5", "1.5e-3", "123456789012345678901234567890"))
        withClue(n)(isNumber(n) shouldBe true)
      for (n <- Seq("", "-", "01", "1.", ".5", "1e", "1e+", "+1", "0x1", "1,5", "REDACTED", "1 ", "NaN"))
        withClue(n)(isNumber(n) shouldBe false)
      isLiteral("true") shouldBe true
      isLiteral("True") shouldBe false
    }
    "quote strings as JSON" in {
      quote("a\"b\\c\n\u0001é") shouldEqual "\"a\\\"b\\\\c\\n\\u0001é\""
      quote("REDACTED") shouldEqual "\"REDACTED\""
    }
  }
}
