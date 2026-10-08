package pl.iterators.stir.server.directives

import org.http4s.Uri
import org.typelevel.ci.CIString

/**
 * The redaction policy a logging directive applies to headers, the request URI, `Referer`/`Location`, the logged
 * body and rejections (design spec, section 5.2). `default` has every stage on; `none` reproduces 0.5.0 and an
 * operation switches on the stage it needs. The `add…`/`redact…`/`hide…` operations keep every default in place;
 * `with…` replaces one.
 */
final class LogRedaction private (
    private val namesStage: Option[String => Boolean],
    private val uriHeaderStage: Boolean,
    private val uriRules: Vector[Uri => Uri],
    private val base: BodyRedactor,
    private val overrides: Vector[BodyRedactor], // most recent first
    private val valueRules: Vector[(String => String, Boolean)],
    private val rejectionsHidden: Boolean) {

  /** The name predicate shared by every channel; `_ => false` while the names stage is off. */
  val names: String => Boolean = namesStage.getOrElse(_ => false)

  /** `SensitiveNames.headerFrom(names)` while the names stage is on. */
  val headers: CIString => Boolean = namesStage.fold[CIString => Boolean](_ => false)(SensitiveNames.headerFrom)

  /** Query and userinfo masking while the names stage is on, then the added URI rules in order. */
  val uri: Uri => Uri = {
    val namesPart: Uri => Uri =
      namesStage.fold[Uri => Uri](identity)(p => UriRedactor.query(p).andThen(UriRedactor.userInfo))
    uriRules.foldLeft(namesPart)(_.andThen(_))
  }

  /** Whether `Referer` and `Location` are parsed and transformed with `uri`. */
  val transformUriHeaders: Boolean = uriHeaderStage

  val rejectionValues: LogRedaction.RejectionValues =
    if (rejectionsHidden) LogRedaction.RejectionValues.Hidden
    else if (namesStage.isDefined) LogRedaction.RejectionValues.ByName
    else LogRedaction.RejectionValues.Shown

  /** Overrides (most recent first), then name-based JSON and form masking, then the base, then the value rules. */
  val body: BodyRedactor = {
    val namesPart = namesStage.map(p => BodyRedactor.jsonKeys(p).orElse(BodyRedactor.formFields(p)))
    val chain = namesPart.fold(base)(_.orElse(base))
    val withOverrides = overrides.foldRight(chain)((o, rest) => o.orElse(rest))
    valueRules.foldLeft(withOverrides) { case (acc, (f, prefixSafe)) => LogRedaction.valueStage(acc, f, prefixSafe) }
  }

  /** Also treat `entries` as sensitive in every channel (normalised like names; no suffix rules). */
  def addNames(entries: String*): LogRedaction =
    if (entries.isEmpty) this
    else {
      val added = SensitiveNames.matching(entries.toSet, suffixes = Set.empty)
      val current = names
      copy(namesStage = Some(n => current(n) || added(n)), uriHeaderStage = true)
    }

  /** `redactUri(UriRedactor.pathTemplate(template))`. */
  def redactPath(template: String): LogRedaction = redactUri(UriRedactor.pathTemplate(template))

  /** `f` runs after the name-based query and userinfo masking and after earlier URI rules. */
  def redactUri(f: Uri => Uri): LogRedaction = copy(uriRules = uriRules :+ f, uriHeaderStage = true)

  /**
   * `f` runs on the values of the logged body after the chain: JSON scalars, decoded form values, the whole text
   * otherwise. Unless `prefixSafe`, a truncated or repaired body is hidden and `f` is not called.
   */
  def redactBodyValues(f: String => String, prefixSafe: Boolean = false): LogRedaction =
    copy(valueRules = valueRules :+ ((f, prefixSafe)))

  /** The four value-carrying rejections always render `<REDACTED>`. */
  def hideRejectionValues: LogRedaction = copy(rejectionsHidden = true)

  /** Replaces the name predicate (earlier `addNames` are discarded). */
  def withNames(p: String => Boolean): LogRedaction = copy(namesStage = Some(p), uriHeaderStage = true)

  /** `r` is consulted before everything else; bodies it does not `Skip` bypass the name-based masking. */
  def withBodyRedactor(r: BodyRedactor): LogRedaction = copy(overrides = r +: overrides)

  private def copy(
      namesStage: Option[String => Boolean] = this.namesStage,
      uriHeaderStage: Boolean = this.uriHeaderStage,
      uriRules: Vector[Uri => Uri] = this.uriRules,
      base: BodyRedactor = this.base,
      overrides: Vector[BodyRedactor] = this.overrides,
      valueRules: Vector[(String => String, Boolean)] = this.valueRules,
      rejectionsHidden: Boolean = this.rejectionsHidden): LogRedaction =
    new LogRedaction(namesStage, uriHeaderStage, uriRules, base, overrides, valueRules, rejectionsHidden)
}

object LogRedaction {

  sealed trait RejectionValues
  object RejectionValues {
    case object Shown extends RejectionValues
    case object ByName extends RejectionValues
    case object Hidden extends RejectionValues
  }

  /** Every stage on: names from `SensitiveNames.default`, plain text shown, every other body hidden. */
  val default: LogRedaction = new LogRedaction(
    namesStage = Some(SensitiveNames.default),
    uriHeaderStage = true,
    uriRules = Vector.empty,
    base = BodyRedactor.when(_.isPlainText)(BodyRedactor.passThrough).orElse(BodyRedactor.hidden),
    overrides = Vector.empty,
    valueRules = Vector.empty,
    rejectionsHidden = false)

  /** Every stage off: the 0.5.0 behaviour. */
  val none: LogRedaction = new LogRedaction(
    namesStage = None,
    uriHeaderStage = false,
    uriRules = Vector.empty,
    base = BodyRedactor.passThrough,
    overrides = Vector.empty,
    valueRules = Vector.empty,
    rejectionsHidden = false)

  /**
   * One value rule over the chain's outcome. The stage checks `body.truncated`, the incoming `Text.incomplete` and,
   * for JSON, the flag of its own scan before `f` is called on anything; the outcome carries the combined flag so
   * that a later rule still sees it.
   */
  private def valueStage(chain: BodyRedactor, f: String => String, prefixSafe: Boolean): BodyRedactor =
    BodyRedactor.unguarded { body =>
      chain.redact(body).map {
        case BodyRedactor.Text(text, note, incomplete) if body.isJson =>
          val scanned = JsonRedaction.scan(text, body.truncated, JsonRedaction.Keep)
          val inc = body.truncated || incomplete || scanned.incomplete
          val combinedNote = note.orElse(scanned.note)
          if (inc && !prefixSafe) BodyRedactor.Hidden(combinedNote)
          else if (scanned.text.isEmpty) BodyRedactor.Text("", combinedNote, inc)
          else {
            val transformed = JsonRedaction.scan(scanned.text, truncated = false, JsonRedaction.Transform(f))
            BodyRedactor.Text(transformed.text, combinedNote, inc)
          }
        case BodyRedactor.Text(text, note, incomplete) =>
          val inc = body.truncated || incomplete
          if (inc && !prefixSafe) BodyRedactor.Hidden(note)
          else if (body.isForm)
            BodyRedactor.Text(FormRedaction.transformValues(text, body.charset.nioCharset, f), note, inc)
          else BodyRedactor.Text(f(text), note, inc)
        case other => other
      }
    }
}
