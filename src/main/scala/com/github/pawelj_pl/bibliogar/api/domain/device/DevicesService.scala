package com.github.pawelj_pl.bibliogar.api.domain.device

import cats.data.EitherT
import cats.effect.Sync
import cats.{Applicative, ~>}
import cats.syntax.apply._
import cats.syntax.bifunctor._
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.github.pawelj_pl.bibliogar.api.DeviceError
import com.github.pawelj_pl.bibliogar.api.domain.user.{ApiKey, ApiKeyRepositoryAlgebra, KeyType}
import com.github.pawelj_pl.bibliogar.api.infrastructure.config.Config.MobileAppConfig
import com.github.pawelj_pl.bibliogar.api.infrastructure.dto.devices.DeviceRegistrationReq
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.{RandomProvider, TimeProvider}
import com.vdurmont.semver4j.Semver
import io.chrisdavenport.fuuid.FUUID
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

trait DevicesService[F[_]] {
  def isAppCompatibleWithApi(appVersion: Semver): F[Boolean]
  def registerDevice(userId: FUUID, dto: DeviceRegistrationReq): F[(Device, ApiKey)]
  def unregisterDeviceAs(userId: FUUID, devicesApiKeyId: FUUID): EitherT[F, DeviceError, Unit]
}

object DevicesService {
  def apply[F[_]](implicit ev: DevicesService[F]): DevicesService[F] = ev

  def withDb[F[_]: Applicative, D[_]: Sync: TimeProvider: RandomProvider: DevicesRepositoryAlgebra: ApiKeyRepositoryAlgebra](
    mobileAppConfig: MobileAppConfig
  )(implicit dbToF: D ~> F
  ): DevicesService[F] = new DevicesService[F] {
    private val logD: Logger[D] = Slf4jLogger.getLogger[D]

    override def isAppCompatibleWithApi(appVersion: Semver): F[Boolean] =
      Applicative[F].pure(appVersion.getMajor >= mobileAppConfig.minRequiredMajor)

    override def registerDevice(userId: FUUID, dto: DeviceRegistrationReq): F[(Device, ApiKey)] =
      dbToF(for {
        device       <- dto.toDomain[D]
        savedDevice  <- DevicesRepositoryAlgebra[D].create(device)
        now          <- TimeProvider[D].now
        apiKeyId     <- RandomProvider[D].randomFuuid
        apiKeySecret <- RandomProvider[D].secureRandomString(64)
        apiKey = ApiKey(
          apiKeyId,
          apiKeySecret,
          userId,
          Some(savedDevice.device_id),
          KeyType.Device,
          Some(savedDevice.deviceDescription.asKeyDescription),
          enabled = true,
          None,
          now,
          now
        )
        savedKey <- ApiKeyRepositoryAlgebra[D].create(apiKey)
        _        <- logD.info(s"Assigned device ${savedDevice.device_id} to user $userId with API key ${savedKey.keyId}")
      } yield (savedDevice, savedKey))

    override def unregisterDeviceAs(userId: FUUID, devicesApiKeyId: FUUID): EitherT[F, DeviceError, Unit] =
      (for {
        apiKey <- ApiKeyRepositoryAlgebra[D]
          .findById(devicesApiKeyId)
          .toRight(DeviceError.DeviceIdNotFound(devicesApiKeyId))
          .leftWiden[DeviceError]
        _ <- EitherT
          .cond[D](apiKey.keyType == KeyType.Device, (), DeviceError.ApiKeyIsNotDeviceType(apiKey.keyId, apiKey.keyType))
          .leftWiden[DeviceError]
        deviceId <- EitherT.fromOption[D](apiKey.deviceId, DeviceError.ApiKeyNotRelatedToAnyDevice(apiKey.keyId)).leftWiden[DeviceError]
        _        <- EitherT.cond[D](apiKey.userId == userId, (), DeviceError.DeviceNotOwnedByUser(deviceId, userId)).leftWiden[DeviceError]
        _        <- EitherT.right[DeviceError](logD.info(s"Removing device $deviceId") *> DevicesRepositoryAlgebra[D].delete(deviceId))
      } yield ()).mapK(dbToF)
  }
}
