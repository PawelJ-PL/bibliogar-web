package com.github.pawelj_pl.bibliogar.api.infrastructure.utils.tracing

import kamon.Kamon
import kamon.context.Context

import scala.concurrent.ExecutionContext

class ContextAwareExecutionContext(underlying: ExecutionContext) extends ExecutionContext {
  override def execute(runnable: Runnable): Unit = {
    val context: Context = Kamon.currentContext()
    underlying.execute(() => Kamon.runWithContext(context)(runnable.run()))
  }

  override def reportFailure(cause: Throwable): Unit = underlying.reportFailure(cause)
}

object ContextAwareExecutionContext {
  def apply(underlying: ExecutionContext): ContextAwareExecutionContext = new ContextAwareExecutionContext(underlying)
}