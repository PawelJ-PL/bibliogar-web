package com.github.pawelj_pl.bibliogar.api.infrastructure.messagebus.handlers

import cats.effect.Sync
import cats.syntax.applicativeError._
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.show._
import com.github.pawelj_pl.bibliogar.api.infrastructure.messagebus.Message
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.tracing.{MessageEnvelope, Tracing}
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.{Correspondence, MessageComposer}
import fs2.INothing
import fs2.concurrent.Topic
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

class EmailSenderHandler[F[_]: Sync: MessageComposer: Correspondence: Tracing](messageTopic: Topic[F, MessageEnvelope], maxTopicSize: Int) {
  private val logger = Slf4jLogger.getLogger[F]

  val handle: fs2.Stream[F, INothing] = messageTopic
    .subscribe(maxTopicSize)
    .evalMap(_.processWithContext(handleMessage))
    .drain

  private def handleMessage(message: Message): F[Unit] = {
    message match {
      case Message.UserCreated(user, registrationToken) =>
        val variables = Map("token" -> registrationToken.token)
        sendFromTemplate("newRegistration", user.email, "Rejestracja w systemie Bibliogar", variables) *>
          logger.info(show"Registration message has been sent to user related to registration ${user.id}")
      case Message.PasswordResetRequested(user, resetToken) =>
        val variables = Map("token" -> resetToken.token)
        sendFromTemplate("resetPassword", user.email, "Bibliogar - reset hasła", variables) *>
          logger.info(show"Password reset message has been sent to user ${user.id}")
      case msg => logger.trace(show"Ignoring message $msg")
    }
  }.handleErrorWith(err => logger.error(err)("Unable to process message"))

  private def sendFromTemplate(templateName: String, to: String, subject: String, variables: Map[String, AnyRef]): F[Unit] = {
    for {
      message <- MessageComposer[F].generateMessage(templateName, variables)
      _       <- Tracing[F].preserveContext(Correspondence[F].sendMessage(to, subject, message))
    } yield ()
  }
}
