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

  "fragment" should {
    "mask values of sensitive pairs in a query-shaped fragment and keep the rest as received" in {
      UriRedactor.fragment(names)(uri("https://app.example/cb#access_token=X&expires_in=3600&state=s;id_token=I"))
        .renderString shouldEqual
      "https://app.example/cb#access_token=REDACTED&expires_in=3600&state=s;id_token=REDACTED"
      UriRedactor.fragment(names)(uri("/cb#token=T")).renderString shouldEqual "/cb#token=REDACTED"
    }
    "decode the pair name for matching only, keeping the raw fragment text" in {
      // the fragment is rewritten as text: http4s would re-encode % on render, LogRendering appends it as received
      UriRedactor.fragment(names)(uri("/cb#access%5Ftoken=X&user+name=u&state=%7Bs%7D")).fragment shouldEqual
      Some("access%5Ftoken=REDACTED&user+name=u&state=%7Bs%7D")
    }
    "treat ; as part of a value, as URLSearchParams does, and still honour it as a legacy separator" in {
      // a sensitive value is masked whole; inside another value, a ;-separated sensitive pair is masked too
      UriRedactor.fragment(names)(uri("/cb#access_token=prefix;CANARY&state=s")).fragment shouldEqual
      Some("access_token=REDACTED&state=s")
      UriRedactor.fragment(names)(uri("/cb#state=s;id_token=I&x=1")).fragment shouldEqual
      Some("state=s;id_token=REDACTED&x=1")
    }
    "match parameter names after a hash route's ?, so that an exact predicate sees them" in {
      val exact: String => Boolean = _ == "id_token"
      UriRedactor.fragment(exact)(uri("https://spa.example/#/login?id_token=X&state=s")).fragment shouldEqual
      Some("/login?id_token=REDACTED&state=s")
      UriRedactor.fragment(exact)(uri("/cb#id_token=X&state=a?b")).fragment shouldEqual
      Some("id_token=REDACTED&state=a?b")
      val routeOnly = uri("/cb#/login?")
      UriRedactor.fragment(exact)(routeOnly) shouldEqual routeOnly
      // the route prefix may itself contain =, and there may be more than one ?
      UriRedactor.fragment(names)(uri("/a#/user/YQ==?access_token=CANARY&state=s")).fragment shouldEqual
      Some("/user/YQ==?access_token=REDACTED&state=s")
      UriRedactor.fragment(_ == "token")(uri("/a#/x?y?token=X")).fragment shouldEqual Some("/x?y?token=REDACTED")
    }
    "mask a fragment with more than MaxFragmentQuestionMarks ? characters whole, in bounded time" in {
      val atLimit = "?" * UriRedactor.MaxFragmentQuestionMarks + "token=X&x=1"
      UriRedactor.fragment(names)(uri("/a").withFragment(atLimit)).fragment shouldEqual
      Some("?" * UriRedactor.MaxFragmentQuestionMarks + "token=REDACTED&x=1")
      val overLimit = "?" * (UriRedactor.MaxFragmentQuestionMarks + 1) + "x=1"
      UriRedactor.fragment(names)(uri("/a").withFragment(overLimit)).fragment shouldEqual Some("REDACTED")
      // the reviewer's benchmark input: 8000 question marks before a harmless pair
      UriRedactor.fragment(names)(uri("/a").withFragment("?" * 8000 + "x=1")).fragment shouldEqual Some("REDACTED")
    }
    "match a legacy ;-separated name before the first = as well, masking the whole value" in {
      UriRedactor.fragment(_ == "id_token")(uri("/cb#flag;id_token=CANARY&x=1")).fragment shouldEqual
      Some("flag;id_token=REDACTED&x=1")
      UriRedactor.fragment(_ == "id_token")(uri("/cb#/r?flag;id_token=a;b")).fragment shouldEqual
      Some("/r?flag;id_token=REDACTED")
    }
    "leave a fragment that is not query-shaped and a URI without a fragment untouched" in {
      val route = uri("https://app.example/#/users/42")
      UriRedactor.fragment(names)(route) shouldEqual route
      val empty = uri("/a#")
      UriRedactor.fragment(names)(empty) shouldEqual empty
      UriRedactor.fragment(names)(uri("/a?token=t")).renderString shouldEqual "/a?token=t"
    }
    "keep a fragment without a masked value as received" in {
      val received = uri("https://app.example/cb#x=a+b&y=%41&flag")
      val logged = UriRedactor.fragment(names)(received)
      logged shouldEqual received
      logged.fragment shouldEqual Some("x=a+b&y=%41&flag")
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
