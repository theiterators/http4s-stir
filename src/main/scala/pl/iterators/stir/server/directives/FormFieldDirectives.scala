package pl.iterators.stir.server.directives

import cats.effect.IO
import cats.implicits._
import org.http4s.{EntityDecoder, UrlForm}
import pl.iterators.stir.common._
import pl.iterators.stir.impl.util._
import pl.iterators.stir.server._
import pl.iterators.stir.server.directives.MarshallingDirectives._
import pl.iterators.stir.unmarshalling.Unmarshaller

import scala.annotation.tailrec
import scala.collection.immutable
import scala.util.{Failure, Success}

trait FormFieldDirectives extends FormFieldDirectivesInstances with ToNameReceptacleEnhancements {
  import FormFieldDirectives._

  /**
   * Extracts HTTP form fields from the request as a ``Map[String, String]``.
   *
   * @group form
   */
  def formFieldMap: Directive1[Map[String, String]] = _formFieldMap

  /**
   * Extracts HTTP form fields from the request as a ``Map[String, List[String]]``.
   *
   * @group form
   */
  def formFieldMultiMap: Directive1[Map[String, List[String]]] = _formFieldMultiMap

  /**
   * Extracts HTTP form fields from the request as a ``Seq[(String, String)]``.
   *
   * @group form
   */
  def formFieldSeq: Directive1[immutable.Seq[(String, String)]] = _formFieldSeq
}

object FormFieldDirectives {
  private val _formFieldSeq: Directive1[Seq[(String, String)]] = {
    entity(as[UrlForm]).map { form =>
      form.values.toSeq.flatMap { case (k, vs) => vs.map(v => k -> v).toList }
    }
  }
  private val _formFieldMap: Directive1[Map[String, String]] = _formFieldSeq.map(toMap)

  private val _formFieldMultiMap: Directive1[Map[String, List[String]]] = {
    @tailrec def append(
                         map: Map[String, List[String]],
                         fields: immutable.Seq[(String, String)]): Map[String, List[String]] = {
      if (fields.isEmpty) {
        map
      } else {
        val (key, value) = fields.head
        append(map.updated(key, value :: map.getOrElse(key, Nil)), fields.tail)
      }
    }

    _formFieldSeq.map { seq =>
        append(immutable.TreeMap.empty, seq)
    }
  }

  private def toMap(seq: Seq[(String, String)]): Map[String, String] = immutable.TreeMap(seq: _*)

  trait FieldSpec {
    type Out

    def get: Directive1[Out]
  }

  object FieldSpec {
    type Aux[T] = FieldSpec {type Out = T}

    def apply[T](directive: Directive1[T]): FieldSpec.Aux[T] = new FieldSpec {
      type Out = T

      def get: Directive1[T] = directive
    }

    import Impl._

    type FSFFU[T] = Unmarshaller[String, T]
    type FSFFOU[T] = Unmarshaller[Option[String], T]

    implicit def forString(fieldName: String): FieldSpec.Aux[String] = forName(fieldName, stringFromStrictForm)

    implicit def forSymbol(symbol: Symbol): FieldSpec.Aux[String] = forName(symbol.name, stringFromStrictForm)

    implicit def forNR[T](r: NameReceptacle[T])(implicit fu: FSFFU[T]): FieldSpec.Aux[T] = forName(r.name, fu)

    implicit def forNUR[T](r: NameUnmarshallerReceptacle[T]): FieldSpec.Aux[T] = forName(r.name, r.um)

    implicit def forNOR[T](r: NameOptionReceptacle[T])(implicit fu: FSFFOU[T]): FieldSpec.Aux[Option[T]] = forName(r.name, fu)

    implicit def forNDR[T](r: NameDefaultReceptacle[T])(implicit fu: FSFFOU[T]): FieldSpec.Aux[T] = forName(r.name, fu withDefaultValue r.default)

    implicit def forNOUR[T](r: NameOptionUnmarshallerReceptacle[T]): FieldSpec.Aux[Option[T]] = forName(r.name, r.um: FSFFOU[T])

    implicit def forNDUR[T](r: NameDefaultUnmarshallerReceptacle[T]): FieldSpec.Aux[T] = forName(r.name, (r.um) withDefaultValue r.default)

    //////////////////// required formField support ////////////////////

    implicit def forRVR[T](r: RequiredValueReceptacle[T])(implicit fu: FSFFU[T]): FieldSpec.Aux[Unit] =
      forNameRequired(r.name, fu, r.requiredValue)

    implicit def forRVDR[T](r: RequiredValueUnmarshallerReceptacle[T]): FieldSpec.Aux[Unit] =
      forNameRequired(r.name, (r.um), r.requiredValue)

    //////////////////// repeated formField support ////////////////////

    implicit def forRepVR[T](r: RepeatedValueReceptacle[T])(implicit fu: FSFFU[T]): FieldSpec.Aux[Iterable[T]] =
      forNameRepeated(r.name, fu)

    implicit def forRepVDR[T](r: RepeatedValueUnmarshallerReceptacle[T]): FieldSpec.Aux[Iterable[T]] =
      forNameRepeated(r.name, (r.um))

    private def forName[T](name: String, fu: FSFFOU[T]): FieldSpec.Aux[T] = FieldSpec(filter(name, fu))

    private def forNameRequired[T](name: String, fsu: FSFFOU[T], requiredValue: T): FieldSpec.Aux[Unit] = FieldSpec(requiredFilter(name, fsu, requiredValue).tmap(_ => Tuple1(())))

    private def forNameRepeated[T](name: String, fsu: FSFFU[T]): FieldSpec.Aux[Iterable[T]] = FieldSpec(repeatedFilter(name, fsu))
  }

  private[stir] object Impl {

    import BasicDirectives._
    import IODirectives._
    import RouteDirectives._

    type FSFFU[T] = Unmarshaller[String, T]
    type SFU = EntityDecoder[IO, UrlForm]
    type FSFFOU[T] = Unmarshaller[Option[String], T]

    protected def handleFieldResult[T](fieldName: String, result: IO[T]): Directive1[T] = onComplete(result).flatMap {
      case Success(x) => provide(x)
      case Failure(Unmarshaller.NoContentException) => reject(MissingFormFieldRejection(fieldName))
      case Failure(x) => reject(MalformedFormFieldRejection(fieldName, x.getMessage.nullAsEmpty, Option(x.getCause)))
    }

    private def strictFormUnmarshaller(ctx: RequestContext): SFU =
      UrlForm.entityDecoder

    val stringFromStrictForm: FSFFU[String] = Unmarshaller.identityUnmarshaller

    def fieldOfForm[T](fieldName: String, fu: Unmarshaller[Option[String], T]): RequestContext => IO[T] = { ctx =>
      strictFormUnmarshaller(ctx).decode(ctx.request, strict = false).foldF(x => IO.raiseError(x.getCause()), form => fu(form.getFirst(fieldName)))
    }

    def filter[T](fieldName: String, fu: FSFFOU[T]): Directive1[T] =
      extract(fieldOfForm(fieldName, fu)).flatMap(r => handleFieldResult(fieldName, r))

    def repeatedFilter[T](fieldName: String, fu: FSFFU[T]): Directive1[Iterable[T]] =
      extract { ctx =>
        strictFormUnmarshaller(ctx).decode(ctx.request, strict = false).foldF(x => IO.raiseError(x.getCause()), form => form.get(fieldName).toList.map(fu.apply).parSequence)
      }.flatMap { result =>
        handleFieldResult(fieldName, result)
      }

    def requiredFilter[T](fieldName: String, fu: Unmarshaller[Option[String], T], requiredValue: Any): Directive0 =
      extract(fieldOfForm(fieldName, fu)).flatMap {
        onComplete(_).flatMap {
          case Success(value) if value == requiredValue => pass
          case _ => reject
        }
      }
  }
}
