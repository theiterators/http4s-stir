package pdsl.server.directives

import cats.effect.IO
import org.http4s.{EntityEncoder, Response, Status}
import pdsl.server.{Rejection, RouteResult, StandardRoute, ToResponseMarshallable}

object RouteDirectives {
  def complete(m: ToResponseMarshallable): StandardRoute = { _ =>
    m.marshaller.toResponse(m.value).map(RouteResult.Complete)
  }

  def reject(rejection: Rejection): StandardRoute = { _ =>
    IO.pure(RouteResult.Reject(List(rejection)))
  }

  def reject: StandardRoute = { _ =>
    IO.pure(RouteResult.Reject(List.empty))
  }
}