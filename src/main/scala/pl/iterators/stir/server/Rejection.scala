package pl.iterators.stir.server

import org.http4s.{Challenge, DecodeFailure, Method}

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
final case class InvalidRequiredValueForQueryParamRejection(parameterName: String, expectedValue: String,
    actualValue: String) extends Rejection

/**
 * Rejection created by parameter filters.
 * Signals that the request was rejected because a query parameter was not found.
 */
final case class MissingQueryParamRejection(parameterName: String) extends Rejection

/**
 * Rejection created by parameter filters.
 * Signals that the request was rejected because a query parameter could not be interpreted.
 */
final case class MalformedQueryParamRejection(parameterName: String, errorMsg: String, cause: Option[Throwable] = None)
    extends Rejection

/**
 * Rejection created by header directives.
 * Signals that the request was rejected because a header value is malformed.
 */
final case class MalformedHeaderRejection(headerName: String, errorMsg: String, cause: Option[Throwable] = None)
    extends Rejection

/**
 * Rejection created by header directives.
 * Signals that the request was rejected because a required header could not be found.
 */
final case class MissingHeaderRejection(headerName: String) extends Rejection

/**
 * Rejection created by method filters.
 * Signals that the request was rejected because the HTTP method is unsupported.
 */
final case class MethodRejection(supported: Method) extends Rejection

/**
 * Rejection created by the `cookie` directive.
 * Signals that the request was rejected because a cookie was not found.
 */
final case class MissingCookieRejection(cookieName: String) extends Rejection

/**
 * A special Rejection that serves as a container for a transformation function on rejections.
 * It is used by some directives to "cancel" rejections that are added by later directives of a similar type.
 *
 * Consider this route structure for example:
 *
 * {{{
 *     put { reject(ValidationRejection("no") } ~ get { ... }
 * }}}
 *
 * If this structure is applied to a PUT request the list of rejections coming back contains three elements:
 *
 * 1. A ValidationRejection
 * 2. A MethodRejection
 * 3. A TransformationRejection holding a function filtering out the MethodRejection
 *
 * so that in the end the RejectionHandler will only see one rejection (the ValidationRejection), because the
 * MethodRejection added by the `get` directive is canceled by the `put` directive (since the HTTP method
 * did indeed match eventually).
 */
final case class TransformationRejection(transform: Seq[Rejection] => Seq[Rejection]) extends Rejection

/**
 * Rejection created by the various [[pl.iterators.stir.server.directives.SecurityDirectives]].
 * Signals that the request was rejected because the user could not be authenticated. The reason for the rejection is
 * specified in the cause.
 */
final case class AuthenticationFailedRejection(cause: AuthenticationFailedRejection.Cause, challenge: Challenge)
    extends Rejection

object AuthenticationFailedRejection {

  /**
   * Signals the cause of the failed authentication.
   */
  sealed trait Cause

  /**
   * Signals the cause of the rejecting was that the user could not be authenticated, because the `WWW-Authenticate`
   * header was not supplied.
   */
  case object CredentialsMissing extends Cause

  /**
   * Signals the cause of the rejecting was that the user could not be authenticated, because the supplied credentials
   * are invalid.
   */
  case object CredentialsRejected extends Cause
}

/**
 * Rejection created by the 'authorize' directive.
 * Signals that the request was rejected because the user is not authorized.
 */
case object AuthorizationFailedRejection extends Rejection

/**
 * Rejection created by form field filters.
 * Signals that the request was rejected because a form field was not found.
 */
final case class MissingFormFieldRejection(fieldName: String) extends Rejection

/**
 * Rejection created by form field filters.
 * Signals that the request was rejected because a form field could not be interpreted.
 */
final case class MalformedFormFieldRejection(fieldName: String, errorMsg: String, cause: Option[Throwable] = None) extends Rejection