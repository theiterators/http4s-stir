package pl.iterators.stir.marshalling

import cats.effect.IO
import org.http4s.{ EntityEncoder, Headers, Response, Status }

trait ToResponseMarshaller[T] {
  def toResponse(trm: T): IO[Response[IO]]

  /**
   * Whether `complete` negotiates the produced `Content-Type` against the request's `Accept` header and rejects
   * with an `UnacceptedResponseContentTypeRejection` (406 when sealed) when it is not acceptable, as Pekko does for
   * its marshallers. The marshallers built on an `EntityEncoder` negotiate; an explicit `Response` or a bare status
   * is opaque and never rejected. Override in a custom marshaller to opt in.
   */
  def negotiable: Boolean = false
}

object ToResponseMarshaller {
  def apply[T](implicit trm: ToResponseMarshaller[T]): ToResponseMarshaller[T] = trm

  /** A marshaller whose produced content type is negotiated against the request's `Accept`. */
  def negotiated[T](f: T => IO[Response[IO]]): ToResponseMarshaller[T] = new ToResponseMarshaller[T] {
    def toResponse(trm: T): IO[Response[IO]] = f(trm)
    override def negotiable: Boolean = true
  }

  implicit def fromEncoderable[E](implicit encoder: EntityEncoder[IO, E]): ToResponseMarshaller[E] =
    negotiated(trm => IO.pure(Response[IO]().withEntity(trm)))

  implicit def fromEncoderableWithStatus[E](
      implicit encoder: EntityEncoder[IO, E]): ToResponseMarshaller[(Status, E)] =
    negotiated(trm => IO.pure(Response[IO](status = trm._1).withEntity(trm._2)))

  implicit def fromStatusWithHeaders: ToResponseMarshaller[(Status, Headers)] = {
    (trm: (Status, Headers)) =>
      {
        IO.pure(Response[IO](status = trm._1, headers = trm._2))
      }
  }

  implicit def fromEncoderableWithStatusAndHeaders[E](
      implicit encoder: EntityEncoder[IO, E]): ToResponseMarshaller[(Status, Headers, E)] =
    negotiated(trm => IO.pure(Response[IO](status = trm._1, headers = trm._2).withEntity(trm._3)))

  implicit def fromStatus: ToResponseMarshaller[Status] = {
    (trm: Status) =>
      {
        IO.pure(Response[IO](status = trm))
      }
  }

  implicit def fromIO[E](implicit trm: ToResponseMarshaller[E]): ToResponseMarshaller[IO[E]] =
    new ToResponseMarshaller[IO[E]] {
      def toResponse(trmIO: IO[E]): IO[Response[IO]] = trmIO.flatMap(trm.toResponse)
      override def negotiable: Boolean = trm.negotiable
    }

  implicit def fromResponse[E]: ToResponseMarshaller[Response[IO]] = {
    (trmResponse: Response[IO]) =>
      {
        IO.pure(trmResponse)
      }
  }
}
