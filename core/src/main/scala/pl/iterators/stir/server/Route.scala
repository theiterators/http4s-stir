package pl.iterators.stir.server

object Route {

  /**
   * Helper for constructing a Route from a function literal.
   */
  def apply(f: Route): Route = f

  /**
   * "Seals" a route by wrapping it with default exception handling and rejection conversion.
   *
   * A sealed route has these properties:
   *  - The result of the route will always be a complete response, i.e. the result of the future is a
   *    ``Success(RouteResult.Complete(response))``, never a failed future and never a rejected route. These
   *    will be already be handled using the implicitly given [[RejectionHandler]] and [[ExceptionHandler]] (or
   *    the default handlers if none are given or can be found implicitly).
   *  - Consequently, no route alternatives will be tried that were combined with this route
   *    using the ``~`` on routes or the [[Directive.|]] operator on directives.
   */
  def seal(route: Route)(implicit rejectionHandler: RejectionHandler = RejectionHandler.default, exceptionHandler: ExceptionHandler = null): Route = {
    import directives.ExecutionDirectives._
    (handleExceptions(ExceptionHandler.seal(exceptionHandler)()) & handleRejections(rejectionHandler.seal))
      .tapply(_ => route)
  }
}