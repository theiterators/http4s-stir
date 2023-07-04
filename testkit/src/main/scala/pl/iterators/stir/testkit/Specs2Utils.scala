package pl.iterators.stir.testkit

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.http4s.{ EntityDecoder, Request }
import org.specs2.matcher.AnyMatchers._
import org.specs2.matcher.{ Expectable, MatchResult, Matcher }

import scala.util.{ Failure, Try }

trait Specs2Utils extends MarshallingTestUtils {

  def evaluateTo[T](value: T)(implicit runtime: IORuntime): Matcher[IO[T]] = new Matcher[IO[T]] {
    override def apply[S <: IO[T]](s: Expectable[S]): MatchResult[S] = result(
      s.value.unsafeRunSync() == value,
      s"${s.description} should evaluate to $value",
      s"${s.description} should not evaluate to $value",
      s)
  }

  def haveFailedWith(t: Throwable)(implicit runtime: IORuntime): Matcher[IO[_]] = new Matcher[IO[_]] {
    override def apply[S <: IO[_]](s: Expectable[S]): MatchResult[S] = result(
      Try(s.value.unsafeRunSync()) == Failure(t),
      s"${s.description} should have failed with $t",
      s"${s.description} should not have failed with $t",
      s)
  }

  def unmarshalToValue[T](value: T)(implicit um: EntityDecoder[IO, T], runtime: IORuntime): Matcher[Request[IO]] =
    beEqualTo(value).^^(unmarshalValue(_: Request[IO]))

  def unmarshalTo[T](value: Try[T])(implicit um: EntityDecoder[IO, T], runtime: IORuntime): Matcher[Request[IO]] =
    beEqualTo(value).^^(unmarshal(_: Request[IO]))
}

trait Specs2RouteTest extends RouteTest with Specs2FrameworkInterface.Specs2 with Specs2Utils
