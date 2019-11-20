package com.github.pawelj_pl.bibliogar.api.infrastructure.dto.devices

import com.github.pawelj_pl.bibliogar.api.domain.device.DeviceDescription
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

object Implicits extends DeviceDescriptionImplicits

trait DeviceDescriptionImplicits {
  implicit val DeviceDescriptionCirceEncoder: Encoder[DeviceDescription] = deriveEncoder[DeviceDescription]
  implicit val DeviceDescriptionCirceDecoder: Decoder[DeviceDescription] = deriveDecoder[DeviceDescription]
}