package pl.iterators.stir.server.directives

import pl.iterators.stir.server.Directive1
import pl.iterators.stir.server.directives.BasicDirectives._

object ParameterDirectives {
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
}
