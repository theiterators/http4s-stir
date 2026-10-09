package pl.iterators.stir.server.directives

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import io.circe.parser.parse
import org.http4s.headers.`Content-Type`
import org.http4s.{ Header, MediaType, Response, Status, UrlForm }
import org.typelevel.ci._

class DebuggingDirectivesCanarySpec extends RoutingSpec {
  override implicit def runtime: IORuntime = IORuntime.global
  val Marker = "CANARY"
  var lines = Vector.empty[String]
  val logAction: Option[String => IO[Unit]] = Some(msg => IO { lines :+= msg })

  val requestJson =
    """{
      |  "user": "alice",
      |  "password": "CANARY-pass",
      |  "nested": {"apiKey": "CANARY-key", "n": 1, "secret": {"deep": ["CANARY-deep", 2]}},
      |  "tokens": ["CANARY-t1", "CANARY-t2"],
      |  "card": 4111111111111111,
      |  "note": "card 4111111111111111",
      |  "list": [{"client_secret": "CANARY-cs", "ok": true}],
      |  "ssn": 123456789
      |}""".stripMargin
  val responseJson =
    """{"access_token":"CANARY-access","refresh_token":"CANARY-refresh","expires":3600,"user":{"id":7,"sessionId":"CANARY-sess"}}"""
  val formBody = "user=alice&password=CANARY-form&pin=1234&client_secret=CANARY-fcs;token=CANARY-ftok"
  val jsonType = `Content-Type`(MediaType.application.json)

  val echo = entity(as[String]) { _ =>
    complete(Response[IO](Status.Ok).withEntity(responseJson).withContentType(jsonType)
      .putHeaders(Header.Raw(ci"Location", "https://app.example/cb?access_token=CANARY-loc&x=1")))
  }
  def jsonRequest = Post("/login?token=CANARY-q&user=alice").withEntity(requestJson).withContentType(jsonType)
    .putHeaders(Header.Raw(ci"X-Api-Key", "CANARY-header"), Header.Raw(ci"Authorization", "Bearer CANARY-auth"),
      Header.Raw(ci"Referer", "https://r.example/?reset_token=CANARY-ref"), Header.Raw(ci"Cookie", "s=CANARY-cookie"))
  def formRequest = Post("/login").withEntity(UrlForm.decodeString(org.http4s.Charset.`UTF-8`)(formBody).toOption.get)

  val card = "\\b[0-9]{13,19}\\b".r
  val policies: Seq[(String, LogRedaction)] = Seq(
    "default" -> LogRedaction.default,
    "addNames" -> LogRedaction.default.addNames("pin"),
    "redactPath" -> LogRedaction.default.redactPath("/login/{x}"),
    "keepOnly" ->
    LogRedaction.default.withBodyRedactor(BodyRedactor.when(_.request.exists(_.uri.path.renderString == "/login"))(
      BodyRedactor.jsonKeepOnly(Set("user", "n", "ok", "expires", "id")))),
    "values" -> LogRedaction.default.redactBodyValues(card.replaceAllIn(_, "REDACTED")).hideRejectionValues,
    "hidden" -> LogRedaction.default.withBodyRedactor(BodyRedactor.hidden),
    "everything" ->
    LogRedaction.default.addNames("pin", "iban").redactPath("/users/*/tokens/{token}").hideRejectionValues,
    "throwing" -> LogRedaction.default.withBodyRedactor(BodyRedactor(_ => throw new IllegalStateException("boom"))),
    "throwingPredicate" -> LogRedaction.default.withBodyRedactor(
      BodyRedactor.when(_ => throw new IllegalStateException("p"))(BodyRedactor.hidden)))

  // Planted values without the marker are found by a prefix of them, because a cut can leave only the first
  // characters of a value in a line.
  // ssn 123456789 is masked by name under every policy.
  val SsnPrefix = "12345"
  // pin=1234 in the form body is masked by name only where "pin" was added as a name.
  val PinPrefix = "pin=1"
  // 4111111111111111 is masked by a value rule only (spec section 5.8, example 4), never by name, and the rule matches
  // 13 digits or more: up to 12 digits of a cut number stay visible unless the whole body is hidden.
  val CardPrefix = "41111111"

  /** What must not appear in a line logged under a policy, on top of `Marker` and `SsnPrefix`. */
  val forbidden: Map[String, Seq[String]] = Map(
    "addNames" -> Seq(PinPrefix),
    "everything" -> Seq(PinPrefix),
    "values" -> Seq(CardPrefix))
  // a renamed or removed policy must not silently drop its assertions
  require(forbidden.keySet.subsetOf(policies.map(_._1).toSet), "forbidden names a policy that does not exist")

  /** The text between `body="` and the closing quote, with the note and truncation suffix removed. */
  def bodyText(line: String): Option[String] = {
    val start = line.indexOf("body=\"")
    if (start < 0) None
    else {
      var rest = line.substring(start + 6)
      val suffix = rest.indexOf(" ... (")
      if (suffix >= 0) rest = rest.substring(0, suffix)
      val note = rest.lastIndexOf("\" (unparseable from position ")
      if (note >= 0) rest = rest.substring(0, note + 1)
      Some(rest.dropRight(1))
    }
  }

  def assertSafe(policyName: String, maxBodyBytes: Int): Unit =
    withClue(s"policy=$policyName maxBodyBytes=$maxBodyBytes\n${lines.mkString("\n")}") {
      lines should not be empty
      val mustNotAppear = Seq(Marker, SsnPrefix) ++ forbidden.getOrElse(policyName, Seq.empty)
      for (line <- lines) {
        for (value <- mustNotAppear) (line should not).include(value)
        if (line.contains("application/json")) bodyText(line).filter(_.nonEmpty).foreach { text =>
          parse(text).isRight shouldBe true
        }
      }
    }

  "The logging directives" should {
    "never log a planted secret for any policy and any maxBodyBytes, and never fail the request" in {
      val longest = math.max(requestJson.getBytes("UTF-8").length, responseJson.getBytes("UTF-8").length)
      for ((name, policy) <- policies; max <- 1 to longest) {
        lines = Vector.empty
        jsonRequest ~> logRequestResult(logAction = logAction, maxBodyBytes = max, redaction = policy)(echo) ~> check {
          response.status shouldEqual Status.Ok
          responseAs[String] shouldEqual responseJson
        }
        assertSafe(name, max)
        lines = Vector.empty
        formRequest                                  ~> logRequestResult(logAction = logAction, maxBodyBytes = max, redaction = policy)(
          formField("user") { _ => complete("ok") }) ~> check {
          responseAs[String] shouldEqual "ok"
        }
        assertSafe(name, max)
      }
    }
    "never log a planted secret in a rejection under the default policy" in {
      for ((name, policy) <- policies if name != "throwing" && name != "throwingPredicate") {
        lines = Vector.empty
        Get("/q?password=CANARY-x")                                                       ~> logRequestResult(logAction = logAction, redaction = policy)(
          parameter("password".requiredValue("CANARY-expected")) { _ => complete("ok") }) ~> check {
          handled shouldBe false
        }
        assertSafe(name, 4096)
        lines = Vector.empty
        Get("/h").putHeaders(Header.Raw(ci"Authorization", "Bearer CANARY-h")) ~>
        logRequestResult(logAction = logAction,
          redaction = policy)(headerValue { h =>
          if (h.name == ci"Authorization") throw new IllegalArgumentException(s"bad: ${h.value}") else None
        } { (_: Nothing) => complete("ok") }) ~> check {
          handled shouldBe false
        }
        assertSafe(name, 4096)
      }
    }
    "log the plain-text and JSON content that is not sensitive" in {
      lines = Vector.empty
      jsonRequest ~> logRequestResult(logAction = logAction)(echo) ~> check {
        responseAs[String] shouldEqual responseJson
      }
      lines.head should include("\"user\":\"alice\"")
      lines.head should include("\"n\":1")
      lines.head should include("user=alice")
      lines.last should include("\"expires\":3600")
    }
  }
}
