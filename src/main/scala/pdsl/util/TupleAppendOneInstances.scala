package pdsl.util

import TupleOps.AppendOne

private[util] abstract class TupleAppendOneInstances {
  type Aux[P, S, Out0] = AppendOne[P, S] { type Out = Out0 }

  implicit def append0[T1]: Aux[Unit, T1, Tuple1[T1]] =
    new AppendOne[Unit, T1] {
      type Out = Tuple1[T1]
      def apply(prefix: Unit, last: T1): Tuple1[T1] = Tuple1(last)
    }

  implicit def append1[T1, L]: Aux[Tuple1[T1], L, Tuple2[T1, L]] =
    new AppendOne[Tuple1[T1], L] {
      type Out = Tuple2[T1, L]
      def apply(prefix: Tuple1[T1], last: L): Tuple2[T1, L] = Tuple2(prefix._1, last)
    }
  implicit def append2[T1, T2, L]: Aux[Tuple2[T1, T2], L, Tuple3[T1, T2, L]] =
    new AppendOne[Tuple2[T1, T2], L] {
      type Out = Tuple3[T1, T2, L]
      def apply(prefix: Tuple2[T1, T2], last: L): Tuple3[T1, T2, L] = Tuple3(prefix._1, prefix._2, last)
    }
  implicit def append3[T1, T2, T3, L]: Aux[Tuple3[T1, T2, T3], L, Tuple4[T1, T2, T3, L]] =
    new AppendOne[Tuple3[T1, T2, T3], L] {
      type Out = Tuple4[T1, T2, T3, L]
      def apply(prefix: Tuple3[T1, T2, T3], last: L): Tuple4[T1, T2, T3, L] = Tuple4(prefix._1, prefix._2, prefix._3, last)
    }
  implicit def append4[T1, T2, T3, T4, L]: Aux[Tuple4[T1, T2, T3, T4], L, Tuple5[T1, T2, T3, T4, L]] =
    new AppendOne[Tuple4[T1, T2, T3, T4], L] {
      type Out = Tuple5[T1, T2, T3, T4, L]
      def apply(prefix: Tuple4[T1, T2, T3, T4], last: L): Tuple5[T1, T2, T3, T4, L] = Tuple5(prefix._1, prefix._2, prefix._3, prefix._4, last)
    }
  implicit def append5[T1, T2, T3, T4, T5, L]: Aux[Tuple5[T1, T2, T3, T4, T5], L, Tuple6[T1, T2, T3, T4, T5, L]] =
    new AppendOne[Tuple5[T1, T2, T3, T4, T5], L] {
      type Out = Tuple6[T1, T2, T3, T4, T5, L]
      def apply(prefix: Tuple5[T1, T2, T3, T4, T5], last: L): Tuple6[T1, T2, T3, T4, T5, L] = Tuple6(prefix._1, prefix._2, prefix._3, prefix._4, prefix._5, last)
    }
  implicit def append6[T1, T2, T3, T4, T5, T6, L]: Aux[Tuple6[T1, T2, T3, T4, T5, T6], L, Tuple7[T1, T2, T3, T4, T5, T6, L]] =
    new AppendOne[Tuple6[T1, T2, T3, T4, T5, T6], L] {
      type Out = Tuple7[T1, T2, T3, T4, T5, T6, L]
      def apply(prefix: Tuple6[T1, T2, T3, T4, T5, T6], last: L): Tuple7[T1, T2, T3, T4, T5, T6, L] = Tuple7(prefix._1, prefix._2, prefix._3, prefix._4, prefix._5, prefix._6, last)
    }
  implicit def append7[T1, T2, T3, T4, T5, T6, T7, L]: Aux[Tuple7[T1, T2, T3, T4, T5, T6, T7], L, Tuple8[T1, T2, T3, T4, T5, T6, T7, L]] =
    new AppendOne[Tuple7[T1, T2, T3, T4, T5, T6, T7], L] {
      type Out = Tuple8[T1, T2, T3, T4, T5, T6, T7, L]
      def apply(prefix: Tuple7[T1, T2, T3, T4, T5, T6, T7], last: L): Tuple8[T1, T2, T3, T4, T5, T6, T7, L] = Tuple8(prefix._1, prefix._2, prefix._3, prefix._4, prefix._5, prefix._6, prefix._7, last)
    }
  implicit def append8[T1, T2, T3, T4, T5, T6, T7, T8, L]: Aux[Tuple8[T1, T2, T3, T4, T5, T6, T7, T8], L, Tuple9[T1, T2, T3, T4, T5, T6, T7, T8, L]] =
    new AppendOne[Tuple8[T1, T2, T3, T4, T5, T6, T7, T8], L] {
      type Out = Tuple9[T1, T2, T3, T4, T5, T6, T7, T8, L]
      def apply(prefix: Tuple8[T1, T2, T3, T4, T5, T6, T7, T8], last: L): Tuple9[T1, T2, T3, T4, T5, T6, T7, T8, L] = Tuple9(prefix._1, prefix._2, prefix._3, prefix._4, prefix._5, prefix._6, prefix._7, prefix._8, last)
    }
  implicit def append9[T1, T2, T3, T4, T5, T6, T7, T8, T9, L]: Aux[Tuple9[T1, T2, T3, T4, T5, T6, T7, T8, T9], L, Tuple10[T1, T2, T3, T4, T5, T6, T7, T8, T9, L]] =
    new AppendOne[Tuple9[T1, T2, T3, T4, T5, T6, T7, T8, T9], L] {
      type Out = Tuple10[T1, T2, T3, T4, T5, T6, T7, T8, T9, L]
      def apply(prefix: Tuple9[T1, T2, T3, T4, T5, T6, T7, T8, T9], last: L): Tuple10[T1, T2, T3, T4, T5, T6, T7, T8, T9, L] = Tuple10(prefix._1, prefix._2, prefix._3, prefix._4, prefix._5, prefix._6, prefix._7, prefix._8, prefix._9, last)
    }
  implicit def append10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, L]: Aux[Tuple10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10], L, Tuple11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, L]] =
    new AppendOne[Tuple10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10], L] {
      type Out = Tuple11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, L]
      def apply(prefix: Tuple10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10], last: L): Tuple11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, L] = Tuple11(prefix._1, prefix._2, prefix._3, prefix._4, prefix._5, prefix._6, prefix._7, prefix._8, prefix._9, prefix._10, last)
    }
  implicit def append11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, L]: Aux[Tuple11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11], L, Tuple12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, L]] =
    new AppendOne[Tuple11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11], L] {
      type Out = Tuple12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, L]
      def apply(prefix: Tuple11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11], last: L): Tuple12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, L] = Tuple12(prefix._1, prefix._2, prefix._3, prefix._4, prefix._5, prefix._6, prefix._7, prefix._8, prefix._9, prefix._10, prefix._11, last)
    }
  implicit def append12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, L]: Aux[Tuple12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12], L, Tuple13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, L]] =
    new AppendOne[Tuple12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12], L] {
      type Out = Tuple13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, L]
      def apply(prefix: Tuple12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12], last: L): Tuple13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, L] = Tuple13(prefix._1, prefix._2, prefix._3, prefix._4, prefix._5, prefix._6, prefix._7, prefix._8, prefix._9, prefix._10, prefix._11, prefix._12, last)
    }
  implicit def append13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, L]: Aux[Tuple13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13], L, Tuple14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, L]] =
    new AppendOne[Tuple13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13], L] {
      type Out = Tuple14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, L]
      def apply(prefix: Tuple13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13], last: L): Tuple14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, L] = Tuple14(prefix._1, prefix._2, prefix._3, prefix._4, prefix._5, prefix._6, prefix._7, prefix._8, prefix._9, prefix._10, prefix._11, prefix._12, prefix._13, last)
    }
  implicit def append14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, L]: Aux[Tuple14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14], L, Tuple15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, L]] =
    new AppendOne[Tuple14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14], L] {
      type Out = Tuple15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, L]
      def apply(prefix: Tuple14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14], last: L): Tuple15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, L] = Tuple15(prefix._1, prefix._2, prefix._3, prefix._4, prefix._5, prefix._6, prefix._7, prefix._8, prefix._9, prefix._10, prefix._11, prefix._12, prefix._13, prefix._14, last)
    }
  implicit def append15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, L]: Aux[Tuple15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15], L, Tuple16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, L]] =
    new AppendOne[Tuple15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15], L] {
      type Out = Tuple16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, L]
      def apply(prefix: Tuple15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15], last: L): Tuple16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, L] = Tuple16(prefix._1, prefix._2, prefix._3, prefix._4, prefix._5, prefix._6, prefix._7, prefix._8, prefix._9, prefix._10, prefix._11, prefix._12, prefix._13, prefix._14, prefix._15, last)
    }
  implicit def append16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, L]: Aux[Tuple16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16], L, Tuple17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, L]] =
    new AppendOne[Tuple16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16], L] {
      type Out = Tuple17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, L]
      def apply(prefix: Tuple16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16], last: L): Tuple17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, L] = Tuple17(prefix._1, prefix._2, prefix._3, prefix._4, prefix._5, prefix._6, prefix._7, prefix._8, prefix._9, prefix._10, prefix._11, prefix._12, prefix._13, prefix._14, prefix._15, prefix._16, last)
    }
  implicit def append17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, L]: Aux[Tuple17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17], L, Tuple18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, L]] =
    new AppendOne[Tuple17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17], L] {
      type Out = Tuple18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, L]
      def apply(prefix: Tuple17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17], last: L): Tuple18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, L] = Tuple18(prefix._1, prefix._2, prefix._3, prefix._4, prefix._5, prefix._6, prefix._7, prefix._8, prefix._9, prefix._10, prefix._11, prefix._12, prefix._13, prefix._14, prefix._15, prefix._16, prefix._17, last)
    }
  implicit def append18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, L]: Aux[Tuple18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18], L, Tuple19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, L]] =
    new AppendOne[Tuple18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18], L] {
      type Out = Tuple19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, L]
      def apply(prefix: Tuple18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18], last: L): Tuple19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, L] = Tuple19(prefix._1, prefix._2, prefix._3, prefix._4, prefix._5, prefix._6, prefix._7, prefix._8, prefix._9, prefix._10, prefix._11, prefix._12, prefix._13, prefix._14, prefix._15, prefix._16, prefix._17, prefix._18, last)
    }
  implicit def append19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, L]: Aux[Tuple19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19], L, Tuple20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, L]] =
    new AppendOne[Tuple19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19], L] {
      type Out = Tuple20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, L]
      def apply(prefix: Tuple19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19], last: L): Tuple20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, L] = Tuple20(prefix._1, prefix._2, prefix._3, prefix._4, prefix._5, prefix._6, prefix._7, prefix._8, prefix._9, prefix._10, prefix._11, prefix._12, prefix._13, prefix._14, prefix._15, prefix._16, prefix._17, prefix._18, prefix._19, last)
    }
  implicit def append20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, L]: Aux[Tuple20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20], L, Tuple21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, L]] =
    new AppendOne[Tuple20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20], L] {
      type Out = Tuple21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, L]
      def apply(prefix: Tuple20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20], last: L): Tuple21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, L] = Tuple21(prefix._1, prefix._2, prefix._3, prefix._4, prefix._5, prefix._6, prefix._7, prefix._8, prefix._9, prefix._10, prefix._11, prefix._12, prefix._13, prefix._14, prefix._15, prefix._16, prefix._17, prefix._18, prefix._19, prefix._20, last)
    }
  implicit def append21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, L]: Aux[Tuple21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21], L, Tuple22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, L]] =
    new AppendOne[Tuple21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21], L] {
      type Out = Tuple22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, L]
      def apply(prefix: Tuple21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21], last: L): Tuple22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, L] = Tuple22(prefix._1, prefix._2, prefix._3, prefix._4, prefix._5, prefix._6, prefix._7, prefix._8, prefix._9, prefix._10, prefix._11, prefix._12, prefix._13, prefix._14, prefix._15, prefix._16, prefix._17, prefix._18, prefix._19, prefix._20, prefix._21, last)
    }
}