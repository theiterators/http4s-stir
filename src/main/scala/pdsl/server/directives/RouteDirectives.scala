package pdsl.server.directives

import cats.effect.IO
import org.http4s.{Response, Status}
import pdsl.server.{Rejection, RouteResult, StandardRoute}

object RouteDirectives {
  def complete(status: Status): StandardRoute = { _ =>
    IO.pure(RouteResult.Complete(Response[IO](status = status)))
  }

  def complete(f: IO[Status]): StandardRoute = { _ =>
    f.map(status => RouteResult.Complete(Response[IO](status = status)))
  }

  def reject(rejection: Rejection): StandardRoute = { _ =>
    IO.pure(RouteResult.Reject(List(rejection)))
  }

  def reject: StandardRoute = { _ =>
    IO.pure(RouteResult.Reject(List.empty))
  }
}