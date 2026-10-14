package pl.iterators.stir.server.directives

import cats.effect.IO
import fs2.{ Chunk, Stream }
import org.http4s.{ DecodeResult, EntityDecoder, EntityEncoder, Response }
import pl.iterators.stir.common.EntityStreamingSupport
import pl.iterators.stir.marshalling.ToResponseMarshaller
import pl.iterators.stir.unmarshalling.Unmarshaller

/**
 * Allows the [[MarshallingDirectives.entity]] directive to extract a `fs2.Stream` of elements, and `complete` to
 * render one, framed according to an in-scope [[EntityStreamingSupport]].
 *
 * @groupname streaming Framed entity streaming directives
 * @groupprio streaming 240
 */
trait FramedEntityStreamingDirectives extends MarshallingDirectives {

  type RequestToSourceUnmarshaller[T] = EntityDecoder[IO, Stream[IO, T]]

  /**
   * Extracts the entity as a `Stream` of elements of type `T`: the request bytes are split into frames by the
   * `Content-Type`-aware framing of the in-scope [[EntityStreamingSupport]], and each frame is decoded as UTF-8
   * text with the given `Unmarshaller`. A `Content-Type` the support does not accept rejects with an
   * `UnsupportedRequestContentTypeRejection`; a frame that breaks the framing limits fails the stream with a 400.
   *
   * @group streaming
   */
  final def asSourceOf[T](implicit um: Unmarshaller[String, T],
      support: EntityStreamingSupport): RequestToSourceUnmarshaller[T] =
    asSourceOfInternal(um, support)

  /**
   * @group streaming
   */
  final def asSourceOf[T](support: EntityStreamingSupport)(
      implicit um: Unmarshaller[String, T]): RequestToSourceUnmarshaller[T] =
    asSourceOfInternal(um, support)

  private def asSourceOfInternal[T](um: Unmarshaller[String, T],
      support: EntityStreamingSupport): RequestToSourceUnmarshaller[T] =
    EntityDecoder.decodeBy(support.supported) { media =>
      DecodeResult.successT[IO, Stream[IO, T]](
        media.body.through(support.framingDecoder).evalMap(frame => um(new String(frame.toArray, "UTF-8"))))
    }

  /**
   * Renders a `Stream` of elements as a response: each element is encoded with its `EntityEncoder` and the frames
   * are joined by the in-scope [[EntityStreamingSupport]], whose content type the response carries.
   *
   * @group streaming
   */
  implicit def streamToResponseMarshaller[T](implicit encoder: EntityEncoder[IO, T],
      support: EntityStreamingSupport): ToResponseMarshaller[Stream[IO, T]] =
    (stream: Stream[IO, T]) =>
      IO.pure(Response[IO]().withContentType(support.contentType).withBodyStream(
        stream.evalMap(element => encoder.toEntity(element).body.compile.to(Chunk)).through(support.framingRenderer)))
}

object FramedEntityStreamingDirectives extends FramedEntityStreamingDirectives
