package com.github.pawelj_pl.bibliogar.api.doobie.setup

import cats.effect.{Async, Blocker, ContextShift}
import com.zaxxer.hikari.HikariDataSource
import doobie.HC
import doobie.util.transactor.Transactor

trait TestDatabase {
  private val ds = new HikariDataSource()

  def tx[F[_]: Async: ContextShift]: Transactor[F] = {
    val dbHost = sys.env.getOrElse("BIBLIOGAR_TESTDB_HOST", "localhost")
    val dbPort = sys.env.getOrElse("BIBLIOGAR_TESTDB_PORT", "5432")
    val dbName = sys.env.getOrElse("BIBLIOGAR_TESTDB_NAME", "bibliogar")
    val dbUser = sys.env.getOrElse("BIBLIOGAR_TESTDB_USER", "bibliogar")
    val dbPassword = sys.env.getOrElse("BIBLIOGAR_TESTDB_PASSWORD", "secret")

    ds.setDataSourceClassName("org.postgresql.ds.PGSimpleDataSource")
    ds.addDataSourceProperty("url", s"jdbc:postgresql://$dbHost:$dbPort/$dbName")
    ds.addDataSourceProperty("user", dbUser)
    ds.addDataSourceProperty("password", dbPassword)
    ds.setPoolName("HikariPool-Bibliogar-IT")
    ds.setMaximumPoolSize(1)

    val ec = scala.concurrent.ExecutionContext.global
    val transactor = Transactor.fromDataSource[F](ds, ec, Blocker.liftExecutionContext(ec))
    Transactor.after.set(transactor, HC.rollback)
  }

  def closeDataSource(): Unit = ds.close()
}
