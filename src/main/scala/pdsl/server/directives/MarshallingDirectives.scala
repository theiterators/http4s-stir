package pdsl.server.directives

import cats.effect.IO
import org.http4s.EntityDecoder
import pdsl.server._

object MarshallingDirectives {
  def entityAs[A](implicit entityDecoder: EntityDecoder[IO, A]): Directive1[A] = Directive[Tuple1[A]] {
    inner => ctx => entityDecoder.decode(ctx, strict = false).value.flatMap {
        case Right(value) => inner(Tuple1(value))(ctx)
        case Left(e) =>
          println(e)
          IO.pure(RouteResult.Reject(Nil))
      }
  }
}
