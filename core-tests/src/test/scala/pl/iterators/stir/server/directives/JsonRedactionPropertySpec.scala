package pl.iterators.stir.server.directives

import io.circe.parser.parse
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import pl.iterators.stir.server.directives.JsonRedaction._

class JsonRedactionPropertySpec extends AnyWordSpec with Matchers {
  // Secrets are made of the marker character only, so a leak is one `indexOf`.
  val Marker = 'Z'
  val sensitive: String => Boolean = Set("password", "secret", "token", "apiKey")
  val allowed = Set("user", "id", "ok", "tags", "n", "note", "nested", "list")

  val bodies = Seq(
    """{"user":"alice","password":"ZZZZ","tags":["x","y"],"n":12.5,"note":"a\"q A","ok":true}""",
    """{"secret":{"b":[1,"ZZZ",{"c":"ZZ"}],"d":null},"user":"bob","token":["ZZZZ",{"e":"ZZ"}],"id":7}""",
    """[{"password":"ZZ"},[{"apiKey":"ZZZZZZ","ok":false}],"plain",42,{"nested":{"password":-1.5e3}}]""",
    "{\n  \"user\" : \"carol\" ,\n  \"password\" : \"ZZZZZZZZ\" ,\n  \"list\" : [ 1 , 2 , 3 ]\n}\n",
    """{"token":"Z\"Z\\ZZZ","user":"free","password":12345,"secret":true,"apiKey":[[["ZZZ"]]]}""")

  def check(name: String, output: String): Unit = withClue(s"$name -> $output") {
    output.indexOf(Marker) shouldBe -1
    if (output.nonEmpty) parse(output).isRight shouldBe true
    ()
  }

  "the deny-list mode" should {
    "never leak a marker and always produce JSON, at every cut" in {
      for (body <- bodies; n <- 0 to body.length) {
        val r = scan(body.take(n), truncated = true, DenyList(sensitive))
        check(s"deny cut $n of $body", r.text)
      }
    }
  }

  "the allow-list mode" should {
    "never leak a marker and always produce JSON, at every cut" in {
      for (body <- bodies; n <- 0 to body.length) {
        val r = scan(body.take(n), truncated = true, KeepOnly(allowed, sensitive))
        check(s"keepOnly cut $n of $body", r.text)
      }
    }
  }

  "the keep and transform modes" should {
    "always produce JSON at every cut" in {
      for (body <- bodies; n <- 0 to body.length) {
        val kept = scan(body.take(n), truncated = true, Keep)
        if (kept.text.nonEmpty) parse(kept.text).isRight shouldBe true
        val transformed =
          scan(body.take(n), truncated = true, Transform(s => if (s.contains(Marker)) "REDACTED" else s))
        check(s"transform cut $n of $body", transformed.text)
      }
    }
    "produce the same tree as circe after compaction of a complete body" in {
      for (body <- bodies) {
        val kept = scan(body, truncated = false, Keep)
        kept.note shouldBe None
        kept.incomplete shouldBe false
        parse(kept.text) shouldEqual parse(body)
      }
    }
  }
}
