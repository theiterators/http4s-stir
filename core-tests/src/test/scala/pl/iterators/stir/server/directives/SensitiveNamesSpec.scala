package pl.iterators.stir.server.directives

import java.util.Locale
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.typelevel.ci._

class SensitiveNamesSpec extends AnyWordSpec with Matchers {
  val sensitive = Seq(
    "X-Api-Key", "reset_token", "accessToken", "X-Access-Token", "accesstoken", "clientSecret", "userpassword",
    "AWSAccessKeyId", "Set-Cookie", "Proxy-Authorization", "sessionId", "tokens", "keys", "credentials",
    "aUthorization", "X-Api-kEy", "PASSWORD", "pASSWORD", "AUTHORIZATION", "password", "passwd", "pwd", "pass",
    "passphrase", "secret", "token", "key", "apikey", "api_key", "auth", "certificate", "cookie", "credential",
    "csrf", "csrftoken", "cvc", "cvv", "otp", "privatekey", "salt", "session", "sessionid", "sig", "signature",
    "ssn", "xsrf", "Authorization", "Cookie", "X-Amz-Signature", "X-Amz-Credential", "X-Amz-Security-Token",
    "X-Goog-Signature", "private_key", "X-Csrf-Token", "x-xsrf-token")

  val notSensitive = Seq(
    "monkeys", "keyboard", "WWW-Authenticate", "Content-Type", "zip_code", "tokenizer", "email", "api",
    "api_version", "code", "promo_code", "id", "client", "user", "X-Forwarded-For", "Host", "Origin", "", "-",
    "X-Tokenizer-Version", "authenticate", "passport")

  "SensitiveNames.default" should {
    "match every conventional name" in {
      for (n <- sensitive) withClue(n)(SensitiveNames.default(n) shouldBe true)
    }
    "leave ordinary names alone" in {
      for (n <- notSensitive) withClue(n)(SensitiveNames.default(n) shouldBe false)
    }
    "contain every word of the published lists" in {
      SensitiveNames.words shouldEqual Set("auth", "authorization", "apikey", "certificate", "cookie", "credential",
        "csrf", "cvc", "cvv", "key", "otp", "pass", "passphrase", "passwd", "password", "privatekey", "pwd", "salt",
        "secret", "session", "sessionid", "sig", "signature", "ssn", "token", "xsrf")
      SensitiveNames.suffixes shouldEqual Set("token", "secret", "password", "passwd")
    }
    "be invariant under capitalization" in {
      val variants = Seq("password", "PaSsWoRd", "pASSWORD", "x-api-key", "X-API-KEY", "x-API-key", "accessToken",
        "ACCESSTOKEN", "AccessToken", "Proxy-Authorization", "PROXY-AUTHORIZATION", "proxy-authorization")
      for (n <- variants) withClue(n) {
        SensitiveNames.default(n) shouldBe true
        SensitiveNames.default(n.toLowerCase(Locale.ROOT)) shouldBe true
      }
    }
  }

  "SensitiveNames.matching" should {
    "normalise entries like names" in {
      val pin = SensitiveNames.matching(Set("PIN"), suffixes = Set.empty)
      for (n <- Seq("pin", "user_pin", "pinCode", "PINS", "Pin")) withClue(n)(pin(n) shouldBe true)
      for (n <- Seq("pinned", "spin", "password", "secret", "access_token")) withClue(n)(pin(n) shouldBe false)
    }
    "match multi-word entries as phrases or concatenations" in {
      val clientId = SensitiveNames.matching(Set("clientId"), suffixes = Set.empty)
      for (n <- Seq("clientId", "client_id", "ClientID", "clientid", "clientIds", "x-client-id", "client-ids"))
        withClue(n)(clientId(n) shouldBe true)
      for (n <- Seq("id", "client", "clientele", "identity", "client_identity")) withClue(n)(clientId(n) shouldBe false)
      val card = SensitiveNames.matching(Set("card_number"), suffixes = Set.empty)
      for (n <- Seq("cardNumber", "card_number", "cardnumber", "CARD-NUMBER")) withClue(n)(card(n) shouldBe true)
      card("number") shouldBe false
    }
    "apply suffix rules only when given" in {
      SensitiveNames.matching(Set("pin"), suffixes = Set("token"))("accesstoken") shouldBe true
      SensitiveNames.matching(Set("pin"), suffixes = Set.empty)("accesstoken") shouldBe false
      SensitiveNames.matching(SensitiveNames.words, SensitiveNames.suffixes + "pin")("userpin") shouldBe true
    }
    "reject an entry without a letter or digit at construction" in {
      an[IllegalArgumentException] should be thrownBy SensitiveNames.matching(Set("--"))
      an[IllegalArgumentException] should be thrownBy SensitiveNames.matching(Set(""))
    }
  }

  "SensitiveNames.header" should {
    "match lowercased names identically for every capitalization" in {
      for (n <- Seq("aUthorization", "X-Api-kEy", "COOKIE", "x-csrf-TOKEN", "Set-Cookie", "X-Access-Key"))
        withClue(n) {
          SensitiveNames.header(CIString(n)) shouldBe true
          SensitiveNames.header(CIString(n.toUpperCase(Locale.ROOT))) shouldBe true
          SensitiveNames.header(CIString(n.toLowerCase(Locale.ROOT))) shouldBe true
        }
    }
    "not split camelCase inside a header name" in {
      SensitiveNames.header(ci"X-AccessKey") shouldBe false
      SensitiveNames.header(ci"X-Access-Key") shouldBe true
      SensitiveNames.header(ci"Content-Type") shouldBe false
      SensitiveNames.header(ci"WWW-Authenticate") shouldBe false
    }
    "pass the lowercased name to a custom predicate" in {
      var seen = ""
      SensitiveNames.headerFrom { n => seen = n; false }(ci"X-Api-Key") shouldBe false
      seen shouldEqual "x-api-key"
    }
  }

  "SensitiveNames under the tr-TR locale" should {
    "still match upper-case names" in {
      val saved = Locale.getDefault
      Locale.setDefault(Locale.forLanguageTag("tr-TR"))
      try {
        SensitiveNames.default("AUTHORIZATION") shouldBe true
        SensitiveNames.default("PIN-CODE") shouldBe false
        SensitiveNames.matching(Set("PIN"), suffixes = Set.empty)("PIN") shouldBe true
        SensitiveNames.header(ci"AUTHORIZATION") shouldBe true
        SensitiveNames.header(ci"X-API-KEY") shouldBe true
        for (n <- sensitive) withClue(n)(SensitiveNames.default(n) shouldBe true)
        for (n <- notSensitive) withClue(n)(SensitiveNames.default(n) shouldBe false)
      } finally Locale.setDefault(saved)
    }
  }
}
