package pdsl.server

import cats.effect.IO
import org.http4s.Response

trait Rejection

sealed trait RouteResult

object RouteResult {
  case class Complete(response: Response[IO]) extends RouteResult

  case class Reject(rejections: List[Rejection]) extends RouteResult
}