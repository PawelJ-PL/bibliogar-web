package com.github.pawelj_pl.bibliogar.api.infrastructure.utils

import cats.Monad
import cats.effect.{Console, Sync, SyncConsole}
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.github.pawelj_pl.bibliogar.api.infrastructure.config.Config

trait Correspondence[F[_]] {
  def sendMessage(to: String, subject: String, message: String): F[Unit]
}

object Correspondence {
  def apply[F[_]](implicit ev: Correspondence[F]): Correspondence[F] = ev
  def create[F[_]: Sync](notificationConfig: Config.CorrespondenceConfig): Correspondence[F] = notificationConfig match {
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

class EmailCorrespondence[F[_]](config: Config.CorrespondenceConfig.EmailCorrespondenceConfig) extends Correspondence[F] {
  override def sendMessage(to: String, subject: String, message: String): F[Unit] = {
    val _ = config
    ???
  }
}
