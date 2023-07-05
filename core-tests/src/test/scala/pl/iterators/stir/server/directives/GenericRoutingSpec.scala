package pl.iterators.stir.server.directives

import cats.effect.IO
import org.http4s.{ Response, Status }
import org.scalatest.Suite
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import pl.iterators.stir.server._
import pl.iterators.stir.testkit.ScalatestRouteTest

trait GenericRoutingSpec extends Matchers with Directives with ScalatestRouteTest { this: Suite =>
  val Ok = Response[IO](status = Status.Ok)
  val completeOk = complete(Ok)

  def echoComplete[T]: T => Route = { x => complete(x.toString) }
  def echoComplete2[T, U]: (T, U) => Route = { (x, y) => complete(s"$x $y") }
}

abstract class RoutingSpec extends AnyWordSpec with GenericRoutingSpec {}
