package pl.iterators.stir.server

import cats.effect.IO
import org.http4s.Response

sealed trait RouteResult

object RouteResult {
  case class Complete(response: Response[IO]) extends RouteResult

  case class Rejected(rejections: Seq[Rejection]) extends RouteResult
}