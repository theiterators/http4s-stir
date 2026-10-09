package pl.iterators.stir.server.directives

import java.nio.charset.StandardCharsets
import org.http4s.{ Charset, UrlForm }
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class FormRedactionSpec extends AnyWordSpec with Matchers {
  val sensitive: String => Boolean = SensitiveNames.default
  def mask(text: String, truncated: Boolean = false): String =
    FormRedaction.maskFields(text, truncated, StandardCharsets.UTF_8, sensitive)

  "maskFields" should {
    "mask the values of sensitive fields and keep everything else verbatim" in {
      mask("user=alice&password=hunter2&x=1") shouldEqual "user=alice&password=REDACTED&x=1"
    }
    "honour ; and mixed separators" in {
      mask("user=alice;password=X&x=1") shouldEqual "user=alice;password=REDACTED&x=1"
      mask("password=a;token=b;x=c") shouldEqual "password=REDACTED;token=REDACTED;x=c"
    }
    "keep flags, empty values and repeated fields" in {
      mask("flag&password=&password=b&=v&k=") shouldEqual "flag&password=REDACTED&password=REDACTED&=v&k="
    }
    "decode the field name for matching only" in {
      mask("pass%77ord=x&user+name=y&%74oken=z") shouldEqual "pass%77ord=REDACTED&user+name=y&%74oken=REDACTED"
    }
    "decode the field name with the body charset, as UrlForm does" in {
      // "password" percent-encoded as UTF-16BE bytes; decoded as UTF-8 it is NUL-separated letters and matches nothing
      val name = "%00%70%00%61%00%73%00%73%00%77%00%6F%00%72%00%64"
      val text = s"$name=CANARY&user=a"
      UrlForm.decodeString(Charset.`UTF-16BE`)(text).toOption.get.getFirst("password") shouldEqual Some("CANARY")
      FormRedaction.maskFields(text, truncated = false, StandardCharsets.UTF_16BE, sensitive) shouldEqual
      s"$name=REDACTED&user=a"
      mask(text) shouldEqual text
    }
    "keep a value containing = intact" in {
      mask("q=a=b&password=c=d") shouldEqual "q=a=b&password=REDACTED"
    }
    "drop a last part without = when truncated, keep a partial value otherwise" in {
      mask("user=alice&pass", truncated = true) shouldEqual "user=alice"
      mask("user=ali", truncated = true) shouldEqual "user=ali"
      mask("user=alice&password=hun", truncated = true) shouldEqual "user=alice&password=REDACTED"
      mask("user=alice&", truncated = true) shouldEqual "user=alice"
      mask("", truncated = true) shouldEqual ""
      mask("user=alice&pass") shouldEqual "user=alice&pass"
    }
    "agree with UrlForm on which names carry which values" in {
      val text = "a=1;b=2&c=3&e=%26"
      val form = UrlForm.decodeString(Charset.`UTF-8`)(text).toOption.get
      val masked = FormRedaction.maskFields(text, truncated = false, StandardCharsets.UTF_8, Set("b", "e"))
      masked shouldEqual "a=1;b=REDACTED&c=3&e=REDACTED"
      form.values.keySet shouldEqual Set("a", "b", "c", "e")
      form.getFirst("e") shouldEqual Some("&")
    }
    "be prefix-safe at every cut" in {
      val body = "user=alice&password=ZZZZ&token=ZZ;note=ok&x=Z-free"
      for (n <- 0 to body.length) withClue(n) {
        val out =
          FormRedaction.maskFields(body.take(n), truncated = true, StandardCharsets.UTF_8, Set("password", "token"))
        out.indexOf("ZZ") shouldBe -1
      }
    }
  }

  "transformValues" should {
    val card: String => String = s => if (s.forall(_.isDigit) && s.length >= 13) "REDACTED" else s
    def transform(text: String, f: String => String = card): String =
      FormRedaction.transformValues(text, StandardCharsets.UTF_8, f)

    "hand f decoded values and keep the text when f changes nothing" in {
      transform("card=4111111111111111&note=a+b%20c&flag") shouldEqual "card=REDACTED&note=a+b%20c&flag"
    }
    "see percent-encoded values decoded" in {
      transform("card=%34%31%31%31%31%31%31%31%31%31%31%31%31%31%31%31") shouldEqual "card=REDACTED"
    }
    "form-encode a changed result so that it cannot change the structure" in {
      transform("k=v", _ => "a&b=c d;e") shouldEqual "k=a%26b%3Dc+d%3Be"
    }
    "keep malformed escapes and decode with the given charset" in {
      var seen = List.empty[String]
      transform("k=%ZZ%4&j=100%", s => { seen ::= s; s }) shouldEqual "k=%ZZ%4&j=100%"
      seen.reverse shouldEqual List("%ZZ%4", "100%")
      FormRedaction.transformValues("k=%E9", StandardCharsets.ISO_8859_1, s => { seen = List(s); s })
      seen shouldEqual List("é")
      FormRedaction.transformValues("k=%E9", StandardCharsets.UTF_8, s => { seen = List(s); s })
      seen shouldEqual List("�")
    }
    "apply f to every value, including empty ones, and never to names" in {
      transform("card=&4111111111111111=x", _ => "R") shouldEqual "card=R&4111111111111111=R"
    }
  }
}
