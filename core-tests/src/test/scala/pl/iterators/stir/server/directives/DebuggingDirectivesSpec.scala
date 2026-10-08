package pl.iterators.stir.server.directives

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import fs2.{ Chunk, Stream }
import org.http4s.headers.{ `Content-Type`, `Transfer-Encoding` }
import org.http4s.{ Header, MediaType, Response, Status, TransferCoding, UrlForm }
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
      val route = logRequest(logAction = logAction)(completeOk)

      resetDebugMsg()
      Get("/hello") ~> route ~> check {
        response.status shouldEqual Status.Ok
        normalizedDebugMsg() shouldEqual "HTTP/1.1 GET /hello Headers()\n"
      }
    }
  }

  "The 'logResult' directive" should {
    "produce a proper log message for outgoing responses" in {
      val route = logResult(logAction = logAction)(completeOk)

      resetDebugMsg()
      Get("/hello") ~> route ~> check {
        response.status shouldEqual Status.Ok
        normalizedDebugMsg() shouldEqual "HTTP/1.1 200 OK Headers()\n"
      }
    }
  }

  "The 'logRequestResult' directive" should {
    "produce proper log messages for outgoing responses, thereby showing the corresponding request" in {
      val route = logRequestResult(logAction = logAction)(completeOk)

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
      jsonRequest(json) ~> logRequestResult(logAction = logAction)(echoJson) ~> check {
        responseAs[String] shouldEqual jsonResponse
        normalizedDebugMsg() shouldEqual
        """|HTTP/1.1 POST /login Headers(Content-Length: 37, Content-Type: application/json) body="{"user":"alice","password":"hunter2"}"
           |HTTP/1.1 200 OK Headers(Content-Length: 37, Content-Type: application/json) body="{"access_token":"tok","expires":3600}"
           |""".stripMarginWithNewline("\n")
      }
    }

    "log a pretty printed JSON request body as received" in {
      resetDebugMsg()
      jsonRequest(prettyJson) ~> logRequestResult(logAction = logAction)(echoJson) ~> check {
        responseAs[String] shouldEqual jsonResponse
        normalizedDebugMsg() shouldEqual
        "HTTP/1.1 POST /login Headers(Content-Length: 46, Content-Type: application/json) body=\"{\n  \"user\": \"alice\",\n  \"password\": \"hunter2\"\n}\"\n" +
        "HTTP/1.1 200 OK Headers(Content-Length: 37, Content-Type: application/json) body=\"{\"access_token\":\"tok\",\"expires\":3600}\"\n"
      }
    }

    "indicate truncation when the body exceeds maxBodyBytes" in {
      resetDebugMsg()
      jsonRequest(json) ~> logRequestResult(logAction = logAction, maxBodyBytes = 10)(echoJson) ~> check {
        responseAs[String] shouldEqual jsonResponse
        normalizedDebugMsg() shouldEqual
        """|HTTP/1.1 POST /login Headers(Content-Length: 37, Content-Type: application/json) body="{"user":"a" ... (37 bytes total)
           |HTTP/1.1 200 OK Headers(Content-Length: 37, Content-Type: application/json) body="{"access_t" ... (37 bytes total)
           |""".stripMarginWithNewline("\n")
      }
    }

    "indicate a request body that was never consumed" in {
      resetDebugMsg()
      jsonRequest(json) ~> logRequestResult(logAction = logAction)(complete("ok")) ~> check {
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
      chunked ~> logRequestResult(logAction = logAction)(echoJson) ~> check {
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
      binary ~> logRequestResult(logAction = logAction)(entity(as[Array[Byte]]) { _ => complete("ok") }) ~> check {
        responseAs[String] shouldEqual "ok"
        normalizedDebugMsg() shouldEqual
        """|HTTP/1.1 POST /upload Headers(Content-Length: 3, Content-Type: application/octet-stream) body="0001ff"
           |HTTP/1.1 200 OK Headers(Content-Length: 2, Content-Type: text/plain; charset=UTF-8) body="ok"
           |""".stripMarginWithNewline("\n")
      }
    }

    "omit the header section when logHeaders = false" in {
      resetDebugMsg()
      jsonRequest(json) ~> logRequestResult(logAction = logAction, logHeaders = false)(echoJson) ~> check {
        responseAs[String] shouldEqual jsonResponse
        normalizedDebugMsg() shouldEqual
        """|HTTP/1.1 POST /login body="{"user":"alice","password":"hunter2"}"
           |HTTP/1.1 200 OK body="{"access_token":"tok","expires":3600}"
           |""".stripMarginWithNewline("\n")
      }
    }

    "log a text/plain body" in {
      resetDebugMsg()
      Post("/t").withEntity("hello world") ~> logRequestResult(logAction = logAction)(entity(as[String]) { _ =>
        complete("ok")
      }) ~> check {
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
      Get("/s") ~> logRequestResult(logAction = logAction)(streamed) ~> check {
        responseAs[String] shouldEqual "abc"
        normalizedDebugMsg() shouldEqual
        """|HTTP/1.1 GET /s Headers()
           |HTTP/1.1 200 OK Headers() body="abc" ... (??? bytes total)
           |""".stripMarginWithNewline("\n")
      }
    }

    "log a MalformedQueryParamRejection with its value" in {
      resetDebugMsg()
      Get("/q?pin=12ab-SECRET") ~> logRequestResult(logAction = logAction)(parameter("pin".as[Int]) { _ =>
        complete("ok")
      }) ~> check {
        handled shouldBe false
        normalizedDebugMsg() shouldEqual
        """|HTTP/1.1 GET /q?pin=12ab-SECRET Headers()
           |Request was rejected with rejections: MalformedQueryParamRejection(pin,'12ab-SECRET' is not a valid 32-bit signed integer value,Some(java.lang.NumberFormatException: For input string: "12ab-SECRET"))
           |""".stripMarginWithNewline("\n")
      }
    }

    "log an InvalidRequiredValueForQueryParamRejection with both values" in {
      resetDebugMsg()
      Get("/q?password=x")                                                            ~> logRequestResult(logAction = logAction)(
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
      logRequestResult(logAction = logAction)(
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
      Get("/h").putHeaders(Header.Raw(ci"Authorization", "Bearer SECRET-TOKEN")) ~> logRequestResult(
        logAction = logAction)(failing { (_: Nothing) => complete("ok") })       ~> check {
        handled shouldBe false
        normalizedDebugMsg() shouldEqual
        """|HTTP/1.1 GET /h Headers(Authorization: <REDACTED>)
           |Request was rejected with rejections: MalformedHeaderRejection(Authorization,bad: Bearer SECRET-TOKEN,Some(java.lang.IllegalArgumentException: bad: Bearer SECRET-TOKEN))
           |""".stripMarginWithNewline("\n")
      }
    }

    "log an unparseable Referer as received" in {
      resetDebugMsg()
      Get("/hello").putHeaders(Header.Raw(ci"Referer", "http://[")) ~> logRequestResult(logAction = logAction)(
        completeOk)                                                 ~> check {
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
      Get("/go") ~> logRequestResult(logAction = logAction)(redirect) ~> check {
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
        logAction = logAction)(completeOk)                                            ~> check {
        response.status shouldEqual Status.Ok
        normalizedDebugMsg() shouldEqual
        """|HTTP/1.1 GET http://bob:pw@example.com/a?reset_token=QS&user=alice%20b&flag&t=1&t=2 Headers(X-Api-Key: KEY1, Cookie: <REDACTED>)
           |HTTP/1.1 200 OK Headers()
           |""".stripMarginWithNewline("\n")
      }
    }
  }
}
