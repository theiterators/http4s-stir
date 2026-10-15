package pl.iterators.stir.server

import org.http4s.Status

/**
 * Raised by directives that detect an illegal request after routing started, e.g. `toStrictEntity` when the
 * entity does not arrive in time. The default [[ExceptionHandler]] completes it with the given status and message.
 */
final case class IllegalRequestException(status: Status, message: String) extends RuntimeException(message)
