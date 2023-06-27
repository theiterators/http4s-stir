package pl.iterators.stir.server.directives

import cats.effect.IO
import org.http4s.{ Request, Response }
import pl.iterators.stir.server.{ Route, RouteResult }

trait Http4sDirectives {
  def httpRoutesOf(pf: PartialFunction[Request[IO], IO[Response[IO]]]): Route = ctx => {
    pf.lift(ctx.request) match {
      case Some(response) => response.map(RouteResult.Complete)
      case None           => IO.pure(RouteResult.Rejected(Nil))
    }
  }
}

object Http4sDirectives extends Http4sDirectives
