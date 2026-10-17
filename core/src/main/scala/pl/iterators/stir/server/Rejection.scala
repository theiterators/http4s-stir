package pl.iterators.stir.server

import org.http4s.{ Challenge, ContentCoding, MediaRange, MediaType, Method }
import org.http4s.headers.{ `Content-Type`, Origin, Range }
import org.typelevel.vault.Key

trait Rejection

/**
 * Rejection created by the `validation` directive as well as for `IllegalArgumentExceptions`
 * thrown by domain model constructors (e.g. via `require`).
 * It signals that an expected value was semantically invalid.
 */
case class ValidationRejection(message: String, cause: Option[Throwable]) extends Rejection

/**
 * Rejection created by unmarshallers.
 * Signals that the request was rejected because the request's Content-Type is not supported by the decoder.
 * `supported` lists the media ranges the decoder accepts, `contentType` the media type the request declared.
 */
final case class UnsupportedRequestContentTypeRejection(supported: Set[MediaRange],
    contentType: Option[MediaType] = None) extends Rejection

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
final case class MalformedFormFieldRejection(fieldName: String, errorMsg: String, cause: Option[Throwable] = None)
    extends Rejection

/**
 * Rejection created by scheme filters.
 * Signals that the request was rejected because the Uri scheme is unsupported.
 */
final case class SchemeRejection(supported: String) extends Rejection

/**
 * Rejection created by the `checkSameOrigin` directive.
 * Signals that the request was rejected because `Origin` header value is not in the allowed list.
 */
final case class InvalidOriginRejection(allowedOrigins: Seq[Origin.Host]) extends Rejection

/**
 * Rejection created by unmarshallers and the `requestEntityPresent` directive.
 * Signals that the request was rejected because an entity was expected but not supplied.
 */
case object RequestEntityExpectedRejection extends Rejection

/**
 * Rejection created by marshallers.
 * Signals that the request was rejected because the service is not capable of producing a response entity whose
 * content type is accepted by the client (`Accept`).
 */
final case class UnacceptedResponseContentTypeRejection(supported: Set[`Content-Type`]) extends Rejection

/**
 * Rejection created by encoding filters.
 * Signals that the request was rejected because the client does not accept any of the response encodings the
 * route can produce.
 */
final case class UnacceptedResponseEncodingRejection(supported: Set[ContentCoding]) extends Rejection

/**
 * Rejection created by decoding filters.
 * Signals that the request was rejected because the request's Content-Encoding is not supported.
 */
final case class UnsupportedRequestEncodingRejection(supported: ContentCoding) extends Rejection

/**
 * Rejection created by the WebSocket directives.
 * Signals that the request was rejected because it was not a WebSocket upgrade request.
 */
case object ExpectedWebSocketRequestRejection extends Rejection

/**
 * Rejection created by the WebSocket directives.
 * Signals that the request was rejected because none of the offered subprotocols is supported.
 */
final case class UnsupportedWebSocketSubprotocolRejection(supportedProtocol: String) extends Rejection

/**
 * Rejection created by the `attribute` directive.
 * Signals that the request was rejected because a request attribute was expected but missing; the default handler
 * answers `500 Internal Server Error`, since a missing attribute is a server-side configuration error.
 */
final case class MissingAttributeRejection[T](key: Key[T]) extends Rejection

/**
 * Rejection created by the `withRangeSupport` directive.
 * Signals that the request was rejected because none of the requested ranges can be satisfied by an entity of
 * `actualEntityLength` bytes (-1 when the length is unknown).
 */
final case class UnsatisfiableRangeRejection(unsatisfiableRanges: Seq[Range.SubRange], actualEntityLength: Long)
    extends Rejection

/**
 * Rejection created by the `withRangeSupport` directive.
 * Signals that the request was rejected because it asked for more than `maxRanges` ranges.
 */
final case class TooManyRangesRejection(maxRanges: Int) extends Rejection
