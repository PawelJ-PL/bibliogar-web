package com.github.pawelj_pl.bibliogar.api

import cats.effect.{Blocker, ExitCode, IO, IOApp, Resource}
import cats.~>
import com.github.pawelj_pl.bibliogar.api.infrastructure.config.Config
import com.github.pawelj_pl.bibliogar.api.infrastructure.database.Database
import com.github.pawelj_pl.bibliogar.api.infrastructure.tasks.Scheduler
import doobie.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import scalacache.CatsEffect.modes._

object Application extends IOApp {
  def serverResource: Resource[IO, Unit] =
    for {
      config     <- Config.load[IO]
      _          <- Resource.liftF(Database.migration[IO](config.database))
      blocker    <- Blocker[IO]
      transactor <- Database.transactor[IO](config.database)
      dbTransaction = new (DB ~> IO) {
        override def apply[A](fa: DB[A]): IO[A] = fa.transact(transactor)
      }
      app = {
        implicit val dbToIO: DB ~> IO = dbTransaction
        new BibliogarApp[IO](blocker, config)
      }
      scheduler = {
        implicit val dbToIO: DB ~> IO = dbTransaction
        new Scheduler[IO](config).run
      }
      server = BlazeServerBuilder[IO].bindHttp(config.server.port, config.server.host).withHttpApp(app.httpApp).serve
      service <- Resource.liftF((scheduler concurrently server).compile.drain)
    } yield service

  override def run(args: List[String]): IO[ExitCode] = serverResource.use(_ => IO.never)
}
