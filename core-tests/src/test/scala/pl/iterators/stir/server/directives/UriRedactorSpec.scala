package pl.iterators.stir.server.directives

import org.http4s.Uri
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class UriRedactorSpec extends AnyWordSpec with Matchers {
  def uri(s: String): Uri = Uri.unsafeFromString(s)
  val names = SensitiveNames.default

  "query" should {
    "mask values of sensitive parameters and keep keys, order, flags and repeats" in {
      UriRedactor.query(names)(
        uri("/a?reset_token=QS&user=alice%20b&flag&t=1&t=2&password=&X-Api-Key=k")).renderString shouldEqual
      "/a?reset_token=REDACTED&user=alice%20b&flag&t=1&t=2&password=REDACTED&X-Api-Key=REDACTED"
    }
    "honour ; separators, re-rendering with &" in {
      UriRedactor.query(names)(uri("/a?token=t;user=u")).renderString shouldEqual "/a?token=REDACTED&user=u"
    }
    "leave a URI without a query untouched" in {
      UriRedactor.query(names)(uri("/a")).renderString shouldEqual "/a"
      UriRedactor.query(names)(uri("http://h/a?")).renderString shouldEqual "http://h/a?"
    }
    "leave a value already equal to the mask as it is" in {
      UriRedactor.query(names)(uri("/a?password=REDACTED")).renderString shouldEqual "/a?password=REDACTED"
    }
    "keep a query without a masked value as received" in {
      val received = uri("http://h/a?x=a+b;y=%41&z=%2F")
      val logged = UriRedactor.query(names)(received)
      logged shouldEqual received
      logged.toString shouldEqual "http://h/a?x=a+b;y=%41&z=%2F"
    }
  }

  "userInfo" should {
    "mask user and password" in {
      UriRedactor.userInfo(uri("http://bob:pw@example.com/a?x=1")).renderString shouldEqual
      "http://REDACTED:REDACTED@example.com/a?x=1"
      UriRedactor.userInfo(uri("http://bob@example.com/")).renderString shouldEqual "http://REDACTED@example.com/"
      UriRedactor.userInfo(uri("/relative?x=1")).renderString shouldEqual "/relative?x=1"
    }
  }

  "segmentAfter" should {
    "mask the segment following a sensitive one" in {
      UriRedactor.segmentAfter(names)(uri("/password-reset/XYZ123/confirm/")).renderString shouldEqual
      "/password-reset/REDACTED/confirm/"
      UriRedactor.segmentAfter(names)(uri("/users/7/tokens/abc/x")).renderString shouldEqual
      "/users/7/tokens/REDACTED/x"
      UriRedactor.segmentAfter(names)(uri("/tokens")).renderString shouldEqual "/tokens"
      UriRedactor.segmentAfter(names)(uri("/a/b")).renderString shouldEqual "/a/b"
    }
  }

  "pathTemplate" should {
    val t = UriRedactor.pathTemplate("/users/*/tokens/{token}")
    "mask the named segments of a matching path and keep extra segments" in {
      t(uri("/users/7/tokens/abc")).renderString shouldEqual "/users/7/tokens/REDACTED"
      t(uri("/users/7/tokens/abc/extra?x=1")).renderString shouldEqual "/users/7/tokens/REDACTED/extra?x=1"
      t(uri("/users/7/tokens/abc/")).renderString shouldEqual "/users/7/tokens/REDACTED/"
    }
    "leave a shorter or non-matching path untouched" in {
      t(uri("/users/7/tokens")).renderString shouldEqual "/users/7/tokens"
      t(uri("/users/7/keys/abc")).renderString shouldEqual "/users/7/keys/abc"
      t(uri("/")).renderString shouldEqual "/"
    }
    "match literals against decoded segments" in {
      UriRedactor.pathTemplate("/a b/{x}")(uri("/a%20b/secret")).renderString shouldEqual "/a%20b/REDACTED"
    }
    "ignore a trailing slash in the template and compose" in {
      val both = UriRedactor.pathTemplate("/password-reset/{token}/").andThen(UriRedactor.pathTemplate("/keys/{k}"))
      both(uri("/password-reset/XYZ")).renderString shouldEqual "/password-reset/REDACTED"
      both(uri("/keys/K")).renderString shouldEqual "/keys/REDACTED"
    }
  }
}
