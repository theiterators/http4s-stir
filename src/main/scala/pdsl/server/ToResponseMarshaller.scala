package pdsl.server

import cats.effect.IO
import org.http4s.{EntityEncoder, Response, Status}

trait ToResponseMarshaller[T] {
  def toResponse(trm: T): IO[Response[IO]]
}

object ToResponseMarshaller {
  def apply[T](implicit trm: ToResponseMarshaller[T]): ToResponseMarshaller[T] = trm

  implicit def fromEncoderable[E](implicit encoder: EntityEncoder[IO, E]): ToResponseMarshaller[E] = {
    (trm: E) => {
      IO.pure(Response[IO]().withEntity(trm))
    }
  }

  implicit def fromEncoderableWithStatus[E](implicit encoder: EntityEncoder[IO, E]): ToResponseMarshaller[(Status, E)] = {
    (trm: (Status, E)) => {
      IO.pure(Response[IO](status = trm._1).withEntity(trm._2))
    }
  }

  implicit def fromStatus: ToResponseMarshaller[Status] = {
    (trm: Status) => {
      IO.pure(Response[IO](status = trm))
    }
  }

  implicit def fromIO[E](implicit trm: ToResponseMarshaller[E]): ToResponseMarshaller[IO[E]] = {
    (trmIO: IO[E]) => {
      trmIO.flatMap(trm.toResponse)
    }
  }
}
