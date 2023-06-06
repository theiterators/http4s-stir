package pl.iterators.stir.server

import org.http4s.DecodeFailure

trait Rejection

/**
 * Rejection created by the `validation` directive as well as for `IllegalArgumentExceptions`
 * thrown by domain model constructors (e.g. via `require`).
 * It signals that an expected value was semantically invalid.
 */
case class ValidationRejection(message: String, cause: Option[Throwable]) extends Rejection

/**
 * Rejection created by unmarshallers.
 * Signals that the request was rejected because the requests content-type is unsupported.
 */
case class EntityRejection(e: DecodeFailure) extends Rejection

/**
 * Rejection created by unmarshallers.
 * Signals that the request was rejected because unmarshalling failed with an error that wasn't
 * an `IllegalArgumentException`. Usually that means that the request content was not of the expected format.
 * Note that semantic issues with the request content (e.g. because some parameter was out of range)
 * will usually trigger a `ValidationRejection` instead.
 */
case class MalformedRequestContentRejection(message: String, cause: Throwable) extends Rejection

/**
 * Rejection created by parameter filters.
 * Signals that the request was rejected because a query parameter value was not equal to required one.
 */
final case class InvalidRequiredValueForQueryParamRejection(parameterName: String, expectedValue: String, actualValue: String) extends Rejection

/**
 * Rejection created by parameter filters.
 * Signals that the request was rejected because a query parameter was not found.
 */
final case class MissingQueryParamRejection(parameterName: String) extends Rejection

/**
 * Rejection created by parameter filters.
 * Signals that the request was rejected because a query parameter could not be interpreted.
 */
final case class MalformedQueryParamRejection(parameterName: String, errorMsg: String, cause: Option[Throwable] = None) extends Rejection