package pl.iterators.stir.server.directives

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import fs2.{ Chunk, Stream }
import io.circe.parser.parse
import org.http4s.{ Charset, Entity, EntityEncoder, MediaType, Status }
import org.http4s.headers.`Content-Type`
import pl.iterators.stir.common.EntityStreamingSupport
import pl.iterators.stir.server._
import pl.iterators.stir.unmarshalling.Unmarshaller

import java.util.concurrent.atomic.{ AtomicBoolean, AtomicInteger }
import scala.concurrent.duration._

class FramedEntityStreamingDirectivesSpec extends RoutingSpec {
  override implicit def runtime: IORuntime = IORuntime.global

  /** Reads the `a` field of a JSON object such as `{"a":1}`. */
  implicit val aField: Unmarshaller[String, Int] = Unmarshaller.strict[String, Int] { s =>
    parse(s).flatMap(_.hcursor.get[Int]("a")).fold(throw _, identity)
  }

  val json = `Content-Type`(MediaType.application.json)
  val csv = `Content-Type`(MediaType.text.csv)

  def sumRoute(implicit support: EntityStreamingSupport): Route =
    entity(asSourceOf[Int]) { ints => onSuccess(ints.compile.toList) { list => complete(list.mkString(",")) } }

  def sumRouteUsing(um: Unmarshaller[String, Int], support: EntityStreamingSupport): Route =
    entity(asSourceOf[Int](support)(um)) { ints =>
      onSuccess(ints.compile.toList) { list => complete(list.mkString(",")) }
    }

  /** Runs `body`, re-chunked to `chunkSize` bytes, through the support's framing decoder. */
  def frames(support: EntityStreamingSupport, body: String, chunkSize: Int): Either[Throwable, List[String]] =
    Stream.chunk(Chunk.array(body.getBytes("UTF-8"))).covary[IO].chunkN(chunkSize).unchunks
      .through(support.framingDecoder).map(frame => new String(frame.toArray, "UTF-8")).compile.toList
      .attempt.unsafeRunSync()

  def framesUnchunked(support: EntityStreamingSupport, body: String): List[String] =
    frames(support, body, Int.MaxValue).fold(throw _, identity)

  /** Counts how many `slow` steps run at the same time, to prove that elements were processed in parallel. */
  final class Concurrency {
    private val running = new AtomicInteger(0)
    private val peak = new AtomicInteger(0)
    def max: Int = peak.get()

    /**
     * Sleeps `(9 - n) * 15 ms` (for `n` in 1 to 8): the elements with a smaller `n` sleep longer, so that unordered
     * processing emits them in reverse.
     */
    def slow[A](n: Int, result: A): IO[A] =
      IO(running.incrementAndGet()).flatMap(now => IO(peak.accumulateAndGet(now, math.max))) *>
      IO.sleep((9 - n) * 15.millis) *> IO(running.decrementAndGet()).as(result)
  }

  /** Encodes an `Int` as its decimal text after a `Concurrency.slow` step. */
  def slowEncoder(concurrency: Concurrency): EntityEncoder[IO, Int] =
    EntityEncoder.encodeBy[IO, Int](`Content-Type`(MediaType.text.plain)) { n =>
      Entity(Stream.eval(concurrency.slow(n, n)).flatMap(n => Stream.chunk(Chunk.array(n.toString.getBytes("UTF-8")))))
    }

  /** Reads the `a` field after a `Concurrency.slow` step. */
  def slowUnmarshaller(concurrency: Concurrency): Unmarshaller[String, Int] =
    Unmarshaller[String, Int](s => aField(s).flatMap(n => concurrency.slow(n, n)))

  /** Checks that every chunking of `body`, from one byte per chunk up to the whole body, frames identically. */
  def frameIndependentOfChunking(support: EntityStreamingSupport, body: String): Unit = {
    val reference = frames(support, body, Int.MaxValue)
    (1 to body.getBytes("UTF-8").length).foreach { size =>
      withClue(s"body ${body.replace("\r", "\\r").replace("\n", "\\n")} in $size-byte chunks: ") {
        frames(support, body, size) shouldEqual reference
      }
    }
  }

  "asSourceOf with JSON streaming support" should {
    implicit val support: EntityStreamingSupport = EntityStreamingSupport.json()

    "decode the objects of a JSON array" in {
      Post("/", """[{"a":1},{"a":2}]""") ~> addHeader(json) ~> sumRoute ~> check {
        responseAs[String] shouldEqual "1,2"
      }
    }
    "decode newline separated objects" in {
      Post("/", "{\"a\":1}\n{\"a\":2}\n") ~> addHeader(json) ~> sumRoute ~> check {
        responseAs[String] shouldEqual "1,2"
      }
    }
    "keep braces inside strings and nested objects together" in {
      Post("/", """[{"a":1,"s":"}{","n":{"x":[1,2]}}, {"a":3}]""") ~> addHeader(json) ~> sumRoute ~> check {
        responseAs[String] shouldEqual "1,3"
      }
    }
    "decode a body that arrives one byte at a time" in {
      val body = Stream.emits("""[{"a":1,"s":"\"}"},{"a":2}]""".getBytes("UTF-8")).covary[IO].chunkN(1).unchunks
      Post("/", body) ~> addHeader(json) ~> sumRoute ~> check {
        responseAs[String] shouldEqual "1,2"
      }
    }
    "complete with no elements for an empty array" in {
      Post("/", "[]") ~> addHeader(json) ~> sumRoute ~> check {
        responseAs[String] shouldEqual ""
      }
    }
    "accept a Content-Type with a charset parameter" in {
      Post("/", """[{"a":1}]""") ~> addHeader(`Content-Type`(MediaType.application.json, Charset.`UTF-8`)) ~>
      sumRoute                   ~> check {
        responseAs[String] shouldEqual "1"
      }
    }
    "accept the media type of a customised support" in {
      val custom = EntityStreamingSupport.json().withSupported(MediaType.text.plain)
      Post("/", "{\"a\":1}\n") ~> addHeader(`Content-Type`(MediaType.text.plain)) ~> sumRoute(custom) ~> check {
        responseAs[String] shouldEqual "1"
      }
    }
    "reject a request whose Content-Type is not supported" in {
      Post("/", """[{"a":1}]""") ~> addHeader(`Content-Type`(MediaType.text.plain)) ~> sumRoute ~> check {
        rejection shouldEqual UnsupportedRequestContentTypeRejection(Set(MediaType.application.json),
          Some(MediaType.text.plain))
      }
    }
    "complete with 400 when an object exceeds the maximum length" in {
      val small = EntityStreamingSupport.json(maxObjectLength = 8)
      Post("/", """[{"a":1234567}]""") ~> addHeader(json) ~> Route.seal(sumRoute(small)) ~> check {
        status shouldEqual Status.BadRequest
      }
    }
    "reject a top-level array of scalars with 400" in {
      Post("/", "[1,2,3]") ~> addHeader(json) ~> Route.seal(sumRoute) ~> check { status shouldEqual Status.BadRequest }
    }
    "reject trailing garbage after the last object with 400" in {
      Post("/", """[{"a":1}] nonsense""") ~> addHeader(json) ~> Route.seal(sumRoute) ~> check {
        status shouldEqual Status.BadRequest
      }
    }
    "reject a body cut off inside an object with 400" in {
      Post("/", """[{"a":1""") ~> addHeader(json) ~> Route.seal(sumRoute) ~> check {
        status shouldEqual Status.BadRequest
      }
    }
    "fail with 500 when an element cannot be unmarshalled" in {
      Post("/", """[{"b":1}]""") ~> addHeader(json) ~> Route.seal(sumRoute) ~> check {
        status shouldEqual Status.InternalServerError
      }
    }
    "fail the response body, not the status, when a later object is invalid and the frames are streamed back" in {
      val echo: Route = entity(asSourceOf[String]) { objects => complete(objects) }
      Post("/", """[{"a":1}, nonsense]""") ~> addHeader(json) ~> echo ~> check {
        status shouldEqual Status.Ok
        an[IllegalRequestException] should be thrownBy responseAs[String]
      }
    }
    "decode a streamed array over a real server" in {
      Post("/", """[{"a":1},{"a":2},{"a":3}]""") ~> addHeader(json) ~!> sumRoute ~> check {
        responseAs[String] shouldEqual "1,2,3"
      }
    }
    "render a stream as a JSON array" in {
      Get() ~> complete(Stream("\"a\"", "\"b\"").covary[IO]) ~> check {
        mediaType shouldEqual MediaType.application.json
        responseAs[String] shouldEqual """["a","b"]"""
      }
    }
    "render an empty stream and a single element as JSON arrays" in {
      Get() ~> complete(Stream.empty.covaryAll[IO, String]) ~> check { responseAs[String] shouldEqual "[]" }
      Get() ~> complete(Stream("\"a\"").covary[IO])         ~> check { responseAs[String] shouldEqual """["a"]""" }
    }
    "render with the content type of a customised support" in {
      implicit val support: EntityStreamingSupport =
        EntityStreamingSupport.json().withContentType(`Content-Type`(MediaType.text.plain))
      Get() ~> complete(Stream("\"a\"").covary[IO]) ~> check {
        mediaType shouldEqual MediaType.text.plain
        responseAs[String] shouldEqual """["a"]"""
      }
    }
    "render newline separated objects with a custom framing renderer" in {
      implicit val support: EntityStreamingSupport = EntityStreamingSupport.json()
        .withFramingRenderer(frames => frames.flatMap(frame => Stream.chunk(frame) ++ Stream.emit('\n'.toByte)))
      Get() ~> complete(Stream("\"a\"", "\"b\"").covary[IO]) ~> check {
        mediaType shouldEqual MediaType.application.json
        responseAs[String] shouldEqual "\"a\"\n\"b\"\n"
      }
    }
    "marshal elements sequentially by default" in {
      val concurrency = new Concurrency
      implicit val slow: EntityEncoder[IO, Int] = slowEncoder(concurrency)
      Get() ~> complete(Stream.emits(1 to 4).covary[IO]) ~> check {
        responseAs[String] shouldEqual "[1,2,3,4]"
        concurrency.max shouldEqual 1
      }
    }
    "marshal elements in parallel, keeping their order" in {
      val concurrency = new Concurrency
      implicit val slow: EntityEncoder[IO, Int] = slowEncoder(concurrency)
      implicit val support: EntityStreamingSupport =
        EntityStreamingSupport.json().withParallelMarshalling(parallelism = 4, unordered = false)
      Get() ~> complete(Stream.emits(1 to 8).covary[IO]) ~> check {
        responseAs[String] shouldEqual "[1,2,3,4,5,6,7,8]"
        concurrency.max should be > 1
      }
    }
    "marshal elements in parallel and unordered" in {
      val concurrency = new Concurrency
      implicit val slow: EntityEncoder[IO, Int] = slowEncoder(concurrency)
      implicit val support: EntityStreamingSupport =
        EntityStreamingSupport.json().withParallelMarshalling(parallelism = 8, unordered = true)
      Get() ~> complete(Stream.emits(1 to 8).covary[IO]) ~> check {
        val emitted = responseAs[String].stripPrefix("[").stripSuffix("]").split(',').map(_.toInt).toList
        emitted.sorted shouldEqual (1 to 8).toList
        (emitted should not).equal((1 to 8).toList)
        concurrency.max should be > 1
      }
    }
    "fail the response body when an element cannot be encoded in parallel" in {
      implicit val failing: EntityEncoder[IO, Int] = EntityEncoder.encodeBy[IO, Int](
        `Content-Type`(MediaType.text.plain)) {
        n =>
          Entity(
            if (n == 3) Stream.raiseError[IO](new IllegalStateException("no 3")) else Stream.emits(n.toString.getBytes))
      }
      implicit val support: EntityStreamingSupport =
        EntityStreamingSupport.json().withParallelMarshalling(parallelism = 4, unordered = false)
      Get() ~> complete(Stream.emits(1 to 8).covary[IO]) ~> check {
        status shouldEqual Status.Ok
        an[IllegalStateException] should be thrownBy responseAs[String]
      }
    }
    "unmarshal frames in parallel, keeping their order" in {
      val concurrency = new Concurrency
      val support = EntityStreamingSupport.json().withParallelMarshalling(parallelism = 4, unordered = false)
      val route = sumRouteUsing(slowUnmarshaller(concurrency), support)
      Post("/", (1 to 8).map(n => s"""{"a":$n}""").mkString("[", ",", "]")) ~> addHeader(json) ~> route ~> check {
        responseAs[String] shouldEqual "1,2,3,4,5,6,7,8"
        concurrency.max should be > 1
      }
    }
    "unmarshal frames in parallel and unordered" in {
      val concurrency = new Concurrency
      val support = EntityStreamingSupport.json().withParallelMarshalling(parallelism = 8, unordered = true)
      val route = sumRouteUsing(slowUnmarshaller(concurrency), support)
      Post("/", (1 to 8).map(n => s"""{"a":$n}""").mkString("[", ",", "]")) ~> addHeader(json) ~> route ~> check {
        val emitted = responseAs[String].split(',').map(_.toInt).toList
        emitted.sorted shouldEqual (1 to 8).toList
        (emitted should not).equal((1 to 8).toList)
        concurrency.max should be > 1
      }
    }
    "fail with 500 when a frame cannot be unmarshalled in parallel" in {
      val failing = Unmarshaller[String, Int](s =>
        aField(s).flatMap(n =>
          if (n == 3) IO.raiseError(new IllegalStateException("no 3")) else IO.pure(n)))
      val support = EntityStreamingSupport.json().withParallelMarshalling(parallelism = 4, unordered = false)
      Post("/", (1 to 8).map(n => s"""{"a":$n}""").mkString("[", ",", "]")) ~> addHeader(json) ~>
      Route.seal(sumRouteUsing(failing, support))                           ~> check { status shouldEqual Status.InternalServerError }
    }
    "treat parallelism 1 as sequential even when unordered" in {
      val concurrency = new Concurrency
      implicit val slow: EntityEncoder[IO, Int] = slowEncoder(concurrency)
      implicit val support: EntityStreamingSupport =
        EntityStreamingSupport.json().withParallelMarshalling(parallelism = 1, unordered = true)
      Get() ~> complete(Stream.emits(1 to 4).covary[IO]) ~> check {
        responseAs[String] shouldEqual "[1,2,3,4]"
        concurrency.max shouldEqual 1
      }
    }
    "keep its other settings when reconfigured" in {
      val base = EntityStreamingSupport.json()
      base.parallelism shouldEqual 1
      base.unordered shouldEqual false
      val reconfigured = base.withParallelMarshalling(4, unordered = true)
        .withFramingRenderer(frames => frames.flatMap(Stream.chunk))
        .withContentType(`Content-Type`(MediaType.text.plain))
        .withSupported(MediaType.text.plain)
      reconfigured.parallelism shouldEqual 4
      reconfigured.unordered shouldEqual true
      reconfigured.contentType shouldEqual `Content-Type`(MediaType.text.plain)
      reconfigured.supported shouldEqual MediaType.text.plain
      an[IllegalArgumentException] should be thrownBy base.withParallelMarshalling(0, unordered = false)
    }
  }

  "the JSON framing decoder" should {
    val support = EntityStreamingSupport.json()

    "frame the same objects however the body is chunked" in {
      List(
        """[{"a":1},{"a":2}]""",
        "{\"a\":1}\n{\"a\":2}\n",
        """[{"a":1,"s":"}{","n":{"x":[1,2]}}, {"a":3}]""",
        """[{"a":1,"s":"\"}"},{"a":2,"s":"\\"},{"a":3,"s":"\\\""}]""",
        "[\t{\"a\":1}\r\n,\n{\"a\":2}\t]\n",
        """[{"a":"ż ü €"},{"a":"🙂"}]""").foreach(frameIndependentOfChunking(support, _))
    }
    "frame escaped quotes and backslashes inside strings" in {
      framesUnchunked(support, """[{"a":1,"s":"\"}"},{"a":2,"s":"\\"},{"a":3,"s":"\\\""}]""") shouldEqual
      List("""{"a":1,"s":"\"}"}""", """{"a":2,"s":"\\"}""", """{"a":3,"s":"\\\""}""")
    }
    "frame nothing for an empty, whitespace-only or empty-array body" in {
      List("", " \r\n\t", "[]", "[]\n", "[ ]").foreach { body =>
        withClue(s"body '$body': ") { framesUnchunked(support, body) shouldEqual Nil }
      }
    }
    "accept an object of exactly the maximum length and fail one byte over" in {
      val small = EntityStreamingSupport.json(maxObjectLength = 9)
      frames(small, """[{"a":123}]""", Int.MaxValue) shouldEqual Right(List("""{"a":123}"""))
      frames(small, """[{"a":1234}]""", Int.MaxValue) match {
        case Left(IllegalRequestException(Status.BadRequest, message)) => message should include("9 bytes")
        case other                                                     => fail(s"unexpected $other")
      }
    }
    "fail with 400 as soon as the limit is exceeded, before the object ends" in {
      val small = EntityStreamingSupport.json(maxObjectLength = 4)
      val readPastTheLimit = new AtomicBoolean(false)
      val body = Stream.emits("""[{"a":""".getBytes("UTF-8")).covary[IO] ++
        Stream.exec(IO(readPastTheLimit.set(true))) ++ Stream.emits("""1}]""".getBytes("UTF-8"))
      body.through(small.framingDecoder).compile.drain.attempt.unsafeRunSync() match {
        case Left(IllegalRequestException(Status.BadRequest, _)) => readPastTheLimit.get shouldEqual false
        case other                                               => fail(s"unexpected $other")
      }
    }
    "report the position of the offending byte" in {
      frames(support, """[{"a":1}, x]""", Int.MaxValue) match {
        case Left(IllegalRequestException(Status.BadRequest, message)) => message should include("position 10")
        case other                                                     => fail(s"unexpected $other")
      }
      frames(support, """[{"a":1}, x]""", 3) match {
        case Left(IllegalRequestException(Status.BadRequest, message)) => message should include("position 10")
        case other                                                     => fail(s"unexpected $other")
      }
    }
    "emit the objects before an error" in {
      Stream.emits("""[{"a":1},{"a":2}, x]""".getBytes("UTF-8")).covary[IO].through(support.framingDecoder)
        .map(frame => new String(frame.toArray, "UTF-8")).attempt.compile.toList.unsafeRunSync() match {
        case List(Right(first), Right(second), Left(_: IllegalRequestException)) =>
          (first, second) shouldEqual ("""{"a":1}""", """{"a":2}""")
        case other => fail(s"unexpected $other")
      }
    }
  }

  "asSourceOf with CSV streaming support" should {
    implicit val support: EntityStreamingSupport = EntityStreamingSupport.csv()

    "decode one element per line" in {
      Post("/", "a,b\r\nc,d\n") ~> addHeader(csv) ~> entity(asSourceOf[String]) { lines =>
        onSuccess(lines.compile.toList) { list => complete(list.mkString("|")) }
      } ~> check { responseAs[String] shouldEqual "a,b|c,d" }
    }
    "fail a line over the limit before the line ends" in {
      val small = EntityStreamingSupport.csv(maxLineLength = 4)
      val readPastTheLimit = new AtomicBoolean(false)
      val body: Stream[IO, Byte] =
        Stream.emits("abcdefgh".getBytes("UTF-8")).covary[IO] ++
        Stream.exec(IO(readPastTheLimit.set(true))) ++
        Stream.emits("ijkl\n".getBytes("UTF-8"))
      Post("/", body) ~> addHeader(csv) ~> Route.seal(entity(asSourceOf[String](small)) { lines =>
        onSuccess(lines.compile.toList) { list => complete(list.mkString("|")) }
      }) ~> check {
        status shouldEqual Status.BadRequest
        readPastTheLimit.get shouldEqual false
      }
    }
    "accept a CRLF-terminated line of exactly the maximum length" in {
      val small = EntityStreamingSupport.csv(maxLineLength = 4)
      Post("/", "abcd\r\nefgh\r\n") ~> addHeader(csv) ~> entity(asSourceOf[String](small)) { lines =>
        onSuccess(lines.compile.toList) { list => complete(list.mkString("|")) }
      } ~> check { responseAs[String] shouldEqual "abcd|efgh" }
    }
    "render a stream as lines" in {
      Get() ~> complete(Stream("a,b", "c,d").covary[IO]) ~> check {
        mediaType shouldEqual MediaType.text.csv
        responseAs[String] shouldEqual "a,b\nc,d\n"
      }
    }
  }

  "the line framing decoder" should {
    val support = EntityStreamingSupport.csv()

    "frame the same lines however the body is chunked" in {
      List("a,b\r\nc,d\n", "a\nb", "a\n\n\r\nb\n", "a\rb\n", "ż,ü\r\n€\n", "\r\n\r\n", "abcd\r\n")
        .foreach(frameIndependentOfChunking(support, _))
    }
    "emit a final line that has no trailing newline" in {
      framesUnchunked(support, "a\nb") shouldEqual List("a", "b")
      framesUnchunked(support, "a\nb\r") shouldEqual List("a", "b")
    }
    "skip blank lines" in {
      framesUnchunked(support, "a\n\n\r\nb\n") shouldEqual List("a", "b")
      framesUnchunked(support, "") shouldEqual Nil
    }
    "keep a carriage return that is not followed by a newline" in {
      framesUnchunked(support, "a\rb\n") shouldEqual List("a\rb")
    }
    "fail a CRLF line one byte over the limit" in {
      val small = EntityStreamingSupport.csv(maxLineLength = 4)
      frames(small, "abcde\r\n", Int.MaxValue) match {
        case Left(IllegalRequestException(Status.BadRequest, message)) => message should include("4 bytes")
        case other                                                     => fail(s"unexpected $other")
      }
    }
  }
}
