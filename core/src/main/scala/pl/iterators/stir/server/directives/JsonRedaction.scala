package pl.iterators.stir.server.directives

import scala.collection.mutable

/**
 * The incremental JSON scanner behind `BodyRedactor.jsonKeys`, `jsonKeepOnly`, `json` and the value rules of
 * `LogRedaction` (design spec, section 7). One pass, left to right, explicit stack, no recursion, no regular
 * expressions, no JSON library. The output is compact and is valid JSON at every truncation point.
 */
private[directives] object JsonRedaction {

  final case class Result(text: String, note: Option[String], incomplete: Boolean)

  sealed trait Mode
  case object Keep extends Mode
  final case class DenyList(isSensitive: String => Boolean) extends Mode
  final case class KeepOnly(keys: Set[String], isSensitive: String => Boolean) extends Mode
  final case class Transform(f: String => String) extends Mode

  /** The JSON literal that replaces a masked value; `BodyRedactor.Mask` (Task 9) is the same word unquoted. */
  val MaskLiteral: String = "\"" + BodyRedactor.Mask + "\""

  def scan(input: String, truncated: Boolean, mode: Mode): Result = new Scanner(input, truncated, mode).run()

  def isLiteral(s: String): Boolean = s == "true" || s == "false" || s == "null"

  /** The JSON number grammar: `-? (0 | [1-9][0-9]*) (. [0-9]+)? ([eE] [+-]? [0-9]+)?`. */
  def isNumber(s: String): Boolean = {
    val n = s.length
    var i = 0
    def digit(c: Char) = c >= '0' && c <= '9'
    def digits(): Boolean = {
      val start = i
      while (i < n && digit(s.charAt(i))) i += 1
      i > start
    }
    if (i < n && s.charAt(i) == '-') i += 1
    val intOk =
      if (i < n && s.charAt(i) == '0') { i += 1; true }
      else digits()
    if (!intOk) false
    else {
      val fracOk = if (i < n && s.charAt(i) == '.') { i += 1; digits() }
      else true
      if (!fracOk) false
      else {
        val expOk =
          if (i < n && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
            i += 1
            if (i < n && (s.charAt(i) == '+' || s.charAt(i) == '-')) i += 1
            digits()
          } else true
        expOk && i == n
      }
    }
  }

  /** `s` as a JSON string literal: quotes, backslashes and control characters escaped. */
  def quote(s: String): String = {
    val sb = new StringBuilder(s.length + 2)
    sb.append('"')
    var i = 0
    while (i < s.length) {
      val c = s.charAt(i)
      c match {
        case '"'           => sb.append("\\\"")
        case '\\'          => sb.append("\\\\")
        case '\n'          => sb.append("\\n")
        case '\r'          => sb.append("\\r")
        case '\t'          => sb.append("\\t")
        case '\b'          => sb.append("\\b")
        case '\f'          => sb.append("\\f")
        case c if c < 0x20 =>
          sb.append("\\u00")
          sb.append(Character.forDigit((c >> 4) & 0xF, 16))
          sb.append(Character.forDigit(c & 0xF, 16))
        case c => sb.append(c)
      }
      i += 1
    }
    sb.append('"')
    sb.toString
  }

  // States between tokens.
  private val ExpectValue = 0
  private val ExpectKeyOrEnd = 1
  private val ExpectKey = 2
  private val ExpectColon = 3
  private val ExpectCommaOrEnd = 4
  private val Done = 5
  // States inside tokens.
  private val InString = 6
  private val InNumber = 7
  private val InLiteral = 8

  // What to do with the value that is about to start.
  private val Show = 0
  private val MaskWhole = 1
  private val TransformIt = 2

  // How string characters are consumed.
  private val EmitChars = 0 // kept value: emitted as they come, closed at a cut
  private val SwallowChars = 1 // masked value: the mask was emitted at the opening quote
  private val BufferChars = 2 // transformed value: unescaped content collected, f applied at the end
  private val KeyChars = 3 // key: raw text pending, unescaped text collected for matching

  private final class Frame(val isObject: Boolean, val governingKey: Option[String], val silent: Boolean) {
    var key: Option[String] = None // current member key (objects)
    var afterComma: Boolean = false // `]` is not allowed right after a comma
  }

  private final class Scanner(input: String, truncated: Boolean, mode: Mode) {
    private val out = new StringBuilder(input.length)
    // Comma, key and colon of a member whose value has not started, or a number/literal token not yet complete.
    private val pending = new StringBuilder
    private val strBuf = new StringBuilder // unescaped content of the string being read (keys, transformed values)
    private val escBuf = new StringBuilder // raw text of the escape sequence being read
    private val frames = mutable.ArrayBuffer.empty[Frame]
    private var state = ExpectValue
    private var charMode = EmitChars
    private var escaping = false
    private var swallowScalar = false // a masked string value is being swallowed
    private var tokenStart = 0
    private var numberLength = 0 // characters of the number token at the end of `pending`
    private var literal = "" // the literal the current token must spell
    private var malformedAt = -1
    private val transform: String => String = mode match {
      case Transform(f) => f
      case _            => identity
    }

    private def silent: Boolean = swallowScalar || (frames.nonEmpty && frames.last.silent)

    private def emit(s: String): Unit = if (!silent) { out.append(s); () }

    private def emit(c: Char): Unit = if (!silent) { out.append(c); () }

    private def commitPending(): Unit = { emit(pending.toString); pending.clear() }

    private def afterValue(): Unit = state = if (frames.isEmpty) Done else ExpectCommaOrEnd

    private def malformed(at: Int): Unit = malformedAt = at

    private def isWhitespace(c: Char) = c == ' ' || c == '\t' || c == '\n' || c == '\r'

    private def isNumberChar(c: Char) =
      (c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E'

    /** The value of an ASCII hex digit, -1 for any other character: JSON allows no other digit in a `\u` escape. */
    private def hexValue(c: Char): Int =
      if (c >= '0' && c <= '9') c - '0'
      else if (c >= 'a' && c <= 'f') c - 'a' + 10
      else if (c >= 'A' && c <= 'F') c - 'A' + 10
      else -1

    /** The key governing the value about to start: the member key in an object, the inherited key in an array. */
    private def currentKey: Option[String] =
      if (frames.isEmpty) None else if (frames.last.isObject) frames.last.key else frames.last.governingKey

    private def inObject: Boolean = frames.nonEmpty && frames.last.isObject

    private def disposition(isScalar: Boolean): Int =
      if (silent) Show
      else mode match {
        case Keep              => Show
        case DenyList(p)       => if (inObject && currentKey.exists(p)) MaskWhole else Show
        case KeepOnly(keys, p) =>
          if (inObject && currentKey.exists(p)) MaskWhole
          else if (isScalar) { if (currentKey.exists(keys.contains)) Show else MaskWhole }
          else Show
        case Transform(_) => if (isScalar) TransformIt else Show
      }

    def run(): Result = {
      val n = input.length
      var i = 0
      var reprocess = false
      while (i < n && malformedAt < 0) {
        val c = input.charAt(i)
        reprocess = false
        state match {
          case InString => inString(c, i)
          case InNumber =>
            if (isNumberChar(c)) { pending.append(c); numberLength += 1 }
            else { endNumber(); reprocess = true }
          case InLiteral      => inLiteral(c, i)
          case ExpectValue    => startValue(c, i)
          case ExpectKeyOrEnd =>
            if (isWhitespace(c)) ()
            else if (c == '}') closeContainer(isObject = true, i)
            else if (c == '"') startKey()
            else malformed(i)
          case ExpectKey =>
            if (isWhitespace(c)) () else if (c == '"') startKey() else malformed(i)
          case ExpectColon =>
            if (isWhitespace(c)) ()
            else if (c == ':') { pending.append(':'); state = ExpectValue }
            else malformed(i)
          case ExpectCommaOrEnd =>
            if (isWhitespace(c)) ()
            else if (c == ',') {
              pending.append(',')
              frames.last.afterComma = true
              state = if (frames.last.isObject) ExpectKey else ExpectValue
            } else if (c == '}') closeContainer(isObject = true, i)
            else if (c == ']') closeContainer(isObject = false, i)
            else malformed(i)
          case _ => // Done
            if (!isWhitespace(c)) malformed(i)
        }
        if (!reprocess) i += 1
      }
      if (malformedAt < 0) atEnd()
      val wellFormed = malformedAt < 0 && state == Done && frames.isEmpty && pending.isEmpty
      val note =
        if (malformedAt >= 0) Some(s"unparseable from position $malformedAt")
        else if (!truncated && !wellFormed) Some(s"unparseable from position $n")
        else None
      close()
      Result(out.toString, note, incomplete = truncated || !wellFormed)
    }

    private def startKey(): Unit = {
      state = InString
      charMode = KeyChars
      strBuf.clear()
      pending.append('"')
    }

    private def startValue(c: Char, i: Int): Unit =
      if (isWhitespace(c)) ()
      else if (c == ']' && frames.nonEmpty && !frames.last.isObject && !frames.last.afterComma)
        closeContainer(isObject = false, i)
      else if (c == '"') {
        disposition(isScalar = true) match {
          case Show =>
            commitPending(); emit('"'); charMode = EmitChars
          case MaskWhole =>
            commitPending(); emit(MaskLiteral); swallowScalar = true; charMode = SwallowChars
          case _ =>
            commitPending(); strBuf.clear(); charMode = BufferChars
        }
        state = InString
      } else if (c == '{' || c == '[') {
        val key = currentKey
        val masked = disposition(isScalar = false) == MaskWhole
        commitPending()
        if (masked) emit(MaskLiteral) else emit(c)
        frames += new Frame(isObject = c == '{', governingKey = key, silent = masked || silent)
        state = if (c == '{') ExpectKeyOrEnd else ExpectValue
      } else if (c == '-' || (c >= '0' && c <= '9')) {
        tokenStart = i
        numberLength = 1
        pending.append(c)
        state = InNumber
      } else if (c == 't' || c == 'f' || c == 'n') {
        tokenStart = i
        literal = if (c == 't') "true" else if (c == 'f') "false" else "null"
        pending.append(c)
        state = InLiteral
      } else malformed(i)

    private def inLiteral(c: Char, i: Int): Unit = {
      val len = i - tokenStart
      if (len < literal.length) {
        if (c == literal.charAt(len)) {
          pending.append(c)
          if (len == literal.length - 1) endToken(literal)
        } else malformed(i)
      } else malformed(i) // unreachable: the token completes on its last character
    }

    private def endNumber(): Unit = {
      val token = pending.toString.substring(pending.length - numberLength)
      if (!isNumber(token)) malformed(tokenStart) else endToken(token)
    }

    private def endToken(token: String): Unit = {
      // `pending` ends with the token; everything before it is the member prefix.
      val prefix = pending.toString.substring(0, pending.length - token.length)
      pending.clear()
      pending.append(prefix)
      disposition(isScalar = true) match {
        case Show      => commitPending(); emit(token)
        case MaskWhole => commitPending(); emit(MaskLiteral)
        case _         =>
          commitPending()
          val r = transform(token)
          emit(if (isNumber(r) || isLiteral(r)) r else quote(r))
      }
      afterValue()
    }

    private def inString(c: Char, i: Int): Unit =
      if (escaping) {
        escBuf.append(c)
        if (escBuf.length == 2) {
          c match {
            case '"' | '\\' | '/' | 'b' | 'f' | 'n' | 'r' | 't' =>
              val unescaped = c match {
                case 'b' => '\b'; case 'f' => '\f'; case 'n' => '\n'; case 'r' => '\r'; case 't' => '\t'; case x => x
              }
              deliver(escBuf.toString, unescaped.toString)
              escaping = false
            case 'u' => ()
            case _   => malformed(i)
          }
        } else if (escBuf.length == 6) {
          val code = escapedCodeUnit()
          if (code >= 0) {
            deliver(escBuf.toString, code.toChar.toString)
            escaping = false
          } else malformed(i)
        } else if (hexValue(c) < 0) malformed(i)
      } else if (c == '\\') { escaping = true; escBuf.clear(); escBuf.append(c) }
      else if (c == '"') endString()
      else deliver(c.toString, c.toString)

    /** The code unit of the `\uXXXX` escape in `escBuf`, or -1 when a digit is not an ASCII hex digit. */
    private def escapedCodeUnit(): Int = {
      var code = 0
      var k = 2
      while (k < 6 && code >= 0) {
        val v = hexValue(escBuf.charAt(k))
        code = if (v < 0) -1 else code * 16 + v
        k += 1
      }
      code
    }

    private def deliver(raw: String, unescaped: String): Unit = charMode match {
      case EmitChars    => emit(raw)
      case SwallowChars => ()
      case BufferChars  => strBuf.append(unescaped); ()
      case _            => pending.append(raw); strBuf.append(unescaped); ()
    }

    private def endString(): Unit = charMode match {
      case KeyChars =>
        pending.append('"')
        frames.last.key = Some(strBuf.toString)
        state = ExpectColon
      case EmitChars =>
        emit('"'); afterValue()
      case SwallowChars =>
        swallowScalar = false; afterValue()
      case _ =>
        emit(quote(transform(strBuf.toString))); afterValue()
    }

    private def closeContainer(isObject: Boolean, i: Int): Unit =
      if (frames.isEmpty || frames.last.isObject != isObject) malformed(i)
      else {
        val frame = frames.remove(frames.length - 1)
        pending.clear()
        if (!frame.silent) { out.append(if (isObject) '}' else ']'); () }
        afterValue()
      }

    /**
     * End of input: a trailing number is complete when the body was received whole; a literal still in progress
     * is always incomplete (a complete one left `InLiteral` on its last character).
     */
    private def atEnd(): Unit = state match {
      case InNumber =>
        val token = pending.toString.substring(pending.length - numberLength)
        if (!truncated && isNumber(token)) endToken(token) else pending.clear()
      case InLiteral => pending.clear()
      case _         => ()
    }

    /** Drop dangling text, close an open kept string and close every open container, innermost first. */
    private def close(): Unit = {
      if (state == InString) charMode match {
        case EmitChars    => emit('"')
        case SwallowChars => swallowScalar = false
        case BufferChars  => emit(quote(transform(strBuf.toString)))
        case _            => () // dangling key: dropped with its pending comma
      }
      pending.clear()
      while (frames.nonEmpty) {
        val frame = frames.remove(frames.length - 1)
        if (!frame.silent) { out.append(if (frame.isObject) '}' else ']'); () }
      }
    }
  }
}
