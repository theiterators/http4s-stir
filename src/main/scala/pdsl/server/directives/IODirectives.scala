package pdsl.server.directives

import cats.effect.IO
import pdsl.server._

import scala.util.Try

object IODirectives {
//  def onComplete[T](io: IO[T]): Directive[Either[Throwable, T]] =
//    Directive { inner => ctx =>
//      ctx.
//      io.attempt.flatMap { result =>
//        inner(Tuple1(result))(ctx)
//      }
//    }
}
