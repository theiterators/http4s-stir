package pl.iterators.stir.testkit

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.http4s.{EntityDecoder, Request}
import org.scalatest.matchers.Matcher
import org.scalatest.{Suite, matchers}

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Try

trait ScalatestUtils extends MarshallingTestUtils {
  import matchers.should.Matchers._

  def evaluateTo[T](value: T)(implicit runtime: IORuntime): Matcher[IO[T]] =
    equal(value).matcher[T].compose(x => Await.result(x.unsafeToFuture(), Duration.Inf))

  def haveFailedWith(t: Throwable)(implicit runtime: IORuntime): Matcher[IO[_]] =
    equal(t).matcher[Throwable].compose(x => Await.result(x.unsafeToFuture().failed, Duration.Inf))

  def unmarshalToValue[T](value: T)(implicit um: EntityDecoder[IO, T], runtime: IORuntime): Matcher[Request[IO]] =
    equal(value).matcher[T].compose(unmarshalValue(_))

  def unmarshalTo[T](value: Try[T])(implicit um: EntityDecoder[IO, T], runtime: IORuntime): Matcher[Request[IO]] =
    equal(value).matcher[Try[T]].compose(unmarshal(_))
}

trait ScalatestRouteTest extends RouteTest with TestFrameworkInterface.Scalatest with ScalatestUtils { this: Suite => }