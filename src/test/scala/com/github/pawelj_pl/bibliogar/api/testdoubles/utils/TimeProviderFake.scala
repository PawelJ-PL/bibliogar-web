package com.github.pawelj_pl.bibliogar.api.testdoubles.utils

import java.time.Instant

import cats.{Applicative, Monad}
import cats.mtl.MonadState
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.github.pawelj_pl.bibliogar.api.constants.UserConstants
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.TimeProvider

object TimeProviderFake extends UserConstants {
  case class TimeProviderState(actualTick: Long = NowMilis - 1000L)

  def instance[F[_]: Monad](implicit S: MonadState[F, TimeProviderState]): TimeProvider[F] = new TimeProvider[F] {

    override def time: F[Long] =
      S.modify(state => state.copy(actualTick = state.actualTick + 1000L)).flatMap(_ => S.get.map(state => state.actualTick))

    override def now: F[Instant] = time.map(Instant.ofEpochMilli)
  }

  def withFixedValue[F[_]: Applicative](value: Instant): TimeProvider[F] = new TimeProvider[F] {
    override def time: F[Long] = Applicative[F].pure(value.toEpochMilli)

    override def now: F[Instant] = Applicative[F].pure(value)
  }
}
