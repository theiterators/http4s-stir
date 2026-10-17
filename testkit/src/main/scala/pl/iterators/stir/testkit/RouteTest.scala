package pl.iterators.stir.testkit

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.comcast.ip4s._
import fs2.{ Chunk, Stream }
import org.http4s._
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server._
import org.http4s.headers.{ `Content-Type`, Upgrade }
import org.typelevel.ci.CIString
import pl.iterators.stir.server._
import pl.iterators.stir.server.directives.WebSocketDirectives.WebSocketHandlerKey

import java.util.concurrent.ConcurrentLinkedQueue
import scala.annotation.unused
import scala.concurrent.duration.{ Duration, FiniteDuration }
import scala.reflect.ClassTag
import scala.util.DynamicVariable

trait RouteTest extends RequestBuilding with WSTestRequestBuilding with RouteTestResultComponent
    with MarshallingTestUtils {
  this: TestFrameworkInterface =>

  implicit def runtime: IORuntime

  /** Cancels the WebSocket handlers still running from this suite's `WS(...)` requests; called from `afterAll`. */
  def cleanUp(): Unit = closeWebSocketProbes()

  private val connectedProbes = new ConcurrentLinkedQueue[WSProbe]()

  /** Wires the probe a `WS(...)` request carries to the handler the route upgraded with, when both are present. */
  private def connectWebSocketProbe(request: Request[IO], response: Response[IO]): IO[Unit] =
    (request.attributes.lookup(WSProbe.RequestKey), response.attributes.lookup(WebSocketHandlerKey)) match {
      case (Some(probe), Some(handler)) => probe.connect(handler) *> IO(connectedProbes.add(probe)).void
      case _                            => IO.unit
    }

  /** Cancels every handler a probe of this suite is still running. */
  protected def closeWebSocketProbes(): Unit =
    Iterator.continually(connectedProbes.poll()).takeWhile(_ != null).foreach(_.close().unsafeRunSync())

  private val dynRR = new DynamicVariable[RouteTestResult](null)
  private def result =
    if (dynRR.value ne null) dynRR.value
    else sys.error("This value is only available inside of a `check` construct!")

  def check[T](body: => T): RouteTestResult => T = result => dynRR.withValue(result)(body)

  private def responseSafe = if (dynRR.value ne null) dynRR.value.response else "<not available anymore>"

  def handled: Boolean = result.handled
  def response: Response[IO] = result.response
  def responseEntity: EntityBody[IO] = result.entity
  private def rawResponse: Response[IO] = result.rawResponse

  /**
   * The chunks of the response body as the route produced them (one `fs2.Chunk` per emitted chunk). Consumes the
   * raw body: for a one-shot body use either `chunks` / `chunksStream` or `response` / `responseAs`, not both.
   */
  def chunks: Seq[Chunk[Byte]] =
    result.awaitWithinTimeout(rawResponse.body.chunks.compile.toList, "Response chunks were not read")

  /** The response body as a stream of chunks; see `chunks` for the consumption caveat. */
  def chunksStream: Stream[IO, Chunk[Byte]] = rawResponse.body.chunks

  /**
   * The trailer headers the route attached to the response (`Response.withTrailerHeaders`), empty when none.
   * Over a real server (`~!>`) trailers arrive after the body, so the body is read (and cached) first.
   */
  def trailer: Headers = {
    val _ = responseEntity
    result.awaitWithinTimeout(rawResponse.trailerHeaders, "Response trailers were not read")
  }
  def entityAs[T](implicit um: EntityDecoder[IO, T], cls: ClassTag[T]): T = {
    def msg(e: Throwable) =
      s"Could not unmarshal entity to type '${implicitly[ClassTag[T]]}' for `entityAs` assertion: $e\n\nResponse was: $responseSafe"
    um.decode(response, strict = false).value.flatMap {
      case Right(t) => IO.pure(t)
      case Left(e)  => IO.raiseError(e)
    }.recover { case error => failTest(msg(error)) }.unsafeRunSync()
  }
  def responseAs[T](implicit um: EntityDecoder[IO, T], cls: ClassTag[T]): T = {
    def msg(e: Throwable) =
      s"Could not unmarshal response to type '${implicitly[ClassTag[T]]}' for `responseAs` assertion: $e\n\nResponse was: $responseSafe"
    um.decode(response, strict = false).value.flatMap {
      case Right(t) => IO.pure(t)
      case Left(e)  => IO.raiseError(e)
    }.recover { case error => failTest(msg(error)) }.unsafeRunSync()
  }
  def contentType: `Content-Type` =
    rawResponse.contentType.getOrElse(sys.error("Binary entity does not have a ContentType"))
  def mediaType: MediaType = contentType.mediaType
  def charsetOption: Option[Charset] = contentType.charset
  def charset: Charset = charsetOption.getOrElse(sys.error("Binary entity does not have charset"))
  def headers: Headers = rawResponse.headers
  def header[T](implicit ev: Header.Select[T], @unused cls: ClassTag[T]): Option[ev.F[T]] = rawResponse.headers.get(ev)
  def header(name: String): Option[Header.Raw] = rawResponse.headers.get(CIString(name)).map(_.head)
  def status: Status = rawResponse.status

  def rejections: Seq[Rejection] = result.rejections
  def rejection: Rejection = {
    val r = rejections
    if (r.size == 1) r.head else failTest("Expected a single rejection but got %s (%s)".format(r.size, r))
  }

  /** True when the route answered with a WebSocket upgrade (`101 Switching Protocols` and `Upgrade: websocket`). */
  def isWebSocketUpgrade: Boolean =
    status == Status.SwitchingProtocols &&
    rawResponse.headers.get[Upgrade].exists(_.values.exists(_.name == CIString("websocket")))

  /**
   * Asserts that the received response is a WebSocket upgrade response and the extracts
   * the chosen subprotocol and passes it to the handler.
   */
  def expectWebSocketUpgradeWithProtocol(body: String => Unit): Unit = {
    if (!isWebSocketUpgrade) failTest("Response was no WebSocket Upgrade response")
    header("Sec-WebSocket-Protocol").map(_.value.trim).filter(_.nonEmpty) match {
      case Some(protocol) => body(protocol)
      case None           => failTest("No WebSocket protocol found in response.")
    }
  }

  /**
   * A dummy that can be used as `~> runRoute` to run the route but without blocking for the result.
   * The result of the pipeline is the result that can later be checked with `check`. See the
   * "separate running route from checking" example from ScalatestRouteTestSpec.scala.
   */
  def runRoute: RouteTestResult => RouteTestResult = identity

  // there is already an implicit class WithTransformation in scope (inherited from org.apache.pekko.http.scaladsl.testkit.TransformerPipelineSupport)
  // however, this one takes precedence
  implicit class WithTransformation2(request: Request[IO]) {

    /**
     * Apply request to given routes for further inspection in `check { }` block.
     */
    def ~>[A, B](f: A => B)(implicit ta: TildeArrow[A, B]): ta.Out = ta(request, f)

    /**
     * Evaluate request against routes run in server mode for further
     * inspection in `check { }` block.
     *
     * Compared to [[~>]], the given routes are run in a fully fledged
     * server, which allows more types of directives to be tested at the
     * cost of additional overhead related with server setup.
     */
    def ~!>[A, B](f: A => B)(implicit tba: TildeBangArrow[A, B]): tba.Out = tba(request, f)
  }

  abstract class TildeArrow[A, B] {
    type Out
    def apply(request: Request[IO], f: A => B): Out
  }
//
//  case class DefaultHostInfo(host: Host, securedConnection: Boolean)
//  object DefaultHostInfo {
//    implicit def defaultHost: DefaultHostInfo = DefaultHostInfo(Host("example.com"), securedConnection = false)
//  }
  object TildeArrow {
    implicit object InjectIntoRequestTransformer extends TildeArrow[Request[IO], Request[IO]] {
      type Out = Request[IO]
      def apply(request: Request[IO], f: Request[IO] => Request[IO]) = f(request)
    }
    implicit def injectIntoRoute(implicit timeout: RouteTestTimeout)
        : TildeArrow[RequestContext, IO[RouteResult]] { type Out = RouteTestResult } =
      new TildeArrow[RequestContext, IO[RouteResult]] {
        type Out = RouteTestResult
        def apply(request: Request[IO], route: Route): Out = {
//          if (request.method == HttpMethods.HEAD && ServerSettings(system).transparentHeadRequests)
//            failTest(
//              "`pekko.http.server.transparent-head-requests = on` not supported in RouteTest using `~>`. Use `~!>` instead " +
//                "for a full-stack test, e.g. `req ~!> route ~> check {...}`")

//          implicit val executionContext: ExecutionContext = system.classicSystem.dispatcher
//          val routingSettings = RoutingSettings(system)
//          val routingLog = RoutingLog(system.classicSystem.log)

          val routeTestResult = new RouteTestResult(timeout)
          val effectiveRequest = request

          val sealedExceptionHandler = ExceptionHandler.seal(testExceptionHandler)()

          val semiSealedRoute = // sealed for exceptions but not for rejections
            Directives.handleExceptions(sealedExceptionHandler)(route)
          val deferrableRouteResult = RequestContext.forRequest(effectiveRequest).flatMap(semiSealedRoute).flatTap {
            case RouteResult.Complete(response) => connectWebSocketProbe(effectiveRequest, response)
            case _                              => IO.unit
          }
          routeTestResult.awaitWithinTimeout(deferrableRouteResult.map(routeTestResult.handleResult),
            "Request was neither completed nor rejected")
          routeTestResult
        }
      }
  }

  abstract class TildeBangArrow[A, B] {
    type Out
    def apply(request: Request[IO], f: A => B): Out
  }

  object TildeBangArrow {
    implicit def injectIntoRoute(implicit timeout: RouteTestTimeout)
        : TildeBangArrow[RequestContext, IO[RouteResult]] { type Out = RouteTestResult } =
      new TildeBangArrow[RequestContext, IO[RouteResult]] {
        type Out = RouteTestResult
        def apply(request: Request[IO], route: Route): Out = {
          val routeTestResult = new RouteTestResult(timeout)
          // the server and client start outside the bound; the request and the body read are bounded inside
          val response = routeTestResult.failOnTimeout(RouteTest.runRouteClientServer(request, route, timeout.duration),
            "Request was neither completed nor rejected")
          routeTestResult.handleResponse(response)
          routeTestResult
        }
      }
  }
}
private[stir] object RouteTest {

  def runRouteClientServer(request: Request[IO], route: Route, timeout: FiniteDuration): IO[Response[IO]] = {
    val resources = for {
      server <- EmberServerBuilder
        .default[IO]
        .withHost(ipv4"127.0.0.1")
        .withPort(port"0")
        .withHttpApp(route.toHttpApp)
        .withShutdownTimeout(Duration.Zero) // a hanging route must not hold the test for the graceful-shutdown period
        .build
      client <- EmberClientBuilder
        .default[IO]
        .build
    } yield (server, client)
    resources.use { case (server, client) =>
      val updatedRequest = request.withUri(request.uri.copy(scheme = Some(Uri.Scheme.http),
        authority = Some(Uri.Authority(None, host = Uri.Host.fromIp4sHost(ipv4"127.0.0.1"),
          port = Some(server.address.getPort)))))
      // read the body and the trailers while the client and the server are still alive: a chunked response
      // cannot be read after the connection is closed; `run(...).use` releases the connection even when the
      // timeout cancels the read
      client.run(updatedRequest).use { response =>
        response.body.compile.to(Chunk).flatMap { bytes =>
          response.trailerHeaders.map { trailers =>
            response.withBodyStream(Stream.chunk(bytes)).withTrailerHeaders(IO.pure(trailers))
          }
        }
      }.timeout(timeout)
    }
  }
}
