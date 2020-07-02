package com.github.pawelj_pl.bibliogar.api.infrastructure.messagebus.handlers

import cats.{Applicative, Functor, Monad}
import cats.data.StateT
import cats.effect.IO
import cats.mtl.instances.all._
import cats.mtl.MonadState
import com.github.pawelj_pl.bibliogar.api.constants.{DeviceConstants, LibraryConstants, LoanConstants}
import com.github.pawelj_pl.bibliogar.api.domain.device.DevicesService
import com.github.pawelj_pl.bibliogar.api.infrastructure.config.Config.FcmConfig
import com.github.pawelj_pl.bibliogar.api.infrastructure.messagebus.Message
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.tracing.{MessageEnvelope, Tracing}
import com.github.pawelj_pl.bibliogar.api.testdoubles.domain.device.DevicesServiceStub
import com.github.pawelj_pl.bibliogar.api.testdoubles.messagebus.MessageTopicFake
import com.github.pawelj_pl.bibliogar.api.testdoubles.thirdparty.MessagingFake
import com.github.pawelj_pl.bibliogar.api.testdoubles.utils.tracing.DummyTracer
import com.github.pawelj_pl.fcm4s.auth.config.CredentialsConfig
import com.github.pawelj_pl.fcm4s.messaging.extrafields.Android
import com.github.pawelj_pl.fcm4s.messaging.{DataMessage, Destination, Messaging}
import com.olegpy.meow.hierarchy.deriveMonadState
import com.softwaremill.diffx.scalatest.DiffMatcher
import fs2.concurrent.Topic
import org.http4s.implicits._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class FcmSenderHandlerSpec
    extends AnyWordSpec
    with Matchers
    with LoanConstants
    with DiffMatcher
    with DeviceConstants
    with LibraryConstants {
  case class TestState(
    messageTopicState: MessageTopicFake.MessageTopicState = MessageTopicFake.MessageTopicState(),
    devicesServiceState: DevicesServiceStub.DevicesServiceState = DevicesServiceStub.DevicesServiceState(),
    messagingState: MessagingFake.MessagingState = MessagingFake.MessagingState())
  type TestEffect[A] = StateT[IO, TestState, A]

  final val ExampleFcmConfig =
    FcmConfig(10, CredentialsConfig("someClientId", "someClientEmail", uri"http://localhost/fcm", "someKey", "someKeyId", "someProjectId"))

  val instance: FcmSenderHandler[TestEffect] = {
    def messageTopic[F[_]: Monad: MonadState[*[_], TestState]]: Topic[F, MessageEnvelope] = MessageTopicFake.instance[F]
    implicit def devicesService[F[_]: Functor: Applicative: MonadState[*[_], TestState]]: DevicesService[F] = DevicesServiceStub.instance[F]
    implicit def messaging[F[_]: Functor: MonadState[*[_], TestState]]: Messaging[F] = MessagingFake.instance[F]
    implicit val tracing: Tracing[TestEffect] = DummyTracer.instance[TestEffect]

    new FcmSenderHandler[TestEffect](messageTopic, ExampleFcmConfig)
  }

  "Handler" should {
    "handle new loan messages" in {
      val rawMessages = List(
        Message.NewLoan(ExampleLoan),
        Message.TopicStarted,
        Message.NewLoan(ExampleLoan.copy(id = ExampleId1))
      )
      val messages = rawMessages.map(MessageEnvelope(_, None))
      val initialState = TestState(messageTopicState = MessageTopicFake.MessageTopicState(messages))
      val state = instance.handle.compile.drain.runS(initialState).unsafeRunSync()
      state.messagingState.messages should matchTo(
        List[Any](
          DataMessage(Destination.Token(ExampleNotificationToken),
                      Map("messageType" -> "loansUpdate"),
                      android = Some(Android(collapseKey = Some("loansUpdate")))),
          DataMessage(Destination.Token(ExampleNotificationToken),
                      Map("messageType" -> "loansUpdate"),
                      android = Some(Android(collapseKey = Some("loansUpdate"))))
        ))
    }

    "handle loan updated messages" in {
      val rawMessages = List(
        Message.LoanUpdated(ExampleLoan),
        Message.TopicStarted,
        Message.LoanUpdated(ExampleLoan.copy(id = ExampleId1))
      )
      val messages = rawMessages.map(MessageEnvelope(_, None))
      val initialState = TestState(messageTopicState = MessageTopicFake.MessageTopicState(messages))
      val state = instance.handle.compile.drain.runS(initialState).unsafeRunSync()
      state.messagingState.messages should matchTo(
        List[Any](
          DataMessage(Destination.Token(ExampleNotificationToken),
                      Map("messageType" -> "loansUpdate"),
                      android = Some(Android(collapseKey = Some("loansUpdate")))),
          DataMessage(Destination.Token(ExampleNotificationToken),
                      Map("messageType" -> "loansUpdate"),
                      android = Some(Android(collapseKey = Some("loansUpdate"))))
        ))
    }

    "handle library created messages" in {
      val rawMessages = List(
        Message.NewLibrary(ExampleLibrary),
        Message.TopicStarted,
        Message.NewLibrary(ExampleLibrary.copy(id = ExampleId1))
      )
      val messages = rawMessages.map(MessageEnvelope(_, None))
      val initialState = TestState(messageTopicState = MessageTopicFake.MessageTopicState(messages))
      val state = instance.handle.compile.drain.runS(initialState).unsafeRunSync()
      state.messagingState.messages should matchTo(
        List[Any](
          DataMessage(Destination.Token(ExampleNotificationToken),
                      Map("messageType" -> "librariesUpdate"),
                      android = Some(Android(collapseKey = Some("librariesUpdate")))),
          DataMessage(Destination.Token(ExampleNotificationToken),
                      Map("messageType" -> "librariesUpdate"),
                      android = Some(Android(collapseKey = Some("librariesUpdate"))))
        ))
    }

    "handle library deleted messages" in {
      val rawMessages = List(
        Message.LibraryDeleted(ExampleLibrary),
        Message.TopicStarted,
        Message.LibraryDeleted(ExampleLibrary.copy(id = ExampleId1))
      )
      val messages = rawMessages.map(MessageEnvelope(_, None))
      val initialState = TestState(messageTopicState = MessageTopicFake.MessageTopicState(messages))
      val state = instance.handle.compile.drain.runS(initialState).unsafeRunSync()
      state.messagingState.messages should matchTo(
        List[Any](
          DataMessage(Destination.Token(ExampleNotificationToken),
            Map("messageType" -> "librariesUpdate"),
            android = Some(Android(collapseKey = Some("librariesUpdate")))),
          DataMessage(Destination.Token(ExampleNotificationToken),
            Map("messageType" -> "librariesUpdate"),
            android = Some(Android(collapseKey = Some("librariesUpdate"))))
        ))
    }

    "handle library updated messages" in {
      val rawMessages = List(
        Message.LibraryUpdated(ExampleLibrary),
        Message.TopicStarted,
        Message.LibraryUpdated(ExampleLibrary.copy(id = ExampleId1))
      )
      val messages = rawMessages.map(MessageEnvelope(_, None))
      val initialState = TestState(messageTopicState = MessageTopicFake.MessageTopicState(messages))
      val state = instance.handle.compile.drain.runS(initialState).unsafeRunSync()
      state.messagingState.messages should matchTo(
        List[Any](
          DataMessage(Destination.Token(ExampleNotificationToken),
            Map("messageType" -> "librariesUpdate"),
            android = Some(Android(collapseKey = Some("librariesUpdate")))),
          DataMessage(Destination.Token(ExampleNotificationToken),
            Map("messageType" -> "librariesUpdate"),
            android = Some(Android(collapseKey = Some("librariesUpdate"))))
        ))
    }
  }
}
