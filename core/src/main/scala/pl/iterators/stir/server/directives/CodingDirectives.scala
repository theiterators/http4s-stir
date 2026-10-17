package pl.iterators.stir.server.directives

import cats.effect.IO
import fs2.Stream
import fs2.compression.{ Compression, DeflateParams, InflateParams, ZLibParams }
import fs2.io.compression.fs2ioCompressionForIO
import org.http4s.{ ContentCoding, EntityBody, QValue, Request, Response, Status, TransferCoding }
import org.http4s.headers.{ `Accept-Encoding`, `Content-Encoding`, `Content-Length`, `Transfer-Encoding` }
import pl.iterators.stir.server._

import scala.util.control.NonFatal

/**
 * @groupname coding Coding directives
 * @groupprio coding 50
 */
trait CodingDirectives {
  import BasicDirectives._
  import CodingDirectives._
  import RouteDirectives._

  /**
   * Rejects the request with an [[UnacceptedResponseEncodingRejection]] if the given response encoding is not
   * accepted by the client.
   *
   * @group coding
   */
  def responseEncodingAccepted(encoding: ContentCoding): Directive0 =
    extractRequest.flatMap { request =>
      if (qValueFor(request, encoding).isAcceptable) pass
      else reject(UnacceptedResponseEncodingRejection(Set(encoding)))
    }

  /**
   * Encodes the response with the encoding that is requested by the client via the `Accept-Encoding` header,
   * choosing between identity, gzip and deflate by the rules of http://tools.ietf.org/html/rfc7231#section-5.3.4.
   * If the header is missing, or none of gzip and deflate is acceptable, no encoding is used. Responses that are
   * already encoded, have no entity, or have a non-compressible media type are left alone.
   *
   * @group coding
   */
  def encodeResponse: Directive0 = _encodeResponse(DefaultEncodeResponseEncoders)

  /**
   * Encodes the response with the first of the given encoders the client accepts (all of them when the
   * `Accept-Encoding` header is missing; the first one wins ties). Rejects with an
   * [[UnacceptedResponseEncodingRejection]] when the client accepts none of them.
   *
   * @group coding
   */
  def encodeResponseWith(first: ContentCoding, more: ContentCoding*): Directive0 = _encodeResponse(first +: more)

  /**
   * Decodes the incoming request with the given coding (gzip, deflate or identity). Requests encoded differently
   * are rejected with an [[UnsupportedRequestEncodingRejection]]; requests known to carry no body (`Content-Length:
   * 0`, or HTTP/1.x with neither a length nor chunking) pass through untouched. The decoded request carries `Transfer-Encoding: chunked` instead of its `Content-Length`,
   * since the decoded size is unknown. A corrupt compressed body fails the entity with a decode failure, which
   * `entity` turns into a [[MalformedRequestContentRejection]] (400).
   *
   * @group coding
   */
  def decodeRequestWith(decoder: ContentCoding): Directive0 = {
    val applyDecoder: Directive0 =
      if (isIdentity(decoder)) pass
      else mapRequest { request =>
        request.withBodyStream(decodeBody(decoder, request.body))
          .removeHeader[`Content-Encoding`]
          .removeHeader[`Content-Length`]
          .putHeaders(`Transfer-Encoding`(TransferCoding.chunked))
      } & MiscDirectives.withSizeLimit(DefaultDecodeMaxBytes) // nested withSizeLimit / withoutSizeLimit override it

    knownEmptyRequest |
    (requestEncodedWith(decoder) & applyDecoder &
    cancelRejections(classOf[UnsupportedRequestEncodingRejection]))
  }

  /**
   * Rejects the request with an [[UnsupportedRequestEncodingRejection]] if its `Content-Encoding` (identity when
   * absent) does not match the given one.
   *
   * @group coding
   */
  def requestEncodedWith(encoding: ContentCoding): Directive0 =
    extract(ctx => requestEncoding(ctx.request)).flatMap { actual =>
      if (sameCoding(actual, encoding)) pass else reject(UnsupportedRequestEncodingRejection(encoding))
    }

  /**
   * Decodes the incoming request if it is encoded with one of the given codings (gzip, deflate and identity when
   * none are given). A request encoded differently is rejected with one
   * [[UnsupportedRequestEncodingRejection]] per coding.
   *
   * @group coding
   */
  def decodeRequestWith(decoders: ContentCoding*): Directive0 =
    theseOrDefault(decoders).map(decodeRequestWith(_: ContentCoding)).reduce(_ | _)

  /**
   * Decompresses the incoming request if it is `gzip` or `deflate` compressed; uncompressed requests pass through.
   *
   * @group coding
   */
  def decodeRequest: Directive0 = decodeRequestWith(DefaultCoders: _*)

  /**
   * Inspects the response and adds a `Content-Encoding: gzip` header if it was served by `getFromFile`,
   * `getFromDirectory`, `getFromResource` or `getFromResourceDirectory` from a `.svgz` file (the only precompressed
   * media type Pekko knows) and no `Content-Encoding` is present yet.
   *
   * @group coding
   */
  def withPrecompressedMediaTypeSupport: Directive0 =
    mapResponse { response =>
      if (isPrecompressed(response) && response.headers.get[`Content-Encoding`].isEmpty)
        response.putHeaders(`Content-Encoding`(ContentCoding.gzip))
      else response
    }
}

object CodingDirectives extends CodingDirectives {
  import BasicDirectives._
  import RouteDirectives._

  /** The codings `decodeRequest` understands, in order. */
  /** Pekko's `decode-max-size`: the most bytes a decoded request body may expand to (8 MiB). */
  val DefaultDecodeMaxBytes: Long = 8L * 1024 * 1024

  val DefaultCoders: Seq[ContentCoding] = Seq(ContentCoding.gzip, ContentCoding.deflate, ContentCoding.identity)

  // same entries as DefaultCoders but in different order
  private val DefaultEncodeResponseEncoders =
    Seq(ContentCoding.identity, ContentCoding.gzip, ContentCoding.deflate)

  private val compression: Compression[IO] = fs2ioCompressionForIO

  private val GzipParams = DeflateParams(header = ZLibParams.Header.GZIP)
  private val DeflateZlibParams = DeflateParams(header = ZLibParams.Header.ZLIB)
  private val InflateZlibParams = InflateParams(header = ZLibParams.Header.ZLIB)

  private def theseOrDefault(these: Seq[ContentCoding]): Seq[ContentCoding] =
    if (these.isEmpty) DefaultCoders else these

  private def sameCoding(a: ContentCoding, b: ContentCoding): Boolean = a.coding.equalsIgnoreCase(b.coding)
  private def isIdentity(coding: ContentCoding): Boolean = sameCoding(coding, ContentCoding.identity)
  private def isGzip(coding: ContentCoding): Boolean = sameCoding(coding, ContentCoding.gzip)

  /** A response served from a `.svgz` file is already gzipped; never compress it again. */
  private def isPrecompressed(response: Response[IO]): Boolean =
    response.attributes.lookup(FileAndResourceDirectives.ServedNameKey).exists(_.toLowerCase.endsWith(".svgz"))

  /**
   * Passes requests known to carry no body: `Content-Length: 0`, or an HTTP/1.x request with neither a length nor
   * chunking (RFC 7230 3.3.3). Unlike `requestEntityEmpty`, a length-less HTTP/2 body is not assumed empty here,
   * because skipping the decoder would hand compressed bytes to the route.
   */
  private val knownEmptyRequest: Directive0 =
    extract { ctx =>
      val request = ctx.request
      request.contentLength.contains(0L) ||
      (request.httpVersion.major < 2 && request.contentLength.isEmpty && !request.isChunked)
    }.flatMap(if (_) pass else reject)

  private def requestEncoding(request: Request[IO]): ContentCoding =
    request.headers.get[`Content-Encoding`].map(_.contentCoding).getOrElse(ContentCoding.identity)

  /** Without an `Accept-Encoding` header every coding is acceptable; otherwise the header's q-value (`*` included). */
  private def qValueFor(request: Request[IO], coding: ContentCoding): QValue =
    request.headers.get[`Accept-Encoding`].fold(QValue.One)(_.qValue(coding))

  private def hasMatchingFor(request: Request[IO], coding: ContentCoding): Boolean =
    request.headers.get[`Accept-Encoding`].exists(_.values.exists(v => sameCoding(v, coding) || v.coding == "*"))

  private def _encodeResponse(encoders: Seq[ContentCoding]): Directive0 =
    extractRequest.flatMap { request =>
      val acceptable = encoders.map(encoder => encoder -> qValueFor(request, encoder)).filter(_._2.isAcceptable)
      acceptable.maxByOption(_._2.thousandths) match {
        case Some((encoder, _)) => mapResponse(encodeResponse(encoder))
        case None               =>
          // identity is acceptable unless the client explicitly refused it (RFC 7231 5.3.4)
          if (encoders.exists(isIdentity) && !hasMatchingFor(request, ContentCoding.identity)) pass
          else reject(UnacceptedResponseEncodingRejection(encoders.toSet))
      }
    }

  private def encodeResponse(coding: ContentCoding)(response: Response[IO]): Response[IO] =
    if (isIdentity(coding) || !isCompressible(response)) response
    else
      response.withBodyStream(encodeBody(coding, response.body))
        .removeHeader[`Content-Length`]
        .putHeaders(`Content-Encoding`(coding))

  private def isCompressible(response: Response[IO]): Boolean =
    response.status.isEntityAllowed &&
    !isPrecompressed(response) &&
    !MiscDirectives.isKnownEmpty(response) &&
    response.headers.get[`Content-Encoding`].isEmpty &&
    response.contentType.forall(_.mediaType.compressible)

  private def encodeBody(coding: ContentCoding, body: EntityBody[IO]): EntityBody[IO] =
    if (isGzip(coding))
      body.through(compression.gzip(fileName = None, modificationTime = None, comment = None,
        deflateParams = GzipParams))
    else body.through(compression.deflate(DeflateZlibParams))

  private def decodeBody(coding: ContentCoding, body: EntityBody[IO]): EntityBody[IO] = {
    val decoded =
      if (isGzip(coding)) body.through(compression.gunzip()).flatMap(_.content)
      else body.through(compression.inflate(InflateZlibParams))
    decoded.handleErrorWith {
      case e: EntityStreamSizeException => Stream.raiseError[IO](e)
      case NonFatal(_)                  =>
        Stream.raiseError[IO](IllegalRequestException(Status.BadRequest, "The request's encoding is corrupt"))
    }
  }
}
