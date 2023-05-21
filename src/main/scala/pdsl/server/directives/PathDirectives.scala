package pdsl.server.directives

import pdsl.server._

trait PathDirectives extends PathMatchers with ImplicitPathMatcherConstruction {
  import BasicDirectives._
  import RouteDirectives._
  import pdsl.server.PathMatcher._

  /**
   * Applies the given [[PathMatcher]] to the remaining unmatched path after consuming a leading slash.
   * The matcher has to match the remaining path completely.
   * If matched the value extracted by the [[PathMatcher]] is extracted on the directive level.
   *
   * @group path
   */
  def path[L](pm: PathMatcher[L]): Directive[L] = _path(Slash ~ pm ~ PathEnd)

  /**
   * Applies the given [[PathMatcher]] to a prefix of the remaining unmatched path after consuming a leading slash.
   * The matcher has to match a prefix of the remaining path.
   * If matched the value extracted by the PathMatcher is extracted on the directive level.
   *
   * @group path
   */
  def pathPrefix[L](pm: PathMatcher[L]): Directive[L] = _path(Slash ~ pm)

  private def _path[L](pm: PathMatcher[L]): Directive[L] = {
    implicit val LIsTuple = pm.ev
    extract(ctx => pm(ctx.pathInfo)).flatMap {
      case Matched(_, values) => tprovide(values)
      case Unmatched             => reject
    }
  }
}

object PathDirectives extends PathDirectives
