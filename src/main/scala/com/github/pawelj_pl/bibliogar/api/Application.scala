package com.github.pawelj_pl.bibliogar.api

import java.util.concurrent.Executors

import cats.effect.{Async, Blocker, Clock, ContextShift, ExitCode, IO, IOApp, Resource, Timer}
import cats.~>
import com.github.pawelj_pl.bibliogar.api.domain.device.DevicesService
import com.github.pawelj_pl.bibliogar.api.infrastructure.config.Config
import com.github.pawelj_pl.bibliogar.api.infrastructure.database.Database
import com.github.pawelj_pl.bibliogar.api.infrastructure.messagebus.Message
import com.github.pawelj_pl.bibliogar.api.infrastructure.messagebus.handlers.{EmailSenderHandler, FcmSenderHandler}
import com.github.pawelj_pl.bibliogar.api.infrastructure.tasks.Scheduler
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.tracing.{ContextAwareExecutionContext, MessageEnvelope, Tracing}
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.{Correspondence, CryptProvider, MessageComposer, tracing}
import com.github.pawelj_pl.fcm4s.http4s.Http4sBackend
import com.github.pawelj_pl.fcm4s.messaging.Messaging
import doobie.implicits._
import fs2.concurrent.Topic
import kamon.Kamon
import kamon.http4s.middleware.client.KamonSupport
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.server.blaze.BlazeServerBuilder
import scalacache.CatsEffect.modes._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

object Application extends IOApp {
  Kamon.init()

  private val ioAppEcField = Class.forName("cats.effect.internals.PoolUtils$").getDeclaredField("ioAppGlobal")
  ioAppEcField.setAccessible(true)
  private val ioAppEc = ioAppEcField.get(ExecutionContext.getClass).asInstanceOf[ExecutionContext]

  override implicit val contextShift: ContextShift[IO] = IO.contextShift(ContextAwareExecutionContext(ioAppEc))

  def ttm(underlying: Timer[IO]): Timer[IO] = new Timer[IO] {
    override def clock: Clock[IO] = underlying.clock

    override def sleep(duration: FiniteDuration): IO[Unit] = IO(Kamon.currentContext()).flatMap(ctx => underlying.sleep(duration).guarantee(IO(Kamon.storeContext(ctx)).void))
  }

  override implicit val timer: Timer[IO] = ttm(super.timer)

  def serverResource: Resource[IO, Unit] =
    for {
      config      <- Config.load[IO]
      _           <- Resource.liftF(Database.migration[IO](config.database))
      baseBlocker <- Blocker[IO]
      blocker = Blocker.liftExecutionContext(ContextAwareExecutionContext(baseBlocker.blockingContext))
      transactor <- Database.transactor[IO](config.database)
      baseNbExecutionContext <- Resource.make(
        IO(ExecutionContext.fromExecutorService(Executors.newCachedThreadPool()))
      )(
        ec => IO(ec.shutdown())
      )
      nbExecutionContext = ContextAwareExecutionContext(baseNbExecutionContext)
      dbTransaction = new (DB ~> IO) {
        override def apply[A](fa: DB[A]): IO[A] = fa.transact(transactor)
      }
      liftF = new (IO ~> DB) {
        override def apply[A](fa: IO[A]): DB[A] = Async[DB].liftIO(fa)
      }
      httpClient   <- BlazeClientBuilder[IO](nbExecutionContext).withExecutionContext(nbExecutionContext).resource
      messageTopic <- Resource.liftF(Topic[IO, MessageEnvelope](tracing.MessageEnvelope(Message.TopicStarted)))
      app = {
        implicit val dbToIO: DB ~> IO = dbTransaction
        implicit val IoToDb: IO ~> DB = liftF
        new BibliogarApp[IO](blocker, config, httpClient, messageTopic)
      }
      scheduler = {
        implicit val tracingF: Tracing[IO] = Tracing.usingKamon()
        implicit val dbToIO: DB ~> IO = dbTransaction
        new Scheduler[IO](config).run
      }
      fcmHandler = {
        val tracedClient = KamonSupport(httpClient)
        implicit val backend: Http4sBackend[IO] = Http4sBackend(tracedClient)
        implicit val messaging: Messaging[IO] = Messaging.defaultIoMessaging(config.fcm.credentials)
        implicit val devicesService: DevicesService[IO] = app.devicesService
        implicit val tracingF: Tracing[IO] = Tracing.usingKamon()
        new FcmSenderHandler[IO](messageTopic, config.fcm).handle
      }
      mailHandler = {
        implicit val tracingF: Tracing[IO] = Tracing.usingKamon()
        implicit val cryptProvider: CryptProvider[IO] = CryptProvider.create(config.auth.cryptRounds)
        implicit val messageComposerD: MessageComposer[IO] = MessageComposer.create[IO]
        implicit val correspondenceD: Correspondence[IO] = Correspondence.create[IO](config.correspondence)
        new EmailSenderHandler[IO](messageTopic, config.correspondence.maxTopicSize).handle
      }
      server = BlazeServerBuilder[IO]
        .withExecutionContext(nbExecutionContext)
        .bindHttp(config.server.port, config.server.host)
        .withHttpApp(app.httpApp)
        .serve
      service <- Resource.liftF((scheduler concurrently server concurrently fcmHandler concurrently mailHandler).compile.drain)
    } yield service

  override def run(args: List[String]): IO[ExitCode] = serverResource.use(_ => IO.never)
}
