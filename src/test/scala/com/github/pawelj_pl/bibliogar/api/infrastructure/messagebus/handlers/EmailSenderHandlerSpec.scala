package com.github.pawelj_pl.bibliogar.api.infrastructure.messagebus.handlers

import cats.{Applicative, Monad}
import cats.data.StateT
import cats.effect.IO
import cats.mtl.instances.all._
import cats.mtl.MonadState
import com.github.pawelj_pl.bibliogar.api.constants.UserConstants
import com.github.pawelj_pl.bibliogar.api.domain.user.{TokenType, UserToken}
import com.github.pawelj_pl.bibliogar.api.infrastructure.messagebus.Message
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.{Correspondence, MessageComposer}
import com.github.pawelj_pl.bibliogar.api.testdoubles.messagebus.MessageTopicFake
import com.github.pawelj_pl.bibliogar.api.testdoubles.utils.{CorrespondenceMock, MessageComposerMock}
import com.github.pawelj_pl.bibliogar.api.testdoubles.utils.CorrespondenceMock.{Message => MailMessage}
import com.olegpy.meow.hierarchy.deriveMonadState
import com.softwaremill.diffx.scalatest.DiffMatcher
import fs2.concurrent.Topic
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class EmailSenderHandlerSpec extends AnyWordSpec with Matchers with DiffMatcher with UserConstants {
  case class TestState(
    notificationState: CorrespondenceMock.CorrespondenceState = CorrespondenceMock.CorrespondenceState(),
    messageTopicState: MessageTopicFake.MessageTopicState = MessageTopicFake.MessageTopicState())
  type TestEffect[A] = StateT[IO, TestState, A]

  val instance: EmailSenderHandler[TestEffect] = {
    implicit def notification[F[_]: MonadState[*[_], TestState]]: Correspondence[F] = CorrespondenceMock.instance[F]
    implicit def messageComposer[F[_]: Applicative]: MessageComposer[F] = MessageComposerMock.instance[F]
    def messageTopic[F[_]: Monad: MonadState[*[_], TestState]]: Topic[F, Message] = MessageTopicFake.instance[F]

    new EmailSenderHandler[TestEffect](messageTopic, 10)
  }

  final val ExampleRegistrationToken = UserToken("foo", ExampleUser.id, TokenType.Registration, Now, Now)
  final val ExampleResetPasswordToken = UserToken("bar", ExampleUser.id, TokenType.PasswordReset, Now, Now)

  "Handler" should {
    "handle new user messages" in {
      val messages = List(
        Message.UserCreated(ExampleUser, ExampleRegistrationToken),
        Message.TopicStarted,
        Message.UserCreated(ExampleUser.copy(email = "other@example.org"), ExampleRegistrationToken.copy(token = "aaa"))
      )
      val initialState = TestState(messageTopicState = MessageTopicFake.MessageTopicState(messages))
      val state = instance.handle.compile.drain.runS(initialState).unsafeRunSync()
      state.notificationState.sentMessages.toList should matchTo(List(
        MailMessage(ExampleUser.email, "Rejestracja w systemie Bibliogar", "newRegistration - token: foo"),
        MailMessage("other@example.org", "Rejestracja w systemie Bibliogar", "newRegistration - token: aaa")
      ))
    }

    "handle reset password requested messages" in {
      val messages = List(
        Message.PasswordResetRequested(ExampleUser, ExampleResetPasswordToken),
        Message.TopicStarted,
        Message.PasswordResetRequested(ExampleUser.copy(email = "other@example.org"), ExampleRegistrationToken.copy(token = "bbb"))
      )
      val initialState = TestState(messageTopicState = MessageTopicFake.MessageTopicState(messages))
      val state = instance.handle.compile.drain.runS(initialState).unsafeRunSync()
      state.notificationState.sentMessages.toList should matchTo(List(
        MailMessage(ExampleUser.email, "Bibliogar - reset hasła", "resetPassword - token: bar"),
        MailMessage("other@example.org", "Bibliogar - reset hasła", "resetPassword - token: bbb")
      ))
    }
  }
}
