package com.github.pawelj_pl.bibliogar.api.infrastructure.config

import cats.effect.{Resource, Sync}
import com.github.pawelj_pl.fcm4s.auth.config.CredentialsConfig
import com.typesafe.config.ConfigFactory
import cron4s.CronExpr
import pureconfig.{ConfigReader, ConfigSource}
import pureconfig.generic.semiauto.deriveReader

import scala.concurrent.duration.FiniteDuration

final case class Config(
  server: Config.ServerConfig,
  database: Config.DbConfig,
  auth: Config.AuthConfig,
  correspondence: Config.CorrespondenceConfig,
  tasks: Config.TasksConfig,
  mobileApp: Config.MobileAppConfig,
  fcm: Config.FcmConfig)

object Config {
  import CorrespondenceConfigModule._
  import Cron4sModule._
  import pureconfig.module.http4s._

  sealed trait CorrespondenceConfig

  object CorrespondenceConfig {
    final case class ConsoleCorrespondenceConfig() extends CorrespondenceConfig
    final case class EmailCorrespondenceConfig(smtpHost: String, smtpPort: Int) extends CorrespondenceConfig
  }

  final case class ServerConfig(host: String, port: Int)
  final case class DbConfig(url: String, user: String, password: String, poolSize: Int, driver: String)
  final case class RegistrationConfig(ttl: FiniteDuration)
  final case class ResetPasswordConfig(ttl: FiniteDuration)
  final case class AuthConfig(
    cryptRounds: Int,
    registration: RegistrationConfig,
    resetPassword: ResetPasswordConfig,
    cookie: CookieConfig,
    dummyPasswordHash: String)
  final case class RegistrationCleanerConfig(cron: CronExpr)
  final case class TasksConfig(registrationCleaner: RegistrationCleanerConfig)
  final case class CookieConfig(maxAge: FiniteDuration, secure: Boolean, httpOnly: Boolean)
  final case class MobileAppConfig(minRequiredMajor: Int)
  final case class FcmConfig(maxTopicSize: Int, credentials: CredentialsConfig)

  implicit val configReader: ConfigReader[Config] = deriveReader[Config]
  implicit val serverConfigReader: ConfigReader[ServerConfig] = deriveReader[ServerConfig]
  implicit val dbConfigReader: ConfigReader[DbConfig] = deriveReader[DbConfig]
  implicit val regConfigReader: ConfigReader[RegistrationConfig] = deriveReader[RegistrationConfig]
  implicit val authConfigReader: ConfigReader[AuthConfig] = deriveReader[AuthConfig]
  implicit val regCleanerConfigReader: ConfigReader[RegistrationCleanerConfig] = deriveReader[RegistrationCleanerConfig]
  implicit val tasksConfigReader: ConfigReader[TasksConfig] = deriveReader[TasksConfig]
  implicit val cookieConfigReader: ConfigReader[CookieConfig] = deriveReader[CookieConfig]
  implicit val resetPasswordConfigReader: ConfigReader[ResetPasswordConfig] = deriveReader[ResetPasswordConfig]
  implicit val mobileAppConfigReader: ConfigReader[MobileAppConfig] = deriveReader[MobileAppConfig]
  implicit val fcmConfigReader: ConfigReader[FcmConfig] = deriveReader[FcmConfig]
  implicit val fcmCredentialsConfigReader: ConfigReader[CredentialsConfig] = deriveReader[CredentialsConfig]

  def load[F[_]: Sync]: Resource[F, Config] =
    Resource.liftF[F, Config](Sync[F].delay(ConfigSource.fromConfig(ConfigFactory.load(getClass.getClassLoader)).loadOrThrow[Config]))
}
