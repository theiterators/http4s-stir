package pl.iterators.stir.server.directives

import pl.iterators.stir.server.Directive

trait ParameterDirectivesInstances {
  import ParameterDirectives.ParamSpec

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def parameter(pdef1: ParamSpec): Directive[Tuple1[pdef1.Out]] = pdef1.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def parameters(pdef1: ParamSpec): Directive[Tuple1[pdef1.Out]] = pdef1.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def parameter(pdef1: ParamSpec, pdef2: ParamSpec): Directive[Tuple2[pdef1.Out, pdef2.Out]] = pdef1.get & pdef2.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def parameters(pdef1: ParamSpec, pdef2: ParamSpec): Directive[Tuple2[pdef1.Out, pdef2.Out]] = pdef1.get & pdef2.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def parameter(pdef1: ParamSpec, pdef2: ParamSpec, pdef3: ParamSpec): Directive[Tuple3[pdef1.Out, pdef2.Out, pdef3.Out]] = pdef1.get & pdef2.get & pdef3.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def parameters(pdef1: ParamSpec, pdef2: ParamSpec, pdef3: ParamSpec): Directive[Tuple3[pdef1.Out, pdef2.Out, pdef3.Out]] = pdef1.get & pdef2.get & pdef3.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def parameter(pdef1: ParamSpec, pdef2: ParamSpec, pdef3: ParamSpec, pdef4: ParamSpec): Directive[Tuple4[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out]] = pdef1.get & pdef2.get & pdef3.get & pdef4.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def parameters(pdef1: ParamSpec, pdef2: ParamSpec, pdef3: ParamSpec, pdef4: ParamSpec): Directive[Tuple4[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out]] = pdef1.get & pdef2.get & pdef3.get & pdef4.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def parameter(pdef1: ParamSpec, pdef2: ParamSpec, pdef3: ParamSpec, pdef4: ParamSpec, pdef5: ParamSpec): Directive[Tuple5[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out]] = pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def parameters(pdef1: ParamSpec, pdef2: ParamSpec, pdef3: ParamSpec, pdef4: ParamSpec, pdef5: ParamSpec): Directive[Tuple5[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out]] = pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def parameter(pdef1: ParamSpec, pdef2: ParamSpec, pdef3: ParamSpec, pdef4: ParamSpec, pdef5: ParamSpec, pdef6: ParamSpec): Directive[Tuple6[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out]] = pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def parameters(pdef1: ParamSpec, pdef2: ParamSpec, pdef3: ParamSpec, pdef4: ParamSpec, pdef5: ParamSpec, pdef6: ParamSpec): Directive[Tuple6[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out]] = pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def parameter(pdef1: ParamSpec, pdef2: ParamSpec, pdef3: ParamSpec, pdef4: ParamSpec, pdef5: ParamSpec, pdef6: ParamSpec, pdef7: ParamSpec): Directive[Tuple7[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out]] = pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def parameters(pdef1: ParamSpec, pdef2: ParamSpec, pdef3: ParamSpec, pdef4: ParamSpec, pdef5: ParamSpec, pdef6: ParamSpec, pdef7: ParamSpec): Directive[Tuple7[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out]] = pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def parameter(pdef1: ParamSpec, pdef2: ParamSpec, pdef3: ParamSpec, pdef4: ParamSpec, pdef5: ParamSpec, pdef6: ParamSpec, pdef7: ParamSpec, pdef8: ParamSpec): Directive[Tuple8[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out]] = pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def parameters(pdef1: ParamSpec, pdef2: ParamSpec, pdef3: ParamSpec, pdef4: ParamSpec, pdef5: ParamSpec, pdef6: ParamSpec, pdef7: ParamSpec, pdef8: ParamSpec): Directive[Tuple8[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out]] = pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def parameter(pdef1: ParamSpec, pdef2: ParamSpec, pdef3: ParamSpec, pdef4: ParamSpec, pdef5: ParamSpec, pdef6: ParamSpec, pdef7: ParamSpec, pdef8: ParamSpec, pdef9: ParamSpec): Directive[Tuple9[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out, pdef9.Out]] = pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def parameters(pdef1: ParamSpec, pdef2: ParamSpec, pdef3: ParamSpec, pdef4: ParamSpec, pdef5: ParamSpec, pdef6: ParamSpec, pdef7: ParamSpec, pdef8: ParamSpec, pdef9: ParamSpec): Directive[Tuple9[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out, pdef9.Out]] = pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def parameter(pdef1: ParamSpec, pdef2: ParamSpec, pdef3: ParamSpec, pdef4: ParamSpec, pdef5: ParamSpec, pdef6: ParamSpec, pdef7: ParamSpec, pdef8: ParamSpec, pdef9: ParamSpec, pdef10: ParamSpec): Directive[Tuple10[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out, pdef9.Out, pdef10.Out]] = pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def parameters(pdef1: ParamSpec, pdef2: ParamSpec, pdef3: ParamSpec, pdef4: ParamSpec, pdef5: ParamSpec, pdef6: ParamSpec, pdef7: ParamSpec, pdef8: ParamSpec, pdef9: ParamSpec, pdef10: ParamSpec): Directive[Tuple10[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out, pdef9.Out, pdef10.Out]] = pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def parameter(pdef1: ParamSpec, pdef2: ParamSpec, pdef3: ParamSpec, pdef4: ParamSpec, pdef5: ParamSpec, pdef6: ParamSpec, pdef7: ParamSpec, pdef8: ParamSpec, pdef9: ParamSpec, pdef10: ParamSpec, pdef11: ParamSpec): Directive[Tuple11[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out, pdef9.Out, pdef10.Out, pdef11.Out]] = pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def parameters(pdef1: ParamSpec, pdef2: ParamSpec, pdef3: ParamSpec, pdef4: ParamSpec, pdef5: ParamSpec, pdef6: ParamSpec, pdef7: ParamSpec, pdef8: ParamSpec, pdef9: ParamSpec, pdef10: ParamSpec, pdef11: ParamSpec): Directive[Tuple11[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out, pdef9.Out, pdef10.Out, pdef11.Out]] = pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def parameter(pdef1: ParamSpec, pdef2: ParamSpec, pdef3: ParamSpec, pdef4: ParamSpec, pdef5: ParamSpec, pdef6: ParamSpec, pdef7: ParamSpec, pdef8: ParamSpec, pdef9: ParamSpec, pdef10: ParamSpec, pdef11: ParamSpec, pdef12: ParamSpec): Directive[Tuple12[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out, pdef9.Out, pdef10.Out, pdef11.Out, pdef12.Out]] = pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get & pdef12.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def parameters(pdef1: ParamSpec, pdef2: ParamSpec, pdef3: ParamSpec, pdef4: ParamSpec, pdef5: ParamSpec, pdef6: ParamSpec, pdef7: ParamSpec, pdef8: ParamSpec, pdef9: ParamSpec, pdef10: ParamSpec, pdef11: ParamSpec, pdef12: ParamSpec): Directive[Tuple12[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out, pdef9.Out, pdef10.Out, pdef11.Out, pdef12.Out]] = pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get & pdef12.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def parameter(pdef1: ParamSpec, pdef2: ParamSpec, pdef3: ParamSpec, pdef4: ParamSpec, pdef5: ParamSpec, pdef6: ParamSpec, pdef7: ParamSpec, pdef8: ParamSpec, pdef9: ParamSpec, pdef10: ParamSpec, pdef11: ParamSpec, pdef12: ParamSpec, pdef13: ParamSpec): Directive[Tuple13[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out, pdef9.Out, pdef10.Out, pdef11.Out, pdef12.Out, pdef13.Out]] = pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get & pdef12.get & pdef13.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def parameters(pdef1: ParamSpec, pdef2: ParamSpec, pdef3: ParamSpec, pdef4: ParamSpec, pdef5: ParamSpec, pdef6: ParamSpec, pdef7: ParamSpec, pdef8: ParamSpec, pdef9: ParamSpec, pdef10: ParamSpec, pdef11: ParamSpec, pdef12: ParamSpec, pdef13: ParamSpec): Directive[Tuple13[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out, pdef9.Out, pdef10.Out, pdef11.Out, pdef12.Out, pdef13.Out]] = pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get & pdef12.get & pdef13.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def parameter(pdef1: ParamSpec, pdef2: ParamSpec, pdef3: ParamSpec, pdef4: ParamSpec, pdef5: ParamSpec, pdef6: ParamSpec, pdef7: ParamSpec, pdef8: ParamSpec, pdef9: ParamSpec, pdef10: ParamSpec, pdef11: ParamSpec, pdef12: ParamSpec, pdef13: ParamSpec, pdef14: ParamSpec): Directive[Tuple14[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out, pdef9.Out, pdef10.Out, pdef11.Out, pdef12.Out, pdef13.Out, pdef14.Out]] = pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get & pdef12.get & pdef13.get & pdef14.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def parameters(pdef1: ParamSpec, pdef2: ParamSpec, pdef3: ParamSpec, pdef4: ParamSpec, pdef5: ParamSpec, pdef6: ParamSpec, pdef7: ParamSpec, pdef8: ParamSpec, pdef9: ParamSpec, pdef10: ParamSpec, pdef11: ParamSpec, pdef12: ParamSpec, pdef13: ParamSpec, pdef14: ParamSpec): Directive[Tuple14[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out, pdef9.Out, pdef10.Out, pdef11.Out, pdef12.Out, pdef13.Out, pdef14.Out]] = pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get & pdef12.get & pdef13.get & pdef14.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def parameter(pdef1: ParamSpec, pdef2: ParamSpec, pdef3: ParamSpec, pdef4: ParamSpec, pdef5: ParamSpec, pdef6: ParamSpec, pdef7: ParamSpec, pdef8: ParamSpec, pdef9: ParamSpec, pdef10: ParamSpec, pdef11: ParamSpec, pdef12: ParamSpec, pdef13: ParamSpec, pdef14: ParamSpec, pdef15: ParamSpec): Directive[Tuple15[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out, pdef9.Out, pdef10.Out, pdef11.Out, pdef12.Out, pdef13.Out, pdef14.Out, pdef15.Out]] = pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get & pdef12.get & pdef13.get & pdef14.get & pdef15.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def parameters(pdef1: ParamSpec, pdef2: ParamSpec, pdef3: ParamSpec, pdef4: ParamSpec, pdef5: ParamSpec, pdef6: ParamSpec, pdef7: ParamSpec, pdef8: ParamSpec, pdef9: ParamSpec, pdef10: ParamSpec, pdef11: ParamSpec, pdef12: ParamSpec, pdef13: ParamSpec, pdef14: ParamSpec, pdef15: ParamSpec): Directive[Tuple15[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out, pdef9.Out, pdef10.Out, pdef11.Out, pdef12.Out, pdef13.Out, pdef14.Out, pdef15.Out]] = pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get & pdef12.get & pdef13.get & pdef14.get & pdef15.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def parameter(pdef1: ParamSpec, pdef2: ParamSpec, pdef3: ParamSpec, pdef4: ParamSpec, pdef5: ParamSpec, pdef6: ParamSpec, pdef7: ParamSpec, pdef8: ParamSpec, pdef9: ParamSpec, pdef10: ParamSpec, pdef11: ParamSpec, pdef12: ParamSpec, pdef13: ParamSpec, pdef14: ParamSpec, pdef15: ParamSpec, pdef16: ParamSpec): Directive[Tuple16[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out, pdef9.Out, pdef10.Out, pdef11.Out, pdef12.Out, pdef13.Out, pdef14.Out, pdef15.Out, pdef16.Out]] = pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get & pdef12.get & pdef13.get & pdef14.get & pdef15.get & pdef16.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def parameters(pdef1: ParamSpec, pdef2: ParamSpec, pdef3: ParamSpec, pdef4: ParamSpec, pdef5: ParamSpec, pdef6: ParamSpec, pdef7: ParamSpec, pdef8: ParamSpec, pdef9: ParamSpec, pdef10: ParamSpec, pdef11: ParamSpec, pdef12: ParamSpec, pdef13: ParamSpec, pdef14: ParamSpec, pdef15: ParamSpec, pdef16: ParamSpec): Directive[Tuple16[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out, pdef9.Out, pdef10.Out, pdef11.Out, pdef12.Out, pdef13.Out, pdef14.Out, pdef15.Out, pdef16.Out]] = pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get & pdef12.get & pdef13.get & pdef14.get & pdef15.get & pdef16.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def parameter(pdef1: ParamSpec, pdef2: ParamSpec, pdef3: ParamSpec, pdef4: ParamSpec, pdef5: ParamSpec, pdef6: ParamSpec, pdef7: ParamSpec, pdef8: ParamSpec, pdef9: ParamSpec, pdef10: ParamSpec, pdef11: ParamSpec, pdef12: ParamSpec, pdef13: ParamSpec, pdef14: ParamSpec, pdef15: ParamSpec, pdef16: ParamSpec, pdef17: ParamSpec): Directive[Tuple17[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out, pdef9.Out, pdef10.Out, pdef11.Out, pdef12.Out, pdef13.Out, pdef14.Out, pdef15.Out, pdef16.Out, pdef17.Out]] = pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get & pdef12.get & pdef13.get & pdef14.get & pdef15.get & pdef16.get & pdef17.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def parameters(pdef1: ParamSpec, pdef2: ParamSpec, pdef3: ParamSpec, pdef4: ParamSpec, pdef5: ParamSpec, pdef6: ParamSpec, pdef7: ParamSpec, pdef8: ParamSpec, pdef9: ParamSpec, pdef10: ParamSpec, pdef11: ParamSpec, pdef12: ParamSpec, pdef13: ParamSpec, pdef14: ParamSpec, pdef15: ParamSpec, pdef16: ParamSpec, pdef17: ParamSpec): Directive[Tuple17[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out, pdef9.Out, pdef10.Out, pdef11.Out, pdef12.Out, pdef13.Out, pdef14.Out, pdef15.Out, pdef16.Out, pdef17.Out]] = pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get & pdef12.get & pdef13.get & pdef14.get & pdef15.get & pdef16.get & pdef17.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def parameter(pdef1: ParamSpec, pdef2: ParamSpec, pdef3: ParamSpec, pdef4: ParamSpec, pdef5: ParamSpec, pdef6: ParamSpec, pdef7: ParamSpec, pdef8: ParamSpec, pdef9: ParamSpec, pdef10: ParamSpec, pdef11: ParamSpec, pdef12: ParamSpec, pdef13: ParamSpec, pdef14: ParamSpec, pdef15: ParamSpec, pdef16: ParamSpec, pdef17: ParamSpec, pdef18: ParamSpec): Directive[Tuple18[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out, pdef9.Out, pdef10.Out, pdef11.Out, pdef12.Out, pdef13.Out, pdef14.Out, pdef15.Out, pdef16.Out, pdef17.Out, pdef18.Out]] = pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get & pdef12.get & pdef13.get & pdef14.get & pdef15.get & pdef16.get & pdef17.get & pdef18.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def parameters(pdef1: ParamSpec, pdef2: ParamSpec, pdef3: ParamSpec, pdef4: ParamSpec, pdef5: ParamSpec, pdef6: ParamSpec, pdef7: ParamSpec, pdef8: ParamSpec, pdef9: ParamSpec, pdef10: ParamSpec, pdef11: ParamSpec, pdef12: ParamSpec, pdef13: ParamSpec, pdef14: ParamSpec, pdef15: ParamSpec, pdef16: ParamSpec, pdef17: ParamSpec, pdef18: ParamSpec): Directive[Tuple18[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out, pdef9.Out, pdef10.Out, pdef11.Out, pdef12.Out, pdef13.Out, pdef14.Out, pdef15.Out, pdef16.Out, pdef17.Out, pdef18.Out]] = pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get & pdef12.get & pdef13.get & pdef14.get & pdef15.get & pdef16.get & pdef17.get & pdef18.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def parameter(pdef1: ParamSpec, pdef2: ParamSpec, pdef3: ParamSpec, pdef4: ParamSpec, pdef5: ParamSpec, pdef6: ParamSpec, pdef7: ParamSpec, pdef8: ParamSpec, pdef9: ParamSpec, pdef10: ParamSpec, pdef11: ParamSpec, pdef12: ParamSpec, pdef13: ParamSpec, pdef14: ParamSpec, pdef15: ParamSpec, pdef16: ParamSpec, pdef17: ParamSpec, pdef18: ParamSpec, pdef19: ParamSpec): Directive[Tuple19[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out, pdef9.Out, pdef10.Out, pdef11.Out, pdef12.Out, pdef13.Out, pdef14.Out, pdef15.Out, pdef16.Out, pdef17.Out, pdef18.Out, pdef19.Out]] = pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get & pdef12.get & pdef13.get & pdef14.get & pdef15.get & pdef16.get & pdef17.get & pdef18.get & pdef19.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def parameters(pdef1: ParamSpec, pdef2: ParamSpec, pdef3: ParamSpec, pdef4: ParamSpec, pdef5: ParamSpec, pdef6: ParamSpec, pdef7: ParamSpec, pdef8: ParamSpec, pdef9: ParamSpec, pdef10: ParamSpec, pdef11: ParamSpec, pdef12: ParamSpec, pdef13: ParamSpec, pdef14: ParamSpec, pdef15: ParamSpec, pdef16: ParamSpec, pdef17: ParamSpec, pdef18: ParamSpec, pdef19: ParamSpec): Directive[Tuple19[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out, pdef9.Out, pdef10.Out, pdef11.Out, pdef12.Out, pdef13.Out, pdef14.Out, pdef15.Out, pdef16.Out, pdef17.Out, pdef18.Out, pdef19.Out]] = pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get & pdef12.get & pdef13.get & pdef14.get & pdef15.get & pdef16.get & pdef17.get & pdef18.get & pdef19.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def parameter(pdef1: ParamSpec, pdef2: ParamSpec, pdef3: ParamSpec, pdef4: ParamSpec, pdef5: ParamSpec, pdef6: ParamSpec, pdef7: ParamSpec, pdef8: ParamSpec, pdef9: ParamSpec, pdef10: ParamSpec, pdef11: ParamSpec, pdef12: ParamSpec, pdef13: ParamSpec, pdef14: ParamSpec, pdef15: ParamSpec, pdef16: ParamSpec, pdef17: ParamSpec, pdef18: ParamSpec, pdef19: ParamSpec, pdef20: ParamSpec): Directive[Tuple20[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out, pdef9.Out, pdef10.Out, pdef11.Out, pdef12.Out, pdef13.Out, pdef14.Out, pdef15.Out, pdef16.Out, pdef17.Out, pdef18.Out, pdef19.Out, pdef20.Out]] = pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get & pdef12.get & pdef13.get & pdef14.get & pdef15.get & pdef16.get & pdef17.get & pdef18.get & pdef19.get & pdef20.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def parameters(pdef1: ParamSpec, pdef2: ParamSpec, pdef3: ParamSpec, pdef4: ParamSpec, pdef5: ParamSpec, pdef6: ParamSpec, pdef7: ParamSpec, pdef8: ParamSpec, pdef9: ParamSpec, pdef10: ParamSpec, pdef11: ParamSpec, pdef12: ParamSpec, pdef13: ParamSpec, pdef14: ParamSpec, pdef15: ParamSpec, pdef16: ParamSpec, pdef17: ParamSpec, pdef18: ParamSpec, pdef19: ParamSpec, pdef20: ParamSpec): Directive[Tuple20[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out, pdef9.Out, pdef10.Out, pdef11.Out, pdef12.Out, pdef13.Out, pdef14.Out, pdef15.Out, pdef16.Out, pdef17.Out, pdef18.Out, pdef19.Out, pdef20.Out]] = pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get & pdef12.get & pdef13.get & pdef14.get & pdef15.get & pdef16.get & pdef17.get & pdef18.get & pdef19.get & pdef20.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def parameter(pdef1: ParamSpec, pdef2: ParamSpec, pdef3: ParamSpec, pdef4: ParamSpec, pdef5: ParamSpec, pdef6: ParamSpec, pdef7: ParamSpec, pdef8: ParamSpec, pdef9: ParamSpec, pdef10: ParamSpec, pdef11: ParamSpec, pdef12: ParamSpec, pdef13: ParamSpec, pdef14: ParamSpec, pdef15: ParamSpec, pdef16: ParamSpec, pdef17: ParamSpec, pdef18: ParamSpec, pdef19: ParamSpec, pdef20: ParamSpec, pdef21: ParamSpec): Directive[Tuple21[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out, pdef9.Out, pdef10.Out, pdef11.Out, pdef12.Out, pdef13.Out, pdef14.Out, pdef15.Out, pdef16.Out, pdef17.Out, pdef18.Out, pdef19.Out, pdef20.Out, pdef21.Out]] = pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get & pdef12.get & pdef13.get & pdef14.get & pdef15.get & pdef16.get & pdef17.get & pdef18.get & pdef19.get & pdef20.get & pdef21.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def parameters(pdef1: ParamSpec, pdef2: ParamSpec, pdef3: ParamSpec, pdef4: ParamSpec, pdef5: ParamSpec, pdef6: ParamSpec, pdef7: ParamSpec, pdef8: ParamSpec, pdef9: ParamSpec, pdef10: ParamSpec, pdef11: ParamSpec, pdef12: ParamSpec, pdef13: ParamSpec, pdef14: ParamSpec, pdef15: ParamSpec, pdef16: ParamSpec, pdef17: ParamSpec, pdef18: ParamSpec, pdef19: ParamSpec, pdef20: ParamSpec, pdef21: ParamSpec): Directive[Tuple21[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out, pdef9.Out, pdef10.Out, pdef11.Out, pdef12.Out, pdef13.Out, pdef14.Out, pdef15.Out, pdef16.Out, pdef17.Out, pdef18.Out, pdef19.Out, pdef20.Out, pdef21.Out]] = pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get & pdef12.get & pdef13.get & pdef14.get & pdef15.get & pdef16.get & pdef17.get & pdef18.get & pdef19.get & pdef20.get & pdef21.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def parameter(pdef1: ParamSpec, pdef2: ParamSpec, pdef3: ParamSpec, pdef4: ParamSpec, pdef5: ParamSpec, pdef6: ParamSpec, pdef7: ParamSpec, pdef8: ParamSpec, pdef9: ParamSpec, pdef10: ParamSpec, pdef11: ParamSpec, pdef12: ParamSpec, pdef13: ParamSpec, pdef14: ParamSpec, pdef15: ParamSpec, pdef16: ParamSpec, pdef17: ParamSpec, pdef18: ParamSpec, pdef19: ParamSpec, pdef20: ParamSpec, pdef21: ParamSpec, pdef22: ParamSpec): Directive[Tuple22[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out, pdef9.Out, pdef10.Out, pdef11.Out, pdef12.Out, pdef13.Out, pdef14.Out, pdef15.Out, pdef16.Out, pdef17.Out, pdef18.Out, pdef19.Out, pdef20.Out, pdef21.Out, pdef22.Out]] = pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get & pdef12.get & pdef13.get & pdef14.get & pdef15.get & pdef16.get & pdef17.get & pdef18.get & pdef19.get & pdef20.get & pdef21.get & pdef22.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def parameters(pdef1: ParamSpec, pdef2: ParamSpec, pdef3: ParamSpec, pdef4: ParamSpec, pdef5: ParamSpec, pdef6: ParamSpec, pdef7: ParamSpec, pdef8: ParamSpec, pdef9: ParamSpec, pdef10: ParamSpec, pdef11: ParamSpec, pdef12: ParamSpec, pdef13: ParamSpec, pdef14: ParamSpec, pdef15: ParamSpec, pdef16: ParamSpec, pdef17: ParamSpec, pdef18: ParamSpec, pdef19: ParamSpec, pdef20: ParamSpec, pdef21: ParamSpec, pdef22: ParamSpec): Directive[Tuple22[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out, pdef9.Out, pdef10.Out, pdef11.Out, pdef12.Out, pdef13.Out, pdef14.Out, pdef15.Out, pdef16.Out, pdef17.Out, pdef18.Out, pdef19.Out, pdef20.Out, pdef21.Out, pdef22.Out]] = pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get & pdef12.get & pdef13.get & pdef14.get & pdef15.get & pdef16.get & pdef17.get & pdef18.get & pdef19.get & pdef20.get & pdef21.get & pdef22.get

}
