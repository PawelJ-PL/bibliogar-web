package com.github.pawelj_pl.bibliogar.api.infrastructure.database

import java.sql.DriverManager

import cats.effect.{Async, Blocker, ContextShift, Resource, Sync}
import cats.syntax.applicative._
import com.github.pawelj_pl.bibliogar.api.infrastructure.config.Config
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.tracing.ContextAwareExecutionContext
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor

object Database {
  final val LiquibaseChangelogMaster = "db/changelog/changelog-master.yml"

  def migration[F[_]: Sync](config: Config.DbConfig): F[Unit] =
    (for {
      connection <- Resource.fromAutoCloseable(
        Sync[F].delay(DriverManager.getConnection(config.url, config.user, config.password))
      )
      db <- Resource.make(
        Sync[F].delay(
          DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection))
        )
      )(d => Sync[F].delay(d.close()))
      liquibase <- Resource.liftF(
        Sync[F].delay(new Liquibase(LiquibaseChangelogMaster, new ClassLoaderResourceAccessor(getClass.getClassLoader), db))
      )
    } yield liquibase)
      .use(l => Sync[F].delay(l.update("main")))

  def transactor[F[_]: Async: ContextShift](dbConfig: Config.DbConfig): Resource[F, HikariTransactor[F]] =
    for {
      baseConnectionEc <- ExecutionContexts.fixedThreadPool[F](dbConfig.poolSize)
      connectionEc = ContextAwareExecutionContext(baseConnectionEc)
      baseBlocker <- Blocker[F]
      txEc = Blocker.liftExecutionContext(ContextAwareExecutionContext(baseBlocker.blockingContext))
      transactor <- HikariTransactor
        .newHikariTransactor(dbConfig.driver, dbConfig.url, dbConfig.user, dbConfig.password, connectionEc, txEc)
        .map(tx => {
          tx.configure(ds => {
            ds.setPoolName("HikariPool-Bibliogar")
            ds.setMaximumPoolSize(dbConfig.poolSize).pure[F]
          })
          tx
        })
    } yield transactor
}
