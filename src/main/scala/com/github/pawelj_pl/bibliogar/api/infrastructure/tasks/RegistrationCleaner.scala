package com.github.pawelj_pl.bibliogar.api.infrastructure.tasks

import cats.data.NonEmptyList
import cats.effect.Sync
import cats.~>
import cats.syntax.applicative._
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.github.pawelj_pl.bibliogar.api.domain.user.UserRepositoryAlgebra
import com.github.pawelj_pl.bibliogar.api.infrastructure.config.Config
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.TimeProvider
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.timeSyntax._
import cron4s.expr.CronExpr
import io.chrisdavenport.fuuid.FUUID
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

class RegistrationCleaner[F[_], D[_]: Sync: TimeProvider: UserRepositoryAlgebra](config: Config)(implicit dbToF: D ~> F)
    extends TaskDefinition[F] {
  private val logD: Logger[D] = Slf4jLogger.getLogger[D]

  override def cron: CronExpr = config.tasks.registrationCleaner.cron

  override def task: F[Unit] =
    dbToF(for {
      now <- TimeProvider[D].now
      outdatedRegistrations <- UserRepositoryAlgebra[D].findNotConfirmedAuthDataOlderThan(
        now.minus(config.auth.registration.ttl.toTemporal))
      _ <- NonEmptyList
        .fromList(outdatedRegistrations)
        .map(auths => deleteOutdated(auths.map(_.userId)))
        .getOrElse(().pure[D])
    } yield ())

  private def deleteOutdated(userIds: NonEmptyList[FUUID]): D[Unit] =
    for {
      _       <- logD.info(s"Following outdated registrations will be removed: ${userIds.toList.mkString("; ")}")
      removed <- UserRepositoryAlgebra[D].deleteByIds(userIds.toList: _*)
      _       <- logD.info(s"Removed $removed outdated registrations")
    } yield ()
}
