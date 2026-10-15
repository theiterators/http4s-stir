package pl.iterators.stir.server.directives

import org.typelevel.vault.Key
import pl.iterators.stir.server._

/**
 * @groupname attribute Attribute directives
 * @groupprio attribute 115
 */
trait AttributeDirectives {
  import BasicDirectives._
  import RouteDirectives._

  /**
   * Extracts the request attribute stored under the given `Key` (http4s keeps request attributes in a `Vault`).
   * Rejects with a [[MissingAttributeRejection]] (`500 Internal Server Error` when sealed) when it is absent.
   *
   * @group attribute
   */
  def attribute[T](key: Key[T]): Directive1[T] =
    extract(_.request.attributes.lookup(key)).flatMap {
      case Some(value) => provide(value)
      case None        => reject(MissingAttributeRejection(key)): Directive1[T]
    }

  /**
   * Extracts the optional request attribute stored under the given `Key`.
   *
   * @group attribute
   */
  def optionalAttribute[T](key: Key[T]): Directive1[Option[T]] =
    extract(_.request.attributes.lookup(key))
}

object AttributeDirectives extends AttributeDirectives
