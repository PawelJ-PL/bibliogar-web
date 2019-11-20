package com.github.pawelj_pl.bibliogar.api.doobie.setup

import java.time.Instant

import cats.Applicative
import cats.effect.{ContextShift, IO}
import com.github.pawelj_pl.bibliogar.api.DB
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.TimeProvider

trait TestImplicits {
  final val RepoTimestamp = Instant.ofEpochSecond(1573049401L)

  implicit val timeProviderD: TimeProvider[DB] = new TimeProvider[DB] {
    override def time: DB[Long] = Applicative[DB].pure(RepoTimestamp.getEpochSecond)

    override def now: DB[Instant] = Applicative[DB].pure(RepoTimestamp)
  }
  implicit val ioCs: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.global)
}
