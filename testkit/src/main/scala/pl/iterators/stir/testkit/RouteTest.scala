package pl.iterators.stir.testkit

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.comcast.ip4s._
import org.http4s._
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server._
import org.http4s.headers.`Content-Type`
import org.typelevel.ci.CIString
import pl.iterators.stir.server._

import scala.reflect.ClassTag
import scala.util.DynamicVariable

trait RouteTest extends RequestBuilding with RouteTestResultComponent
    with MarshallingTestUtils {
  this: TestFrameworkInterface =>

  implicit def runtime: IORuntime

  def cleanUp(): Unit = ()

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
//  def chunks: immutable.Seq[HttpEntity.ChunkStreamPart] = result.chunks
//  def chunksStream: Source[ChunkStreamPart, Any] = result.chunksStream
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
  def header[T](implicit ev: Header.Select[T], cls: ClassTag[T]): Option[ev.F[T]] = rawResponse.headers.get(ev)
  def header(name: String): Option[Header.Raw] = rawResponse.headers.get(CIString(name)).map(_.head)
  def status: Status = rawResponse.status

//  def closingExtension: String = chunks.lastOption match {
//    case Some(HttpEntity.LastChunk(extension, _)) => extension
//    case _                                        => ""
//  }
//  def trailer: immutable.Seq[HttpHeader] = chunks.lastOption match {
//    case Some(HttpEntity.LastChunk(_, trailer)) => trailer
//    case _                                      => Nil
//  }

  def rejections: Seq[Rejection] = result.rejections
  def rejection: Rejection = {
    val r = rejections
    if (r.size == 1) r.head else failTest("Expected a single rejection but got %s (%s)".format(r.size, r))
  }

//  def isWebSocketUpgrade: Boolean =
//    status == StatusCodes.SwitchingProtocols && header[Upgrade].exists(_.hasWebSocket)

//  /**
//   * Asserts that the received response is a WebSocket upgrade response and the extracts
//   * the chosen subprotocol and passes it to the handler.
//   */
//  def expectWebSocketUpgradeWithProtocol(body: String => Unit): Unit = {
//    if (!isWebSocketUpgrade) failTest("Response was no WebSocket Upgrade response")
//    header[`Sec-WebSocket-Protocol`] match {
//      case Some(`Sec-WebSocket-Protocol`(Seq(protocol))) => body(protocol)
//      case _                                             => failTest("No WebSocket protocol found in response.")
//    }
//  }

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
    implicit def injectIntoRoute: TildeArrow[RequestContext, IO[RouteResult]] { type Out = RouteTestResult } =
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

          val routeTestResult = new RouteTestResult()
          val effectiveRequest = request
          val ctx = RequestContext(effectiveRequest)

          val sealedExceptionHandler = ExceptionHandler.seal(testExceptionHandler)()

          val semiSealedRoute = // sealed for exceptions but not for rejections
            Directives.handleExceptions(sealedExceptionHandler)(route)
          val deferrableRouteResult = semiSealedRoute(ctx)
          deferrableRouteResult.map(routeTestResult.handleResult).unsafeRunSync()
          routeTestResult
        }
      }
  }

  abstract class TildeBangArrow[A, B] {
    type Out
    def apply(request: Request[IO], f: A => B): Out
  }

  object TildeBangArrow {
    implicit def injectIntoRoute: TildeBangArrow[RequestContext, IO[RouteResult]] { type Out = RouteTestResult } =
      new TildeBangArrow[RequestContext, IO[RouteResult]] {
        type Out = RouteTestResult
        def apply(request: Request[IO], route: Route): Out = {
          val routeTestResult = new RouteTestResult()
          val responseF = RouteTest.runRouteClientServer(request, route).unsafeRunSync()
          routeTestResult.handleResponse(responseF)
          routeTestResult
        }
      }
  }
}
private[stir] object RouteTest {
  def runRouteClientServer(request: Request[IO], route: Route): IO[Response[IO]] = {
    (for {
      server <- EmberServerBuilder
        .default[IO]
        .withHost(ipv4"127.0.0.1")
        .withPort(port"0")
        .withHttpApp(route.toHttpApp)
        .build
      client <- EmberClientBuilder
        .default[IO]
        .build
      updatedRequest = request.withUri(request.uri.copy(scheme = Some(Uri.Scheme.http),
        authority = Some(Uri.Authority(None, host = Uri.Host.fromIp4sHost(ipv4"127.0.0.1"),
          port = Some(server.address.getPort)))))
      response <- client.run(updatedRequest)
    } yield {
      response
    }).use(IO.pure)
  }
}
