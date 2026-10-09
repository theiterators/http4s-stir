package pl.iterators.stir.server.directives

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.Chunk
import org.http4s.headers.`Content-Type`
import org.http4s.{ Charset, Header, MediaType, Method, Request, Response, Status, Uri }
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.typelevel.ci.CIString
import pl.iterators.stir.server.directives.BodyRedactor._

class BodyRedactorSpec extends AnyWordSpec with Matchers {
  def body(text: String, mediaType: MediaType, truncated: Boolean = false, charset: Option[Charset] = None,
      path: String = "/x"): LoggedBody = {
    val ct = charset.fold(`Content-Type`(mediaType))(cs => `Content-Type`(mediaType, cs))
    val bytes = text.getBytes(charset.getOrElse(Charset.`UTF-8`).nioCharset)
    LoggedBody(Request[IO](Method.POST, Uri.unsafeFromString(path)).withContentType(ct), Chunk.array(bytes), truncated)
  }
  def json(text: String, truncated: Boolean = false) = body(text, MediaType.application.json, truncated)
  def form(text: String, truncated: Boolean = false) =
    body(text, MediaType.application.`x-www-form-urlencoded`, truncated)
  def run(r: BodyRedactor, b: LoggedBody): Outcome = r.redact(b).unsafeRunSync()
  // A request whose Content-Type is the given wire text, which http4s parses on demand as it does for a real request.
  def wire(contentType: String, text: String = "x"): LoggedBody = LoggedBody(
    Request[IO](Method.POST, Uri.unsafeFromString("/x")).putHeaders(Header.Raw(CIString("Content-Type"), contentType)),
    Chunk.array(text.getBytes), truncated = false)
  def kinds(b: LoggedBody) = (b.isJson, b.isForm, b.isPlainText, b.isBinary)

  "LoggedBody" should {
    "decode text with the message charset, UTF-8 when absent" in {
      body("é", MediaType.text.plain, charset = Some(Charset.`ISO-8859-1`)).text shouldEqual "é"
      body("é", MediaType.text.plain).text shouldEqual "é"
      body("é", MediaType.application.json, charset = Some(Charset.`ISO-8859-1`)).charset shouldEqual
      Charset.`ISO-8859-1`
    }
    "classify content types" in {
      val j = json("{}")
      (j.isJson, j.isForm, j.isPlainText, j.isBinary) shouldEqual ((true, false, false, false))
      body("{}", MediaType.unsafeParse("application/vnd.api+json")).isJson shouldBe true
      body("{}", MediaType.unsafeParse("application/vnd.api+json")).isBinary shouldBe false
      val f = form("a=1")
      (f.isJson, f.isForm, f.isBinary) shouldEqual ((false, true, false))
      body("x", MediaType.text.plain).isPlainText shouldBe true
      body("x", MediaType.application.`octet-stream`).isBinary shouldBe true
      val noType =
        LoggedBody(Request[IO](Method.POST, Uri.unsafeFromString("/x")), Chunk.array("x".getBytes), truncated = false)
      (noType.isJson, noType.isForm, noType.isPlainText, noType.isBinary) shouldEqual ((false, false, false, false))
      // http4s keeps every parameter but charset on the media type, and MediaType.equals compares them
      kinds(wire("application/json; version=2")) shouldEqual ((true, false, false, false))
      kinds(wire("application/json; odata.metadata=minimal; charset=utf-8")) shouldEqual ((true, false, false, false))
      kinds(wire("application/vnd.api+json; ext=bulk")) shouldEqual ((true, false, false, false))
      kinds(wire("application/x-www-form-urlencoded; foo=bar")) shouldEqual ((false, true, false, false))
      kinds(wire("text/plain; format=flowed")) shouldEqual ((false, false, true, false))
      kinds(wire("Application/JSON")) shouldEqual ((true, false, false, false))
    }
    "expose the request and not a response" in {
      json("{}", truncated = false).request.map(_.uri.path.renderString) shouldEqual Some("/x")
      LoggedBody(Response[IO](Status.Ok), Chunk.empty, truncated = false).request shouldBe None
    }
  }

  "built-in redactors" should {
    "hide" in {
      run(hidden, json("{}")) shouldEqual Hidden()
    }
    "pass text through and binary as hex, marking truncation" in {
      run(passThrough, body("hi", MediaType.text.plain)) shouldEqual Text("hi", None, incomplete = false)
      run(passThrough, body("hi", MediaType.text.plain, truncated = true)) shouldEqual
      Text("hi", None, incomplete = true)
      val bin = LoggedBody(Request[IO](Method.POST, Uri.unsafeFromString("/x")).withContentType(
          `Content-Type`(MediaType.application.`octet-stream`)), Chunk.array(Array[Byte](0, 1, -1)), truncated = false)
      run(passThrough, bin) shouldEqual Text("0001ff")
      run(passThrough, json("""{"a":1}""")) shouldEqual Text("""{"a":1}""")
    }
    "mask JSON keys and skip other bodies" in {
      run(jsonKeys(SensitiveNames.default), json("""{"password":"x","a":1}""")) shouldEqual
      Text("""{"password":"REDACTED","a":1}""", None, incomplete = false)
      run(jsonKeys(SensitiveNames.default), json("""{"password":"x","a":1,}""")) shouldEqual
      Text("""{"password":"REDACTED","a":1}""", Some("unparseable from position 22"), incomplete = true)
      run(jsonKeys(SensitiveNames.default), json("""{"password":"x""", truncated = true)) shouldEqual
      Text("""{"password":"REDACTED"}""", None, incomplete = true)
      run(jsonKeys(SensitiveNames.default), form("password=x")) shouldEqual Skip
      run(jsonKeys(SensitiveNames.default), body("""{"password":"x"}""", MediaType.text.plain)) shouldEqual Skip
    }
    "apply the content-type redactors whatever parameters the content type carries" in {
      val chain = jsonKeys(SensitiveNames.default).orElse(formFields(SensitiveNames.default)).orElse(passThrough)
      run(chain, wire("application/json; version=2", """{"password":"x"}""")) shouldEqual
      Text("""{"password":"REDACTED"}""")
      run(chain, wire("application/x-www-form-urlencoded; foo=bar", "password=x")) shouldEqual Text("password=REDACTED")
    }
    "mask JSON keys decoded with the body charset" in {
      val latin =
        body("""{"password":"é","note":"é"}""", MediaType.application.json, charset = Some(Charset.`ISO-8859-1`))
      run(jsonKeys(SensitiveNames.default), latin) shouldEqual Text("""{"password":"REDACTED","note":"é"}""")
    }
    "keep only allow-listed keys, conventional names masked first" in {
      run(jsonKeepOnly(Set("scope", "password")), json("""{"password":{"scope":"x"},"scope":"y","z":1}""")) shouldEqual
      Text("""{"password":"REDACTED","scope":"y","z":"REDACTED"}""")
      run(jsonKeepOnly(Set("a"), _ => false), json("""{"password":1,"a":2}""")) shouldEqual
      Text("""{"password":"REDACTED","a":2}""")
    }
    "mask form fields" in {
      run(formFields(SensitiveNames.default), form("user=a&password=b")) shouldEqual Text("user=a&password=REDACTED")
      run(formFields(SensitiveNames.default), form("user=a&pass", truncated = true)) shouldEqual
      Text("user=a", None, incomplete = true)
      run(formFields(SensitiveNames.default), json("{}")) shouldEqual Skip
    }
    "decode percent-encoded form field names with the body charset" in {
      val name = "%00%70%00%61%00%73%00%73%00%77%00%6F%00%72%00%64" // "password" as UTF-16BE bytes
      val utf16 = body(s"$name=CANARY&user=a", MediaType.application.`x-www-form-urlencoded`,
        charset = Some(Charset.`UTF-16BE`))
      run(formFields(SensitiveNames.default), utf16) shouldEqual Text(s"$name=REDACTED&user=a")
    }
    "run json(f) on the closed prefix, hiding repaired or truncated input unless prefix-safe" in {
      var calls = 0
      val upper = BodyRedactor.json({ s => calls += 1; s.toUpperCase }, prefixSafe = false)
      run(upper, json("""{"a":"x"}""")) shouldEqual Text("""{"A":"X"}""", None, incomplete = false)
      run(upper, json("""{"card":"411111111111""")) shouldEqual Hidden(Some("unparseable from position 21"))
      run(upper, json("""{"a":"x"}""", truncated = true)) shouldEqual Hidden(None)
      run(upper, json("hello")) shouldEqual Hidden(Some("unparseable from position 0"))
      calls shouldEqual 1
      run(BodyRedactor.json({ s => calls += 1; s }, prefixSafe = true), json("hello")) shouldEqual
      Text("", Some("unparseable from position 0"), incomplete = true)
      calls shouldEqual 1
      run(BodyRedactor.json(_.toUpperCase, prefixSafe = true), json("""{"card":"411111111111""")) shouldEqual
      Text("""{"CARD":"411111111111"}""", Some("unparseable from position 21"), incomplete = true)
      run(BodyRedactor.json(_.toUpperCase), form("a=1")) shouldEqual Skip
    }
    "compose with orElse and when" in {
      val chain = when(_.isForm)(hidden).orElse(jsonKeys(_ == "k")).orElse(passThrough)
      run(chain, form("k=1")) shouldEqual Hidden()
      run(chain, json("""{"k":1}""")) shouldEqual Text("""{"k":"REDACTED"}""")
      run(chain, body("t", MediaType.text.plain)) shouldEqual Text("t")
      run(when(_.request.exists(_.uri.path.renderString == "/oauth/token"))(hidden),
        json("{}", truncated = false)) shouldEqual Skip
      run(when(_.request.exists(_.uri.path.renderString == "/oauth/token"))(hidden),
        body("{}", MediaType.application.json, path = "/oauth/token")) shouldEqual Hidden()
      run(when(_.isForm)(hidden), json("{}")) shouldEqual Skip
    }
    "guard apply and eval by truncation unless prefix-safe" in {
      run(BodyRedactor(_.text.length.toString), json("abc")) shouldEqual Text("3")
      run(BodyRedactor(_.text.length.toString), json("abc", truncated = true)) shouldEqual Hidden()
      run(BodyRedactor(_.text.length.toString, prefixSafe = true), json("abc", truncated = true)) shouldEqual Text("3")
      run(eval(b => IO.pure(if (b.isJson) Skip else Text("t"))), json("{}")) shouldEqual Skip
      run(eval(b => IO.pure(Text(b.text, Some("n"), incomplete = true))), json("{}")) shouldEqual
      Text("{}", Some("n"), incomplete = true)
      run(eval(_ => IO.pure(Text("x"))), json("{}", truncated = true)) shouldEqual Hidden()
      run(eval(_ => IO.pure(Text("x")), prefixSafe = true), json("{}", truncated = true)) shouldEqual Text("x")
    }
    "adapt an http4s logBody function" in {
      val adapter = fromLogBodyText { stream =>
        Some(stream.through(fs2.text.utf8.decode).compile.string.map(_.reverse))
      }
      run(adapter, json("abc")) shouldEqual Text("cba")
      run(fromLogBodyText(_ => None), json("abc")) shouldEqual Hidden()
      run(adapter, json("abc", truncated = true)) shouldEqual Hidden()
    }
    "fail the IO when user code throws, instead of logging raw content" in {
      // redact itself must return the failed IO: no IO.defer around it, a synchronous throw would escape this helper
      def failure(r: BodyRedactor): Either[String, Outcome] =
        r.redact(json("{}")).attempt.unsafeRunSync().left.map(_.getMessage)
      failure(BodyRedactor(_ => throw new IllegalStateException("boom"))) shouldEqual Left("boom")
      failure(when(_ => throw new IllegalStateException("pred"))(hidden).orElse(passThrough)) shouldEqual Left("pred")
      failure(when(_ => throw new IllegalStateException("pred"))(passThrough)) shouldEqual Left("pred")
      failure(eval(_ => throw new IllegalStateException("boom"))) shouldEqual Left("boom")
      failure(fromLogBodyText(_ => throw new IllegalStateException("boom"))) shouldEqual Left("boom")
    }
    "share one mask with the scanner and the URI redactor" in {
      Mask shouldEqual "REDACTED"
      JsonRedaction.MaskLiteral shouldEqual "\"REDACTED\""
      UriRedactor.query(_ => true)(Uri.unsafeFromString("/?a=b")).renderString shouldEqual "/?a=REDACTED"
    }
  }
}
