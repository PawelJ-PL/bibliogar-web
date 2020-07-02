package com.github.pawelj_pl.bibliogar.api.testdoubles.utils.tracing

import cats.data.{EitherT, OptionT}
import cats.effect.Resource
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.tracing.Tracing
import kamon.context.Context

object DummyTracer {
  def instance[F[_]]: Tracing[F] = new Tracing[F] {
    override def currentContext: F[Context] = ???

    override def storeContext(newContext: Context): Resource[F, Unit] = ???

    override def createSpan[A](name: String, operation: F[A], tags: Map[String, Any]): F[A] = operation

    override def createEitherSpan[A, E](name: String, operation: EitherT[F, E, A], tags: Map[String, Any]): EitherT[F, E, A] = operation

    override def createOptionSpan[A](name: String, operation: OptionT[F, A], tags: Map[String, Any]): OptionT[F, A] = operation

    override def preserveContext[A](operation: F[A]): F[A] = operation

    override def withContext[A](operation: F[A], context: Context): F[A] = operation
  }
}
