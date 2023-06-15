package pl.iterators.stir.server.directives

import cats.effect.IO
import org.http4s.EntityDecoder
import pl.iterators.stir.server._
import pl.iterators.stir.server.{ EntityRejection, ToResponseMarshaller }

trait MarshallingDirectives {
  import BasicDirectives._
  import RouteDirectives._

  /**
   * Unmarshalls the requests entity to the given type passes it to its inner Route.
   * If there is a problem with unmarshalling the request is rejected with the [[Rejection]]
   * produced by the unmarshaller.
   *
   * @group marshalling
   */
  def entity[T](entityDecoder: EntityDecoder[IO, T]): Directive1[T] = Directive[Tuple1[T]] {
    inner => ctx =>
      entityDecoder.decode(ctx.request, strict = false).value.flatMap {
        case Right(value) => inner(Tuple1(value))(ctx)
        case Left(e)      => IO.pure(RouteResult.Rejected(Seq(EntityRejection(e))))
      }
  } & cancelRejections(classOf[EntityRejection])

  /**
   * Returns the in-scope [[EntityDecoder]] for the given type.
   *
   * @group marshalling
   */
  def as[T](implicit um: EntityDecoder[IO, T]) = um

  /**
   * Returns the in-scope Marshaller for the given type.
   *
   * @group marshalling
   */
  def instanceOf[T](implicit m: ToResponseMarshaller[T]): ToResponseMarshaller[T] = m

  /**
   * Completes the request using the given function. The input to the function is produced with the in-scope
   * entity unmarshaller and the result value of the function is marshalled with the in-scope marshaller.
   *
   * @group marshalling
   */
  def handleWith[A, B](f: A => B)(implicit entityDecoder: EntityDecoder[IO, A], m: ToResponseMarshaller[B]): Route =
    entity(entityDecoder) { a => complete(f(a)) }
}

object MarshallingDirectives extends MarshallingDirectives
