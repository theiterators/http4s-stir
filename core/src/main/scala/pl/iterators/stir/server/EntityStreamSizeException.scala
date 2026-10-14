package pl.iterators.stir.server

/**
 * Raised when a request entity exceeds the size limit set by `withSizeLimit` or `toStrictEntity`.
 * The default [[ExceptionHandler]] completes it with `413 Payload Too Large`.
 */
final case class EntityStreamSizeException(limit: Long, actualSize: Option[Long] = None) extends RuntimeException {
  override def getMessage: String = {
    val actual = actualSize.fold("")(size => s"($size) ")
    s"Incoming entity size ${actual}exceeded size limit ($limit bytes)"
  }
}
