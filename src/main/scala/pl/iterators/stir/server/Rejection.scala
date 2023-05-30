package pl.iterators.stir.server

import org.http4s.DecodeFailure

trait Rejection

case class ValidationRejection(message: String, cause: Option[Throwable]) extends Rejection

case class EntityRejection(e: DecodeFailure) extends Rejection