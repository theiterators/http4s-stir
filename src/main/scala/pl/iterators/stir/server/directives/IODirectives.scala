package pl.iterators.stir.server.directives

import cats.effect.IO
import pl.iterators.stir.server.{Directive, Directive1, ToResponseMarshaller}
import pl.iterators.stir.util.Tupler

import scala.util._

trait IODirectives {
  /**
   * "Unwraps" a `IO[T]` and runs the inner route after io
   * completion with the io's value as an extraction of type `Try[T]`.
   *
   * @group future
   */
  def onComplete[T](io: => IO[T]): Directive1[Try[T]] =
    Directive { inner =>
      ctx =>
        io.attempt.flatMap {
          case Right(t) => inner(Tuple1(Success(t)))(ctx)
          case Left(e) => inner(Tuple1(Failure(e)))(ctx)
        }
    }

  /**
   * "Unwraps" a `IO[T]` and runs the inner route after future
   * completion with the future's value as an extraction of type `T`.
   * If the future fails its failure Throwable is bubbled up to the nearest
   * ExceptionHandler.
   * If type `T` is already a Tuple it is directly expanded into the respective
   * number of extractions.
   *
   * @group future
   */
  def onSuccess(magnet: OnSuccessMagnet): Directive[magnet.Out] = magnet.directive

  /**
   * "Unwraps" a `IO[T]` and runs the inner route when the future has failed
   * with the future's failure exception as an extraction of type `Throwable`.
   * If the future succeeds the request is completed using the values marshaller
   * (This directive therefore requires a marshaller for the futures type to be
   * implicitly available.)
   *
   * @group future
   */
  def completeOrRecoverWith(magnet: CompleteOrRecoverWithMagnet): Directive1[Throwable] = magnet.directive
}

object IODirectives extends IODirectives

trait OnSuccessMagnet {
  type Out
  def directive: Directive[Out]
}

object OnSuccessMagnet {
  implicit def apply[T](io: => IO[T])(implicit tupler: Tupler[T]): OnSuccessMagnet { type Out = tupler.Out } =
    new OnSuccessMagnet {
      type Out = tupler.Out
      val directive = Directive[tupler.Out] { inner => ctx =>
        io.flatMap(t => inner(tupler(t))(ctx))
      }(tupler.OutIsTuple)
    }
}

trait CompleteOrRecoverWithMagnet {
  def directive: Directive1[Throwable]
}

object CompleteOrRecoverWithMagnet {
  implicit def apply[T](io: => IO[T])(implicit m: ToResponseMarshaller[T]): CompleteOrRecoverWithMagnet =
    new CompleteOrRecoverWithMagnet {
      val directive = Directive[Tuple1[Throwable]] { inner => ctx =>
        io.attempt.flatMap {
          case Right(res)   => ctx.complete(res)
          case Left(error) => inner(Tuple1(error))(ctx)
        }
      }
    }
}