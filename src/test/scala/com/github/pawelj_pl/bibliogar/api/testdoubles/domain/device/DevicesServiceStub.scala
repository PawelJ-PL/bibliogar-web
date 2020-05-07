package com.github.pawelj_pl.bibliogar.api.testdoubles.domain.device

import cats.Functor
import cats.data.EitherT
import cats.mtl.MonadState
import cats.syntax.functor._
import com.github.pawelj_pl.bibliogar.api.DeviceError
import com.github.pawelj_pl.bibliogar.api.constants.{DeviceConstants, UserConstants}
import com.github.pawelj_pl.bibliogar.api.domain.device.{Device, DevicesService, NotificationToken}
import com.github.pawelj_pl.bibliogar.api.domain.user.ApiKey
import com.github.pawelj_pl.bibliogar.api.infrastructure.dto.devices.DeviceRegistrationReq
import com.vdurmont.semver4j.Semver
import io.chrisdavenport.fuuid.FUUID

object DevicesServiceStub extends UserConstants with DeviceConstants {
  final case class DevicesServiceState(
    isAppCompatible: Boolean = true,
    device: Device = ExampleDevice,
    apiKey: ApiKey = ExampleApiKey,
    maybeError: Option[DeviceError] = None,
    notificationTokens: List[NotificationToken] = List(NotificationToken(ExampleNotificationToken, ExampleDevice.device_id, Now, Now)))

  def instance[F[_]: Functor](implicit S: MonadState[F, DevicesServiceState]): DevicesService[F] = new DevicesService[F] {
    override def isAppCompatibleWithApi(appVersion: Semver): F[Boolean] = S.get.map(_.isAppCompatible)

    override def registerDevice(userId: FUUID, dto: DeviceRegistrationReq): F[(Device, ApiKey)] =
      S.get.map(state => (state.device, state.apiKey))

    override def unregisterDeviceAs(userId: FUUID, devicesApiKeyId: FUUID): EitherT[F, DeviceError, Unit] =
      EitherT(S.get.map(_.maybeError.toLeft((): Unit)))

    override def getNotificationTokensRelatedToUser(userId: FUUID): F[List[NotificationToken]] = S.get.map(_.notificationTokens)
  }
}
