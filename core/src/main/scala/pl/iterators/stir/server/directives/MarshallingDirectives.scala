package pl.iterators.stir.server.directives

import cats.effect.IO
import org.http4s.{
  DecodeFailure,
  DecodeResult,
  EntityDecoder,
  InvalidMessageBodyFailure,
  MediaRange,
  MediaTypeMismatch,
  MediaTypeMissing,
  Request
}
import pl.iterators.stir.impl.util._
import pl.iterators.stir.marshalling.ToResponseMarshaller
import pl.iterators.stir.server._

trait MarshallingDirectives {
  import BasicDirectives._
  import RouteDirectives._

  /**
   * Unmarshalls the request entity to the given type and passes it to its inner route.
   * A `Content-Type` the decoder does not accept rejects with an [[UnsupportedRequestContentTypeRejection]]
   * (a missing `Content-Type` is accepted), a body that cannot be decoded rejects with a
   * [[MalformedRequestContentRejection]], and a decoder failure caused by an `IllegalArgumentException`
   * rejects with a [[ValidationRejection]].
   *
   * @group marshalling
   */
  def entity[T](entityDecoder: EntityDecoder[IO, T]): Directive1[T] = Directive[Tuple1[T]] {
    inner => ctx =>
      MarshallingDirectives.decodeEntity(ctx.request, entityDecoder).flatMap {
        case Right(value)    => inner(Tuple1(value))(ctx)
        case Left(rejection) => IO.pure(RouteResult.Rejected(rejection :: Nil))
      }
  } & cancelRejections(RequestEntityExpectedRejection.getClass, classOf[UnsupportedRequestContentTypeRejection])

  /**
   * Returns the in-scope [[EntityDecoder]] for the given type.
   *
   * @group marshalling
   */
  def as[T](implicit um: EntityDecoder[IO, T]) = um

  /**
   * Decodes the request body as a `String` whatever its `Content-Type` is, like Pekko HTTP's string unmarshaller.
   * It takes precedence over http4s' text-only decoder wherever the directives are in scope.
   *
   * @group marshalling
   */
  implicit val stringEntityDecoder: EntityDecoder[IO, String] =
    EntityDecoder.decodeBy(MediaRange.`*/*`)(media => DecodeResult.success(EntityDecoder.decodeText(media)))

  /**
   * Returns the in-scope Marshaller for the given type.
   *
   * @group marshalling
   */
  def instanceOf[T](implicit m: ToResponseMarshaller[T]): ToResponseMarshaller[T] = m

  /**
   * Uses the marshaller for the given type to produce a completion function that is passed to its inner route.
   * You can use it to decouple the marshaller resolution from the completion of the request, as Pekko's
   * `completeWith`. The completion function may be called from any thread; the first call completes the request
   * and later calls are ignored (Pekko's `Promise` throws on a second call). The request never completes when the
   * function is never called.
   *
   * @group marshalling
   */
  def completeWith[T](marshaller: ToResponseMarshaller[T])(inner: (T => Unit) => Unit): Route = {
    implicit val m: ToResponseMarshaller[T] = marshaller
    complete(IO.async_[T](callback => inner(value => callback(Right(value)))))
  }

  /**
   * Completes the request using the given function. The input to the function is produced with the in-scope
   * entity unmarshaller and the result value of the function is marshalled with the in-scope marshaller.
   *
   * @group marshalling
   */
  def handleWith[A, B](f: A => B)(implicit entityDecoder: EntityDecoder[IO, A], m: ToResponseMarshaller[B]): Route =
    entity(entityDecoder) { a => complete(f(a)) }
}

object MarshallingDirectives extends MarshallingDirectives {

  /**
   * Decodes the request with the given decoder. The `Content-Type` is checked only when the request has one.
   * A `DecodeFailure` (returned or raised), an `EntityStreamSizeException` or an `IllegalRequestException` becomes a
   * rejection; any other error is re-raised. A decoder that fails on a request known to carry no body
   * (`Content-Length: 0`, or an HTTP/1.x request with neither a length nor chunking) rejects with
   * `RequestEntityExpectedRejection`, as Pekko's unmarshallers do on an empty entity, unless the failure is about the
   * `Content-Type`. A length-less HTTP/2 body cannot be told from a missing one by its headers, so its failures stay
   * what the decoder reported.
   */
  private[stir] def decodeEntity[T](request: Request[IO], decoder: EntityDecoder[IO, T]): IO[Either[Rejection, T]] =
    decoder.decode(request, strict = request.contentType.isDefined).value.attempt.flatMap {
      case Right(Right(value))                => IO.pure(Right(value))
      case Right(Left(failure))               => IO.pure(Left(rejectionFor(request, failure)))
      case Left(failure: DecodeFailure)       => IO.pure(Left(rejectionFor(request, failure)))
      case Left(e: EntityStreamSizeException) => IO.pure(Left(MalformedRequestContentRejection(e.getMessage, e)))
      case Left(e: IllegalRequestException)   =>
        // e.g. a corrupt compressed body from decodeRequest: a rejection, so alternatives and handlers still apply
        IO.pure(Left(MalformedRequestContentRejection(e.message, e)))
      case Left(error) => IO.raiseError(error)
    }

  private def rejectionFor(request: Request[IO], failure: DecodeFailure): Rejection = failure match {
    case _: MediaTypeMismatch | _: MediaTypeMissing => rejectionFor(failure)
    case _ if knownEmptyBody(request)               => RequestEntityExpectedRejection
    case _                                          => rejectionFor(failure)
  }

  /** `Content-Length: 0`, or an HTTP/1.x request without a length and not chunked (HTTP/2 bodies carry no length). */
  private def knownEmptyBody(request: Request[IO]): Boolean =
    MiscDirectives.isKnownEmpty(request) && !(request.httpVersion.major >= 2 && request.contentLength.isEmpty)

  private[stir] def rejectionFor(failure: DecodeFailure): Rejection = failure match {
    case MediaTypeMismatch(actual, expected)                                 => UnsupportedRequestContentTypeRejection(expected, Some(actual))
    case MediaTypeMissing(expected)                                          => UnsupportedRequestContentTypeRejection(expected, None)
    case InvalidMessageBodyFailure(_, Some(cause: IllegalArgumentException)) =>
      ValidationRejection(cause.getMessage.nullAsEmpty, Some(cause))
    case other => MalformedRequestContentRejection(other.message, other)
  }
}
