package pl.iterators.stir.server.directives

///**
// * Allows the [[MarshallingDirectives.entity]] directive to extract a [[akka.stream.scaladsl.Source]] of elements.
// *
// * See [[common.EntityStreamingSupport]] for useful default framing `Flow` instances and
// * support traits such as `SprayJsonSupport` (or your other favourite JSON library) to provide the needed [[Marshaller]] s.
// */
trait FramedEntityStreamingDirectives extends MarshallingDirectives {
//
//  type RequestToSourceUnmarshaller[T] = FromRequestUnmarshaller[Source[T, NotUsed]]
//
//  /**
//   * Extracts entity as [[akka.stream.scaladsl.Source]] of elements of type `T`.
//   * This is achieved by applying the implicitly provided (in the following order):
//   *
//   * - 1st: chunk-up the incoming [[ByteString]]s by applying the `Content-Type`-aware framing
//   * - 2nd: apply the [[Unmarshaller]] (from [[ByteString]] to `T`) for each of the respective "chunks" (e.g. for each JSON element contained within an array).
//   *
//   * The request will be rejected with an [[akka.http.scaladsl.server.UnsupportedRequestContentTypeRejection]] if
//   * its [[ContentType]] is not supported by the used `framing` or `unmarshaller`.
//   *
//   * Cancelling extracted [[akka.stream.scaladsl.Source]] closes the connection abruptly (same as cancelling the `entity.dataBytes`).
//   *
//   * See also [[MiscDirectives.withoutSizeLimit]] as you may want to allow streaming infinite streams of data in this route.
//   * By default the uploaded data is limited by the `akka.http.parsing.max-content-length`.
//   */
//  final def asSourceOf[T](implicit um: FromByteStringUnmarshaller[T], support: EntityStreamingSupport): RequestToSourceUnmarshaller[T] =
//    asSourceOfInternal(um, support)
//
//  /**
//   * Extracts entity as [[akka.stream.scaladsl.Source]] of elements of type `T`.
//   * This is achieved by applying the implicitly provided (in the following order):
//   *
//   * - 1st: chunk-up the incoming [[ByteString]]s by applying the `Content-Type`-aware framing
//   * - 2nd: apply the [[Unmarshaller]] (from [[ByteString]] to `T`) for each of the respective "chunks" (e.g. for each JSON element contained within an array).
//   *
//   * The request will be rejected with an [[akka.http.scaladsl.server.UnsupportedRequestContentTypeRejection]] if
//   * its [[ContentType]] is not supported by the used `framing` or `unmarshaller`.
//   *
//   * Cancelling extracted [[akka.stream.scaladsl.Source]] closes the connection abruptly (same as cancelling the `entity.dataBytes`).
//   *
//   * See also [[MiscDirectives.withoutSizeLimit]] as you may want to allow streaming infinite streams of data in this route.
//   * By default the uploaded data is limited by the `akka.http.parsing.max-content-length`.
//   */
//  final def asSourceOf[T](support: EntityStreamingSupport)(implicit um: FromByteStringUnmarshaller[T]): RequestToSourceUnmarshaller[T] =
//    asSourceOfInternal(um, support)
//
//  // format: OFF
//  private final def asSourceOfInternal[T](um: Unmarshaller[ByteString, T], support: EntityStreamingSupport): RequestToSourceUnmarshaller[T] =
//    Unmarshaller.withMaterializer[HttpRequest, Source[T, NotUsed]] { implicit ec => implicit mat => req =>
//      val entity = req.entity
//      if (support.supported.matches(entity.contentType)) {
//        val bytes = entity.dataBytes
//        val frames = bytes.via(support.framingDecoder)
//        val marshalling =
//          if (support.unordered) Flow[ByteString].mapAsyncUnordered(support.parallelism)(bs => um(bs)(ec, mat))
//          else Flow[ByteString].mapAsync(support.parallelism)(bs => um(bs)(ec, mat))
//
//        val elements = frames.viaMat(marshalling)(Keep.right)
//        FastFuture.successful(elements)
//
//      } else FastFuture.failed(UnsupportedContentTypeException(Some(entity.contentType), support.supported))
//    }
//  // format: ON

}
