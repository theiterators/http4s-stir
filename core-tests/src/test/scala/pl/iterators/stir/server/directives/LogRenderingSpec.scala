package pl.iterators.stir.server.directives

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.Chunk
import org.http4s.headers.`Content-Type`
import org.http4s.{ Header, MediaType, Method, Request, Response, Status, Uri }
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.typelevel.ci._
import pl.iterators.stir.server.directives.BodyRedactor.{ Hidden, Skip, Text }
import pl.iterators.stir.server.directives.LogRendering._
import pl.iterators.stir.server.{
  InvalidRequiredValueForQueryParamRejection,
  MalformedFormFieldRejection,
  MalformedHeaderRejection,
  MalformedQueryParamRejection,
  MissingQueryParamRejection,
  Rejection
}

class LogRenderingSpec extends AnyWordSpec with Matchers {
  val default = Config(logHeaders = true, org.http4s.Headers.SensitiveHeaders.contains, LogRedaction.default)
  val none = default.copy(redaction = LogRedaction.none)
  val boom = new IllegalStateException("boom")

  "bodyPart" should {
    "follow the grammar of spec section 6" in {
      bodyPart(Right(Text("{}")), Some(2L), 4096) shouldEqual "body=\"{}\""
      bodyPart(Right(Text("{}", Some("unparseable from position 1"))), Some(2L), 4096) shouldEqual
      "body=\"{}\" (unparseable from position 1)"
      bodyPart(Right(Text("{}", Some("n"))), Some(50L), 10) shouldEqual "body=\"{}\" (n) ... (50 bytes total)"
      bodyPart(Right(Text("{}")), None, 10) shouldEqual "body=\"{}\" ... (??? bytes total)"
      bodyPart(Right(Text("{}")), Some(10L), 10) shouldEqual "body=\"{}\""
      bodyPart(Right(Hidden()), Some(50L), 10) shouldEqual "body=<hidden> (50 bytes total)"
      bodyPart(Right(Hidden(Some("n"))), None, 10) shouldEqual "body=<hidden> (n) (??? bytes total)"
      bodyPart(Right(Skip), Some(3L), 10) shouldEqual "body=<hidden> (3 bytes total)"
      bodyPart(Left(boom), Some(3L), 10) shouldEqual
      "body=<redaction failed: java.lang.IllegalStateException> (3 bytes total)"
      bodyPart(Left(boom), None, 10) shouldEqual
      "body=<redaction failed: java.lang.IllegalStateException> (??? bytes total)"
    }
    "escape control characters in Text values" in {
      bodyPart(Right(Text("a\nb\r\tc\u0001\u007fd")), Some(1L), 10) shouldEqual "body=\"a\\nb\\r\\tc\\u0001\\u007fd\""
      escapeControl("plain") shouldEqual "plain"
      escapeControl("back\\slash") shouldEqual "back\\slash"
    }
    "escape control characters in notes" in {
      bodyPart(Right(Hidden(Some("a\nb"))), Some(3L), 10) shouldEqual "body=<hidden> (a\\nb) (3 bytes total)"
      bodyPart(Right(Text("{}", Some("a\nb"))), Some(2L), 10) shouldEqual "body=\"{}\" (a\\nb)"
    }
  }

  "isTruncated" should {
    "compare the content length with maxBodyBytes, or the chunk size when unknown" in {
      isTruncated(Some(11L), Chunk.array(new Array[Byte](10)), 10) shouldBe true
      isTruncated(Some(10L), Chunk.array(new Array[Byte](10)), 10) shouldBe false
      isTruncated(None, Chunk.array(new Array[Byte](10)), 10) shouldBe true
      isTruncated(None, Chunk.array(new Array[Byte](9)), 10) shouldBe false
    }
  }

  "requestLine and responseLine" should {
    val request = Request[IO](Method.GET, Uri.unsafeFromString("http://u:p@h/a?token=t&x=1"))
      .putHeaders(Header.Raw(ci"X-Api-Key", "k"), Header.Raw(ci"Referer", "https://r/?access_token=s&y=2"),
        Header.Raw(ci"Accept", "*/*"))
    "render the http4s format with the URI and headers redacted" in {
      requestLine(request, default) shouldEqual
      "HTTP/1.1 GET http://REDACTED:REDACTED@h/a?token=REDACTED&x=1 Headers(X-Api-Key: <REDACTED>, Referer: https://r/?access_token=REDACTED&y=2, Accept: */*)"
      requestLine(request, none) shouldEqual
      "HTTP/1.1 GET http://u:p@h/a?token=t&x=1 Headers(X-Api-Key: k, Referer: https://r/?access_token=s&y=2, Accept: */*)"
      requestLine(request, default.copy(logHeaders = false)) shouldEqual
      "HTTP/1.1 GET http://REDACTED:REDACTED@h/a?token=REDACTED&x=1"
      requestLine(Request[IO](Method.GET, Uri.unsafeFromString("/hello")), default) shouldEqual
      "HTTP/1.1 GET /hello Headers()"
    }
    "union redactHeadersWhen with the policy's header predicate" in {
      requestLine(request, default.copy(redactHeadersWhen = _ == ci"Accept")) shouldEqual
      "HTTP/1.1 GET http://REDACTED:REDACTED@h/a?token=REDACTED&x=1 Headers(X-Api-Key: <REDACTED>, Referer: https://r/?access_token=REDACTED&y=2, Accept: <REDACTED>)"
      requestLine(request, none.copy(redactHeadersWhen = _ == ci"X-Api-Key")) should
      include("X-Api-Key: <REDACTED>, Referer: https://r/?access_token=s")
    }
    "mask an unparseable Referer or Location only when the URI-header stage is on" in {
      val bad = Request[IO](Method.GET, Uri.unsafeFromString("/")).putHeaders(Header.Raw(ci"Referer", "http://["))
      requestLine(bad, default) shouldEqual "HTTP/1.1 GET / Headers(Referer: <REDACTED>)"
      requestLine(bad, none) shouldEqual "HTTP/1.1 GET / Headers(Referer: http://[)"
      requestLine(bad, none.copy(redaction = LogRedaction.none.redactPath("/x/{y}"))) shouldEqual
      "HTTP/1.1 GET / Headers(Referer: <REDACTED>)"
      val response = Response[IO](Status.Found).putHeaders(Header.Raw(ci"Location", "/cb?token=t"))
      responseLine(response, default) shouldEqual "HTTP/1.1 302 Found Headers(Location: /cb?token=REDACTED)"
      responseLine(response, none) shouldEqual "HTTP/1.1 302 Found Headers(Location: /cb?token=t)"
      responseLine(Response[IO](Status.Ok), default) shouldEqual "HTTP/1.1 200 OK Headers()"
    }
    "render placeholders when user code throws" in {
      val badUri = default.copy(redaction = LogRedaction.default.redactUri(_ => throw boom))
      requestLine(request, badUri) shouldEqual
      "HTTP/1.1 GET <redaction failed: java.lang.IllegalStateException> Headers(X-Api-Key: <REDACTED>, Referer: <redaction failed: java.lang.IllegalStateException>, Accept: */*)"
      requestLine(request, default.copy(redactHeadersWhen = _ => throw boom)) shouldEqual
      "HTTP/1.1 GET http://REDACTED:REDACTED@h/a?token=REDACTED&x=1 Headers(<redaction failed: java.lang.IllegalStateException>)"
      requestLine(request, default.copy(redaction = LogRedaction.default.withNames(_ => throw boom))) should include(
        "Headers(<redaction failed: java.lang.IllegalStateException>)")
    }
  }

  "renderBody" should {
    // withEntity(String) replaces Content-Type with text/plain, so the content type is set after the entity
    def json(body: String) = Request[IO](Method.POST, Uri.unsafeFromString("/")).withEntity(body)
      .withContentType(`Content-Type`(MediaType.application.json))
    def bytes(s: String) = Chunk.array(s.getBytes("UTF-8"))
    "run the policy on the captured bytes" in {
      renderBody(json("""{"password":"x"}"""), bytes("""{"password":"x"}"""), 4096, default).unsafeRunSync() shouldEqual
      "body=\"{\"password\":\"REDACTED\"}\""
      renderBody(json("""{"password":"x"}"""), bytes("""{"pass"""), 6, default).unsafeRunSync() shouldEqual
      "body=\"{}\" ... (16 bytes total)"
    }
    "guard throwing redactors and predicates" in {
      val throwing = default.copy(redaction = LogRedaction.default.withBodyRedactor(BodyRedactor(_ => throw boom)))
      renderBody(json("{}"), bytes("{}"), 4096, throwing).unsafeRunSync() shouldEqual
      "body=<redaction failed: java.lang.IllegalStateException> (2 bytes total)"
      val throwingPredicate = default.copy(
        redaction = LogRedaction.default.withBodyRedactor(BodyRedactor.when(_ => throw boom)(BodyRedactor.hidden)))
      renderBody(json("{}"), bytes("{}"), 4096, throwingPredicate).unsafeRunSync() shouldEqual
      "body=<redaction failed: java.lang.IllegalStateException> (2 bytes total)"
    }
    "render a placeholder when an outcome cannot be rendered" in {
      val nullText = default.copy(redaction = LogRedaction.default.withBodyRedactor(BodyRedactor(_ => null)))
      renderBody(json("{}"), bytes("{}"), 4096, nullText).unsafeRunSync() shouldEqual
      "body=<redaction failed: java.lang.NullPointerException> (2 bytes total)"
    }
  }

  "rejectionLine" should {
    val query = MalformedQueryParamRejection("password", "'x' is not valid", None)
    val pin = MalformedQueryParamRejection("pin", "'x' is not valid", None)
    val required = InvalidRequiredValueForQueryParamRejection("password", "SERVER_CANARY", "REDACTED")
    val form = MalformedFormFieldRejection("cardNumber", "'4111111111111111' is not a valid Long value", None)
    val header = MalformedHeaderRejection("Authorization", "bad: Bearer S", None)
    val referer = MalformedHeaderRejection("Referer", "bad: http://[", None)
    val plainHeader = MalformedHeaderRejection("X-Custom", "bad: v", None)
    val missing = MissingQueryParamRejection("x")
    "render by name under ByName" in {
      rejectionLine(Seq(query, pin, required, form, header, referer, plainHeader, missing), default) shouldEqual
      "Request was rejected with rejections: MalformedQueryParamRejection(password,<REDACTED>), " +
      "MalformedQueryParamRejection(pin,'x' is not valid,None), " +
      "InvalidRequiredValueForQueryParamRejection(password,<REDACTED>,<REDACTED>), " +
      "MalformedFormFieldRejection(cardNumber,'4111111111111111' is not a valid Long value,None), " +
      "MalformedHeaderRejection(Authorization,<REDACTED>), MalformedHeaderRejection(Referer,<REDACTED>), " +
      "MalformedHeaderRejection(X-Custom,bad: v,None), MissingQueryParamRejection(x)"
      rejectionLine(Seq(pin, form),
        default.copy(redaction = LogRedaction.default.addNames("pin", "cardNumber"))) shouldEqual
      "Request was rejected with rejections: MalformedQueryParamRejection(pin,<REDACTED>), MalformedFormFieldRejection(cardNumber,<REDACTED>)"
      rejectionLine(Seq(plainHeader), default.copy(redactHeadersWhen = _ == ci"X-Custom")) shouldEqual
      "Request was rejected with rejections: MalformedHeaderRejection(X-Custom,<REDACTED>)"
    }
    "render toString under Shown and mask everything under Hidden" in {
      rejectionLine(Seq(query, required, form, header), none) shouldEqual
      "Request was rejected with rejections: " + Seq(query, required, form, header).mkString(", ")
      rejectionLine(Seq(query, pin, required, form, header, plainHeader, missing),
        default.copy(redaction = LogRedaction.default.hideRejectionValues)) shouldEqual
      "Request was rejected with rejections: MalformedQueryParamRejection(password,<REDACTED>), " +
      "MalformedQueryParamRejection(pin,<REDACTED>), InvalidRequiredValueForQueryParamRejection(password,<REDACTED>,<REDACTED>), " +
      "MalformedFormFieldRejection(cardNumber,<REDACTED>), MalformedHeaderRejection(Authorization,<REDACTED>), " +
      "MalformedHeaderRejection(X-Custom,<REDACTED>), MissingQueryParamRejection(x)"
    }
    "count the field as sensitive when a predicate throws" in {
      rejectionLine(Seq(pin, plainHeader),
        default.copy(redaction = LogRedaction.default.withNames(_ => throw boom))) shouldEqual
      "Request was rejected with rejections: MalformedQueryParamRejection(pin,<REDACTED>), MalformedHeaderRejection(X-Custom,<REDACTED>)"
      rejectionLine(Seq(plainHeader), default.copy(redactHeadersWhen = _ => throw boom)) shouldEqual
      "Request was rejected with rejections: MalformedHeaderRejection(X-Custom,<REDACTED>)"
    }
    "render a placeholder for a rejection whose rendering throws and keep the others" in {
      rejectionLine(Seq(new Rejection { override def toString: String = throw boom }, missing), none) shouldEqual
      "Request was rejected with rejections: <redaction failed: java.lang.IllegalStateException>, MissingQueryParamRejection(x)"
    }
    "escape control characters in every rendered rejection, under every policy" in {
      val forged = MalformedQueryParamRejection("n", "'1\nforged' is not valid", None)
      for (config <- Seq(default, none)) withClue(config.redaction.rejectionValues) {
        rejectionLine(Seq(forged, missing), config) shouldEqual
        "Request was rejected with rejections: MalformedQueryParamRejection(n,'1\\nforged' is not valid,None), MissingQueryParamRejection(x)"
      }
    }
  }

  "notConsumed" should {
    "render the size or ???" in {
      notConsumed(Some(37L)) shouldEqual "body=<not consumed> (37 bytes total)"
      notConsumed(None) shouldEqual "body=<not consumed> (??? bytes total)"
    }
  }
}
