package pdsl.util

import pdsl.server.Route

/**
 * ApplyConverter allows generic conversion of functions of type `(T1, T2, ...) => Route` to
 * `(TupleX(T1, T2, ...)) => Route`.
 */
abstract class ApplyConverter[L] {
  type In
  def apply(f: In): L => Route
}

object ApplyConverter extends ApplyConverterInstances
