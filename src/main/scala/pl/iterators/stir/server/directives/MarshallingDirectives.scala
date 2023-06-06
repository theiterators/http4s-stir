package pl.iterators.stir.server.directives

import cats.effect.IO
import org.http4s.EntityDecoder
import pl.iterators.stir.server._
import pl.iterators.stir.server.directives.BasicDirectives.cancelRejections
import pl.iterators.stir.server.directives.RouteDirectives.complete
import pl.iterators.stir.server.{EntityRejection, ToResponseMarshaller}

trait MarshallingDirectives {
  /**
   * Unmarshalls the requests entity to the given type passes it to its inner Route.
   * If there is a problem with unmarshalling the request is rejected with the [[Rejection]]
   * produced by the unmarshaller.
   *
   * @group marshalling
   */
  def entityAs[A](implicit entityDecoder: EntityDecoder[IO, A]): Directive1[A] = Directive[Tuple1[A]] {
    inner => ctx => entityDecoder.decode(ctx.request, strict = false).value.flatMap {
        case Right(value) => inner(Tuple1(value))(ctx)
        case Left(e) => IO.pure(RouteResult.Rejected(Seq(EntityRejection(e))))
      }
  } & cancelRejections(classOf[EntityRejection])

  /**
   * Completes the request using the given function. The input to the function is produced with the in-scope
   * entity unmarshaller and the result value of the function is marshalled with the in-scope marshaller.
   *
   * @group marshalling
   */
  def handleWith[A, B](f: A => B)(implicit entityDecoder: EntityDecoder[IO, A], m: ToResponseMarshaller[B]): Route =
    entityAs(entityDecoder) { a => complete(f(a)) }
}

object MarshallingDirectives extends MarshallingDirectives
