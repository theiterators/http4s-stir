package pl.iterators.stir.server.directives

import cats.implicits._
import cats.effect.IO
import org.http4s.{ Query, Uri }
import pl.iterators.stir.common._
import pl.iterators.stir.impl.util._
import pl.iterators.stir.server._
import pl.iterators.stir.server.directives.BasicDirectives._
import pl.iterators.stir.server.directives.IODirectives._

import scala.util._

trait ParameterDirectives extends ToNameReceptacleEnhancements with ParameterDirectivesInstances {

  /**
   * Extracts the request's query parameters as a `Map[String, String]`. If a parameter repeats, the last value is
   * kept; a parameter without a value (`?flag`) maps to an empty string, as in Pekko.
   *
   * @group param
   */
  def parameterMap: Directive1[Map[String, String]] = _parameterMap

  /**
   * Extracts the request's query parameters as a `Map[String, Seq[String]]` with the values of each parameter in the
   * order of the query string; a parameter without a value (`?flag`) has one empty value, as in Pekko.
   *
   * @group param
   */
  def parameterMultiMap: Directive1[Map[String, Seq[String]]] = _parameterMultiMap

  /**
   * Extracts the request's query parameters as a `Seq[(String, String)]`.
   *
   * @group param
   * The pairs come in the order of the query string, a parameter without a value (`?flag`) as an empty string, as
   * Pekko's `Uri.Query.toSeq`.
   */
  def parameterSeq: Directive1[Seq[(String, String)]] = _parameterSeq

  // the last value of a repeated parameter wins, as in Pekko (http4s' `Query.params` keeps the first)
  private val _parameterMap: Directive1[Map[String, String]] =
    extract(ctx => queryPairs(ctx.request.uri.query).toMap)

  private val _parameterMultiMap: Directive1[Map[String, Seq[String]]] =
    extract { ctx =>
      queryPairs(ctx.request.uri.query).foldLeft(Map.empty[String, List[String]]) { case (map, (name, value)) =>
        map.updated(name, value :: map.getOrElse(name, Nil))
      }.map { case (name, values) => name -> values.reverse }
    }

  private val _parameterSeq: Directive1[Seq[(String, String)]] =
    extract(ctx => queryPairs(ctx.request.uri.query))

  /**
   * The pairs in the order of the query string, parsed as Pekko does: split on `&` only (http4s also splits on `;`),
   * at the first `=`, with `+` and percent escapes decoded; a parameter without a value (`?flag`) has an empty value,
   * an empty segment is a pair of empty strings, a bare `?` has no pairs.
   */
  private[directives] def queryPairs(query: Query): Vector[(String, String)] = {
    val raw = query.renderString
    if (raw.isEmpty) Vector.empty
    else raw.split("&", -1).iterator.map { segment =>
      segment.indexOf('=') match {
        case -1    => decode(segment) -> ""
        case index => decode(segment.substring(0, index)) -> decode(segment.substring(index + 1))
      }
    }.toVector
  }

  private def decode(encoded: String): String = Uri.decode(encoded, plusIsSpace = true)

  trait ParamSpec {
    type Out

    def get: Directive1[Out]
  }

  object ParamSpec {
    type Aux[T] = ParamSpec { type Out = T }

    def apply[T](directive: Directive1[T]): Aux[T] =
      new ParamSpec {
        type Out = T

        override def get: Directive1[T] = directive
      }

    import Impl._
    import pl.iterators.stir.unmarshalling.{ FromStringUnmarshaller => FSU }

    // regular
    implicit def forString(value: String)(implicit fsu: FSU[String]): ParamSpec.Aux[String] = forName(value, fsu)

    implicit def forSymbol(symbol: Symbol)(implicit fsu: FSU[String]): ParamSpec.Aux[String] = forName(symbol.name, fsu)

    implicit def forNR[T](nr: NameReceptacle[T])(implicit fsu: FSU[T]): ParamSpec.Aux[T] = forName(nr.name, fsu)

    implicit def forNUR[T](nur: NameUnmarshallerReceptacle[T]): ParamSpec.Aux[T] = forName(nur.name, nur.um)

    implicit def forNOR[T](nor: NameOptionReceptacle[T])(implicit fsou: FSOU[T]): ParamSpec.Aux[Option[T]] =
      forName(nor.name, fsou)

    implicit def forNDR[T](ndr: NameDefaultReceptacle[T])(implicit fsou: FSOU[T]): ParamSpec.Aux[T] =
      forName(ndr.name, fsou.withDefaultValue(ndr.default))

    implicit def forNOUR[T](nour: NameOptionUnmarshallerReceptacle[T]): ParamSpec.Aux[Option[T]] =
      forName(nour.name, nour.um: FSOU[T])

    implicit def forNDUR[T](ndur: NameDefaultUnmarshallerReceptacle[T]): ParamSpec.Aux[T] =
      forName(ndur.name, (ndur.um: FSOU[T]).withDefaultValue(ndur.default))

    // repeated
    implicit def forRepVR[T](rvr: RepeatedValueReceptacle[T])(implicit fsu: FSU[T]): ParamSpec.Aux[Iterable[T]] =
      forNameRepeated(rvr.name, fsu)

    implicit def forRepVUR[T](rvur: RepeatedValueUnmarshallerReceptacle[T]): ParamSpec.Aux[Iterable[T]] =
      forNameRepeated(rvur.name, rvur.um)

    // required
    implicit def forRVR[T](rvr: RequiredValueReceptacle[T])(implicit fsu: FSU[T]): ParamSpec.Aux[Unit] =
      forNameRequired(rvr.name, fsu, rvr.requiredValue)

    implicit def forRVUR[T](rvur: RequiredValueUnmarshallerReceptacle[T]): ParamSpec.Aux[Unit] =
      forNameRequired(rvur.name, rvur.um, rvur.requiredValue)

    private def forName[T](name: String, fsu: FSOU[T]): ParamSpec.Aux[T] = ParamSpec(filter(name, fsu))

    private def forNameRepeated[T](name: String, fsu: FSU[T]): ParamSpec.Aux[Iterable[T]] =
      ParamSpec(repeatedFilter(name, fsu))

    private def forNameRequired[T](name: String, fsu: FSU[T], requiredValue: T): ParamSpec.Aux[Unit] =
      ParamSpec(requiredFilter(name, fsu, requiredValue).tmap(_ => Tuple1(())))
  }

  private object Impl {
    import BasicDirectives._
    import RouteDirectives._
    import pl.iterators.stir.unmarshalling.{ FromStringUnmarshaller => FSU, _ }

    type FSOU[T] = Unmarshaller[Option[String], T]

    def filter[T](paramName: String, fsou: FSOU[T]): Directive1[T] =
      extractRequestContext.flatMap { ctx =>
        Try(firstValue(ctx.request.uri.query, paramName)) match {
          case Success(value) => handleParamResult(paramName, fsou(value))
          case Failure(t)     => reject(MalformedRequestContentRejection("The request's query string is invalid.", t))
        }
      }

    def requiredFilter[T](paramName: String, fsou: FSOU[T], requiredValue: Any): Directive0 =
      extractRequestContext.flatMap { ctx =>
        onComplete(fsou(firstValue(ctx.request.uri.query, paramName))).flatMap {
          case Success(value) if value == requiredValue => pass
          case Success(value)                           =>
            reject(InvalidRequiredValueForQueryParamRejection(paramName, requiredValue.toString, value.toString))
          case _ => reject(MissingQueryParamRejection(paramName))
        }
      }

    def repeatedFilter[T](paramName: String, fsu: FSU[T]): Directive1[Iterable[T]] =
      extractRequestContext.flatMap { ctx =>
        handleParamResult(paramName,
          ParameterDirectives.queryPairs(ctx.request.uri.query).iterator.collect { case (`paramName`, value) => value }
            .toList.map(fsu.apply).parSequence)
      }

    /** The first value of the parameter, as Pekko's `Query.get`. */
    def firstValue(query: Query, paramName: String): Option[String] =
      ParameterDirectives.queryPairs(query).collectFirst { case (`paramName`, value) => value }

    def handleParamResult[T](paramName: String, result: IO[T]): Directive1[T] =
      onComplete(result).flatMap {
        case Success(x)                               => provide(x)
        case Failure(Unmarshaller.NoContentException) => reject(MissingQueryParamRejection(paramName))
        case Failure(x)                               => reject(MalformedQueryParamRejection(paramName, x.getMessage.nullAsEmpty, Option(x.getCause)))
      }
  }
}

object ParameterDirectives extends ParameterDirectives
