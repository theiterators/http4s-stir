package pl.iterators.stir.server.directives

import pl.iterators.stir.server.Directive

trait FormFieldDirectivesInstances {
  import FormFieldDirectives.FieldSpec

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def formField(pdef1: FieldSpec): Directive[Tuple1[pdef1.Out]] = pdef1.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def formFields(pdef1: FieldSpec): Directive[Tuple1[pdef1.Out]] = pdef1.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def formField(pdef1: FieldSpec, pdef2: FieldSpec): Directive[Tuple2[pdef1.Out, pdef2.Out]] = pdef1.get & pdef2.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def formFields(pdef1: FieldSpec, pdef2: FieldSpec): Directive[Tuple2[pdef1.Out, pdef2.Out]] = pdef1.get & pdef2.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def formField(
      pdef1: FieldSpec, pdef2: FieldSpec, pdef3: FieldSpec): Directive[Tuple3[pdef1.Out, pdef2.Out, pdef3.Out]] =
    pdef1.get & pdef2.get & pdef3.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def formFields(
      pdef1: FieldSpec, pdef2: FieldSpec, pdef3: FieldSpec): Directive[Tuple3[pdef1.Out, pdef2.Out, pdef3.Out]] =
    pdef1.get & pdef2.get & pdef3.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def formField(pdef1: FieldSpec, pdef2: FieldSpec, pdef3: FieldSpec, pdef4: FieldSpec)
      : Directive[Tuple4[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out]] = pdef1.get & pdef2.get & pdef3.get & pdef4.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def formFields(pdef1: FieldSpec, pdef2: FieldSpec, pdef3: FieldSpec, pdef4: FieldSpec)
      : Directive[Tuple4[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out]] = pdef1.get & pdef2.get & pdef3.get & pdef4.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def formField(pdef1: FieldSpec, pdef2: FieldSpec, pdef3: FieldSpec, pdef4: FieldSpec, pdef5: FieldSpec)
      : Directive[Tuple5[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out]] =
    pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def formFields(pdef1: FieldSpec, pdef2: FieldSpec, pdef3: FieldSpec, pdef4: FieldSpec, pdef5: FieldSpec)
      : Directive[Tuple5[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out]] =
    pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def formField(pdef1: FieldSpec, pdef2: FieldSpec, pdef3: FieldSpec, pdef4: FieldSpec, pdef5: FieldSpec,
      pdef6: FieldSpec): Directive[Tuple6[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out]] =
    pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def formFields(pdef1: FieldSpec, pdef2: FieldSpec, pdef3: FieldSpec, pdef4: FieldSpec, pdef5: FieldSpec,
      pdef6: FieldSpec): Directive[Tuple6[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out]] =
    pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def formField(pdef1: FieldSpec, pdef2: FieldSpec, pdef3: FieldSpec, pdef4: FieldSpec, pdef5: FieldSpec,
      pdef6: FieldSpec, pdef7: FieldSpec)
      : Directive[Tuple7[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out]] =
    pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def formFields(pdef1: FieldSpec, pdef2: FieldSpec, pdef3: FieldSpec, pdef4: FieldSpec, pdef5: FieldSpec,
      pdef6: FieldSpec, pdef7: FieldSpec)
      : Directive[Tuple7[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out]] =
    pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def formField(pdef1: FieldSpec, pdef2: FieldSpec, pdef3: FieldSpec, pdef4: FieldSpec, pdef5: FieldSpec,
      pdef6: FieldSpec, pdef7: FieldSpec, pdef8: FieldSpec)
      : Directive[Tuple8[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out]] =
    pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def formFields(pdef1: FieldSpec, pdef2: FieldSpec, pdef3: FieldSpec, pdef4: FieldSpec, pdef5: FieldSpec,
      pdef6: FieldSpec, pdef7: FieldSpec, pdef8: FieldSpec)
      : Directive[Tuple8[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out]] =
    pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def formField(pdef1: FieldSpec, pdef2: FieldSpec, pdef3: FieldSpec, pdef4: FieldSpec, pdef5: FieldSpec,
      pdef6: FieldSpec, pdef7: FieldSpec, pdef8: FieldSpec, pdef9: FieldSpec): Directive[Tuple9[pdef1.Out, pdef2.Out,
    pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out, pdef9.Out]] =
    pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def formFields(pdef1: FieldSpec, pdef2: FieldSpec, pdef3: FieldSpec, pdef4: FieldSpec, pdef5: FieldSpec,
      pdef6: FieldSpec, pdef7: FieldSpec, pdef8: FieldSpec, pdef9: FieldSpec): Directive[Tuple9[pdef1.Out, pdef2.Out,
    pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out, pdef9.Out]] =
    pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def formField(pdef1: FieldSpec, pdef2: FieldSpec, pdef3: FieldSpec, pdef4: FieldSpec, pdef5: FieldSpec,
      pdef6: FieldSpec, pdef7: FieldSpec, pdef8: FieldSpec, pdef9: FieldSpec, pdef10: FieldSpec): Directive[Tuple10[
    pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out, pdef9.Out, pdef10.Out]] =
    pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def formFields(pdef1: FieldSpec, pdef2: FieldSpec, pdef3: FieldSpec, pdef4: FieldSpec, pdef5: FieldSpec,
      pdef6: FieldSpec, pdef7: FieldSpec, pdef8: FieldSpec, pdef9: FieldSpec, pdef10: FieldSpec): Directive[Tuple10[
    pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out, pdef9.Out, pdef10.Out]] =
    pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def formField(pdef1: FieldSpec, pdef2: FieldSpec, pdef3: FieldSpec, pdef4: FieldSpec, pdef5: FieldSpec,
      pdef6: FieldSpec, pdef7: FieldSpec, pdef8: FieldSpec, pdef9: FieldSpec, pdef10: FieldSpec, pdef11: FieldSpec)
      : Directive[Tuple11[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out,
        pdef9.Out, pdef10.Out, pdef11.Out]] =
    pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def formFields(pdef1: FieldSpec, pdef2: FieldSpec, pdef3: FieldSpec, pdef4: FieldSpec, pdef5: FieldSpec,
      pdef6: FieldSpec, pdef7: FieldSpec, pdef8: FieldSpec, pdef9: FieldSpec, pdef10: FieldSpec, pdef11: FieldSpec)
      : Directive[Tuple11[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out,
        pdef9.Out, pdef10.Out, pdef11.Out]] =
    pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def formField(pdef1: FieldSpec, pdef2: FieldSpec, pdef3: FieldSpec, pdef4: FieldSpec, pdef5: FieldSpec,
      pdef6: FieldSpec, pdef7: FieldSpec, pdef8: FieldSpec, pdef9: FieldSpec, pdef10: FieldSpec, pdef11: FieldSpec,
      pdef12: FieldSpec): Directive[Tuple12[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out,
    pdef8.Out, pdef9.Out, pdef10.Out, pdef11.Out, pdef12.Out]] =
    pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get & pdef12.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def formFields(pdef1: FieldSpec, pdef2: FieldSpec, pdef3: FieldSpec, pdef4: FieldSpec, pdef5: FieldSpec,
      pdef6: FieldSpec, pdef7: FieldSpec, pdef8: FieldSpec, pdef9: FieldSpec, pdef10: FieldSpec, pdef11: FieldSpec,
      pdef12: FieldSpec): Directive[Tuple12[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out,
    pdef8.Out, pdef9.Out, pdef10.Out, pdef11.Out, pdef12.Out]] =
    pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get & pdef12.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def formField(pdef1: FieldSpec, pdef2: FieldSpec, pdef3: FieldSpec, pdef4: FieldSpec, pdef5: FieldSpec,
      pdef6: FieldSpec, pdef7: FieldSpec, pdef8: FieldSpec, pdef9: FieldSpec, pdef10: FieldSpec, pdef11: FieldSpec,
      pdef12: FieldSpec, pdef13: FieldSpec): Directive[Tuple13[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out,
    pdef6.Out, pdef7.Out, pdef8.Out, pdef9.Out, pdef10.Out, pdef11.Out, pdef12.Out, pdef13.Out]] =
    pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get & pdef12.get & pdef13.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def formFields(pdef1: FieldSpec, pdef2: FieldSpec, pdef3: FieldSpec, pdef4: FieldSpec, pdef5: FieldSpec,
      pdef6: FieldSpec, pdef7: FieldSpec, pdef8: FieldSpec, pdef9: FieldSpec, pdef10: FieldSpec, pdef11: FieldSpec,
      pdef12: FieldSpec, pdef13: FieldSpec): Directive[Tuple13[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out,
    pdef6.Out, pdef7.Out, pdef8.Out, pdef9.Out, pdef10.Out, pdef11.Out, pdef12.Out, pdef13.Out]] =
    pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get & pdef12.get & pdef13.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def formField(pdef1: FieldSpec, pdef2: FieldSpec, pdef3: FieldSpec, pdef4: FieldSpec, pdef5: FieldSpec,
      pdef6: FieldSpec, pdef7: FieldSpec, pdef8: FieldSpec, pdef9: FieldSpec, pdef10: FieldSpec, pdef11: FieldSpec,
      pdef12: FieldSpec, pdef13: FieldSpec, pdef14: FieldSpec)
      : Directive[Tuple14[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out,
        pdef9.Out, pdef10.Out, pdef11.Out, pdef12.Out, pdef13.Out, pdef14.Out]] =
    pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get & pdef12.get & pdef13.get & pdef14.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def formFields(pdef1: FieldSpec, pdef2: FieldSpec, pdef3: FieldSpec, pdef4: FieldSpec, pdef5: FieldSpec,
      pdef6: FieldSpec, pdef7: FieldSpec, pdef8: FieldSpec, pdef9: FieldSpec, pdef10: FieldSpec, pdef11: FieldSpec,
      pdef12: FieldSpec, pdef13: FieldSpec, pdef14: FieldSpec)
      : Directive[Tuple14[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out,
        pdef9.Out, pdef10.Out, pdef11.Out, pdef12.Out, pdef13.Out, pdef14.Out]] =
    pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get & pdef12.get & pdef13.get & pdef14.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def formField(pdef1: FieldSpec, pdef2: FieldSpec, pdef3: FieldSpec, pdef4: FieldSpec, pdef5: FieldSpec,
      pdef6: FieldSpec, pdef7: FieldSpec, pdef8: FieldSpec, pdef9: FieldSpec, pdef10: FieldSpec, pdef11: FieldSpec,
      pdef12: FieldSpec, pdef13: FieldSpec, pdef14: FieldSpec, pdef15: FieldSpec)
      : Directive[Tuple15[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out,
        pdef9.Out, pdef10.Out, pdef11.Out, pdef12.Out, pdef13.Out, pdef14.Out, pdef15.Out]] =
    pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get & pdef12.get & pdef13.get & pdef14.get & pdef15.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def formFields(pdef1: FieldSpec, pdef2: FieldSpec, pdef3: FieldSpec, pdef4: FieldSpec, pdef5: FieldSpec,
      pdef6: FieldSpec, pdef7: FieldSpec, pdef8: FieldSpec, pdef9: FieldSpec, pdef10: FieldSpec, pdef11: FieldSpec,
      pdef12: FieldSpec, pdef13: FieldSpec, pdef14: FieldSpec, pdef15: FieldSpec)
      : Directive[Tuple15[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out,
        pdef9.Out, pdef10.Out, pdef11.Out, pdef12.Out, pdef13.Out, pdef14.Out, pdef15.Out]] =
    pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get & pdef12.get & pdef13.get & pdef14.get & pdef15.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def formField(pdef1: FieldSpec, pdef2: FieldSpec, pdef3: FieldSpec, pdef4: FieldSpec, pdef5: FieldSpec,
      pdef6: FieldSpec, pdef7: FieldSpec, pdef8: FieldSpec, pdef9: FieldSpec, pdef10: FieldSpec, pdef11: FieldSpec,
      pdef12: FieldSpec, pdef13: FieldSpec, pdef14: FieldSpec, pdef15: FieldSpec, pdef16: FieldSpec)
      : Directive[Tuple16[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out,
        pdef9.Out, pdef10.Out, pdef11.Out, pdef12.Out, pdef13.Out, pdef14.Out, pdef15.Out, pdef16.Out]] =
    pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get & pdef12.get & pdef13.get & pdef14.get & pdef15.get & pdef16.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def formFields(pdef1: FieldSpec, pdef2: FieldSpec, pdef3: FieldSpec, pdef4: FieldSpec, pdef5: FieldSpec,
      pdef6: FieldSpec, pdef7: FieldSpec, pdef8: FieldSpec, pdef9: FieldSpec, pdef10: FieldSpec, pdef11: FieldSpec,
      pdef12: FieldSpec, pdef13: FieldSpec, pdef14: FieldSpec, pdef15: FieldSpec, pdef16: FieldSpec)
      : Directive[Tuple16[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out,
        pdef9.Out, pdef10.Out, pdef11.Out, pdef12.Out, pdef13.Out, pdef14.Out, pdef15.Out, pdef16.Out]] =
    pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get & pdef12.get & pdef13.get & pdef14.get & pdef15.get & pdef16.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def formField(pdef1: FieldSpec, pdef2: FieldSpec, pdef3: FieldSpec, pdef4: FieldSpec, pdef5: FieldSpec,
      pdef6: FieldSpec, pdef7: FieldSpec, pdef8: FieldSpec, pdef9: FieldSpec, pdef10: FieldSpec, pdef11: FieldSpec,
      pdef12: FieldSpec, pdef13: FieldSpec, pdef14: FieldSpec, pdef15: FieldSpec, pdef16: FieldSpec, pdef17: FieldSpec)
      : Directive[Tuple17[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out,
        pdef9.Out, pdef10.Out, pdef11.Out, pdef12.Out, pdef13.Out, pdef14.Out, pdef15.Out, pdef16.Out, pdef17.Out]] =
    pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get & pdef12.get & pdef13.get & pdef14.get & pdef15.get & pdef16.get & pdef17.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def formFields(pdef1: FieldSpec, pdef2: FieldSpec, pdef3: FieldSpec, pdef4: FieldSpec, pdef5: FieldSpec,
      pdef6: FieldSpec, pdef7: FieldSpec, pdef8: FieldSpec, pdef9: FieldSpec, pdef10: FieldSpec, pdef11: FieldSpec,
      pdef12: FieldSpec, pdef13: FieldSpec, pdef14: FieldSpec, pdef15: FieldSpec, pdef16: FieldSpec, pdef17: FieldSpec)
      : Directive[Tuple17[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out,
        pdef9.Out, pdef10.Out, pdef11.Out, pdef12.Out, pdef13.Out, pdef14.Out, pdef15.Out, pdef16.Out, pdef17.Out]] =
    pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get & pdef12.get & pdef13.get & pdef14.get & pdef15.get & pdef16.get & pdef17.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def formField(pdef1: FieldSpec, pdef2: FieldSpec, pdef3: FieldSpec, pdef4: FieldSpec, pdef5: FieldSpec,
      pdef6: FieldSpec, pdef7: FieldSpec, pdef8: FieldSpec, pdef9: FieldSpec, pdef10: FieldSpec, pdef11: FieldSpec,
      pdef12: FieldSpec, pdef13: FieldSpec, pdef14: FieldSpec, pdef15: FieldSpec, pdef16: FieldSpec, pdef17: FieldSpec,
      pdef18: FieldSpec): Directive[Tuple18[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out,
    pdef8.Out, pdef9.Out, pdef10.Out, pdef11.Out, pdef12.Out, pdef13.Out, pdef14.Out, pdef15.Out, pdef16.Out,
    pdef17.Out, pdef18.Out]] =
    pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get & pdef12.get & pdef13.get & pdef14.get & pdef15.get & pdef16.get & pdef17.get & pdef18.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def formFields(pdef1: FieldSpec, pdef2: FieldSpec, pdef3: FieldSpec, pdef4: FieldSpec, pdef5: FieldSpec,
      pdef6: FieldSpec, pdef7: FieldSpec, pdef8: FieldSpec, pdef9: FieldSpec, pdef10: FieldSpec, pdef11: FieldSpec,
      pdef12: FieldSpec, pdef13: FieldSpec, pdef14: FieldSpec, pdef15: FieldSpec, pdef16: FieldSpec, pdef17: FieldSpec,
      pdef18: FieldSpec): Directive[Tuple18[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out,
    pdef8.Out, pdef9.Out, pdef10.Out, pdef11.Out, pdef12.Out, pdef13.Out, pdef14.Out, pdef15.Out, pdef16.Out,
    pdef17.Out, pdef18.Out]] =
    pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get & pdef12.get & pdef13.get & pdef14.get & pdef15.get & pdef16.get & pdef17.get & pdef18.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def formField(pdef1: FieldSpec, pdef2: FieldSpec, pdef3: FieldSpec, pdef4: FieldSpec, pdef5: FieldSpec,
      pdef6: FieldSpec, pdef7: FieldSpec, pdef8: FieldSpec, pdef9: FieldSpec, pdef10: FieldSpec, pdef11: FieldSpec,
      pdef12: FieldSpec, pdef13: FieldSpec, pdef14: FieldSpec, pdef15: FieldSpec, pdef16: FieldSpec, pdef17: FieldSpec,
      pdef18: FieldSpec, pdef19: FieldSpec): Directive[Tuple19[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out,
    pdef6.Out, pdef7.Out, pdef8.Out, pdef9.Out, pdef10.Out, pdef11.Out, pdef12.Out, pdef13.Out, pdef14.Out, pdef15.Out,
    pdef16.Out, pdef17.Out, pdef18.Out, pdef19.Out]] =
    pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get & pdef12.get & pdef13.get & pdef14.get & pdef15.get & pdef16.get & pdef17.get & pdef18.get & pdef19.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def formFields(pdef1: FieldSpec, pdef2: FieldSpec, pdef3: FieldSpec, pdef4: FieldSpec, pdef5: FieldSpec,
      pdef6: FieldSpec, pdef7: FieldSpec, pdef8: FieldSpec, pdef9: FieldSpec, pdef10: FieldSpec, pdef11: FieldSpec,
      pdef12: FieldSpec, pdef13: FieldSpec, pdef14: FieldSpec, pdef15: FieldSpec, pdef16: FieldSpec, pdef17: FieldSpec,
      pdef18: FieldSpec, pdef19: FieldSpec): Directive[Tuple19[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out,
    pdef6.Out, pdef7.Out, pdef8.Out, pdef9.Out, pdef10.Out, pdef11.Out, pdef12.Out, pdef13.Out, pdef14.Out, pdef15.Out,
    pdef16.Out, pdef17.Out, pdef18.Out, pdef19.Out]] =
    pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get & pdef12.get & pdef13.get & pdef14.get & pdef15.get & pdef16.get & pdef17.get & pdef18.get & pdef19.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def formField(pdef1: FieldSpec, pdef2: FieldSpec, pdef3: FieldSpec, pdef4: FieldSpec, pdef5: FieldSpec,
      pdef6: FieldSpec, pdef7: FieldSpec, pdef8: FieldSpec, pdef9: FieldSpec, pdef10: FieldSpec, pdef11: FieldSpec,
      pdef12: FieldSpec, pdef13: FieldSpec, pdef14: FieldSpec, pdef15: FieldSpec, pdef16: FieldSpec, pdef17: FieldSpec,
      pdef18: FieldSpec, pdef19: FieldSpec, pdef20: FieldSpec): Directive[Tuple20[pdef1.Out, pdef2.Out, pdef3.Out,
    pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out, pdef9.Out, pdef10.Out, pdef11.Out, pdef12.Out, pdef13.Out,
    pdef14.Out, pdef15.Out, pdef16.Out, pdef17.Out, pdef18.Out, pdef19.Out, pdef20.Out]] =
    pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get & pdef12.get & pdef13.get & pdef14.get & pdef15.get & pdef16.get & pdef17.get & pdef18.get & pdef19.get & pdef20.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def formFields(pdef1: FieldSpec, pdef2: FieldSpec, pdef3: FieldSpec, pdef4: FieldSpec, pdef5: FieldSpec,
      pdef6: FieldSpec, pdef7: FieldSpec, pdef8: FieldSpec, pdef9: FieldSpec, pdef10: FieldSpec, pdef11: FieldSpec,
      pdef12: FieldSpec, pdef13: FieldSpec, pdef14: FieldSpec, pdef15: FieldSpec, pdef16: FieldSpec, pdef17: FieldSpec,
      pdef18: FieldSpec, pdef19: FieldSpec, pdef20: FieldSpec): Directive[Tuple20[pdef1.Out, pdef2.Out, pdef3.Out,
    pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out, pdef9.Out, pdef10.Out, pdef11.Out, pdef12.Out, pdef13.Out,
    pdef14.Out, pdef15.Out, pdef16.Out, pdef17.Out, pdef18.Out, pdef19.Out, pdef20.Out]] =
    pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get & pdef12.get & pdef13.get & pdef14.get & pdef15.get & pdef16.get & pdef17.get & pdef18.get & pdef19.get & pdef20.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def formField(pdef1: FieldSpec, pdef2: FieldSpec, pdef3: FieldSpec, pdef4: FieldSpec, pdef5: FieldSpec,
      pdef6: FieldSpec, pdef7: FieldSpec, pdef8: FieldSpec, pdef9: FieldSpec, pdef10: FieldSpec, pdef11: FieldSpec,
      pdef12: FieldSpec, pdef13: FieldSpec, pdef14: FieldSpec, pdef15: FieldSpec, pdef16: FieldSpec, pdef17: FieldSpec,
      pdef18: FieldSpec, pdef19: FieldSpec, pdef20: FieldSpec, pdef21: FieldSpec)
      : Directive[Tuple21[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out,
        pdef9.Out, pdef10.Out, pdef11.Out, pdef12.Out, pdef13.Out, pdef14.Out, pdef15.Out, pdef16.Out, pdef17.Out,
        pdef18.Out, pdef19.Out, pdef20.Out, pdef21.Out]] =
    pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get & pdef12.get & pdef13.get & pdef14.get & pdef15.get & pdef16.get & pdef17.get & pdef18.get & pdef19.get & pdef20.get & pdef21.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def formFields(pdef1: FieldSpec, pdef2: FieldSpec, pdef3: FieldSpec, pdef4: FieldSpec, pdef5: FieldSpec,
      pdef6: FieldSpec, pdef7: FieldSpec, pdef8: FieldSpec, pdef9: FieldSpec, pdef10: FieldSpec, pdef11: FieldSpec,
      pdef12: FieldSpec, pdef13: FieldSpec, pdef14: FieldSpec, pdef15: FieldSpec, pdef16: FieldSpec, pdef17: FieldSpec,
      pdef18: FieldSpec, pdef19: FieldSpec, pdef20: FieldSpec, pdef21: FieldSpec)
      : Directive[Tuple21[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out,
        pdef9.Out, pdef10.Out, pdef11.Out, pdef12.Out, pdef13.Out, pdef14.Out, pdef15.Out, pdef16.Out, pdef17.Out,
        pdef18.Out, pdef19.Out, pdef20.Out, pdef21.Out]] =
    pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get & pdef12.get & pdef13.get & pdef14.get & pdef15.get & pdef16.get & pdef17.get & pdef18.get & pdef19.get & pdef20.get & pdef21.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def formField(pdef1: FieldSpec, pdef2: FieldSpec, pdef3: FieldSpec, pdef4: FieldSpec, pdef5: FieldSpec,
      pdef6: FieldSpec, pdef7: FieldSpec, pdef8: FieldSpec, pdef9: FieldSpec, pdef10: FieldSpec, pdef11: FieldSpec,
      pdef12: FieldSpec, pdef13: FieldSpec, pdef14: FieldSpec, pdef15: FieldSpec, pdef16: FieldSpec, pdef17: FieldSpec,
      pdef18: FieldSpec, pdef19: FieldSpec, pdef20: FieldSpec, pdef21: FieldSpec, pdef22: FieldSpec)
      : Directive[Tuple22[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out,
        pdef9.Out, pdef10.Out, pdef11.Out, pdef12.Out, pdef13.Out, pdef14.Out, pdef15.Out, pdef16.Out, pdef17.Out,
        pdef18.Out, pdef19.Out, pdef20.Out, pdef21.Out, pdef22.Out]] =
    pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get & pdef12.get & pdef13.get & pdef14.get & pdef15.get & pdef16.get & pdef17.get & pdef18.get & pdef19.get & pdef20.get & pdef21.get & pdef22.get

  /**
   * Extracts query parameter values from the request.
   * Rejects the request if the defined query parameter matcher(s) don't match.
   *
   * @group param
   */
  def formFields(pdef1: FieldSpec, pdef2: FieldSpec, pdef3: FieldSpec, pdef4: FieldSpec, pdef5: FieldSpec,
      pdef6: FieldSpec, pdef7: FieldSpec, pdef8: FieldSpec, pdef9: FieldSpec, pdef10: FieldSpec, pdef11: FieldSpec,
      pdef12: FieldSpec, pdef13: FieldSpec, pdef14: FieldSpec, pdef15: FieldSpec, pdef16: FieldSpec, pdef17: FieldSpec,
      pdef18: FieldSpec, pdef19: FieldSpec, pdef20: FieldSpec, pdef21: FieldSpec, pdef22: FieldSpec)
      : Directive[Tuple22[pdef1.Out, pdef2.Out, pdef3.Out, pdef4.Out, pdef5.Out, pdef6.Out, pdef7.Out, pdef8.Out,
        pdef9.Out, pdef10.Out, pdef11.Out, pdef12.Out, pdef13.Out, pdef14.Out, pdef15.Out, pdef16.Out, pdef17.Out,
        pdef18.Out, pdef19.Out, pdef20.Out, pdef21.Out, pdef22.Out]] =
    pdef1.get & pdef2.get & pdef3.get & pdef4.get & pdef5.get & pdef6.get & pdef7.get & pdef8.get & pdef9.get & pdef10.get & pdef11.get & pdef12.get & pdef13.get & pdef14.get & pdef15.get & pdef16.get & pdef17.get & pdef18.get & pdef19.get & pdef20.get & pdef21.get & pdef22.get

}
