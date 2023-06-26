package pl.iterators.stir.server

import directives._

/**
 * Collects all default directives into one trait for simple importing.
 */
trait Directives extends RouteConcatenation
    with BasicDirectives
    with CacheConditionDirectives
    with CookieDirectives
    with DebuggingDirectives
    with CodingDirectives
    with ExecutionDirectives
    with FileAndResourceDirectives
//  with FileUploadDirectives
    with FormFieldDirectives
    with IODirectives // instead of FutureDirectives
    with HeaderDirectives
    with HostDirectives
    with MarshallingDirectives
    with MethodDirectives
    with MiscDirectives
    with ParameterDirectives
    with TimeoutDirectives
    with PathDirectives
    with RangeDirectives
    with RespondWithDirectives
    with RouteDirectives
    with SchemeDirectives
    with SecurityDirectives
//  with WebSocketDirectives
    with FramedEntityStreamingDirectives
    with AttributeDirectives

/**
 * Collects all default directives into one object for simple importing.
 */
object Directives extends Directives
