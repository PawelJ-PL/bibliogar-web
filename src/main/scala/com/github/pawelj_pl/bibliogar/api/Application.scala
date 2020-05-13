package com.github.pawelj_pl.bibliogar.api

import java.util.concurrent.Executors

import cats.effect.{Async, Blocker, ExitCode, IO, IOApp, Resource}
import cats.~>
import com.github.pawelj_pl.bibliogar.api.domain.device.DevicesService
import com.github.pawelj_pl.bibliogar.api.infrastructure.config.Config
import com.github.pawelj_pl.bibliogar.api.infrastructure.database.Database
import com.github.pawelj_pl.bibliogar.api.infrastructure.messagebus.Message
import com.github.pawelj_pl.bibliogar.api.infrastructure.messagebus.handlers.{EmailSenderHandler, FcmSenderHandler}
import com.github.pawelj_pl.bibliogar.api.infrastructure.tasks.Scheduler
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.{Correspondence, MessageComposer}
import com.github.pawelj_pl.fcm4s.http4s.Http4sBackend
import com.github.pawelj_pl.fcm4s.messaging.Messaging
import doobie.implicits._
import fs2.concurrent.Topic
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.server.blaze.BlazeServerBuilder
import scalacache.CatsEffect.modes._

import scala.concurrent.ExecutionContext

object Application extends IOApp {

  def serverResource: Resource[IO, Unit] =
    for {
      config     <- Config.load[IO]
      _          <- Resource.liftF(Database.migration[IO](config.database))
      blocker    <- Blocker[IO]
      transactor <- Database.transactor[IO](config.database)
      nbExecutionContext <- Resource.make(
        IO(ExecutionContext.fromExecutorService(Executors.newCachedThreadPool()))
      )(
        ec => IO(ec.shutdown())
      )
      dbTransaction = new (DB ~> IO) {
        override def apply[A](fa: DB[A]): IO[A] = fa.transact(transactor)
      }
      liftF = new (IO ~> DB) {
        override def apply[A](fa: IO[A]): DB[A] = Async[DB].liftIO(fa)
      }
      httpClient <- BlazeClientBuilder[IO](nbExecutionContext).resource
      messageTopic <- Resource.liftF(Topic[IO, Message](Message.TopicStarted))
      app = {
        implicit val dbToIO: DB ~> IO = dbTransaction
        implicit val IoToDb: IO ~> DB = liftF
        new BibliogarApp[IO](blocker, config, httpClient, messageTopic)
      }
      scheduler = {
        implicit val dbToIO: DB ~> IO = dbTransaction
        new Scheduler[IO](config).run
      }
      fcmHandler = {
        implicit val backend: Http4sBackend[IO] = Http4sBackend(httpClient)
        implicit val messaging: Messaging[IO] = Messaging.defaultIoMessaging(config.fcm.credentials)
        implicit val devicesService: DevicesService[IO] = app.devicesService
        new FcmSenderHandler[IO](messageTopic, config.fcm).handle
      }
      mailHandler = {
        implicit val messageComposerD: MessageComposer[IO] = MessageComposer.create[IO]
        implicit val correspondenceD: Correspondence[IO] = Correspondence.create[IO](config.correspondence)
        new EmailSenderHandler[IO](messageTopic, config.correspondence.maxTopicSize).handle
      }
      server = BlazeServerBuilder[IO].bindHttp(config.server.port, config.server.host).withHttpApp(app.httpApp).serve
      service <- Resource.liftF((scheduler concurrently server concurrently fcmHandler concurrently mailHandler).compile.drain)
    } yield service

  override def run(args: List[String]): IO[ExitCode] = serverResource.use(_ => IO.never)
}
