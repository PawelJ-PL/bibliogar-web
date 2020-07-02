package com.github.pawelj_pl.bibliogar.api.infrastructure.utils.tracing

import cats.data.{EitherT, OptionT}
import cats.effect.{Resource, Sync}
import cats.effect.syntax.bracket._
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import cats.syntax.functor._
import kamon.Kamon
import kamon.context.Context
import kamon.tag.TagSet

trait Tracing[F[_]] {
  def currentContext: F[Context]
  def storeContext(newContext: Context): Resource[F, Unit]
  def createSpan[A](name: String, operation: F[A], tags: Map[String, Any] = Map.empty): F[A]
  def createEitherSpan[A, E](name: String, operation: EitherT[F, E, A], tags: Map[String, Any] = Map.empty): EitherT[F, E, A]
  def createOptionSpan[A](name: String, operation: OptionT[F, A], tags: Map[String, Any] = Map.empty): OptionT[F, A]
  def preserveContext[A](operation: F[A]): F[A]
  def withContext[A](operation: F[A], context: Context): F[A]
}

object Tracing {
  def apply[F[_]](implicit ev: Tracing[F]): Tracing[F] = ev

  def usingKamon[F[_]: Sync](): Tracing[F] = new Tracing[F] {

    override val currentContext: F[Context] = Sync[F].delay(Kamon.currentContext())

    override def storeContext(newContext: Context): Resource[F, Unit] = Resource.suspend {
      currentContext.map(rootContext =>
        Resource.make(Sync[F].delay(Kamon.storeContext(newContext)).void)(_ => Sync[F].delay(Kamon.storeContext(rootContext)).void))
    }

    override def createSpan[A](name: String, operation: F[A], tags: Map[String, Any] = Map.empty): F[A] =
      (for {
        span    <- Resource.make(Sync[F].delay(Kamon.spanBuilder(name).tag(TagSet.from(tags)).start()))(span => Sync[F].delay(span.finish()))
        context <- Resource.liftF(currentContext).map(c => c.withEntry(kamon.trace.Span.Key, span))
        _       <- storeContext(context)
      } yield span).use(span => operation.onError { case err => Sync[F].delay(span.fail(err)).void })

    override def createEitherSpan[A, E](name: String, operation: EitherT[F, E, A], tags: Map[String, Any]): EitherT[F, E, A] =
      EitherT(createSpan(name, operation.value, tags))

    override def createOptionSpan[A](name: String, operation: OptionT[F, A], tags: Map[String, Any]): OptionT[F, A] =
      OptionT(createSpan(name, operation.value, tags))

    override def preserveContext[A](operation: F[A]): F[A] =
      currentContext.flatMap(ctx => operation.guarantee(Sync[F].delay(Kamon.storeContext(ctx)).void))

    override def withContext[A](operation: F[A], context: Context): F[A] = Sync[F].delay(Kamon.runWithContext(context)(operation)).flatten
  }
}
