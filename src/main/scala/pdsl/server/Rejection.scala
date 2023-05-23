package pdsl.server

trait Rejection

case class ValidationRejection(message: String, cause: Option[Throwable]) extends Rejection
