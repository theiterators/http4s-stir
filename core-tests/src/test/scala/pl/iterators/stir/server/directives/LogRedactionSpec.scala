package pl.iterators.stir.server.directives

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.Chunk
import org.http4s.headers.`Content-Type`
import org.http4s.{ Charset, MediaType, Method, Request, Uri }
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.typelevel.ci._
import pl.iterators.stir.server.directives.BodyRedactor.{ Hidden, Skip, Text }
import pl.iterators.stir.server.directives.LogRedaction.RejectionValues

class LogRedactionSpec extends AnyWordSpec with Matchers {
  def body(text: String, mediaType: MediaType, truncated: Boolean = false): LoggedBody =
    LoggedBody(Request[IO](Method.POST, Uri.unsafeFromString("/x")).withContentType(`Content-Type`(mediaType,
        Charset.`UTF-8`)),
      Chunk.array(text.getBytes("UTF-8")), truncated)
  def json(text: String, truncated: Boolean = false) = body(text, MediaType.application.json, truncated)
  def form(text: String, truncated: Boolean = false) =
    body(text, MediaType.application.`x-www-form-urlencoded`, truncated)
  def plain(text: String, truncated: Boolean = false) = body(text, MediaType.text.plain, truncated)
  def xml(text: String) = body(text, MediaType.application.xml)
  def run(p: LogRedaction, b: LoggedBody) = p.body.redact(b).unsafeRunSync()
  def uri(s: String) = Uri.unsafeFromString(s)
  val card: String => String = s => if (s.forall(_.isDigit) && s.length >= 13) "REDACTED" else s
  // for rules run on a whole text: unlike `card`, which masks a value only when the value is itself a card number
  val cardText: String => String = s => "\\b[0-9]{13,19}\\b".r.replaceAllIn(s, "REDACTED")

  "LogRedaction.default" should {
    "mask by name in every channel" in {
      val d = LogRedaction.default
      d.names("password") shouldBe true
      d.names("user") shouldBe false
      d.headers(ci"X-Api-Key") shouldBe true
      d.headers(ci"Content-Type") shouldBe false
      d.uri(uri("http://u:p@h/a?token=t&x=1")).renderString shouldEqual
      "http://REDACTED:REDACTED@h/a?token=REDACTED&x=1"
      d.uri(uri("https://h/cb#access_token=t&state=s")).renderString shouldEqual
      "https://h/cb#access_token=REDACTED&state=s"
      d.transformUriHeaders shouldBe true
      d.rejectionValues shouldEqual RejectionValues.ByName
      run(d, json("""{"password":"x","a":1}""")) shouldEqual Text("""{"password":"REDACTED","a":1}""")
      run(d, form("password=x&a=1")) shouldEqual Text("password=REDACTED&a=1")
      run(d, plain("password=x")) shouldEqual Text("password=x")
      run(d, xml("<password>x</password>")) shouldEqual Hidden()
      run(d, body("x", MediaType.application.`octet-stream`)) shouldEqual Hidden()
    }
  }

  "LogRedaction.none" should {
    "switch every stage off" in {
      val n = LogRedaction.none
      n.names("password") shouldBe false
      n.headers(ci"X-Api-Key") shouldBe false
      n.uri(uri("http://u:p@h/a?token=t")).renderString shouldEqual "http://u:p@h/a?token=t"
      n.uri(uri("https://h/cb#access_token=t")).renderString shouldEqual "https://h/cb#access_token=t"
      n.transformUriHeaders shouldBe false
      n.rejectionValues shouldEqual RejectionValues.Shown
      run(n, json("""{"password":"x","a":1}""")) shouldEqual Text("""{"password":"x","a":1}""")
      run(n, json("{\n \"password\": \"x\"\n}")) shouldEqual Text("{\n \"password\": \"x\"\n}")
      run(n, xml("<password>x</password>")) shouldEqual Text("<password>x</password>")
      run(n, form("password=x", truncated = true)) shouldEqual Text("password=x", None, incomplete = true)
    }
    "switch the names stage on with addNames, matching the added names only" in {
      val n = LogRedaction.none.addNames("pin")
      n.names("pin") shouldBe true
      n.names("userPin") shouldBe true
      n.names("password") shouldBe false
      n.names("access_token") shouldBe false
      n.headers(ci"X-Pin") shouldBe true
      n.headers(ci"Authorization") shouldBe false
      n.uri(uri("http://u:p@h/a?pin=1&password=x")).renderString shouldEqual
      "http://REDACTED:REDACTED@h/a?pin=REDACTED&password=x"
      n.transformUriHeaders shouldBe true
      n.rejectionValues shouldEqual RejectionValues.ByName
      run(n, json("""{"pin":"1","password":"x"}""")) shouldEqual Text("""{"pin":"REDACTED","password":"x"}""")
      run(n, form("pin=1;password=x")) shouldEqual Text("pin=REDACTED;password=x")
      run(n, xml("<pin>1</pin>")) shouldEqual Text("<pin>1</pin>")
    }
    "leave the names stage off for addNames() and switch on only the URI-header stage for redactPath" in {
      LogRedaction.none.addNames().rejectionValues shouldEqual RejectionValues.Shown
      LogRedaction.none.addNames().transformUriHeaders shouldBe false
      val p = LogRedaction.none.redactPath("/reset/{token}")
      p.transformUriHeaders shouldBe true
      p.rejectionValues shouldEqual RejectionValues.Shown
      p.names("password") shouldBe false
      p.uri(uri("/reset/XYZ?password=x")).renderString shouldEqual "/reset/REDACTED?password=x"
      LogRedaction.none.hideRejectionValues.transformUriHeaders shouldBe false
      LogRedaction.none.redactBodyValues(card).transformUriHeaders shouldBe false
    }
  }

  "addNames" should {
    "normalise entries and keep every default" in {
      val d = LogRedaction.default.addNames("PIN", "clientId")
      for (n <- Seq("pin", "user_pin", "PINS", "clientId", "client_id", "ClientID", "password", "X-Api-Key"))
        withClue(n)(d.names(n) shouldBe true)
      d.names("id") shouldBe false
      d.headers(ci"X-Client-Id") shouldBe true
      run(d, json("""{"pin":"1","clientId":"c","password":"p","id":7}""")) shouldEqual
      Text("""{"pin":"REDACTED","clientId":"REDACTED","password":"REDACTED","id":7}""")
    }
    "reject an empty entry at construction" in {
      an[IllegalArgumentException] should be thrownBy LogRedaction.default.addNames("")
    }
    "upgrade Shown to ByName and keep Hidden" in {
      LogRedaction.none.hideRejectionValues.addNames("pin").rejectionValues shouldEqual RejectionValues.Hidden
      LogRedaction.default.hideRejectionValues.rejectionValues shouldEqual RejectionValues.Hidden
    }
  }

  "withNames" should {
    "replace the predicate, discarding earlier addNames, and commute with other operations" in {
      val w = LogRedaction.default.addNames("pin").withNames(_ == "only")
      w.names("only") shouldBe true
      w.names("pin") shouldBe false
      w.names("password") shouldBe false
      val a = LogRedaction.default.withNames(_ == "only").redactPath("/r/{t}")
      val b = LogRedaction.default.redactPath("/r/{t}").withNames(_ == "only")
      a.uri(uri("/r/X?only=1&password=2")).renderString shouldEqual b.uri(uri("/r/X?only=1&password=2")).renderString
      a.uri(uri("/r/X?only=1&password=2")).renderString shouldEqual "/r/REDACTED?only=REDACTED&password=2"
      val suffixed =
        LogRedaction.default.withNames(SensitiveNames.matching(SensitiveNames.words, SensitiveNames.suffixes + "pin"))
      suffixed.names("userpin") shouldBe true
    }
  }

  "redactPath and redactUri" should {
    "keep query and userinfo masking and run after it" in {
      val p = LogRedaction.default.redactPath("/password-reset/{token}")
      p.uri(uri("http://u:p@h/password-reset/XYZ?token=t")).renderString shouldEqual
      "http://REDACTED:REDACTED@h/password-reset/REDACTED?token=REDACTED"
      var seen = ""
      val r = LogRedaction.default.redactUri { u => seen = u.renderString; u }
      r.uri(uri("/a?token=t")).renderString shouldEqual "/a?token=REDACTED"
      seen shouldEqual "/a?token=REDACTED"
      val two = LogRedaction.default.redactPath("/a/{x}").redactPath("/b/{y}")
      two.uri(uri("/a/1")).renderString shouldEqual "/a/REDACTED"
      two.uri(uri("/b/2")).renderString shouldEqual "/b/REDACTED"
    }
  }

  "withBodyRedactor" should {
    "be consulted first, fall through on Skip, replace the base for the bodies it handles" in {
      val d = LogRedaction.default.withBodyRedactor(BodyRedactor.when(_.isForm)(BodyRedactor.hidden))
      run(d, form("password=x")) shouldEqual Hidden()
      run(d, json("""{"password":"x"}""")) shouldEqual Text("""{"password":"REDACTED"}""")
      val replaced = LogRedaction.default.withBodyRedactor(BodyRedactor.jsonKeys(_ == "pin"))
      run(replaced, json("""{"pin":1,"password":"x"}""")) shouldEqual Text("""{"pin":"REDACTED","password":"x"}""")
      run(replaced, form("password=x")) shouldEqual Text("password=REDACTED")
      run(LogRedaction.default.withBodyRedactor(BodyRedactor.hidden), plain("t")) shouldEqual Hidden()
      val recent =
        LogRedaction.default.withBodyRedactor(BodyRedactor(_ => "first")).withBodyRedactor(BodyRedactor(_ => "second"))
      run(recent, plain("t")) shouldEqual Text("second")
      val tokenRequests = BodyRedactor.when(_.request.exists(_.uri.path.renderString == "/oauth/token"))(
        BodyRedactor.jsonKeepOnly(Set("grant_type", "scope")))
      run(LogRedaction.default.withBodyRedactor(tokenRequests),
        json("""{"grant_type":"password","username":"alice","password":"x"}""")) shouldEqual
      Text("""{"grant_type":"password","username":"alice","password":"REDACTED"}""")
      val atToken = LoggedBody(Request[IO](Method.POST, uri("/oauth/token")).withContentType(
          `Content-Type`(MediaType.application.json)),
        Chunk.array("""{"grant_type":"password","username":"alice","password":"x"}""".getBytes("UTF-8")),
        truncated = false)
      run(LogRedaction.default.withBodyRedactor(tokenRequests), atToken) shouldEqual
      Text("""{"grant_type":"password","username":"REDACTED","password":"REDACTED"}""")
    }
  }

  "redactBodyValues" should {
    "run after the chain on JSON scalars, keeping JSON valid" in {
      val d = LogRedaction.default.redactBodyValues(card)
      run(d,
        json("""{"card":4111111111111111,"note":"card 4111111111111111","password":"4111111111111111"}""")) shouldEqual
      Text("""{"card":"REDACTED","note":"card 4111111111111111","password":"REDACTED"}""")
      run(
        LogRedaction.default.redactBodyValues(s => "\\b[0-9]{13,19}\\b".r.replaceAllIn(s, "REDACTED")),
        json("""{"card":4111111111111111,"note":"card 4111111111111111"}""")) shouldEqual
      Text("""{"card":"REDACTED","note":"card REDACTED"}""")
    }
    "run on decoded form values and re-encode changed results" in {
      val d = LogRedaction.default.redactBodyValues(card)
      run(d, form("card=%34%31%31%31%31%31%31%31%31%31%31%31%31%31%31%31&password=x&note=a+b")) shouldEqual
      Text("card=REDACTED&password=REDACTED&note=a+b")
      run(LogRedaction.default.redactBodyValues(_ => "a&b=c"), form("k=v&%ZZ=w")) shouldEqual
      Text("k=a%26b%3Dc&%ZZ=a%26b%3Dc")
    }
    "run on the whole text of other bodies" in {
      run(LogRedaction.default.redactBodyValues(cardText), plain("card 4111111111111111")) shouldEqual
      Text("card REDACTED")
      run(LogRedaction.none.redactBodyValues(cardText), xml("<c>4111111111111111</c>")) shouldEqual
      Text("<c>REDACTED</c>")
      run(LogRedaction.default.redactBodyValues(cardText), xml("<c>4111111111111111</c>")) shouldEqual Hidden()
    }
    "hide malformed JSON with the note unless prefix-safe" in {
      var calls = 0
      val rule: String => String = s => { calls += 1; if (s.startsWith("sk_live_")) "REDACTED" else s }
      run(LogRedaction.default.redactBodyValues(rule), json("""{"value":"sk_live_X",}""")) shouldEqual
      Hidden(Some("unparseable from position 21"))
      calls shouldEqual 0
      run(LogRedaction.default.redactBodyValues(rule, prefixSafe = true),
        json("""{"value":"sk_live_X",}""")) shouldEqual
      Text("""{"value":"REDACTED"}""", Some("unparseable from position 21"), incomplete = true)
    }
    "treat a repaired body as truncated: the stage checks its own scan, on default and on none" in {
      var calls = 0
      val counting: String => String = s => { calls += 1; card(s) }
      run(LogRedaction.default.redactBodyValues(counting), json("""{"card":"411111111111""")) shouldEqual
      Hidden(Some("unparseable from position 21"))
      run(LogRedaction.none.redactBodyValues(counting), json("""{"card":"411111111111""")) shouldEqual
      Hidden(Some("unparseable from position 21"))
      run(LogRedaction.none.redactBodyValues(counting), json("""{"card":"4111"}""", truncated = true)) shouldEqual
      Hidden(None)
      calls shouldEqual 0
      run(LogRedaction.none.redactBodyValues(counting, prefixSafe = true),
        json("""{"card":"411111111111""")) shouldEqual
      Text("""{"card":"411111111111"}""", Some("unparseable from position 21"), incomplete = true)
    }
    "hide a truncated plain or form body without calling f, whatever the incoming Text says" in {
      var calls = 0
      val counting: String => String = s => { calls += 1; card(s) }
      run(LogRedaction.none.redactBodyValues(counting), plain("card 4111111111", truncated = true)) shouldEqual
      Hidden(None)
      run(LogRedaction.none.redactBodyValues(counting), form("card=4111111111", truncated = true)) shouldEqual
      Hidden(None)
      // the custom Text reports incomplete = false: only body.truncated tells the stage to hide
      val custom = LogRedaction.default.withBodyRedactor(BodyRedactor(_.text, prefixSafe = true))
      run(custom.redactBodyValues(counting), plain("card 4111111111", truncated = true)) shouldEqual Hidden(None)
      calls shouldEqual 0
    }
    "propagate incompleteness through a preceding prefix-safe rule" in {
      var calls = 0
      val counting: String => String = s => { calls += 1; card(s) }
      val two = LogRedaction.none.redactBodyValues(identity, prefixSafe = true).redactBodyValues(counting)
      run(two, json("""{"card":"411111111111""")) shouldEqual Hidden(Some("unparseable from position 21"))
      calls shouldEqual 0
      val both =
        LogRedaction.none.redactBodyValues(identity, prefixSafe = true).redactBodyValues(counting, prefixSafe = true)
      run(both, json("""{"card":"411111111111""")) shouldEqual
      Text("""{"card":"411111111111"}""", Some("unparseable from position 21"), incomplete = true)
      calls shouldEqual 1 // twelve digits are below the rule's threshold; the point is that f ran and the flag survived
    }
    "pass Hidden through and keep the chain's note" in {
      run(LogRedaction.default.redactBodyValues(card), xml("x")) shouldEqual Hidden()
      run(LogRedaction.default.redactBodyValues(card), json("hello")) shouldEqual
      Hidden(Some("unparseable from position 0"))
      run(LogRedaction.default.redactBodyValues(card, prefixSafe = true), json("hello")) shouldEqual
      Text("", Some("unparseable from position 0"), incomplete = true)
    }
    "run on the Text of a custom body redactor" in {
      val d =
        LogRedaction.default.withBodyRedactor(BodyRedactor(_ => "card 4111111111111111")).redactBodyValues(cardText)
      run(d, plain("ignored")) shouldEqual Text("card REDACTED")
      val notJson = LogRedaction.default.withBodyRedactor(BodyRedactor(_ => "<shape>")).redactBodyValues(cardText)
      run(notJson, json("""{"a":1}""")) shouldEqual Hidden(Some("unparseable from position 0"))
    }
  }

  "the body chain" should {
    "render Skip from a total override as Skip for the directive to hide" in {
      run(LogRedaction.default.withBodyRedactor(BodyRedactor.eval(_ => IO.pure(Skip))), xml("x")) shouldEqual Hidden()
      run(LogRedaction.none.withBodyRedactor(BodyRedactor.eval(_ => IO.pure(Skip))), xml("x")) shouldEqual Text("x")
    }
  }
}
