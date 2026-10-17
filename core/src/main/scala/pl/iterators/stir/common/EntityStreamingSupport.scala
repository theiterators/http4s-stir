package pl.iterators.stir.common

import cats.effect.IO
import fs2.{ Chunk, Pipe, Pull, Stream }
import org.http4s.{ MediaRange, MediaType, Status }
import org.http4s.headers.`Content-Type`
import pl.iterators.stir.server.IllegalRequestException

/**
 * Describes how a request body is split into frames (one element each) and how a stream of elements is rendered
 * into a response body. Used by `asSourceOf` and by the `Stream[IO, T]` response marshaller.
 */
trait EntityStreamingSupport {

  /** The request content types the framing understands. */
  def supported: MediaRange

  /** The content type of rendered responses. */
  def contentType: `Content-Type`

  /** Splits the request bytes into frames, one per element. */
  def framingDecoder: Pipe[IO, Byte, Chunk[Byte]]

  /** Joins rendered elements into the response bytes. */
  def framingRenderer: Pipe[IO, Chunk[Byte], Byte]

  /**
   * How many elements are (un)marshalled at the same time by `asSourceOf` and the stream marshaller (Pekko's
   * `parallelism`, which Pekko applies on the read side only); 1 for the `json()` and `csv()` supports. Values below
   * 1 are treated as 1.
   */
  def parallelism: Int

  /**
   * Whether parallel (un)marshalling may emit the elements out of order, which avoids head-of-line blocking when
   * some elements are much larger than others (Pekko's `unordered`); `false` for the `json()` and `csv()` supports.
   */
  def unordered: Boolean

  def withSupported(range: MediaRange): EntityStreamingSupport
  def withContentType(contentType: `Content-Type`): EntityStreamingSupport

  /** Replaces the renderer, for instance to emit newline separated JSON objects instead of an array. */
  def withFramingRenderer(renderer: Pipe[IO, Chunk[Byte], Byte]): EntityStreamingSupport

  /** (Un)marshals up to `parallelism` elements at a time, in order unless `unordered`. */
  def withParallelMarshalling(parallelism: Int, unordered: Boolean): EntityStreamingSupport

  /** Runs `f` on every element with the configured parallelism and ordering. */
  private[stir] def mapElements[A, B](f: A => IO[B]): Pipe[IO, A, B] =
    if (parallelism <= 1) _.evalMap(f)
    else if (unordered) _.parEvalMapUnordered(parallelism)(f)
    else _.parEvalMap(parallelism)(f)
}

object EntityStreamingSupport {

  /**
   * JSON streaming: the request may be a JSON array of objects or whitespace/newline separated objects; each object
   * (at most `maxObjectLength` bytes) is one element. Responses are rendered as a JSON array.
   */
  def json(maxObjectLength: Int = 8 * 1024): EntityStreamingSupport =
    Impl(MediaType.application.json, `Content-Type`(MediaType.application.json),
      JsonFraming.objectScanner(maxObjectLength), JsonFraming.arrayRenderer)

  /**
   * Line streaming: each line of the request (at most `maxLineLength` bytes, `\n` or `\r\n` terminated) is one
   * element; responses are rendered one element per line. The content type is `text/csv`.
   */
  def csv(maxLineLength: Int = 8 * 1024): EntityStreamingSupport =
    Impl(MediaType.text.csv, `Content-Type`(MediaType.text.csv), LineFraming.decoder(maxLineLength),
      LineFraming.renderer)

  private final case class Impl(supported: MediaRange, contentType: `Content-Type`,
      framingDecoder: Pipe[IO, Byte, Chunk[Byte]], framingRenderer: Pipe[IO, Chunk[Byte], Byte],
      parallelism: Int = 1, unordered: Boolean = false)
      extends EntityStreamingSupport {
    def withSupported(range: MediaRange): EntityStreamingSupport = copy(supported = range)
    def withContentType(contentType: `Content-Type`): EntityStreamingSupport = copy(contentType = contentType)
    def withFramingRenderer(renderer: Pipe[IO, Chunk[Byte], Byte]): EntityStreamingSupport =
      copy(framingRenderer = renderer)
    def withParallelMarshalling(parallelism: Int, unordered: Boolean): EntityStreamingSupport = {
      require(parallelism >= 1, s"parallelism must be at least 1, was $parallelism")
      copy(parallelism = parallelism, unordered = unordered)
    }
  }

  private[stir] object JsonFraming {
    private val OpenBrace = '{'.toByte
    private val CloseBrace = '}'.toByte
    private val Quote = '"'.toByte
    private val Backslash = '\\'.toByte
    private val Framing: Set[Byte] = Set('[', ']', ',', ' ', '\t', '\r', '\n').map(_.toByte)

    /** Scanner state: bytes of the object being read (empty between objects), brace depth, string/escape flags. */
    private final case class State(current: Vector[Byte], depth: Int, inString: Boolean, escaped: Boolean)
    private val Initial = State(Vector.empty, 0, false, false)

    private def invalid(position: Long): Throwable =
      IllegalRequestException(Status.BadRequest, s"Invalid JSON encountered at position $position")

    /** Feeds one chunk; returns the next state, the completed objects, and the first error if any. */
    private def feed(state: State, chunk: Chunk[Byte], offset: Long,
        maxObjectLength: Int): (State, Vector[Chunk[Byte]], Option[Throwable]) = {
      var s = state
      var error: Option[Throwable] = None
      val out = Vector.newBuilder[Chunk[Byte]]
      var index = 0
      while (index < chunk.size && error.isEmpty) {
        val byte = chunk(index)
        if (s.depth == 0) {
          if (byte == OpenBrace) s = State(Vector(byte), 1, false, false)
          else if (!Framing.contains(byte)) error = Some(invalid(offset + index))
        } else {
          val current = s.current :+ byte
          if (current.size > maxObjectLength)
            error = Some(IllegalRequestException(Status.BadRequest,
              s"JSON object exceeds the maximum length of $maxObjectLength bytes"))
          else if (s.inString)
            s = s.copy(current = current, inString = !(byte == Quote && !s.escaped),
              escaped = byte == Backslash && !s.escaped)
          else if (byte == Quote) s = s.copy(current = current, inString = true)
          else if (byte == OpenBrace) s = s.copy(current = current, depth = s.depth + 1)
          else if (byte == CloseBrace && s.depth == 1) {
            out += Chunk.from(current)
            s = Initial
          } else if (byte == CloseBrace) s = s.copy(current = current, depth = s.depth - 1)
          else s = s.copy(current = current)
        }
        index += 1
      }
      (s, out.result(), error)
    }

    def objectScanner(maxObjectLength: Int): Pipe[IO, Byte, Chunk[Byte]] = in => {
      def go(stream: Stream[IO, Byte], state: State, offset: Long): Pull[IO, Chunk[Byte], Unit] =
        stream.pull.uncons.flatMap {
          case None =>
            if (state.depth > 0) Pull.raiseError[IO](IllegalRequestException(Status.BadRequest,
              "Invalid JSON: the body ends inside an object"))
            else Pull.done
          case Some((chunk, rest)) =>
            val (next, objects, error) = feed(state, chunk, offset, maxObjectLength)
            Pull.output(Chunk.from(objects)) >> error.fold(go(rest, next, offset + chunk.size))(Pull.raiseError[IO])
        }
      go(in, Initial, 0L).stream
    }

    val arrayRenderer: Pipe[IO, Chunk[Byte], Byte] = frames =>
      Stream.emit('['.toByte) ++
      frames.zipWithIndex.flatMap {
        case (frame, 0) => Stream.chunk(frame)
        case (frame, _) => Stream.emit(','.toByte) ++ Stream.chunk(frame)
      } ++
      Stream.emit(']'.toByte)
  }

  private[stir] object LineFraming {
    private val NewLine = '\n'.toByte
    private val CarriageReturn = '\r'.toByte

    private def tooLong(maxLineLength: Int): Throwable =
      IllegalRequestException(Status.BadRequest, s"Line exceeds the maximum length of $maxLineLength bytes")

    /** Splits on `\n` (dropping a preceding `\r`), failing as soon as a pending line exceeds the limit. */
    def decoder(maxLineLength: Int): Pipe[IO, Byte, Chunk[Byte]] = in => {
      def line(bytes: Vector[Byte]): Option[Chunk[Byte]] = {
        val trimmed = if (bytes.lastOption.contains(CarriageReturn)) bytes.init else bytes
        if (trimmed.isEmpty) None else Some(Chunk.from(trimmed))
      }
      def go(stream: Stream[IO, Byte], pending: Vector[Byte]): Pull[IO, Chunk[Byte], Unit] =
        stream.pull.uncons.flatMap {
          case None =>
            line(pending) match {
              case Some(last) => Pull.output1[IO, Chunk[Byte]](last)
              case None       => Pull.done
            }
          case Some((chunk, rest)) =>
            var current = pending
            var error: Option[Throwable] = None
            val out = Vector.newBuilder[Chunk[Byte]]
            var index = 0
            while (index < chunk.size && error.isEmpty) {
              val byte = chunk(index)
              if (byte == NewLine) {
                line(current).foreach(out += _)
                current = Vector.empty
              } else {
                current = current :+ byte
                // a trailing carriage return is not part of the line, so it may exceed the limit by one
                val overLimit = current.size > maxLineLength &&
                  !(byte == CarriageReturn && current.size == maxLineLength + 1)
                if (overLimit) error = Some(tooLong(maxLineLength))
              }
              index += 1
            }
            Pull.output(Chunk.from(out.result())) >> error.fold(go(rest, current))(Pull.raiseError[IO])
        }
      go(in, Vector.empty).stream
    }

    val renderer: Pipe[IO, Chunk[Byte], Byte] = _.flatMap(frame => Stream.chunk(frame) ++ Stream.emit('\n'.toByte))
  }
}
