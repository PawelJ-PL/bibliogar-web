package com.github.pawelj_pl.bibliogar.api.infrastructure.messagebus.handlers

import cats.effect.Sync
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.show._
import cats.syntax.traverse._
import com.github.pawelj_pl.bibliogar.api.domain.device.DevicesService
import com.github.pawelj_pl.bibliogar.api.infrastructure.config.Config.FcmConfig
import com.github.pawelj_pl.bibliogar.api.infrastructure.messagebus.Message
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.tracing.{MessageEnvelope, Tracing}
import com.github.pawelj_pl.fcm4s.messaging.extrafields.Android
import com.github.pawelj_pl.fcm4s.messaging.{DataMessage, Destination, MessageDataEncoder, Messaging}
import fs2.INothing
import fs2.concurrent.Topic
import io.chrisdavenport.fuuid.FUUID
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

class FcmSenderHandler[F[_]: Sync: Messaging: DevicesService: Tracing](messageTopic: Topic[F, MessageEnvelope], fcmConfig: FcmConfig) {
  private val logger = Slf4jLogger.getLogger[F]

  val handle: fs2.Stream[F, INothing] = messageTopic
    .subscribe(fcmConfig.maxTopicSize)
    .evalMap(_.processWithContext(handleMessage))
    .drain

  private def handleMessage(message: Message): F[Unit] = {
    message match {
      case Message.NewLoan(loan)           => sendUpdateLoansMessage(loan.userId)
      case Message.LoanUpdated(loan)       => sendUpdateLoansMessage(loan.userId)
      case Message.NewLibrary(library)     => sendUpdateLibrariesMessage(library.ownerId)
      case Message.LibraryUpdated(library) => sendUpdateLibrariesMessage(library.ownerId)
      case Message.LibraryDeleted(library) => sendUpdateLibrariesMessage(library.ownerId)
      case msg                             => logger.trace(show"Ignoring message $msg")
    }
  }.handleErrorWith(err => logger.error(err)("Unable to process message"))

  private def sendUpdateLoansMessage(userId: FUUID): F[Unit] =
    generateDataMessagesForUser(userId, Map("messageType" -> "loansUpdate"), Some("loansUpdate"))
      .flatMap(sendMultipleMessages[Map[String, String]])

  private def sendUpdateLibrariesMessage(userId: FUUID): F[Unit] =
    generateDataMessagesForUser(userId, Map("messageType" -> "librariesUpdate"), Some("librariesUpdate"))
      .flatMap(sendMultipleMessages[Map[String, String]])

  private def sendMultipleMessages[A: MessageDataEncoder](messages: List[DataMessage[A]]): F[Unit] =
    for {
      result <- Tracing[F].preserveContext(Messaging[F].sendMany(messages))
      _ <- result.traverse {
        case Left(err) => logger.warn(show"Unable to send FCM message. Got status code: ${err.statusCode} and body ${err.body}")
        case Right(id) => logger.debug(show"send FCM message $id")
      }
    } yield ()

  private def generateDataMessagesForUser[A: MessageDataEncoder](
    userId: FUUID,
    data: A,
    collapseKey: Option[String]
  ): F[List[DataMessage[A]]] =
    DevicesService[F]
      .getNotificationTokensRelatedToUser(userId)
      .map(tokens =>
        tokens
          .map(token => DataMessage(Destination.Token(token.token), data, android = Some(Android(collapseKey = collapseKey)))))
}
