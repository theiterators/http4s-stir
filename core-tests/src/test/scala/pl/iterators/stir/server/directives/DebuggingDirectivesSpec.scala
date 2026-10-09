package pl.iterators.stir.server.directives

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import fs2.{ Chunk, Stream }
import org.http4s.headers.{ `Content-Length`, `Content-Type`, `Transfer-Encoding` }
import org.http4s.{ Charset, Header, MediaType, Response, Status, TransferCoding, UrlForm }
import org.typelevel.ci._
import pl.iterators.stir.impl.util._

class DebuggingDirectivesSpec extends RoutingSpec {
  override implicit def runtime: IORuntime = IORuntime.global
  var debugMsg = ""

  def resetDebugMsg(): Unit = { debugMsg = "" }

  def normalizedDebugMsg(): String = debugMsg

  val logAction: Option[String => IO[Unit]] = Some(msg => IO { debugMsg += msg + '\n' })

  val json = """{"user":"alice","password":"hunter2"}"""
  val jsonResponse = """{"access_token":"tok","expires":3600}"""
  val prettyJson = "{\n  \"user\": \"alice\",\n  \"password\": \"hunter2\"\n}"
  val jsonType = `Content-Type`(MediaType.application.json)

  def jsonRequest(body: String) = Post("/login").withEntity(body).withContentType(jsonType)

  val echoJson = entity(as[String]) { _ =>
    complete(Response[IO](Status.Ok).withEntity(jsonResponse).withContentType(jsonType))
  }

  "The 'logRequest' directive" should {
    "produce a proper log message for incoming requests" in {
      val route = logRequest(logAction = logAction, redaction = LogRedaction.none)(completeOk)

      resetDebugMsg()
      Get("/hello") ~> route ~> check {
        response.status shouldEqual Status.Ok
        normalizedDebugMsg() shouldEqual "HTTP/1.1 GET /hello Headers()\n"
      }
    }
  }

  "The 'logResult' directive" should {
    "produce a proper log message for outgoing responses" in {
      val route = logResult(logAction = logAction, redaction = LogRedaction.none)(completeOk)

      resetDebugMsg()
      Get("/hello") ~> route ~> check {
        response.status shouldEqual Status.Ok
        normalizedDebugMsg() shouldEqual "HTTP/1.1 200 OK Headers()\n"
      }
    }
  }

  "The 'logRequestResult' directive" should {
    "produce proper log messages for outgoing responses, thereby showing the corresponding request" in {
      val route = logRequestResult(logAction = logAction, redaction = LogRedaction.none)(completeOk)

      resetDebugMsg()
      Get("/hello") ~> route ~> check {
        response.status shouldEqual Status.Ok
        normalizedDebugMsg() shouldEqual
        """|HTTP/1.1 GET /hello Headers()
           |HTTP/1.1 200 OK Headers()
           |""".stripMarginWithNewline("\n")
      }
    }

    "log a compact JSON request and response body" in {
      resetDebugMsg()
      jsonRequest(json) ~> logRequestResult(logAction = logAction, redaction = LogRedaction.none)(echoJson) ~> check {
        responseAs[String] shouldEqual jsonResponse
        normalizedDebugMsg() shouldEqual
        """|HTTP/1.1 POST /login Headers(Content-Length: 37, Content-Type: application/json) body="{"user":"alice","password":"hunter2"}"
           |HTTP/1.1 200 OK Headers(Content-Length: 37, Content-Type: application/json) body="{"access_token":"tok","expires":3600}"
           |""".stripMarginWithNewline("\n")
      }
    }

    "log a pretty printed JSON request body as received, control characters escaped" in {
      resetDebugMsg()
      jsonRequest(prettyJson) ~> logRequestResult(logAction = logAction, redaction = LogRedaction.none)(echoJson) ~>
      check {
        responseAs[String] shouldEqual jsonResponse
        normalizedDebugMsg() shouldEqual
        """|HTTP/1.1 POST /login Headers(Content-Length: 46, Content-Type: application/json) body="{\n  "user": "alice",\n  "password": "hunter2"\n}"
           |HTTP/1.1 200 OK Headers(Content-Length: 37, Content-Type: application/json) body="{"access_token":"tok","expires":3600}"
           |""".stripMarginWithNewline("\n")
      }
    }

    "indicate truncation when the body exceeds maxBodyBytes" in {
      resetDebugMsg()
      jsonRequest(json) ~> logRequestResult(logAction = logAction, maxBodyBytes = 10, redaction = LogRedaction.none)(
        echoJson)       ~> check {
        responseAs[String] shouldEqual jsonResponse
        normalizedDebugMsg() shouldEqual
        """|HTTP/1.1 POST /login Headers(Content-Length: 37, Content-Type: application/json) body="{"user":"a" ... (37 bytes total)
           |HTTP/1.1 200 OK Headers(Content-Length: 37, Content-Type: application/json) body="{"access_t" ... (37 bytes total)
           |""".stripMarginWithNewline("\n")
      }
    }

    "indicate a request body that was never consumed" in {
      resetDebugMsg()
      jsonRequest(json) ~> logRequestResult(logAction = logAction, redaction = LogRedaction.none)(complete("ok")) ~>
      check {
        responseAs[String] shouldEqual "ok"
        normalizedDebugMsg() shouldEqual
        """|HTTP/1.1 POST /login Headers(Content-Length: 37, Content-Type: application/json) body=<not consumed> (37 bytes total)
           |HTTP/1.1 200 OK Headers(Content-Length: 2, Content-Type: text/plain; charset=UTF-8) body="ok"
           |""".stripMarginWithNewline("\n")
      }
    }

    "log a chunked request without a body part" in {
      val chunked = Post("/login", Stream.chunk(Chunk.array(json.getBytes("UTF-8"))).covary[IO])
        .putHeaders(`Transfer-Encoding`(TransferCoding.chunked)).withContentType(jsonType)
      resetDebugMsg()
      chunked ~> logRequestResult(logAction = logAction, redaction = LogRedaction.none)(echoJson) ~> check {
        responseAs[String] shouldEqual jsonResponse
        normalizedDebugMsg() shouldEqual
        """|HTTP/1.1 POST /login Headers(Transfer-Encoding: chunked, Content-Type: application/json)
           |HTTP/1.1 200 OK Headers(Content-Length: 37, Content-Type: application/json) body="{"access_token":"tok","expires":3600}"
           |""".stripMarginWithNewline("\n")
      }
    }

    "log a binary body as hex" in {
      val binary = Post("/upload").withEntity(Array[Byte](0, 1, -1))
        .withContentType(`Content-Type`(MediaType.application.`octet-stream`))
      resetDebugMsg()
      binary ~> logRequestResult(logAction = logAction, redaction = LogRedaction.none)(entity(as[Array[Byte]]) { _ =>
        complete("ok")
      }) ~> check {
        responseAs[String] shouldEqual "ok"
        normalizedDebugMsg() shouldEqual
        """|HTTP/1.1 POST /upload Headers(Content-Length: 3, Content-Type: application/octet-stream) body="0001ff"
           |HTTP/1.1 200 OK Headers(Content-Length: 2, Content-Type: text/plain; charset=UTF-8) body="ok"
           |""".stripMarginWithNewline("\n")
      }
    }

    "omit the header section when logHeaders = false" in {
      resetDebugMsg()
      jsonRequest(json) ~> logRequestResult(logAction = logAction, logHeaders = false, redaction = LogRedaction.none)(
        echoJson)       ~> check {
        responseAs[String] shouldEqual jsonResponse
        normalizedDebugMsg() shouldEqual
        """|HTTP/1.1 POST /login body="{"user":"alice","password":"hunter2"}"
           |HTTP/1.1 200 OK body="{"access_token":"tok","expires":3600}"
           |""".stripMarginWithNewline("\n")
      }
    }

    "log a text/plain body" in {
      resetDebugMsg()
      Post("/t").withEntity("hello world")          ~> logRequestResult(logAction = logAction, redaction = LogRedaction.none)(
        entity(as[String]) { _ => complete("ok") }) ~> check {
        responseAs[String] shouldEqual "ok"
        normalizedDebugMsg() shouldEqual
        """|HTTP/1.1 POST /t Headers(Content-Length: 11, Content-Type: text/plain; charset=UTF-8) body="hello world"
           |HTTP/1.1 200 OK Headers(Content-Length: 2, Content-Type: text/plain; charset=UTF-8) body="ok"
           |""".stripMarginWithNewline("\n")
      }
    }

    "indicate an unknown total size for a response without Content-Length" in {
      val streamed =
        complete(Response[IO](Status.Ok, body = Stream.chunk(Chunk.array("abc".getBytes("UTF-8"))).covary[IO]))
      resetDebugMsg()
      Get("/s") ~> logRequestResult(logAction = logAction, redaction = LogRedaction.none)(streamed) ~> check {
        responseAs[String] shouldEqual "abc"
        normalizedDebugMsg() shouldEqual
        """|HTTP/1.1 GET /s Headers()
           |HTTP/1.1 200 OK Headers() body="abc" ... (??? bytes total)
           |""".stripMarginWithNewline("\n")
      }
    }

    "log a MalformedQueryParamRejection with its value" in {
      resetDebugMsg()
      Get("/q?pin=12ab-SECRET")                           ~> logRequestResult(logAction = logAction, redaction = LogRedaction.none)(
        parameter("pin".as[Int]) { _ => complete("ok") }) ~> check {
        handled shouldBe false
        normalizedDebugMsg() shouldEqual
        """|HTTP/1.1 GET /q?pin=12ab-SECRET Headers()
           |Request was rejected with rejections: MalformedQueryParamRejection(pin,'12ab-SECRET' is not a valid 32-bit signed integer value,Some(java.lang.NumberFormatException: For input string: "12ab-SECRET"))
           |""".stripMarginWithNewline("\n")
      }
    }

    "log an InvalidRequiredValueForQueryParamRejection with both values" in {
      resetDebugMsg()
      Get("/q?password=x")                                                            ~> logRequestResult(logAction = logAction, redaction = LogRedaction.none)(
        parameter("password".requiredValue("SERVER_CANARY")) { _ => complete("ok") }) ~> check {
        handled shouldBe false
        normalizedDebugMsg() shouldEqual
        """|HTTP/1.1 GET /q?password=x Headers()
           |Request was rejected with rejections: InvalidRequiredValueForQueryParamRejection(password,SERVER_CANARY,x)
           |""".stripMarginWithNewline("\n")
      }
    }

    "log a MalformedFormFieldRejection with its value" in {
      resetDebugMsg()
      Post("/pay").withEntity(UrlForm("cardNumber" -> "9999999999999999999")) ~>
      logRequestResult(logAction = logAction, redaction = LogRedaction.none)(
        formField("cardNumber".as[Long]) { _ => complete("ok") }) ~> check {
        handled shouldBe false
        normalizedDebugMsg() shouldEqual
        """|HTTP/1.1 POST /pay Headers(Content-Length: 30, Content-Type: application/x-www-form-urlencoded; charset=UTF-8) body="cardNumber=9999999999999999999"
           |Request was rejected with rejections: MalformedFormFieldRejection(cardNumber,'9999999999999999999' is not a valid 64-bit signed integer value,Some(java.lang.NumberFormatException: For input string: "9999999999999999999"))
           |""".stripMarginWithNewline("\n")
      }
    }

    "log a MalformedHeaderRejection for Authorization with its value" in {
      val failing = headerValue { h =>
        if (h.name == ci"Authorization") throw new IllegalArgumentException(s"bad: ${h.value}") else None
      }
      resetDebugMsg()
      Get("/h").putHeaders(Header.Raw(ci"Authorization", "Bearer SECRET-TOKEN"))                          ~> logRequestResult(
        logAction = logAction, redaction = LogRedaction.none)(failing { (_: Nothing) => complete("ok") }) ~> check {
        handled shouldBe false
        normalizedDebugMsg() shouldEqual
        """|HTTP/1.1 GET /h Headers(Authorization: <REDACTED>)
           |Request was rejected with rejections: MalformedHeaderRejection(Authorization,bad: Bearer SECRET-TOKEN,Some(java.lang.IllegalArgumentException: bad: Bearer SECRET-TOKEN))
           |""".stripMarginWithNewline("\n")
      }
    }

    "log an unparseable Referer as received" in {
      resetDebugMsg()
      Get("/hello").putHeaders(Header.Raw(ci"Referer", "http://[")) ~> logRequestResult(logAction = logAction,
        redaction = LogRedaction.none)(completeOk)                  ~> check {
        response.status shouldEqual Status.Ok
        normalizedDebugMsg() shouldEqual
        """|HTTP/1.1 GET /hello Headers(Referer: http://[)
           |HTTP/1.1 200 OK Headers()
           |""".stripMarginWithNewline("\n")
      }
    }

    "log a Location header as received" in {
      val redirect = complete(Response[IO](Status.Found).putHeaders(
        Header.Raw(ci"Location", "https://app.example/cb?access_token=X&x=1")))
      resetDebugMsg()
      Get("/go") ~> logRequestResult(logAction = logAction, redaction = LogRedaction.none)(redirect) ~> check {
        response.status shouldEqual Status.Found
        normalizedDebugMsg() shouldEqual
        """|HTTP/1.1 GET /go Headers()
           |HTTP/1.1 302 Found Headers(Location: https://app.example/cb?access_token=X&x=1)
           |""".stripMarginWithNewline("\n")
      }
    }

    "log query, userinfo and headers as received, redacting only SensitiveHeaders" in {
      resetDebugMsg()
      Get("http://bob:pw@example.com/a?reset_token=QS&user=alice%20b&flag&t=1&t=2")
        .putHeaders(Header.Raw(ci"X-Api-Key", "KEY1"), Header.Raw(ci"Cookie", "c=1")) ~> logRequestResult(
        logAction = logAction, redaction = LogRedaction.none)(completeOk)             ~> check {
        response.status shouldEqual Status.Ok
        normalizedDebugMsg() shouldEqual
        """|HTTP/1.1 GET http://bob:pw@example.com/a?reset_token=QS&user=alice%20b&flag&t=1&t=2 Headers(X-Api-Key: KEY1, Cookie: <REDACTED>)
           |HTTP/1.1 200 OK Headers()
           |""".stripMarginWithNewline("\n")
      }
    }
  }

  "The 'logRequestResult' directive with the default policy" should {
    val login =
      jsonRequest(json).putHeaders(Header.Raw(ci"X-Api-Key", "KEY1"), Header.Raw(ci"Authorization", "Bearer T"))
        .withUri(org.http4s.Uri.unsafeFromString("/login?token=QS&x=1"))

    "mask secrets in the request line, headers, request body and response body" in {
      resetDebugMsg()
      login ~> logRequestResult(logAction = logAction)(echoJson) ~> check {
        responseAs[String] shouldEqual jsonResponse
        normalizedDebugMsg() shouldEqual
        """|HTTP/1.1 POST /login?token=REDACTED&x=1 Headers(Content-Length: 37, Content-Type: application/json, X-Api-Key: <REDACTED>, Authorization: <REDACTED>) body="{"user":"alice","password":"REDACTED"}"
           |HTTP/1.1 200 OK Headers(Content-Length: 37, Content-Type: application/json) body="{"access_token":"REDACTED","expires":3600}"
           |""".stripMarginWithNewline("\n")
      }
    }

    "log pretty printed JSON compact" in {
      resetDebugMsg()
      jsonRequest(prettyJson) ~> logRequestResult(logAction = logAction)(echoJson) ~> check {
        responseAs[String] shouldEqual jsonResponse
        normalizedDebugMsg() should startWith(
          "HTTP/1.1 POST /login Headers(Content-Length: 46, Content-Type: application/json) body=\"{\"user\":\"alice\",\"password\":\"REDACTED\"}\"\n")
      }
    }

    "close truncated JSON and keep the truncation suffix" in {
      resetDebugMsg()
      jsonRequest(json) ~> logRequestResult(logAction = logAction, maxBodyBytes = 10)(echoJson) ~> check {
        responseAs[String] shouldEqual jsonResponse
        normalizedDebugMsg() shouldEqual
        """|HTTP/1.1 POST /login Headers(Content-Length: 37, Content-Type: application/json) body="{"user":"a"}" ... (37 bytes total)
           |HTTP/1.1 200 OK Headers(Content-Length: 37, Content-Type: application/json) body="{}" ... (37 bytes total)
           |""".stripMarginWithNewline("\n")
      }
    }

    "mask form fields" in {
      resetDebugMsg()
      Post("/login").withEntity(UrlForm("user" -> "alice", "password" -> "hunter2")) ~> logRequestResult(
        logAction = logAction)(formField("user") { _ => complete("ok") })            ~> check {
        responseAs[String] shouldEqual "ok"
        normalizedDebugMsg() should startWith(
          "HTTP/1.1 POST /login Headers(Content-Length: 27, Content-Type: application/x-www-form-urlencoded; charset=UTF-8) body=\"user=alice&password=REDACTED\"\n")
      }
    }

    "hide bodies that are neither JSON, form nor plain text" in {
      val xml = Post("/x").withEntity("<a>1</a>").withContentType(`Content-Type`(MediaType.application.xml))
      resetDebugMsg()
      xml ~> logRequestResult(logAction = logAction)(entity(as[String]) { _ => complete("ok") }) ~> check {
        responseAs[String] shouldEqual "ok"
        normalizedDebugMsg() should startWith(
          "HTTP/1.1 POST /x Headers(Content-Length: 8, Content-Type: application/xml) body=<hidden> (8 bytes total)\n")
      }
      val binary = Post("/upload").withEntity(Array[Byte](0, 1, -1))
        .withContentType(`Content-Type`(MediaType.application.`octet-stream`))
      resetDebugMsg()
      binary ~> logRequestResult(logAction = logAction)(entity(as[Array[Byte]]) { _ => complete("ok") }) ~> check {
        responseAs[String] shouldEqual "ok"
        normalizedDebugMsg() should startWith(
          "HTTP/1.1 POST /upload Headers(Content-Length: 3, Content-Type: application/octet-stream) body=<hidden> (3 bytes total)\n")
      }
    }

    "decode a JSON body with its charset before masking" in {
      val text = "{\"password\":\"é\",\"note\":\"é\"}"
      val latin = Post("/x", Stream.chunk(Chunk.array(text.getBytes("ISO-8859-1"))).covary[IO])
        .putHeaders(`Content-Length`.unsafeFromLong(27L))
        .withContentType(`Content-Type`(MediaType.application.json, Charset.`ISO-8859-1`))
      resetDebugMsg()
      latin ~> logRequestResult(logAction = logAction)(entity(as[String]) { _ => complete("ok") }) ~> check {
        responseAs[String] shouldEqual "ok"
        normalizedDebugMsg() should startWith(
          "HTTP/1.1 POST /x Headers(Content-Length: 27, Content-Type: application/json; charset=ISO-8859-1) body=\"{\"password\":\"REDACTED\",\"note\":\"é\"}\"\n")
      }
    }

    "keep a raw newline inside a JSON string and escape it in the line" in {
      resetDebugMsg()
      jsonRequest("{\"a\":\"x\ny\"}") ~> logRequestResult(logAction = logAction)(echoJson) ~> check {
        responseAs[String] shouldEqual jsonResponse
        normalizedDebugMsg() should startWith(
          "HTTP/1.1 POST /login Headers(Content-Length: 11, Content-Type: application/json) body=\"{\"a\":\"x\\ny\"}\"\n")
      }
    }

    "not count a body of exactly maxBodyBytes as truncated, but an unknown-length one of that size" in {
      resetDebugMsg()
      jsonRequest("""{"a":"bc"}""") ~> logRequestResult(logAction = logAction, maxBodyBytes = 10)(echoJson) ~> check {
        responseAs[String] shouldEqual jsonResponse
        normalizedDebugMsg() should startWith(
          "HTTP/1.1 POST /login Headers(Content-Length: 10, Content-Type: application/json) body=\"{\"a\":\"bc\"}\"\n")
      }
      val streamed =
        complete(Response[IO](Status.Ok, body = Stream.chunk(Chunk.array("0123456789".getBytes("UTF-8"))).covary[IO])
          .withContentType(`Content-Type`(MediaType.text.plain)))
      val flag = LogRedaction.default.withBodyRedactor(BodyRedactor(_.truncated.toString, prefixSafe = true))
      resetDebugMsg()
      Get("/s") ~> logResult(logAction = logAction, maxBodyBytes = 10, redaction = flag)(streamed) ~> check {
        responseAs[String] shouldEqual "0123456789"
        normalizedDebugMsg() shouldEqual
        "HTTP/1.1 200 OK Headers(Content-Type: text/plain) body=\"true\" ... (??? bytes total)\n"
      }
    }

    "transform Referer and Location and mask them when unparseable" in {
      resetDebugMsg()
      Get("/hello").putHeaders(Header.Raw(ci"Referer", "https://app.example/cb?access_token=REF&x=1")) ~>
      logRequestResult(
        logAction = logAction)(completeOk) ~> check {
        response.status shouldEqual Status.Ok
        normalizedDebugMsg() should
        startWith("HTTP/1.1 GET /hello Headers(Referer: https://app.example/cb?access_token=REDACTED&x=1)\n")
      }
      resetDebugMsg()
      Get("/hello").putHeaders(Header.Raw(ci"Referer", "http://[")) ~> logRequestResult(logAction = logAction)(
        completeOk)                                                 ~> check {
        response.status shouldEqual Status.Ok
        normalizedDebugMsg() should startWith("HTTP/1.1 GET /hello Headers(Referer: <REDACTED>)\n")
      }
      val redirect = complete(Response[IO](Status.Found).putHeaders(
        Header.Raw(ci"Location", "https://app.example/cb?access_token=X&x=1")))
      resetDebugMsg()
      Get("/go") ~> logRequestResult(logAction = logAction)(redirect) ~> check {
        response.status shouldEqual Status.Found
        normalizedDebugMsg() shouldEqual
        """|HTTP/1.1 GET /go Headers()
           |HTTP/1.1 302 Found Headers(Location: https://app.example/cb?access_token=REDACTED&x=1)
           |""".stripMarginWithNewline("\n")
      }
    }

    "render rejections by name" in {
      resetDebugMsg()
      Get("/q?password=x")                                                            ~> logRequestResult(logAction = logAction)(
        parameter("password".requiredValue("SERVER_CANARY")) { _ => complete("ok") }) ~> check {
        handled shouldBe false
        normalizedDebugMsg() shouldEqual
        """|HTTP/1.1 GET /q?password=REDACTED Headers()
           |Request was rejected with rejections: InvalidRequiredValueForQueryParamRejection(password,<REDACTED>,<REDACTED>)
           |""".stripMarginWithNewline("\n")
      }
      resetDebugMsg()
      Get("/q?password=REDACTED")                                                     ~> logRequestResult(logAction = logAction)(
        parameter("password".requiredValue("SERVER_CANARY")) { _ => complete("ok") }) ~> check {
        handled shouldBe false
        (normalizedDebugMsg() should not).include("SERVER_CANARY")
        normalizedDebugMsg() should
        include("InvalidRequiredValueForQueryParamRejection(password,<REDACTED>,<REDACTED>)")
      }
      resetDebugMsg()
      Get("/q?pin=12ab-SECRET") ~> logRequestResult(logAction = logAction)(parameter("pin".as[Int]) { _ =>
        complete("ok")
      }) ~> check {
        handled shouldBe false
        normalizedDebugMsg() should include("MalformedQueryParamRejection(pin,'12ab-SECRET' is not a valid")
      }
      resetDebugMsg()
      Get("/q?pin=12ab-SECRET") ~>
      logRequestResult(logAction = logAction, redaction = LogRedaction.default.addNames("pin"))(
        parameter("pin".as[Int]) { _ => complete("ok") }) ~> check {
        handled shouldBe false
        normalizedDebugMsg() shouldEqual
        """|HTTP/1.1 GET /q?pin=REDACTED Headers()
           |Request was rejected with rejections: MalformedQueryParamRejection(pin,<REDACTED>)
           |""".stripMarginWithNewline("\n")
      }
      val failing = headerValue { h =>
        if (h.name == ci"Authorization") throw new IllegalArgumentException(s"bad: ${h.value}") else None
      }
      resetDebugMsg()
      Get("/h").putHeaders(Header.Raw(ci"Authorization", "Bearer SECRET-TOKEN")) ~> logRequestResult(
        logAction = logAction)(failing { (_: Nothing) => complete("ok") })       ~> check {
        handled shouldBe false
        normalizedDebugMsg() shouldEqual
        """|HTTP/1.1 GET /h Headers(Authorization: <REDACTED>)
           |Request was rejected with rejections: MalformedHeaderRejection(Authorization,<REDACTED>)
           |""".stripMarginWithNewline("\n")
      }
    }

    "escape control characters in a rejection line" in {
      resetDebugMsg()
      Get("/q?n=%0D%0Aforged%20line") ~> logResult(logAction = logAction)(parameter("n".as[Int]) { _ =>
        complete("ok")
      }) ~> check {
        handled shouldBe false
        // one entry, one line: CR LF from the query value appear as the two-character escapes
        normalizedDebugMsg() shouldEqual
        "Request was rejected with rejections: MalformedQueryParamRejection(n,'\\r\\nforged line' is not a valid 32-bit signed integer value,Some(java.lang.NumberFormatException: For input string: \"\\r\\nforged line\"))\n"
      }
    }

    "hide form rejection values under example 4's policy and show them by default" in {
      val card = "\\b[0-9]{13,19}\\b".r
      val policy = LogRedaction.default.redactBodyValues(card.replaceAllIn(_, "REDACTED")).hideRejectionValues
      val route = formField("cardNumber".as[Long]) { _ => complete("ok") }
      resetDebugMsg()
      Post("/pay").withEntity(UrlForm("cardNumber" -> "9999999999999999999")) ~> logRequestResult(logAction = logAction,
        redaction = policy)(route)                                            ~> check {
        handled shouldBe false
        normalizedDebugMsg() shouldEqual
        """|HTTP/1.1 POST /pay Headers(Content-Length: 30, Content-Type: application/x-www-form-urlencoded; charset=UTF-8) body="cardNumber=REDACTED"
           |Request was rejected with rejections: MalformedFormFieldRejection(cardNumber,<REDACTED>)
           |""".stripMarginWithNewline("\n")
      }
      resetDebugMsg()
      Post("/pay").withEntity(UrlForm("cardNumber" -> "9999999999999999999")) ~>
      logRequestResult(logAction = logAction)(
        route) ~> check {
        handled shouldBe false
        normalizedDebugMsg() should include(
          "MalformedFormFieldRejection(cardNumber,'9999999999999999999' is not a valid 64-bit signed integer value")
      }
    }

    "apply the examples of spec section 5.8" in {
      resetDebugMsg()
      jsonRequest("""{"pin":"1234","user":"alice"}""")              ~> logRequestResult(logAction = logAction,
        redaction = LogRedaction.default.addNames("pin"))(echoJson) ~> check {
        responseAs[String] shouldEqual jsonResponse
        normalizedDebugMsg() should include("body=\"{\"pin\":\"REDACTED\",\"user\":\"alice\"}\"")
      }
      resetDebugMsg()
      Get("/password-reset/XYZ123?x=1")                                                     ~> logRequestResult(logAction = logAction,
        redaction = LogRedaction.default.redactPath("/password-reset/{token}"))(completeOk) ~> check {
        response.status shouldEqual Status.Ok
        normalizedDebugMsg() should startWith("HTTP/1.1 GET /password-reset/REDACTED?x=1 Headers()\n")
      }
      val tokenRequests = BodyRedactor.when(_.request.exists(_.uri.path.renderString == "/oauth/token"))(
        BodyRedactor.jsonKeepOnly(Set("grant_type", "scope")))
      resetDebugMsg()
      jsonRequest("""{"grant_type":"password","username":"alice","password":"x"}""")
        .withUri(org.http4s.Uri.unsafeFromString("/oauth/token"))                   ~> logRequestResult(logAction = logAction,
        redaction = LogRedaction.default.withBodyRedactor(tokenRequests))(echoJson) ~> check {
        responseAs[String] shouldEqual jsonResponse
        normalizedDebugMsg() should
        include("body=\"{\"grant_type\":\"password\",\"username\":\"REDACTED\",\"password\":\"REDACTED\"}\"")
      }
      val card = "\\b[0-9]{13,19}\\b".r
      resetDebugMsg()
      jsonRequest("""{"card":4111111111111111,"note":"card 4111111111111111"}""") ~>
      logRequestResult(logAction = logAction,
        redaction = LogRedaction.default.redactBodyValues(card.replaceAllIn(_, "REDACTED")).hideRejectionValues)(
        echoJson) ~> check {
        responseAs[String] shouldEqual jsonResponse
        normalizedDebugMsg() should include("body=\"{\"card\":\"REDACTED\",\"note\":\"card REDACTED\"}\"")
      }
      resetDebugMsg()
      jsonRequest(json)                                                                   ~> logRequestResult(logAction = logAction,
        redaction = LogRedaction.default.withBodyRedactor(BodyRedactor.hidden))(echoJson) ~> check {
        responseAs[String] shouldEqual jsonResponse
        normalizedDebugMsg() shouldEqual
        """|HTTP/1.1 POST /login Headers(Content-Length: 37, Content-Type: application/json) body=<hidden> (37 bytes total)
           |HTTP/1.1 200 OK Headers(Content-Length: 37, Content-Type: application/json) body=<hidden> (37 bytes total)
           |""".stripMarginWithNewline("\n")
      }
      resetDebugMsg()
      Get("/users/7/tokens/abc?pin=1&iban=DE00") ~> logRequestResult(logAction = logAction,
        redaction = LogRedaction.default.addNames("pin", "iban").redactPath(
          "/users/*/tokens/{token}").hideRejectionValues)(
        completeOk) ~> check {
        response.status shouldEqual Status.Ok
        normalizedDebugMsg() should
        startWith("HTTP/1.1 GET /users/7/tokens/REDACTED?pin=REDACTED&iban=REDACTED Headers()\n")
      }
      resetDebugMsg()
      jsonRequest("""{"pin":"1234","password":"x"}""")           ~> logRequestResult(logAction = logAction,
        redaction = LogRedaction.none.addNames("pin"))(echoJson) ~> check {
        responseAs[String] shouldEqual jsonResponse
        normalizedDebugMsg() should include("body=\"{\"pin\":\"REDACTED\",\"password\":\"x\"}\"")
      }
    }

    "never fail traffic when user code or the log action throws" in {
      val throwing = LogRedaction.default.withBodyRedactor(BodyRedactor(_ => throw new IllegalStateException("boom")))
      resetDebugMsg()
      jsonRequest(json) ~> logRequestResult(logAction = logAction, redaction = throwing)(echoJson) ~> check {
        responseAs[String] shouldEqual jsonResponse
        normalizedDebugMsg() shouldEqual
        """|HTTP/1.1 POST /login Headers(Content-Length: 37, Content-Type: application/json) body=<redaction failed: java.lang.IllegalStateException> (37 bytes total)
           |HTTP/1.1 200 OK Headers(Content-Length: 37, Content-Type: application/json) body=<redaction failed: java.lang.IllegalStateException> (37 bytes total)
           |""".stripMarginWithNewline("\n")
      }
      val badUri = LogRedaction.default.redactUri(_ => throw new IllegalStateException("uri"))
      resetDebugMsg()
      Get("/hello?token=t") ~> logRequestResult(logAction = logAction, redaction = badUri)(completeOk) ~> check {
        response.status shouldEqual Status.Ok
        normalizedDebugMsg() should
        startWith("HTTP/1.1 GET <redaction failed: java.lang.IllegalStateException> Headers()\n")
      }
      resetDebugMsg()
      Get("/hello").putHeaders(Header.Raw(ci"X-Probe", "1")) ~>
      logRequestResult(logAction = logAction, redactHeadersWhen = _ => throw new IllegalStateException("h"))(
        completeOk) ~> check {
        response.status shouldEqual Status.Ok
        normalizedDebugMsg() should
        startWith("HTTP/1.1 GET /hello Headers(<redaction failed: java.lang.IllegalStateException>)\n")
      }
      val failingLog: Option[String => IO[Unit]] = Some(_ => IO.raiseError(new IllegalStateException("log")))
      jsonRequest(json) ~> logRequestResult(logAction = failingLog)(echoJson) ~> check {
        response.status shouldEqual Status.Ok
        responseAs[String] shouldEqual jsonResponse
      }
      val throwingLog: Option[String => IO[Unit]] = Some(_ => throw new IllegalStateException("log"))
      jsonRequest(json) ~> logRequestResult(logAction = throwingLog)(echoJson) ~> check {
        response.status shouldEqual Status.Ok
        responseAs[String] shouldEqual jsonResponse
      }
    }

    "hand a user redactor the captured bytes as the message body, never the stream being teed" in {
      val reading = LogRedaction.none.withBodyRedactor(
        BodyRedactor.eval(b => b.message.as[String].map(BodyRedactor.Text(_)), prefixSafe = true))
      resetDebugMsg()
      jsonRequest(json)                          ~> logRequestResult(logAction = logAction, maxBodyBytes = 5, redaction = reading)(
        entity(as[String]) { s => complete(s) }) ~> check {
        responseAs[String] shouldEqual json
        normalizedDebugMsg() shouldEqual
        """|HTTP/1.1 POST /login Headers(Content-Length: 37, Content-Type: application/json) body="{"use" ... (37 bytes total)
           |HTTP/1.1 200 OK Headers(Content-Length: 37, Content-Type: text/plain; charset=UTF-8) body="{"use" ... (37 bytes total)
           |""".stripMarginWithNewline("\n")
      }
    }
  }
}
