package pl.iterators.stir.server.directives

import cats.implicits._
import cats.effect.IO
import pl.iterators.stir.common._
import pl.iterators.stir.server._
import pl.iterators.stir.server.directives.BasicDirectives._
import pl.iterators.stir.server.directives.IODirectives._

import scala.util._

object ParameterDirectives extends ToNameReceptacleEnhancements with ParameterDirectivesInstances {
  /**
   * Extracts the request's query parameters as a `Map[String, String]`.
   *
   * @group param
   */
  def parameterMap: Directive1[Map[String, String]] = _parameterMap

  /**
   * Extracts the request's query parameters as a `Map[String, List[String]]`.
   *
   * @group param
   */
  def parameterMultiMap: Directive1[Map[String, Seq[String]]] = _parameterMultiMap

  /**
   * Extracts the request's query parameters as a `Seq[(String, String)]`.
   *
   * @group param
   */
  def parameterSeq: Directive1[Seq[(String, String)]] = _parameterSeq

  private val _parameterMap: Directive1[Map[String, String]] =
    extract(_.request.uri.query.params)

  private val _parameterMultiMap: Directive1[Map[String, Seq[String]]] =
    extract(_.request.uri.query.multiParams)

  private val _parameterSeq: Directive1[Seq[(String, String)]] =
    extract(_.request.uri.query.params.toList)

  trait ParamSpec {
    type Out

    def get: Directive1[Out]
  }

  object ParamSpec {
    type Aux[T] = ParamSpec {type Out = T}

    def apply[T](directive: Directive1[T]): Aux[T] =
      new ParamSpec {
        type Out = T

        override def get: Directive1[T] = directive
      }

    import Impl._
    import pl.iterators.stir.unmarshalling.{FromStringUnmarshaller => FSU}

    // regular
    implicit def forString(value: String)(implicit fsu: FSU[String]): ParamSpec.Aux[String] = forName(value, fsu)

    implicit def forSymbol(symbol: Symbol)(implicit fsu: FSU[String]): ParamSpec.Aux[String] = forName(symbol.name, fsu)

    implicit def forNR[T](nr: NameReceptacle[T])(implicit fsu: FSU[T]): ParamSpec.Aux[T] = forName(nr.name, fsu)

    implicit def forNUR[T](nur: NameUnmarshallerReceptacle[T]): ParamSpec.Aux[T] = forName(nur.name, nur.um)

    implicit def forNOR[T](nor: NameOptionReceptacle[T])(implicit fsou: FSOU[T]): ParamSpec.Aux[Option[T]] = forName(nor.name, fsou)

    implicit def forNDR[T](ndr: NameDefaultReceptacle[T])(implicit fsou: FSOU[T]): ParamSpec.Aux[T] = forName(ndr.name, fsou.withDefaultValue(ndr.default))

    implicit def forNOUR[T](nour: NameOptionUnmarshallerReceptacle[T]): ParamSpec.Aux[Option[T]] = forName(nour.name, nour.um: FSOU[T])

    implicit def forNDUR[T](ndur: NameDefaultUnmarshallerReceptacle[T]): ParamSpec.Aux[T] = forName(ndur.name, (ndur.um: FSOU[T]).withDefaultValue(ndur.default))

    // repeated
    implicit def forRepVR[T](rvr: RepeatedValueReceptacle[T])(implicit fsu: FSU[T]): ParamSpec.Aux[Iterable[T]] = forNameRepeated(rvr.name, fsu)

    implicit def forRepVUR[T](rvur: RepeatedValueUnmarshallerReceptacle[T]): ParamSpec.Aux[Iterable[T]] = forNameRepeated(rvur.name, rvur.um)

    // required
    implicit def forRVR[T](rvr: RequiredValueReceptacle[T])(implicit fsu: FSU[T]): ParamSpec.Aux[Unit] = forNameRequired(rvr.name, fsu, rvr.requiredValue)

    implicit def forRVUR[T](rvur: RequiredValueUnmarshallerReceptacle[T]): ParamSpec.Aux[Unit] = forNameRequired(rvur.name, rvur.um, rvur.requiredValue)

    private def forName[T](name: String, fsu: FSOU[T]): ParamSpec.Aux[T] = ParamSpec(filter(name, fsu))

    private def forNameRepeated[T](name: String, fsu: FSU[T]): ParamSpec.Aux[Iterable[T]] = ParamSpec(repeatedFilter(name, fsu))

    private def forNameRequired[T](name: String, fsu: FSU[T], requiredValue: T): ParamSpec.Aux[Unit] = ParamSpec(requiredFilter(name, fsu, requiredValue).tmap(_ => Tuple1(())))
  }

  private object Impl {
    import BasicDirectives._
    import RouteDirectives._
    import pl.iterators.stir.unmarshalling.{FromStringUnmarshaller => FSU, _}

    type FSOU[T] = Unmarshaller[Option[String], T]

    def filter[T](paramName: String, fsou: FSOU[T]): Directive1[T] =
      extractRequestContext.flatMap { ctx =>
        Try(ctx.request.uri.query.params) match {
          case Success(query) => handleParamResult(paramName, fsou(query.get(paramName)))
          case Failure(t) => reject(MalformedRequestContentRejection("The request's query string is invalid.", t))
        }
      }

    def requiredFilter[T](paramName: String, fsou: FSOU[T], requiredValue: Any): Directive0 =
      extractRequestContext.flatMap { ctx =>
        onComplete(fsou(ctx.request.uri.query.params.get(paramName))).flatMap {
          case Success(value) if value == requiredValue => pass
          case Success(value) => reject(InvalidRequiredValueForQueryParamRejection(paramName, requiredValue.toString, value.toString))
          case _ => reject(MissingQueryParamRejection(paramName))
        }
      }

    def repeatedFilter[T](paramName: String, fsu: FSU[T]): Directive1[Iterable[T]] =
      extractRequestContext.flatMap { ctx =>
        handleParamResult(paramName, ctx.request.uri.query.multiParams.getOrElse(paramName, Seq.empty).map(fsu.apply).parSequence)
      }

    def handleParamResult[T](paramName: String, result: IO[T]): Directive1[T] =
      onComplete(result).flatMap {
        case Success(x) => provide(x)
        case Failure(Unmarshaller.NoContentException) => reject(MissingQueryParamRejection(paramName))
        case Failure(x) => reject(MalformedQueryParamRejection(paramName, x.getMessage, Option(x.getCause)))
      }
  }
}
