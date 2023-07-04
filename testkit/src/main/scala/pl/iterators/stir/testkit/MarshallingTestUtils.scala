package pl.iterators.stir.testkit

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.http4s.{ EntityBody, EntityDecoder, Request, Response }
import pl.iterators.stir.marshalling.ToResponseMarshaller

import scala.util.{ Failure, Success, Try }

trait MarshallingTestUtils {
  def marshal[T](t: T)(implicit m: ToResponseMarshaller[T], runtime: IORuntime): EntityBody[IO] =
    m.toResponse(t).unsafeRunSync().body

  def marshalToResponse[T](t: T)(implicit m: ToResponseMarshaller[T], runtime: IORuntime): Response[IO] = {
    m.toResponse(t).unsafeRunSync()
  }

  def unmarshalValue[T](request: Request[IO])(implicit um: EntityDecoder[IO, T], runtime: IORuntime): T = {
    um.decode(request, strict = false).value.unsafeRunSync().fold(throw _, identity)
  }

  def unmarshal[T](request: Request[IO])(implicit um: EntityDecoder[IO, T], runtime: IORuntime): Try[T] = {
    um.decode(request, strict = false).value.unsafeRunSync().fold(Failure(_), Success(_))
  }
}
