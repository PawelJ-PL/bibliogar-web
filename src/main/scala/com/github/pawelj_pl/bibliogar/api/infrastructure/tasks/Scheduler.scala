package com.github.pawelj_pl.bibliogar.api.infrastructure.tasks

import cats.data.NonEmptyList
import cats.effect.{Clock, Concurrent, Timer}
import cats.~>
import com.github.pawelj_pl.bibliogar.api.DB
import com.github.pawelj_pl.bibliogar.api.infrastructure.config.Config
import com.github.pawelj_pl.bibliogar.api.infrastructure.repositories.DoobieUserRepository
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.TimeProvider
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.tracing.Tracing
import eu.timepit.fs2cron.schedule
import org.log4s.getLogger

class Scheduler[F[_]: Concurrent: Timer: Tracing](config: Config)(implicit dbToF: DB ~> F) {
  private[this] val logger = getLogger

  private implicit val clockD: Clock[DB] = Clock.create[DB]
  private implicit val timeProviderD: TimeProvider[DB] = TimeProvider.create[DB]
  private implicit val userRepository: DoobieUserRepository = new DoobieUserRepository

  private val tasks: NonEmptyList[TaskDefinition[F]] = NonEmptyList.of(
    new RegistrationCleaner[F, DB](config)
  )

  def run: fs2.Stream[F, Unit] = {
    val entries = tasks
      .map(t =>
        t.cron -> fs2.Stream.eval[F, Unit](t.task).handleErrorWith { err =>
          fs2.Stream.emit(logger.error(err)("Error during periodic task"))
      })

    schedule(entries.toList)
  }
}
