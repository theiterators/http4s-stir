package pl.iterators.stir.util

private[util] abstract class ConstructFromTupleInstances {
  implicit def instance1[T1, R](construct: (T1) => R): ConstructFromTuple[Tuple1[T1], R] =
    new ConstructFromTuple[Tuple1[T1], R] {
      def apply(tup: Tuple1[T1]): R = construct(tup._1)
    }
  implicit def instance2[T1, T2, R](construct: (T1, T2) => R): ConstructFromTuple[Tuple2[T1, T2], R] =
    new ConstructFromTuple[Tuple2[T1, T2], R] {
      def apply(tup: Tuple2[T1, T2]): R = construct(tup._1, tup._2)
    }
  implicit def instance3[T1, T2, T3, R](construct: (T1, T2, T3) => R): ConstructFromTuple[Tuple3[T1, T2, T3], R] =
    new ConstructFromTuple[Tuple3[T1, T2, T3], R] {
      def apply(tup: Tuple3[T1, T2, T3]): R = construct(tup._1, tup._2, tup._3)
    }
  implicit def instance4[T1, T2, T3, T4, R](construct: (T1, T2, T3, T4) => R): ConstructFromTuple[Tuple4[T1, T2, T3, T4], R] =
    new ConstructFromTuple[Tuple4[T1, T2, T3, T4], R] {
      def apply(tup: Tuple4[T1, T2, T3, T4]): R = construct(tup._1, tup._2, tup._3, tup._4)
    }
  implicit def instance5[T1, T2, T3, T4, T5, R](construct: (T1, T2, T3, T4, T5) => R): ConstructFromTuple[Tuple5[T1, T2, T3, T4, T5], R] =
    new ConstructFromTuple[Tuple5[T1, T2, T3, T4, T5], R] {
      def apply(tup: Tuple5[T1, T2, T3, T4, T5]): R = construct(tup._1, tup._2, tup._3, tup._4, tup._5)
    }
  implicit def instance6[T1, T2, T3, T4, T5, T6, R](construct: (T1, T2, T3, T4, T5, T6) => R): ConstructFromTuple[Tuple6[T1, T2, T3, T4, T5, T6], R] =
    new ConstructFromTuple[Tuple6[T1, T2, T3, T4, T5, T6], R] {
      def apply(tup: Tuple6[T1, T2, T3, T4, T5, T6]): R = construct(tup._1, tup._2, tup._3, tup._4, tup._5, tup._6)
    }
  implicit def instance7[T1, T2, T3, T4, T5, T6, T7, R](construct: (T1, T2, T3, T4, T5, T6, T7) => R): ConstructFromTuple[Tuple7[T1, T2, T3, T4, T5, T6, T7], R] =
    new ConstructFromTuple[Tuple7[T1, T2, T3, T4, T5, T6, T7], R] {
      def apply(tup: Tuple7[T1, T2, T3, T4, T5, T6, T7]): R = construct(tup._1, tup._2, tup._3, tup._4, tup._5, tup._6, tup._7)
    }
  implicit def instance8[T1, T2, T3, T4, T5, T6, T7, T8, R](construct: (T1, T2, T3, T4, T5, T6, T7, T8) => R): ConstructFromTuple[Tuple8[T1, T2, T3, T4, T5, T6, T7, T8], R] =
    new ConstructFromTuple[Tuple8[T1, T2, T3, T4, T5, T6, T7, T8], R] {
      def apply(tup: Tuple8[T1, T2, T3, T4, T5, T6, T7, T8]): R = construct(tup._1, tup._2, tup._3, tup._4, tup._5, tup._6, tup._7, tup._8)
    }
  implicit def instance9[T1, T2, T3, T4, T5, T6, T7, T8, T9, R](construct: (T1, T2, T3, T4, T5, T6, T7, T8, T9) => R): ConstructFromTuple[Tuple9[T1, T2, T3, T4, T5, T6, T7, T8, T9], R] =
    new ConstructFromTuple[Tuple9[T1, T2, T3, T4, T5, T6, T7, T8, T9], R] {
      def apply(tup: Tuple9[T1, T2, T3, T4, T5, T6, T7, T8, T9]): R = construct(tup._1, tup._2, tup._3, tup._4, tup._5, tup._6, tup._7, tup._8, tup._9)
    }
  implicit def instance10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, R](construct: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10) => R): ConstructFromTuple[Tuple10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10], R] =
    new ConstructFromTuple[Tuple10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10], R] {
      def apply(tup: Tuple10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10]): R = construct(tup._1, tup._2, tup._3, tup._4, tup._5, tup._6, tup._7, tup._8, tup._9, tup._10)
    }
  implicit def instance11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, R](construct: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11) => R): ConstructFromTuple[Tuple11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11], R] =
    new ConstructFromTuple[Tuple11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11], R] {
      def apply(tup: Tuple11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11]): R = construct(tup._1, tup._2, tup._3, tup._4, tup._5, tup._6, tup._7, tup._8, tup._9, tup._10, tup._11)
    }
  implicit def instance12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, R](construct: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12) => R): ConstructFromTuple[Tuple12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12], R] =
    new ConstructFromTuple[Tuple12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12], R] {
      def apply(tup: Tuple12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12]): R = construct(tup._1, tup._2, tup._3, tup._4, tup._5, tup._6, tup._7, tup._8, tup._9, tup._10, tup._11, tup._12)
    }
  implicit def instance13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, R](construct: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13) => R): ConstructFromTuple[Tuple13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13], R] =
    new ConstructFromTuple[Tuple13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13], R] {
      def apply(tup: Tuple13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13]): R = construct(tup._1, tup._2, tup._3, tup._4, tup._5, tup._6, tup._7, tup._8, tup._9, tup._10, tup._11, tup._12, tup._13)
    }
  implicit def instance14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, R](construct: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14) => R): ConstructFromTuple[Tuple14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14], R] =
    new ConstructFromTuple[Tuple14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14], R] {
      def apply(tup: Tuple14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14]): R = construct(tup._1, tup._2, tup._3, tup._4, tup._5, tup._6, tup._7, tup._8, tup._9, tup._10, tup._11, tup._12, tup._13, tup._14)
    }
  implicit def instance15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, R](construct: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15) => R): ConstructFromTuple[Tuple15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15], R] =
    new ConstructFromTuple[Tuple15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15], R] {
      def apply(tup: Tuple15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15]): R = construct(tup._1, tup._2, tup._3, tup._4, tup._5, tup._6, tup._7, tup._8, tup._9, tup._10, tup._11, tup._12, tup._13, tup._14, tup._15)
    }
  implicit def instance16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, R](construct: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16) => R): ConstructFromTuple[Tuple16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16], R] =
    new ConstructFromTuple[Tuple16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16], R] {
      def apply(tup: Tuple16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16]): R = construct(tup._1, tup._2, tup._3, tup._4, tup._5, tup._6, tup._7, tup._8, tup._9, tup._10, tup._11, tup._12, tup._13, tup._14, tup._15, tup._16)
    }
  implicit def instance17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, R](construct: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17) => R): ConstructFromTuple[Tuple17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17], R] =
    new ConstructFromTuple[Tuple17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17], R] {
      def apply(tup: Tuple17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17]): R = construct(tup._1, tup._2, tup._3, tup._4, tup._5, tup._6, tup._7, tup._8, tup._9, tup._10, tup._11, tup._12, tup._13, tup._14, tup._15, tup._16, tup._17)
    }
  implicit def instance18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, R](construct: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18) => R): ConstructFromTuple[Tuple18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18], R] =
    new ConstructFromTuple[Tuple18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18], R] {
      def apply(tup: Tuple18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18]): R = construct(tup._1, tup._2, tup._3, tup._4, tup._5, tup._6, tup._7, tup._8, tup._9, tup._10, tup._11, tup._12, tup._13, tup._14, tup._15, tup._16, tup._17, tup._18)
    }
  implicit def instance19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, R](construct: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19) => R): ConstructFromTuple[Tuple19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19], R] =
    new ConstructFromTuple[Tuple19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19], R] {
      def apply(tup: Tuple19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19]): R = construct(tup._1, tup._2, tup._3, tup._4, tup._5, tup._6, tup._7, tup._8, tup._9, tup._10, tup._11, tup._12, tup._13, tup._14, tup._15, tup._16, tup._17, tup._18, tup._19)
    }
  implicit def instance20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, R](construct: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20) => R): ConstructFromTuple[Tuple20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20], R] =
    new ConstructFromTuple[Tuple20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20], R] {
      def apply(tup: Tuple20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20]): R = construct(tup._1, tup._2, tup._3, tup._4, tup._5, tup._6, tup._7, tup._8, tup._9, tup._10, tup._11, tup._12, tup._13, tup._14, tup._15, tup._16, tup._17, tup._18, tup._19, tup._20)
    }
  implicit def instance21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, R](construct: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21) => R): ConstructFromTuple[Tuple21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21], R] =
    new ConstructFromTuple[Tuple21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21], R] {
      def apply(tup: Tuple21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21]): R = construct(tup._1, tup._2, tup._3, tup._4, tup._5, tup._6, tup._7, tup._8, tup._9, tup._10, tup._11, tup._12, tup._13, tup._14, tup._15, tup._16, tup._17, tup._18, tup._19, tup._20, tup._21)
    }
  implicit def instance22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, R](construct: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22) => R): ConstructFromTuple[Tuple22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22], R] =
    new ConstructFromTuple[Tuple22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22], R] {
      def apply(tup: Tuple22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22]): R = construct(tup._1, tup._2, tup._3, tup._4, tup._5, tup._6, tup._7, tup._8, tup._9, tup._10, tup._11, tup._12, tup._13, tup._14, tup._15, tup._16, tup._17, tup._18, tup._19, tup._20, tup._21, tup._22)
    }
}
