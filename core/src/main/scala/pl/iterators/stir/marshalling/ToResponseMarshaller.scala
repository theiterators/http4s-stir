package pl.iterators.stir.marshalling

import cats.effect.{ IO, SyncIO }
import org.http4s.{ EntityEncoder, Headers, Response, Status }
import org.typelevel.vault.Key

trait ToResponseMarshaller[T] {
  def toResponse(trm: T): IO[Response[IO]]
}

object ToResponseMarshaller {
  def apply[T](implicit trm: ToResponseMarshaller[T]): ToResponseMarshaller[T] = trm

  /**
   * Marks a response whose `Content-Type` `complete` negotiates against the request's `Accept` header, rejecting
   * with an `UnacceptedResponseContentTypeRejection` (406 when sealed) when it is not acceptable, as Pekko does
   * for its marshallers. Carried by the response rather than the marshaller so that it survives every wrapper
   * (`IO[T]`, `ToResponseMarshallable`, a custom marshaller delegating to a built-in one).
   */
  private[stir] val NegotiatedKey: Key[Unit] = Key.newKey[SyncIO, Unit].unsafeRunSync()

  /**
   * A marshaller whose produced content type is negotiated against the request's `Accept`. The marshallers built on
   * an `EntityEncoder` are; an explicit `Response` or a bare status is opaque and never rejected. Build a custom
   * marshaller with it to opt in.
   */
  def negotiated[T](f: T => IO[Response[IO]]): ToResponseMarshaller[T] =
    (trm: T) => f(trm).map(_.withAttribute(NegotiatedKey, ()))

  implicit def fromEncoderable[E](implicit encoder: EntityEncoder[IO, E]): ToResponseMarshaller[E] =
    negotiated(trm => IO.pure(Response[IO]().withEntity(trm)))

  implicit def fromEncoderableWithStatus[E](
      implicit encoder: EntityEncoder[IO, E]): ToResponseMarshaller[(Status, E)] =
    (trm: (Status, E)) => negotiatedIfSuccess(withEntityIfAllowed(Response[IO](status = trm._1), trm._2))

  /** A status that allows no entity (1xx, 204, 205, 304) gets none, whatever the value, as in Pekko. */
  private def withEntityIfAllowed[E](response: Response[IO], value: E)(
      implicit encoder: EntityEncoder[IO, E]): Response[IO] =
    if (response.status.isEntityAllowed) response.withEntity(value) else response

  /**
   * A successful response (`Status.isSuccess`: 1xx to 3xx, as Pekko's `isSuccess`) is negotiated; an error (4xx, 5xx)
   * is sent whatever the request accepts, as Pekko adds an opaque fallback for errors so that
   * `complete(Status.BadRequest, "...")` reaches a client asking for JSON
   * (akka/akka#19397).
   */
  private def negotiatedIfSuccess(response: Response[IO]): IO[Response[IO]] =
    IO.pure(if (response.status.isSuccess) response.withAttribute(NegotiatedKey, ()) else response)

  implicit def fromStatusWithHeaders: ToResponseMarshaller[(Status, Headers)] = {
    (trm: (Status, Headers)) =>
      {
        IO.pure(Response[IO](status = trm._1, headers = trm._2))
      }
  }

  implicit def fromEncoderableWithStatusAndHeaders[E](
      implicit encoder: EntityEncoder[IO, E]): ToResponseMarshaller[(Status, Headers, E)] =
    (trm: (Status, Headers, E)) =>
      negotiatedIfSuccess(withEntityIfAllowed(Response[IO](status = trm._1, headers = trm._2), trm._3))

  implicit def fromStatus: ToResponseMarshaller[Status] = {
    (trm: Status) =>
      {
        IO.pure(Response[IO](status = trm))
      }
  }

  implicit def fromIO[E](implicit trm: ToResponseMarshaller[E]): ToResponseMarshaller[IO[E]] =
    (trmIO: IO[E]) => trmIO.flatMap(trm.toResponse)

  implicit def fromResponse[E]: ToResponseMarshaller[Response[IO]] = {
    (trmResponse: Response[IO]) =>
      {
        IO.pure(trmResponse)
      }
  }
}
