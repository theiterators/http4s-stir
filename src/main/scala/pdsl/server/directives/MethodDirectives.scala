package pdsl.server.directives

import org.http4s.Method
import pdsl.server.{Directive, Directive0, Directive1, StandardRoute}

object MethodDirectives {
  def extractMethod: Directive1[Method] = BasicDirectives.extract[Method](_.method)

  def method(method: Method): Directive0 = {
    extractMethod.flatMap { m =>
      if (m == method) {
        Directive.Empty
      } else {
        StandardRoute(RouteDirectives.reject)
      }
    }
  }

  def get: Directive0 = method(Method.GET)
  def post: Directive0 = method(Method.POST)
  def put: Directive0 = method(Method.PUT)
  def delete: Directive0 = method(Method.DELETE)
  def options: Directive0 = method(Method.OPTIONS)
  def patch: Directive0 = method(Method.PATCH)
  def trace: Directive0 = method(Method.TRACE)
  def connect: Directive0 = method(Method.CONNECT)
  def head: Directive0 = method(Method.HEAD)
}