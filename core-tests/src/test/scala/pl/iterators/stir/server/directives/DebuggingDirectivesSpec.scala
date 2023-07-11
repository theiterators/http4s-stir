package pl.iterators.stir.server.directives

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.http4s.Status
import pl.iterators.stir.impl.util._

class DebuggingDirectivesSpec extends RoutingSpec {
  override implicit def runtime: IORuntime = IORuntime.global
  var debugMsg = ""

  def resetDebugMsg(): Unit = { debugMsg = "" }

  // Gracefully handle prefix difference for `ByteString.ByteString1C`
  // in Scala 2.12 due to https://issues.scala-lang.org/browse/SI-9019.
  def normalizedDebugMsg(): String =
    debugMsg.replace("ByteString.ByteString1C(", "ByteString(")

  val logAction: Option[String => IO[Unit]] = Some(msg => IO { debugMsg += msg + '\n' })

//  val log = new LoggingAdapter {
//    def isErrorEnabled = true
//    def isWarningEnabled = true
//    def isInfoEnabled = true
//    def isDebugEnabled = true
//
//    def notifyError(message: String): Unit = {}
//    def notifyError(cause: Throwable, message: String): Unit = {}
//    def notifyWarning(message: String): Unit = {}
//    def notifyInfo(message: String): Unit = {}
//    def notifyDebug(message: String): Unit = { debugMsg += message + '\n' }
//  }

  "The 'logRequest' directive" should {
    "produce a proper log message for incoming requests" in {
      val route =
          logRequest(logAction = logAction)(
            completeOk)

      resetDebugMsg()
      Get("/hello") ~> route ~> check {
        response.status shouldEqual Status.Ok
        normalizedDebugMsg() shouldEqual "HTTP/1.1 GET /hello Headers() body=\"\"\n"
      }
    }
  }

  "The 'logResult' directive" should {
    "produce a proper log message for outgoing responses" in {
      val route =
          logResult(logAction = logAction)(
            completeOk)

      resetDebugMsg()
      Get("/hello") ~> route ~> check {
        response.status shouldEqual Status.Ok
        normalizedDebugMsg() shouldEqual "HTTP/1.1 200 OK Headers() body=\"\"\n"
      }
    }
  }

  "The 'logRequestResult' directive" should {
    "produce proper log messages for outgoing responses, thereby showing the corresponding request" in {
      val route =
          logRequestResult(logAction = logAction)(
            completeOk)

      resetDebugMsg()
      Get("/hello") ~> route ~> check {
        response.status shouldEqual Status.Ok
        normalizedDebugMsg() shouldEqual
          """|HTTP/1.1 GET /hello Headers() body=""
             |HTTP/1.1 200 OK Headers() body=""
             |""".stripMarginWithNewline("\n")
      }
    }
//    "be able to log only rejections" in {
//      val rejectionLogger: HttpRequest => RouteResult => Option[LogEntry] = req => {
//        case Rejected(rejections) =>
//          Some(LogEntry(s"Request: $req\nwas rejected with rejections:\n$rejections", Logging.DebugLevel))
//        case _ => None
//      }
//
//      val route =
//          logRequestResult(rejectionLogger)(
//            reject(ValidationRejection("The request could not be validated")))
//
//      resetDebugMsg()
//      Get("/hello") ~> route ~> check {
//        handled shouldBe false
//        normalizedDebugMsg() shouldEqual
//          """Request: HttpRequest(HttpMethod(GET),http://example.com/hello,List(),HttpEntity.Strict(none/none,0 bytes total),HttpProtocol(HTTP/1.1))
//            |was rejected with rejections:
//            |List(ValidationRejection(The request could not be validated,None))
//            |""".stripMargin
//      }
//    }
  }
}