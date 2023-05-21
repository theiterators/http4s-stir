package pdsl.server.directives

import cats.effect.IO
import org.http4s.Request
import pdsl.server.{Directive, Directive0, Directive1, RequestContext, Route}
import pdsl.util.Tuple

object BasicDirectives {
  def pass: Directive0 = Directive.Empty

  def mapInnerRoute(f: Route => Route): Directive0 =
    Directive { inner => f(inner(())) }

  def mapRequestContext(f: RequestContext => RequestContext): Directive0 =
    mapInnerRoute { inner => ctx => inner(f(ctx)) }

  /**
   * @group basic
   */
  def mapRequest(f: Request[IO] => Request[IO]): Directive0 =
    mapRequestContext { ctx => f(ctx) }

  /**
   * Injects the given value into a directive.
   *
   * @group basic
   */
  def provide[T](value: T): Directive1[T] = tprovide(Tuple1(value))

  /**
   * Injects the given values into a directive.
   *
   * @group basic
   */
  def tprovide[L: Tuple](values: L): Directive[L] =
    Directive {
      _(values)
    }

  /**
   * Extracts a single value using the given function.
   *
   * @group basic
   */
  def extract[T](f: RequestContext => T): Directive1[T] =
    textract(ctx => Tuple1(f(ctx)))

  /**
   * Extracts a number of values using the given function.
   *
   * @group basic
   */
  def textract[L: Tuple](f: RequestContext => L): Directive[L] =
    Directive { inner => ctx => inner(f(ctx))(ctx) }
}