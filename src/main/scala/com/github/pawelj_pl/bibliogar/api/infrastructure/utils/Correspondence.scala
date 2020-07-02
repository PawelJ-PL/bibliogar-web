package com.github.pawelj_pl.bibliogar.api.infrastructure.utils

import cats.Monad
import cats.effect.{Async, Console, SyncConsole}
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.github.pawelj_pl.bibliogar.api.infrastructure.config.Config
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.tracing.Tracing
import kamon.Kamon
import org.simplejavamail.api.mailer.AsyncResponse
import org.simplejavamail.api.mailer.config.TransportStrategy
import org.simplejavamail.email.EmailBuilder
import org.simplejavamail.mailer.MailerBuilder

trait Correspondence[F[_]] {
  def sendMessage(to: String, subject: String, message: String): F[Unit]
}

object Correspondence {
  def apply[F[_]](implicit ev: Correspondence[F]): Correspondence[F] = ev
  def create[F[_]: Async: Tracing: CryptProvider](notificationConfig: Config.CorrespondenceConfig): Correspondence[F] =
    notificationConfig match {
      case _: Config.CorrespondenceConfig.ConsoleCorrespondenceConfig    => new ConsoleCorrespondence[F](SyncConsole.stdio[F])
      case config: Config.CorrespondenceConfig.EmailCorrespondenceConfig => new EmailCorrespondence[F](config)
    }
}

class ConsoleCorrespondence[F[_]: Monad](console: Console[F]) extends Correspondence[F] {
  override def sendMessage(to: String, subject: String, message: String): F[Unit] = {
    for {
      _ <- console.putStrLn("*" * 40)
      _ <- console.putStrLn(s"Recipient: $to")
      _ <- console.putStrLn(s"Subject: $subject")
      _ <- console.putStrLn("Message:")
      _ <- console.putStrLn(message)
      _ <- console.putStrLn("*" * 40)
    } yield ()
  }
}

class EmailCorrespondence[F[_]: Async: Tracing: CryptProvider](config: Config.CorrespondenceConfig.EmailCorrespondenceConfig)
    extends Correspondence[F] {
  private val transportStrategy = if (config.tlsRequired) TransportStrategy.SMTP_TLS else TransportStrategy.SMTP

  private val mailer = MailerBuilder
    .withSMTPServerHost(config.smtpHost)
    .withSMTPServerPort(config.smtpPort)
    .withSMTPServerUsername(config.username.orNull)
    .withSMTPServerPassword(config.password.orNull)
    .withTransportStrategy(transportStrategy)
    .buildMailer()

  override def sendMessage(to: String, subject: String, message: String): F[Unit] = {
    val traceId = Kamon.currentSpan().trace.id.string //TODO should be in effect
    val email = EmailBuilder
      .startingBlank()
      .from(config.sender)
      .to(to)
      .withSubject(subject)
      .withPlainText(message)
      .withHeader("X-Trace-Id", traceId)
      .buildEmail()
    val response: AsyncResponse = mailer.sendMail(email, true)
    Tracing[F].createSpan(
      "Send email",
      Async[F].async[Unit] { cb =>
        response.onSuccess(() => cb(Right((): Unit)))
        response.onException(err => cb(Left(err)))
      },
      Map("sendTo" -> CryptProvider[F].encodeSha256(to))
    )
  }
}
