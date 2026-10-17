package pl.iterators.stir.server.directives

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import fs2.Stream
import org.http4s.{ Headers, MediaRange, MediaType, Request, Status, UrlForm }
import org.http4s.headers.`Content-Type`
import org.http4s.multipart.{ Multipart, Multiparts, Part }

import scala.concurrent.duration._
import pl.iterators.stir.unmarshalling.Unmarshaller._
import pl.iterators.stir.server._
import pl.iterators.stir.unmarshalling.Unmarshaller

class FormFieldDirectivesSpec extends RoutingSpec {
  override implicit def runtime: IORuntime = IORuntime.global
//  implicit val nodeSeqUnmarshaller: FromEntityUnmarshaller[NodeSeq] =
//    ScalaXmlSupport.nodeSeqUnmarshaller(`text/xml`, `text/html`, `text/plain`)

  val nodeSeq: xml.NodeSeq = <b>yes</b>
  implicit val xmlFromStringUnmarshaller: Unmarshaller[String, xml.NodeSeq] =
    Unmarshaller.strict[String, xml.NodeSeq](xml.XML.loadString)
  val urlEncodedForm = UrlForm("firstName" -> "Mike", "age" -> "42")
  val urlEncodedFormWithVip =
    UrlForm("firstName" -> "Mike", "age" -> "42", "VIP" -> "true", "super" -> "<b>no</b>")
//  val multipartForm = Multipart.FormData {
//    Map(
//      "firstName" -> HttpEntity("Mike"),
//      "age" -> HttpEntity(ContentTypes.`text/xml(UTF-8)`, "<int>42</int>"),
//      "VIPBoolean" -> HttpEntity("true"))
//  }
//  val multipartFormWithTextHtml = Multipart.FormData {
//    Map(
//      "firstName" -> HttpEntity("Mike"),
//      "age" -> HttpEntity(ContentTypes.`text/xml(UTF-8)`, "<int>42</int>"),
//      "VIP" -> HttpEntity(ContentTypes.`text/html(UTF-8)`, "<b>yes</b>"),
//      "super" -> HttpEntity("no"))
//  }
//  val multipartFormWithFile = Multipart.FormData(
//    Multipart.FormData.BodyPart.Strict("file", HttpEntity(ContentTypes.`text/xml(UTF-8)`, "<int>42</int>"),
//      Map("filename" -> "age.xml")))

  "The 'formFields' extraction directive" should {
    "properly extract the value of www-urlencoded form fields" in {
      Post("/", urlEncodedForm) ~> {
        formFields("firstName", "age".as[Int], "sex".optional, "VIP".withDefault(false)) { (firstName, age, sex, vip) =>
          complete(firstName + age + sex + vip)
        }
      } ~> check { responseAs[String] shouldEqual "Mike42Nonefalse" }
    }
    "properly extract the value of www-urlencoded form fields when an explicit unmarshaller is given" in {
      Post("/", urlEncodedForm) ~> {
        formFields("firstName", "age".as(HexInt), "sex".optional, "VIP".withDefault(false)) {
          (firstName, age, sex, vip) =>
            complete(firstName + age + sex + vip)
        }
      } ~> check { responseAs[String] shouldEqual "Mike66Nonefalse" }
    }
//    "properly extract the value of multipart form fields" in {
//      Post("/", multipartForm) ~> {
//        formFields("firstName", "age", "sex".optional, "VIP".withDefault(nodeSeq)) { (firstName, age, sex, vip) =>
//          complete(firstName + age + sex + vip)
//        }
//      } ~> check { responseAs[String] shouldEqual "Mike<int>42</int>None<b>yes</b>" }
//    }
//    "extract StrictForm.FileData from a multipart part" in {
//      Post("/", multipartFormWithFile) ~> {
//        formFields("file".as[StrictForm.FileData]) {
//          case StrictForm.FileData(name, HttpEntity.Strict(ct, data)) =>
//            complete(s"type ${ct.mediaType} length ${data.length} filename ${name.get}")
//        }
//      } ~> check { responseAs[String] shouldEqual "type text/xml length 13 filename age.xml" }
//    }
    "reject the request with a MissingFormFieldRejection if a required form field is missing" in {
      Post("/", urlEncodedForm) ~> {
        formFields("firstName", "age", "sex", "VIP".withDefault(false)) { (firstName, age, sex, vip) =>
          complete(firstName + age + sex + vip)
        }
      } ~> check { rejection shouldEqual MissingFormFieldRejection("sex") }
    }
//    "properly extract the value if only a urlencoded deserializer is available for a multipart field that comes without a" +
//      "Content-Type (or text/plain)" in {
//      Post("/", multipartForm) ~> {
//        formFields("firstName", "age", "sex".optional, "VIPBoolean".withDefault(false)) { (firstName, age, sex, vip) =>
//          complete(firstName + age + sex + vip)
//        }
//      } ~> check {
//        responseAs[String] shouldEqual "Mike<int>42</int>Nonetrue"
//      }
//    }
//    "work even if only a FromStringUnmarshaller is available for a multipart field with custom Content-Type" in {
//      Post("/", multipartFormWithTextHtml) ~> {
//        formFields("firstName", "age", "super".withDefault(false)) { (firstName, age, vip) =>
//          complete(firstName + age + vip)
//        }
//      } ~> check {
//        responseAs[String] shouldEqual "Mike<int>42</int>false"
//      }
//    }
    "work even if only a FromEntityUnmarshaller is available for a www-urlencoded field" in {
      Post("/", urlEncodedFormWithVip) ~> {
        formFields("firstName", "age", "sex".optional, "super".withDefault(nodeSeq)) { (firstName, age, sex, vip) =>
          complete(firstName + age + sex + vip)
        }
      } ~> check {
        responseAs[String] shouldEqual "Mike42None<b>no</b>"
      }
    }
//    "work even when the entity is not strict" in {
//      val request: HttpRequest =
//        Post("/", urlEncodedForm)
//          // transformEntityDataBytes will convert the entity to chunked
//          .transformEntityDataBytes(AllowMaterializationOnlyOnce())
//
//      request.entity.contentType shouldEqual ContentTypes.`application/x-www-form-urlencoded`
//      request.entity.isStrict should be(false)
//
//      request ~> {
//        formFields("firstName", "age".as[Int], "sex".optional, "VIP".withDefault(false)) { (firstName, age, sex, vip) =>
//          complete(firstName + age + sex + vip)
//        }
//      } ~> check { responseAs[String] shouldEqual "Mike42Nonefalse" }
//    }

//    "work even with nested directives when the entity is not strict" in {
//      val request: HttpRequest =
//        Post("/", urlEncodedForm)
//          // transformEntityDataBytes will convert the entity to chunked
//          .transformEntityDataBytes(AllowMaterializationOnlyOnce())
//
//      request.entity.contentType shouldEqual ContentTypes.`application/x-www-form-urlencoded`
//      request.entity.isStrict should be(false)
//
//      request ~> {
//        formFields("firstName") { firstName =>
//          formFields("age".as[Int], "sex".optional) { (age, sex) =>
//            formFields("VIP".withDefault(false)) { vip =>
//              complete(firstName + age + sex + vip)
//            }
//          }
//        }
//      } ~> check { responseAs[String] shouldEqual "Mike42Nonefalse" }
//    }

//    "work even for alternatives when the entity is not strict" in pendingUntilFixed {
//      val request: HttpRequest =
//        Post("/", urlEncodedForm)
//          // transformEntityDataBytes will convert the entity to chunked
//          .transformEntityDataBytes(AllowMaterializationOnlyOnce())
//
//      request.entity.contentType shouldEqual ContentTypes.`application/x-www-form-urlencoded`
//      request.entity.isStrict should be(false)
//
//      request ~> {
//        concat(
//          formFields("firstName", "age".as[Int]) { (firstName, age) =>
//            reject
//          },
//          formFields("firstName", "age".as[Int]) { (firstName, age) =>
//            formFields("sex".optional) { sex =>
//              formFields("VIP".withDefault(false)) { vip =>
//                complete(firstName + age + sex + vip)
//              }
//            }
//          })
//      } ~> check { responseAs[String] shouldEqual "Mike42Nonefalse" }
//    }
  }
  "The 'formField' requirement directive" should {
    "block requests that do not contain the required formField" in {
      Post("/", urlEncodedForm) ~> {
        formField("name".requiredValue("Mr. Mike")) { _ => completeOk }
      } ~> check { handled shouldEqual false }
    }
    "block requests that contain the required parameter but with an unmatching value" in {
      Post("/", urlEncodedForm) ~> {
        formField("firstName".requiredValue("Pete")) { _ => completeOk }
      } ~> check { handled shouldEqual false }
    }
    "let requests pass that contain the required parameter with its required value" in {
      Post("/", urlEncodedForm) ~> {
        formField("firstName".requiredValue("Mike")) { _ => completeOk }
      } ~> check { response.status shouldEqual Status.Ok }
    }
  }

  "The 'formField' requirement with explicit unmarshaller directive" should {
    "block requests that do not contain the required formField" in {
      Post("/", urlEncodedForm) ~> {
        formField("oldAge".as(HexInt).requiredValue(78)) { _ => completeOk }
      } ~> check { handled shouldEqual false }
    }
    "block requests that contain the required parameter but with an unmatching value" in {
      Post("/", urlEncodedForm) ~> {
        formField("age".as(HexInt).requiredValue(78)) { _ => completeOk }
      } ~> check { handled shouldEqual false }
    }
    "let requests pass that contain the required parameter with its required value" in {
      Post("/", urlEncodedForm) ~> {
        formField("age".as(HexInt).requiredValue(66) /* hex! */ ) { _ => completeOk }
      } ~> check { response.status shouldEqual Status.Ok }
    }
  }

  "The 'formField' repeated directive" should {
    "extract an empty Iterable when the parameter is absent" in {
      Post("/", UrlForm("age" -> "42")) ~> {
        formField("hobby".repeated) { echoComplete }
      } ~> check { responseAs[String] shouldEqual "List()" }
    }
    "extract all occurrences into an Iterable when parameter is present" in {
      Post("/", UrlForm("age" -> "42", "hobby" -> "cooking", "hobby" -> "reading")) ~> {
        formField("hobby".repeated) { echoComplete }
      } ~> check { responseAs[String] shouldEqual "List(cooking, reading)" }
    }
    "extract as Iterable[Int]" in {
      Post("/", UrlForm("age" -> "42", "number" -> "3", "number" -> "5")) ~> {
        formField("number".as[Int].repeated) { echoComplete }
      } ~> check { responseAs[String] shouldEqual "List(3, 5)" }
    }
    "extract as Iterable[Int] with an explicit deserializer" in {
      Post("/", UrlForm("age" -> "42", "number" -> "3", "number" -> "A")) ~> {
        formField("number".as(HexInt).repeated) { echoComplete }
      } ~> check { responseAs[String] shouldEqual "List(3, 10)" }
    }
  }

  "The 'formFieldMap' directive" should {
    "extract fields with different keys" in {
      var res: Map[String, String] = null

      Post("/", UrlForm("age" -> "42", "numberA" -> "3", "numberB" -> "5")) ~> {
        formFieldMap { map =>
          res = map
          completeOk
        }
      } ~> check {
        res shouldEqual Map("age" -> "42", "numberA" -> "3", "numberB" -> "5")
      }
    }
//    "not show bad performance characteristics when field names' hashCodes collide" in {
//      val numKeys = 10000
//      val value = "null"
//
//      val regularKeys = Iterator.from(1).map(i => s"key_$i").take(numKeys)
//      val collidingKeys = HashCodeCollider.zeroHashCodeIterator().take(numKeys)
//
//      def createFormData(keys: Iterator[String]): FormData = {
//        val tuples = keys.map((_, value)).toSeq
//        val query = tuples.foldLeft(Uri.Query.newBuilder)((acc, pair) => acc += pair)
//        FormData(query.result())
//      }
//
//      val regularFormData = createFormData(regularKeys)
//      val collidingDormData = createFormData(collidingKeys)
//
//      def regular(): Unit =
//        Post("/", regularFormData) ~> {
//          formFieldMap { _ => complete(StatusCodes.OK) }
//        } ~> check {}
//
//      def colliding(): Unit =
//        Post("/", collidingDormData) ~> {
//          formFieldMap { _ => complete(StatusCodes.OK) }
//        }
//
//      BenchUtils.nanoRace(colliding(), regular()) should be < 3.0 // speed must be in same order of magnitude
//    }
  }

  "The 'formFieldSeq' directive" should {
    val urlEncoded = `Content-Type`(MediaType.application.`x-www-form-urlencoded`)
    "extract all fields in the order of the body" in {
      Post("/", "number=3&age=42&number=5") ~> addHeader(urlEncoded) ~> {
        formFieldSeq { echoComplete }
      } ~> check { responseAs[String] shouldEqual "Vector((number,3), (age,42), (number,5))" }
    }
    "take only '&' as a separator and accept unencoded characters literally" in {
      Post("/", "a=1;b=2&c=<x y>") ~> addHeader(urlEncoded) ~> {
        formFieldSeq { echoComplete }
      } ~> check { responseAs[String] shouldEqual "Vector((a,1;b=2), (c,<x y>))" }
    }
    "decode plus and percent encoded fields" in {
      Post("/", "name=John+Doe&city=%C5%81%C3%B3d%C5%BA&empty=&flag") ~> addHeader(urlEncoded) ~> {
        formFieldSeq { echoComplete }
      } ~> check { responseAs[String] shouldEqual "Vector((name,John Doe), (city,Łódź), (empty,), (flag,))" }
    }
    "extract the text parts of a multipart form in order" in {
      multipartForm("number" -> "3", "age" -> "42", "number" -> "5") ~> {
        formFieldSeq { echoComplete }
      } ~> check { responseAs[String] shouldEqual "Vector((number,3), (age,42), (number,5))" }
    }
    "produce empty Seq when FormData is empty" in {
      Post("/", UrlForm()) ~> {
        formFieldSeq { echoComplete }
      } ~> check { responseAs[String] shouldEqual "Vector()" }
    }
//    "reject with MalformedRequestContentRejection if request entity fails" in {
//      val failedSource = Source.failed(new IllegalStateException("Form was stapled wrongly"))
//      Post("/", HttpEntity(`application/x-www-form-urlencoded`, failedSource)) ~>
//        formFieldSeq { echoComplete } ~>
//        check {
//          rejection shouldBe a[MalformedRequestContentRejection]
//        }
//    }
  }

  "The 'formFieldMultiMap' directive" should {
    "extract fields with different keys (with duplicates)" in {
      var res: Map[String, List[String]] = null

      Post("/", UrlForm("age" -> "42", "number" -> "3", "number" -> "5")) ~> {
        formFieldMultiMap { m =>
          res = m
          completeOk
        }
      } ~> check {
        res shouldEqual Map("age" -> List("42"), "number" -> List("5", "3"))
      }
    }
//    "not show bad performance characteristics when field names' hashCodes collide" in {
//      val numKeys = 10000
//      val value = "null"
//
//      val regularKeys = Iterator.from(1).map(i => s"key_$i").take(numKeys)
//      val collidingKeys = HashCodeCollider.zeroHashCodeIterator().take(numKeys)
//
//      def createFormData(keys: Iterator[String]): FormData = {
//        val tuples = keys.map((_, value)).toSeq
//        val query = tuples.foldLeft(Uri.Query.newBuilder)((acc, pair) => acc += pair)
//        FormData(query.result())
//      }
//
//      val regularFormData = createFormData(regularKeys)
//      val collidingDormData = createFormData(collidingKeys)
//
//      def regular(): Unit =
//        Post("/", regularFormData) ~> {
//          formFieldMultiMap { _ => complete(StatusCodes.OK) }
//        } ~> check {}
//
//      def colliding(): Unit =
//        Post("/", collidingDormData) ~> {
//          formFieldMultiMap { _ => complete(StatusCodes.OK) }
//        }
//
//      BenchUtils.nanoRace(colliding(), regular()) should be < 3.0 // speed must be in same order of magnitude
//    }
  }

  "The 'formFields' extraction directive over a real server" should {
    "extract several fields from a body that is streamed from the socket" in {
      Post("/", urlEncodedForm) ~!> {
        formFields("firstName", "age".as[Int]) { (firstName, age) => complete(firstName + age) }
      } ~> check { responseAs[String] shouldEqual "Mike42" }
    }
    "extract fields from nested formFields directives" in {
      Post("/", urlEncodedForm) ~!> {
        formFields("firstName") { firstName =>
          formFields("age".as[Int]) { age => complete(firstName + age) }
        }
      } ~> check { responseAs[String] shouldEqual "Mike42" }
    }
    "let formFieldMap and a field directive read the same streamed body" in {
      Post("/", urlEncodedForm) ~> removeHeader("Content-Length") ~!> {
        formFieldMap { fields => formFields("age".as[Int]) { age => complete(fields.size.toString + age) } }
      } ~> check { responseAs[String] shouldEqual "242" }
    }
    "wait more than a second for a slow form body, as Pekko does" in {
      val slowBody: Stream[IO, Byte] =
        Stream.sleep_[IO](2.seconds) ++ Stream.emits("firstName=Mike&age=42".getBytes("UTF-8"))
      Post("/", slowBody) ~> addHeader(`Content-Type`(MediaType.application.`x-www-form-urlencoded`)) ~!> {
        formFields("firstName", "age".as[Int]) { (firstName, age) => complete(firstName + age) }
      } ~> check { responseAs[String] shouldEqual "Mike42" }
    }
  }

  "The 'formFields' directive with a non-form body" should {
    "reject with UnsupportedRequestContentTypeRejection" in {
      Post("/", """{"firstName":"Mike"}""") ~> addHeader(`Content-Type`(MediaType.application.json)) ~> {
        formFields("firstName") { echoComplete }
      } ~> check {
        rejection shouldEqual UnsupportedRequestContentTypeRejection(
          Set(MediaType.application.`x-www-form-urlencoded`, MediaRange.`multipart/*`),
          Some(MediaType.application.json))
      }
    }
    "decode a form body without Content-Type" in {
      Post("/", urlEncodedForm) ~> removeHeader("Content-Type") ~> {
        formFields("firstName") { echoComplete }
      } ~> check { responseAs[String] shouldEqual "Mike" }
    }
  }

  def multipartForm(fields: (String, String)*): Request[IO] = {
    val parts = fields.map { case (name, value) => Part.formData[IO](name, value) } :+
      Part.fileData[IO]("file", "age.xml", Stream.emits("<int>42</int>".getBytes("UTF-8")).covary[IO],
        `Content-Type`(MediaType.text.xml))
    val multipart: Multipart[IO] = Multiparts.forSync[IO].flatMap(_.multipart(parts.toVector)).unsafeRunSync()
    Post("/", multipart).transformHeaders(_ ++ multipart.headers)
  }

  "The 'formFields' extraction directive with a multipart form" should {
    "properly extract the value of multipart form fields" in {
      multipartForm("firstName" -> "Mike", "age" -> "42") ~> {
        formFields("firstName", "age".as[Int], "sex".optional, "VIP".withDefault(false)) { (firstName, age, sex, vip) =>
          complete(firstName + age + sex + vip)
        }
      } ~> check { responseAs[String] shouldEqual "Mike42Nonefalse" }
    }
    "reject with MissingFormFieldRejection when a required field is only present as a file part" in {
      multipartForm("firstName" -> "Mike") ~> {
        formFields("file") { echoComplete }
      } ~> check { rejection shouldEqual MissingFormFieldRejection("file") }
    }
    "extract all text fields with formFieldMap" in {
      multipartForm("firstName" -> "Mike", "age" -> "42") ~> {
        formFieldMap { echoComplete }
      } ~> check { responseAs[String] shouldEqual "TreeMap(age -> 42, firstName -> Mike)" }
    }
    "ignore a part without a name" in {
      val nameless = Part[IO](Headers(`Content-Type`(MediaType.text.plain)),
        Stream.emits("x".getBytes("UTF-8")).covary[IO])
      val multipart: Multipart[IO] =
        Multiparts.forSync[IO].flatMap(_.multipart(Vector(nameless,
          Part.formData[IO]("firstName", "Mike")))).unsafeRunSync()
      Post("/", multipart).transformHeaders(_ ++ multipart.headers) ~> formFieldMap { echoComplete } ~> check {
        responseAs[String] shouldEqual "TreeMap(firstName -> Mike)"
      }
    }
    "extract repeated multipart fields" in {
      multipartForm("hobby" -> "cooking", "hobby" -> "reading") ~> {
        formField("hobby".repeated) { echoComplete }
      } ~> check { responseAs[String] shouldEqual "List(cooking, reading)" }
    }
    "extract several multipart fields over a real server" in {
      multipartForm("firstName" -> "Mike", "age" -> "42") ~!> {
        formFields("firstName", "age".as[Int]) { (firstName, age) => complete(firstName + age) }
      } ~> check { responseAs[String] shouldEqual "Mike42" }
    }
  }
}
