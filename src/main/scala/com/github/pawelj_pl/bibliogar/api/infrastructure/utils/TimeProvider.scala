package com.github.pawelj_pl.bibliogar.api.infrastructure.utils

import java.time.temporal.TemporalAmount
import java.time.{Duration, Instant}

import cats.Functor
import cats.effect.Clock
import cats.syntax.functor._

import scala.concurrent.duration.{FiniteDuration, MILLISECONDS}

trait TimeProvider[F[_]] {
  def time: F[Long]
  def now: F[Instant]
}

object TimeProvider {
  def apply[F[_]](implicit ev: TimeProvider[F]): TimeProvider[F] = ev
  def create[F[_]: Functor: Clock]: TimeProvider[F] = new TimeProvider[F] {
    override def time: F[Long] = Clock[F].realTime(MILLISECONDS)

    override def now: F[Instant] = time.map(Instant.ofEpochMilli)
  }
}

object timeSyntax {
  implicit class TemporalDuration(duration: FiniteDuration) {
    def toTemporal: TemporalAmount = Duration.ofMillis(duration.toMillis)
  }
}
