package com.github.pawelj_pl.bibliogar.api.infrastructure.dto.devices

import cats.Monad
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.github.pawelj_pl.bibliogar.api.domain.device.{Device, DeviceDescription, NotificationToken}
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.{RandomProvider, TimeProvider}
import io.chrisdavenport.fuuid.FUUID
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

final case class DeviceRegistrationReq(uniqueId: String, notificationToken: Option[String], deviceDescription: DeviceDescription) {
  def toDomain[F[_]: Monad: RandomProvider: TimeProvider](owner: FUUID): F[(Device, Option[NotificationToken])] =
    for {
      deviceId <- RandomProvider[F].randomFuuid
      now      <- TimeProvider[F].now
    } yield
      (
        Device(deviceId, owner, uniqueId, deviceDescription, now, now),
        notificationToken.map(NotificationToken(_, deviceId, now, now))
      )
}

object DeviceRegistrationReq extends DeviceDescriptionImplicits {
  implicit val encoder: Encoder[DeviceRegistrationReq] = deriveEncoder[DeviceRegistrationReq]
  implicit val decoder: Decoder[DeviceRegistrationReq] = deriveDecoder[DeviceRegistrationReq]
}
