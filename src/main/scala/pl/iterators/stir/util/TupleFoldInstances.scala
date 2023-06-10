package pl.iterators.stir.util

import TupleOps.FoldLeft
import BinaryPolyFunc.Case

private[util] abstract class TupleFoldInstances {

  type Aux[In, T, Op, Out0] = FoldLeft[In, T, Op] { type Out = Out0 }

  implicit def t0[In, Op]: Aux[In, Unit, Op, In] =
    new FoldLeft[In, Unit, Op] {
      type Out = In
      def apply(zero: In, tuple: Unit) = zero
    }

  implicit def t1[In, A, Op](implicit f: Case[In, A, Op]): Aux[In, Tuple1[A], Op, f.Out] =
    new FoldLeft[In, Tuple1[A], Op] {
      type Out = f.Out
      def apply(zero: In, tuple: Tuple1[A]) = f(zero, tuple._1)
    }

  implicit def t2[In, T1, X, T2, Op](
      implicit fold: Aux[In, Tuple1[T1], Op, X], f: Case[X, T2, Op]): Aux[In, Tuple2[T1, T2], Op, f.Out] =
    new FoldLeft[In, Tuple2[T1, T2], Op] {
      type Out = f.Out
      def apply(zero: In, t: Tuple2[T1, T2]) =
        f(fold(zero, Tuple1(t._1)), t._2)
    }
  implicit def t3[In, T1, T2, X, T3, Op](
      implicit fold: Aux[In, Tuple2[T1, T2], Op, X], f: Case[X, T3, Op]): Aux[In, Tuple3[T1, T2, T3], Op, f.Out] =
    new FoldLeft[In, Tuple3[T1, T2, T3], Op] {
      type Out = f.Out
      def apply(zero: In, t: Tuple3[T1, T2, T3]) =
        f(fold(zero, Tuple2(t._1, t._2)), t._3)
    }
  implicit def t4[In, T1, T2, T3, X, T4, Op](implicit fold: Aux[In, Tuple3[T1, T2, T3], Op, X], f: Case[X, T4, Op])
      : Aux[In, Tuple4[T1, T2, T3, T4], Op, f.Out] =
    new FoldLeft[In, Tuple4[T1, T2, T3, T4], Op] {
      type Out = f.Out
      def apply(zero: In, t: Tuple4[T1, T2, T3, T4]) =
        f(fold(zero, Tuple3(t._1, t._2, t._3)), t._4)
    }
  implicit def t5[In, T1, T2, T3, T4, X, T5, Op](implicit fold: Aux[In, Tuple4[T1, T2, T3, T4], Op, X],
      f: Case[X, T5, Op]): Aux[In, Tuple5[T1, T2, T3, T4, T5], Op, f.Out] =
    new FoldLeft[In, Tuple5[T1, T2, T3, T4, T5], Op] {
      type Out = f.Out
      def apply(zero: In, t: Tuple5[T1, T2, T3, T4, T5]) =
        f(fold(zero, Tuple4(t._1, t._2, t._3, t._4)), t._5)
    }
  implicit def t6[In, T1, T2, T3, T4, T5, X, T6, Op](implicit fold: Aux[In, Tuple5[T1, T2, T3, T4, T5], Op, X],
      f: Case[X, T6, Op]): Aux[In, Tuple6[T1, T2, T3, T4, T5, T6], Op, f.Out] =
    new FoldLeft[In, Tuple6[T1, T2, T3, T4, T5, T6], Op] {
      type Out = f.Out
      def apply(zero: In, t: Tuple6[T1, T2, T3, T4, T5, T6]) =
        f(fold(zero, Tuple5(t._1, t._2, t._3, t._4, t._5)), t._6)
    }
  implicit def t7[In, T1, T2, T3, T4, T5, T6, X, T7, Op](implicit fold: Aux[In, Tuple6[T1, T2, T3, T4, T5, T6], Op, X],
      f: Case[X, T7, Op]): Aux[In, Tuple7[T1, T2, T3, T4, T5, T6, T7], Op, f.Out] =
    new FoldLeft[In, Tuple7[T1, T2, T3, T4, T5, T6, T7], Op] {
      type Out = f.Out
      def apply(zero: In, t: Tuple7[T1, T2, T3, T4, T5, T6, T7]) =
        f(fold(zero, Tuple6(t._1, t._2, t._3, t._4, t._5, t._6)), t._7)
    }
  implicit def t8[In, T1, T2, T3, T4, T5, T6, T7, X, T8, Op](
      implicit fold: Aux[In, Tuple7[T1, T2, T3, T4, T5, T6, T7], Op, X], f: Case[X, T8, Op])
      : Aux[In, Tuple8[T1, T2, T3, T4, T5, T6, T7, T8], Op, f.Out] =
    new FoldLeft[In, Tuple8[T1, T2, T3, T4, T5, T6, T7, T8], Op] {
      type Out = f.Out
      def apply(zero: In, t: Tuple8[T1, T2, T3, T4, T5, T6, T7, T8]) =
        f(fold(zero, Tuple7(t._1, t._2, t._3, t._4, t._5, t._6, t._7)), t._8)
    }
  implicit def t9[In, T1, T2, T3, T4, T5, T6, T7, T8, X, T9, Op](
      implicit fold: Aux[In, Tuple8[T1, T2, T3, T4, T5, T6, T7, T8], Op, X], f: Case[X, T9, Op])
      : Aux[In, Tuple9[T1, T2, T3, T4, T5, T6, T7, T8, T9], Op, f.Out] =
    new FoldLeft[In, Tuple9[T1, T2, T3, T4, T5, T6, T7, T8, T9], Op] {
      type Out = f.Out
      def apply(zero: In, t: Tuple9[T1, T2, T3, T4, T5, T6, T7, T8, T9]) =
        f(fold(zero, Tuple8(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8)), t._9)
    }
  implicit def t10[In, T1, T2, T3, T4, T5, T6, T7, T8, T9, X, T10, Op](
      implicit fold: Aux[In, Tuple9[T1, T2, T3, T4, T5, T6, T7, T8, T9], Op, X], f: Case[X, T10, Op])
      : Aux[In, Tuple10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10], Op, f.Out] =
    new FoldLeft[In, Tuple10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10], Op] {
      type Out = f.Out
      def apply(zero: In, t: Tuple10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10]) =
        f(fold(zero, Tuple9(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9)), t._10)
    }
  implicit def t11[In, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, X, T11, Op](
      implicit fold: Aux[In, Tuple10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10], Op, X], f: Case[X, T11, Op])
      : Aux[In, Tuple11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11], Op, f.Out] =
    new FoldLeft[In, Tuple11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11], Op] {
      type Out = f.Out
      def apply(zero: In, t: Tuple11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11]) =
        f(fold(zero, Tuple10(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10)), t._11)
    }
  implicit def t12[In, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, X, T12, Op](
      implicit fold: Aux[In, Tuple11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11], Op, X], f: Case[X, T12, Op])
      : Aux[In, Tuple12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12], Op, f.Out] =
    new FoldLeft[In, Tuple12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12], Op] {
      type Out = f.Out
      def apply(zero: In, t: Tuple12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12]) =
        f(fold(zero, Tuple11(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11)), t._12)
    }
  implicit def t13[In, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, X, T13, Op](
      implicit fold: Aux[In, Tuple12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12], Op, X], f: Case[X, T13, Op])
      : Aux[In, Tuple13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13], Op, f.Out] =
    new FoldLeft[In, Tuple13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13], Op] {
      type Out = f.Out
      def apply(zero: In, t: Tuple13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13]) =
        f(fold(zero, Tuple12(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12)), t._13)
    }
  implicit def t14[In, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, X, T14, Op](
      implicit fold: Aux[In, Tuple13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13], Op, X],
      f: Case[X, T14, Op]): Aux[In, Tuple14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14], Op, f.Out] =
    new FoldLeft[In, Tuple14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14], Op] {
      type Out = f.Out
      def apply(zero: In, t: Tuple14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14]) =
        f(fold(zero, Tuple13(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13)), t._14)
    }
  implicit def t15[In, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, X, T15, Op](
      implicit fold: Aux[In, Tuple14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14], Op, X],
      f: Case[X, T15, Op])
      : Aux[In, Tuple15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15], Op, f.Out] =
    new FoldLeft[In, Tuple15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15], Op] {
      type Out = f.Out
      def apply(zero: In, t: Tuple15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15]) =
        f(fold(zero, Tuple14(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14)),
          t._15)
    }
  implicit def t16[In, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, X, T16, Op](
      implicit fold: Aux[In, Tuple15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15], Op, X],
      f: Case[X, T16, Op])
      : Aux[In, Tuple16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16], Op, f.Out] =
    new FoldLeft[In, Tuple16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16], Op] {
      type Out = f.Out
      def apply(zero: In, t: Tuple16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16]) =
        f(fold(zero,
          Tuple15(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15)),
          t._16)
    }
  implicit def t17[In, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, X, T17, Op](
      implicit fold: Aux[In, Tuple16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16], Op, X],
      f: Case[X, T17, Op])
      : Aux[In, Tuple17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17], Op, f.Out] =
    new FoldLeft[In, Tuple17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17], Op] {
      type Out = f.Out
      def apply(zero: In, t: Tuple17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17]) =
        f(fold(zero,
          Tuple16(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15,
            t._16)), t._17)
    }
  implicit def t18[In, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, X, T18, Op](
      implicit fold: Aux[In, Tuple17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17], Op,
        X], f: Case[X, T18, Op])
      : Aux[In, Tuple18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18], Op, f.Out] =
    new FoldLeft[In, Tuple18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18], Op] {
      type Out = f.Out
      def apply(zero: In, t: Tuple18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18]) =
        f(fold(zero,
          Tuple17(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15, t._16,
            t._17)), t._18)
    }
  implicit def t19[In, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, X, T19, Op](
      implicit fold: Aux[In, Tuple18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18],
        Op, X], f: Case[X, T19, Op]): Aux[In, Tuple19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15,
    T16, T17, T18, T19], Op, f.Out] =
    new FoldLeft[In, Tuple19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19],
      Op] {
      type Out = f.Out
      def apply(zero: In,
          t: Tuple19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19]) =
        f(fold(zero,
          Tuple18(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15, t._16,
            t._17, t._18)), t._19)
    }
  implicit def t20[
      In, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, X, T20, Op](
      implicit fold: Aux[In, Tuple19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18,
        T19], Op, X], f: Case[X, T20, Op]): Aux[In, Tuple20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14,
    T15, T16, T17, T18, T19, T20], Op, f.Out] =
    new FoldLeft[In, Tuple20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20],
      Op] {
      type Out = f.Out
      def apply(zero: In,
          t: Tuple20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20]) =
        f(fold(zero,
          Tuple19(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15, t._16,
            t._17, t._18, t._19)), t._20)
    }
  implicit def t21[
      In, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, X, T21, Op](
      implicit fold: Aux[In, Tuple20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18,
        T19, T20], Op, X], f: Case[X, T21, Op]): Aux[In, Tuple21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13,
    T14, T15, T16, T17, T18, T19, T20, T21], Op, f.Out] =
    new FoldLeft[In, Tuple21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20,
      T21], Op] {
      type Out = f.Out
      def apply(zero: In,
          t: Tuple21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21]) =
        f(fold(zero,
          Tuple20(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15, t._16,
            t._17, t._18, t._19, t._20)), t._21)
    }
  implicit def t22[
      In, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, X, T22, Op](
      implicit fold: Aux[In, Tuple21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18,
        T19, T20, T21], Op, X], f: Case[X, T22, Op]): Aux[In, Tuple22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12,
    T13, T14, T15, T16, T17, T18, T19, T20, T21, T22], Op, f.Out] =
    new FoldLeft[In, Tuple22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20,
      T21, T22], Op] {
      type Out = f.Out
      def apply(zero: In,
          t: Tuple22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21,
            T22]) =
        f(fold(zero,
          Tuple21(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15, t._16,
            t._17, t._18, t._19, t._20, t._21)), t._22)
    }
}
