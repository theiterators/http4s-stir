package pl.iterators.stir.marshalling

trait ToResponseMarshallable {
  type T
  def value: T
  implicit def marshaller: ToResponseMarshaller[T]
}

object ToResponseMarshallable {
  implicit def apply[A](_value: A)(implicit _marshaller: ToResponseMarshaller[A]): ToResponseMarshallable =
    new ToResponseMarshallable {
      type T = A
      def value: T = _value
      def marshaller: ToResponseMarshaller[T] = _marshaller
    }

  implicit val marshaller: ToResponseMarshaller[ToResponseMarshallable] = {
    (trm: ToResponseMarshallable) => trm.marshaller.toResponse(trm.value)
  }
}
