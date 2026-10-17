package pl.iterators.stir.server.directives

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.syntax.all._
import com.comcast.ip4s._
import fs2.Stream
import org.http4s.{ Header, HttpRoutes, HttpVersion, LanguageTag, Method, Request, Response, Status, Uri, UrlForm }
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.h2.H2Keys.Http2PriorKnowledge
import org.http4s.headers.{ `Content-Length`, `X-Forwarded-For` }
import org.typelevel.ci.CIString
import pl.iterators.stir.server._

import java.util.concurrent.atomic.{ AtomicBoolean, AtomicInteger }
import scala.concurrent.duration._

class MiscDirectivesSpec extends RoutingSpec {
  override implicit def runtime: IORuntime = IORuntime.global

  /** `selectFrom("en", "de")` runs `selectPreferredLanguage(en, de)` and returns the language it picked. */
  trait SelectFrom {
    def apply(languages: String*): String
  }

  private def languageTag(s: String): LanguageTag = s.split('-').toList match {
    case primary :: subTags => LanguageTag(primary, subTags: _*)
    case Nil                => LanguageTag(s)
  }

  implicit class AcceptLanguageTest(acceptLanguageHeader: String) {
    def test(body: SelectFrom => Any): Unit =
      s"properly handle `$acceptLanguageHeader`" in {
        val Array(name, value) = acceptLanguageHeader.split(':')
        val header = Header.Raw(CIString(name.trim), value.trim)
        body { languages =>
          val first = languageTag(languages.head)
          val more = languages.tail.map(languageTag)
          Get() ~> addHeader(header) ~> selectPreferredLanguage(first, more: _*) { selected =>
            complete((selected.primaryTag :: selected.subTags).mkString("-"))
          } ~> check { responseAs[String] }
        }
      }
  }

  "the selectPreferredLanguage directive" should {
    // Pekko's table: the header on the left, the preferred of the offered languages on the right. Pekko writes
    // `q=.5`; http4s follows RFC 7231 (`qvalue = ( "0" [ "." 0*3DIGIT ] ) / ...`) and rejects it, so the table
    // uses `0.5`, and the leading-dot form is pinned separately below
    "Accept-Language: de, en".test { selectFrom =>
      selectFrom("de", "en") shouldEqual "de"
      selectFrom("en", "de") shouldEqual "en"
    }
    "Accept-Language: en, de;q=0.5".test { selectFrom =>
      selectFrom("de", "en") shouldEqual "en"
      selectFrom("en", "de") shouldEqual "en"
    }
    "Accept-Language: en;q=0.5, de".test { selectFrom =>
      selectFrom("de", "en") shouldEqual "de"
      selectFrom("en", "de") shouldEqual "de"
    }
    "Accept-Language: en-US, en;q=0.7, *;q=0.1, de;q=0.5".test { selectFrom =>
      selectFrom("en", "en-US") shouldEqual "en-US"
      selectFrom("de", "en") shouldEqual "en"
      selectFrom("de", "hu") shouldEqual "de"
      selectFrom("de-DE", "hu") shouldEqual "de-DE"
      selectFrom("hu", "es") shouldEqual "hu"
      selectFrom("es", "hu") shouldEqual "es"
    }
    "Accept-Language: en, *;q=0.5, de;q=0".test { selectFrom =>
      selectFrom("es", "de") shouldEqual "es"
      selectFrom("de", "es") shouldEqual "es"
      selectFrom("es", "en") shouldEqual "en"
    }
    "Accept-Language: en, *;q=0".test { selectFrom =>
      selectFrom("es", "de") shouldEqual "es"
      selectFrom("de", "es") shouldEqual "de"
      selectFrom("es", "en") shouldEqual "en"
    }
    "fall back to the first language when Accept-Language cannot be parsed, such as `q=.5`" in {
      Get()                                                                                               ~> addHeader(Header.Raw(CIString("Accept-Language"), "en, de;q=.5")) ~>
      selectPreferredLanguage(LanguageTag("de"), LanguageTag("en")) { lang => complete(lang.primaryTag) } ~> check {
        responseAs[String] shouldEqual "de"
      }
    }
    "fall back to the first language without Accept-Language" in {
      Get() ~> selectPreferredLanguage(LanguageTag("de"), LanguageTag("en")) { lang => complete(lang.primaryTag) } ~>
      check { responseAs[String] shouldEqual "de" }
    }
  }

  "the extractClientIP directive" should {
    "extract from a X-Forwarded-For header" in {
      Get()                                           ~> addHeaders(`X-Forwarded-For`(remoteAddress("2.3.4.5")),
        Header.Raw(CIString("x-real-ip"), "1.2.3.4")) ~> {
        extractClientIP { echoComplete }
      } ~> check { responseAs[String] shouldEqual "Some(2.3.4.5)" }
    }
    "extract from a (synthetic) Remote-Address header" in {
      Get() ~> addHeader(Header.Raw(CIString("Remote-Address"), "1.2.3.4")) ~> {
        extractClientIP { echoComplete }
      } ~> check { responseAs[String] shouldEqual "Some(1.2.3.4)" }
    }
    "extract from a X-Real-IP header" in {
      Get() ~> addHeader(Header.Raw(CIString("X-Real-Ip"), "1.2.3.4")) ~> {
        extractClientIP { echoComplete }
      } ~> check { responseAs[String] shouldEqual "Some(1.2.3.4)" }
    }
    "select X-Real-Ip when both X-Real-Ip and Remote-Address headers are present" in {
      Get()                                                ~> addHeaders(Header.Raw(CIString("X-Real-Ip"), "1.2.3.4"),
        Header.Raw(CIString("Remote-Address"), "1.2.3.4")) ~> {
        extractClientIP { echoComplete }
      } ~> check { responseAs[String] shouldEqual "Some(1.2.3.4)" }
    }
    "extract unknown when no headers" in {
      Get() ~> {
        extractClientIP { echoComplete }
      } ~> check { responseAs[String] shouldEqual "None" }
    }
  }

  def remoteAddress(ip: String) = IpAddress.fromString(ip)

  def withConnection(ip: String): Request[IO] => Request[IO] = request =>
    request.withAttributes(request.attributes.insert(Request.Keys.ConnectionInfo,
      Request.Connection(SocketAddress(ipv4"127.0.0.1", port"8080"),
        SocketAddress(IpAddress.fromString(ip).get, port"40000"), secure = false)))

  private def entityOfSize(size: Int) = "0" * size
  private def formDataOfSize(size: Int) = UrlForm("field" -> ("0" * size))

  "the extractDirectClientIP directive" should {
    "extract the remote address of the connection and ignore headers" in {
      Get() ~> addHeader(`X-Forwarded-For`(remoteAddress("2.3.4.5"))) ~> withConnection("1.2.3.4") ~> {
        extractDirectClientIP { echoComplete }
      } ~> check { responseAs[String] shouldEqual "Some(1.2.3.4)" }
    }
    "extract None when the connection is unknown" in {
      Get() ~> addHeader(`X-Forwarded-For`(remoteAddress("2.3.4.5"))) ~> {
        extractDirectClientIP { echoComplete }
      } ~> check { responseAs[String] shouldEqual "None" }
    }
    "extract the loopback address over a real server" in {
      Get() ~!> extractDirectClientIP { echoComplete } ~> check { responseAs[String] shouldEqual "Some(127.0.0.1)" }
    }
  }

  "the requestEntityEmpty and requestEntityPresent directives" should {
    "treat a request without body as empty" in {
      Get() ~> requestEntityEmpty { completeOk }   ~> check { status shouldEqual Status.Ok }
      Get() ~> requestEntityPresent { completeOk } ~> check { rejection shouldEqual RequestEntityExpectedRejection }
    }
    "treat a request with Content-Length: 0 as empty" in {
      Post("/", "") ~> requestEntityEmpty { completeOk } ~> check { status shouldEqual Status.Ok }
    }
    "treat a request with a body as present" in {
      Post("/", "abc") ~> requestEntityPresent { completeOk } ~> check { status shouldEqual Status.Ok }
      Post("/", "abc") ~> requestEntityEmpty { completeOk }   ~> check { rejections shouldEqual Nil }
    }
    "treat a GET without body as empty over a real server" in {
      Get() ~!> (requestEntityEmpty { complete("empty") } ~ complete("present")) ~> check {
        responseAs[String] shouldEqual "empty"
      }
    }
    "complete with 400 when an entity is expected but missing" in {
      Get() ~> Route.seal(requestEntityPresent { completeOk }) ~> check {
        status shouldEqual Status.BadRequest
        responseAs[String] shouldEqual "Request entity expected but not supplied"
      }
    }
  }

  "the requestEntityEmpty and requestEntityPresent directives on HTTP/2" should {
    // HTTP/2 bodies carry no Content-Length; whether the stream ended with the headers only shows in the body
    def http2(request: Request[IO]): Request[IO] =
      request.withHttpVersion(HttpVersion.`HTTP/2`).removeHeader[`Content-Length`]
    val emptyOrBody: Route =
      requestEntityEmpty { complete("empty") } ~ requestEntityPresent { entity(as[String])(s => complete("body:" + s)) }

    val emptyOrEntity: Route =
      requestEntityEmpty { complete("empty") } ~ entity(as[String])(s => complete("body:" + s))

    "treat a length-less body as present and pass all of it on" in {
      val body = Stream("ab", "c", "", "de").through(fs2.text.utf8.encode)
      http2(Post().withBodyStream(body)) ~> emptyOrBody ~> check { responseAs[String] shouldEqual "body:abcde" }
    }
    def oneShot: Stream[IO, Byte] = {
      val read = new AtomicBoolean(false)
      Stream.eval(IO(read.getAndSet(true))).flatMap { again =>
        if (again) Stream.raiseError[IO](new IllegalStateException("read twice"))
        else Stream("ab", "c").through(fs2.text.utf8.encode)
      }
    }
    "read a body that can be read only once a single time across alternatives" in {
      http2(Post().withBodyStream(oneShot)) ~> emptyOrBody ~> check { responseAs[String] shouldEqual "body:abc" }
    }
    "hand the whole body to an alternative that reads it without these directives" in {
      http2(Post().withBodyStream(oneShot)) ~> emptyOrEntity ~> check { responseAs[String] shouldEqual "body:abc" }
    }
    def bracketed(open: AtomicBoolean, released: AtomicInteger): Stream[IO, Byte] =
      Stream.bracket(IO(open.set(true)))(_ => IO { open.set(false); released.incrementAndGet(); () }).flatMap { _ =>
        Stream("ab", "c").through(fs2.text.utf8.encode)
      }
    "release the body it looked at when the route does not read it" in {
      val (open, released) = (new AtomicBoolean(false), new AtomicInteger(0))
      http2(Post().withBodyStream(bracketed(open, released))) ~> requestEntityPresent { complete("ignored") } ~> check {
        responseAs[String] shouldEqual "ignored"
      }
      (open.get, released.get) shouldEqual ((false, 1))
      http2(Post().withBodyStream(bracketed(open, released))) ~> requestEntityEmpty { completeOk } ~> check {
        rejections shouldEqual Nil
      }
      (open.get, released.get) shouldEqual ((false, 2))
    }
    "answer for the body a directive replaced it with" in {
      http2(Post("/", "abc")) ~> mapRequest(_.withEntity("")) {
        requestEntityEmpty { complete("empty") } ~ complete("present")
      }                                          ~> check { responseAs[String] shouldEqual "empty" }
      http2(Post().withBodyStream(Stream.empty)) ~> mapRequest(_.withBodyStream(Stream.emits("abc".getBytes))) {
        requestEntityEmpty { complete("empty") } ~ complete("present")
      } ~> check { responseAs[String] shouldEqual "present" }
    }
    "leave the body alone for the routes after a rejecting toHttpRoutes" in {
      val stir = requestEntityEmpty { complete("empty") }.toHttpRoutes
      val other = HttpRoutes.of[IO] { case request =>
        request.as[String].map(s => Response[IO]().withEntity("other:" + s))
      }
      val response = (stir <+> other).orNotFound.run(http2(Post().withBodyStream(oneShot)))
      response.flatMap(_.as[String]).unsafeRunSync() shouldEqual "other:abc"
    }
    "keep the resources of the body open until the route has read it" in {
      val open = new AtomicBoolean(false)
      val body = Stream.bracket(IO(open.set(true)))(_ => IO(open.set(false))).flatMap { _ =>
        Stream("ab", "c").evalTap(_ => IO(open.get).flatMap(IO.raiseUnless(_)(new IllegalStateException("closed"))))
      }.through(fs2.text.utf8.encode)
      http2(Post().withBodyStream(body)) ~> emptyOrBody ~> check { responseAs[String] shouldEqual "body:abc" }
      open.get shouldEqual false
    }
    "treat a length-less body without bytes as empty" in {
      http2(Post().withBodyStream(Stream.empty)) ~> emptyOrBody                         ~> check { responseAs[String] shouldEqual "empty" }
      http2(Post().withBodyStream(Stream.empty)) ~> requestEntityPresent { completeOk } ~> check {
        rejection shouldEqual RequestEntityExpectedRejection
      }
    }
    "keep an enclosing size limit on the body it looked at" in {
      http2(Post("/", "abcde")) ~> Route.seal(withSizeLimit(3) { emptyOrBody }) ~> check {
        status shouldEqual Status.PayloadTooLarge
      }
    }
    "let withoutSizeLimit lift the enclosing limit after it" in {
      http2(Post("/", "abcde")) ~> withSizeLimit(3) {
        requestEntityPresent { withoutSizeLimit { entity(as[String])(s => complete("body:" + s)) } }
      } ~> check { responseAs[String] shouldEqual "body:abcde" }
    }
    "tell an empty request from one with a body over a real HTTP/2 connection" in {
      val app = extractRequest { request =>
        mapResponse(_.putHeaders("X-Version" -> request.httpVersion.renderString)) {
          path("entity")(emptyOrEntity) ~ emptyOrBody
        }
      }.toHttpApp
      val responses = (for {
        server <- EmberServerBuilder.default[IO].withHttp2.withHost(ipv4"127.0.0.1").withPort(port"0")
          .withHttpApp(app).withShutdownTimeout(Duration.Zero).build
        client <- EmberClientBuilder.default[IO].withHttp2.build
      } yield (server, client)).use { case (server, client) =>
        def send(request: Request[IO], path: String = ""): IO[(String, Option[String])] = {
          val uri = Uri.unsafeFromString(s"http://127.0.0.1:${server.address.getPort}/$path")
          client.run(request.withUri(uri).withAttribute(Http2PriorKnowledge, ())).use { response =>
            response.as[String].map(_ -> response.headers.get(CIString("X-Version")).map(_.head.value))
          }
        }
        val streamed = Stream("ab", "c").through(fs2.text.utf8.encode).covary[IO]
        List(send(Request[IO](Method.POST)), send(Request[IO](Method.POST).withBodyStream(streamed)),
          send(Request[IO](Method.POST), "entity"), send(Request[IO](Method.POST).withBodyStream(streamed), "entity"))
          .sequence
      }.unsafeRunSync()
      responses shouldEqual List("empty", "body:abc", "empty", "body:abc").map(_ -> Some("HTTP/2.0"))
    }
  }

  "the rejectEmptyResponse directive" should {
    "turn a response without body into a rejection" in {
      Get() ~> (rejectEmptyResponse { complete(Status.Ok) } ~ complete("fallback")) ~> check {
        responseAs[String] shouldEqual "fallback"
      }
    }
    "turn a response with an empty string body into a rejection" in {
      Get() ~> (rejectEmptyResponse { complete("") } ~ complete("fallback")) ~> check {
        responseAs[String] shouldEqual "fallback"
      }
    }
    "leave a response with a body alone" in {
      Get() ~> (rejectEmptyResponse { complete("body") } ~ complete("fallback")) ~> check {
        responseAs[String] shouldEqual "body"
      }
    }
  }

  "the withSizeLimit directive" should {
    "not apply if entity is not consumed" in {
      val route = withSizeLimit(500) { completeOk }
      Post("/abc", entityOfSize(500)) ~> route ~> check { status shouldEqual Status.Ok }
      Post("/abc", entityOfSize(501)) ~> route ~> check { status shouldEqual Status.Ok }
    }
    "apply if entity is consumed" in {
      val route = withSizeLimit(500) { entity(as[String]) { _ => completeOk } }
      Post("/abc", entityOfSize(500)) ~> route             ~> check { status shouldEqual Status.Ok }
      Post("/abc", entityOfSize(501)) ~> Route.seal(route) ~> check {
        status shouldEqual Status.PayloadTooLarge
        responseAs[String] should include("exceeded size limit")
      }
    }
    "apply to a body consumed over a real server" in {
      // sent with Content-Length: a chunked body would race the server's early close (413 before the final chunk)
      // against the client's last write and sometimes end in a broken pipe instead of a response
      val route = withSizeLimit(500) { entity(as[String]) { _ => completeOk } }
      Post("/abc", entityOfSize(501)) ~!> route ~> check {
        status shouldEqual Status.PayloadTooLarge
      }
    }
    "apply if form data is fully consumed into a map" in {
      val route = withSizeLimit(64) { formFieldMap { _ => completeOk } }
      Post("/abc", formDataOfSize(32))  ~> route             ~> check { status shouldEqual Status.Ok }
      Post("/abc", formDataOfSize(128)) ~> Route.seal(route) ~> check {
        status shouldEqual Status.PayloadTooLarge
        responseAs[String] should include("exceeded size limit")
      }
    }
    "properly handle nested directives by applying innermost `withSizeLimit` directive" in {
      val route = withSizeLimit(500) { withSizeLimit(800) { entity(as[String]) { _ => completeOk } } }
      Post("/abc", entityOfSize(800)) ~> route             ~> check { status shouldEqual Status.Ok }
      Post("/abc", entityOfSize(801)) ~> Route.seal(route) ~> check { status shouldEqual Status.PayloadTooLarge }

      val route2 = withSizeLimit(500) { withSizeLimit(400) { entity(as[String]) { _ => completeOk } } }
      Post("/abc", entityOfSize(400)) ~> route2             ~> check { status shouldEqual Status.Ok }
      Post("/abc", entityOfSize(401)) ~> Route.seal(route2) ~> check { status shouldEqual Status.PayloadTooLarge }
    }
  }

  "the withoutSizeLimit directive" should {
    "skip request entity size verification" in {
      val route = withSizeLimit(500) { withoutSizeLimit { entity(as[String]) { _ => completeOk } } }
      Post("/abc", entityOfSize(501)) ~> route ~> check { status shouldEqual Status.Ok }
    }
    "keep a body that was buffered in between instead of restoring the consumed stream" in {
      val route = withSizeLimit(500) {
        toStrictEntity(1.second) { withoutSizeLimit { entity(as[String]) { body => complete(body) } } }
      }
      Post("/abc", "abc") ~> removeHeader("Content-Length") ~!> route ~> check {
        responseAs[String] shouldEqual "abc"
      }
    }
  }

  "nested withSizeLimit directives" should {
    "limit the body a directive in between produced instead of restoring the original stream" in {
      val route = withSizeLimit(500) {
        toStrictEntity(1.second) {
          mapRequest(_.withEntity("replaced")) { withSizeLimit(400) { entity(as[String]) { body => complete(body) } } }
        }
      }
      Post("/abc", "abc") ~> removeHeader("Content-Length") ~!> route ~> check {
        responseAs[String] shouldEqual "replaced"
      }
    }
  }
}
