package com.github.pawelj_pl.bibliogar.api.infrastructure.dto.devices

import io.chrisdavenport.fuuid.circe._
import io.chrisdavenport.fuuid.FUUID
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

final case class DeviceRegistrationResp(deviceId: FUUID, deviceApiKey: String)

object DeviceRegistrationResp {
  implicit val encoder: Encoder[DeviceRegistrationResp] = deriveEncoder[DeviceRegistrationResp]
  implicit val decoder: Decoder[DeviceRegistrationResp] = deriveDecoder[DeviceRegistrationResp]
}