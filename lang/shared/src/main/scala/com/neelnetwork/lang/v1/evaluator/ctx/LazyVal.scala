package com.neelnetwork.lang.v1.evaluator.ctx

import cats.data.EitherT
import cats.implicits._
import com.neelnetwork.lang.ExprEvaluator.LogCallback
import com.neelnetwork.lang.TrampolinedExecResult
import com.neelnetwork.lang.v1.compiler.Terms.EVALUATED
import monix.eval.Coeval

sealed trait LazyVal {
  val value: TrampolinedExecResult[EVALUATED]
}

object LazyVal {
  private case class LazyValImpl(v: TrampolinedExecResult[EVALUATED], lc: LogCallback) extends LazyVal {
    override val value: TrampolinedExecResult[EVALUATED] = EitherT(
        Coeval.evalOnce(
          v.value
            .flatTap(a => Coeval.evalOnce(lc(a)))
            .apply()
        )
    )
  }

  def apply(v: TrampolinedExecResult[EVALUATED], lc: LogCallback = _ => ()): LazyVal = LazyValImpl(v, lc)
}
