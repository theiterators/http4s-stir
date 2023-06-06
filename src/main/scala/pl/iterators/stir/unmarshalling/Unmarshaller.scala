package pl.iterators.stir.unmarshalling

import cats.effect.IO

import scala.util.control.NoStackTrace

trait Unmarshaller[-A, B] {
  def apply(value: A): IO[B]

  def transform[C](f: IO[B] => IO[C]): Unmarshaller[A, C] = {
    a => f(this (a))
  }

  def map[C](f: B => C): Unmarshaller[A, C] =
    transform(_.map(f))

  def flatMap[C](f: B => IO[C]): Unmarshaller[A, C] =
    transform(_.flatMap(f))

  def andThen[C](other: Unmarshaller[B, C]): Unmarshaller[A, C] =
    flatMap(data => other(data))

  def recover[C >: B](pf: PartialFunction[Throwable, C]): Unmarshaller[A, C] =
    transform(_.recover(pf))

  def withDefaultValue[BB >: B](defaultValue: BB): Unmarshaller[A, BB] =
    recover {
      case Unmarshaller.NoContentException => defaultValue
    }
}

object Unmarshaller extends LowerPriorityGenericUnmarshallers with PredefinedFromStringUnmarshallers {
  def apply[A, B](f: A => IO[B]): Unmarshaller[A, B] = f(_)

  def strict[A, B](f: A => B): Unmarshaller[A, B] =
    apply(a => IO.pure(f(a)))

  implicit def identityUnmarshaller[T]: Unmarshaller[T, T] = Unmarshaller(IO.pure)

  implicit def liftToTargetOptionUnmarshaller[A, B](um: Unmarshaller[A, B]): Unmarshaller[A, Option[B]] =
    targetOptionUnmarshaller(um)

  implicit def targetOptionUnmarshaller[A, B](implicit um: Unmarshaller[A, B]): Unmarshaller[A, Option[B]] =
    um map (Some(_)) withDefaultValue None

  case object NoContentException extends RuntimeException("Message entity must not be empty") with NoStackTrace
}

sealed trait LowerPriorityGenericUnmarshallers {
  implicit def liftToSourceOptionUnmarshaller[A, B](um: Unmarshaller[A, B]): Unmarshaller[Option[A], B] =
    sourceOptionUnmarshaller(um)

  implicit def sourceOptionUnmarshaller[A, B](implicit um: Unmarshaller[A, B]): Unmarshaller[Option[A], B] =
    Unmarshaller {
      case Some(a) => um(a)
      case None => IO.raiseError(Unmarshaller.NoContentException)
    }
}