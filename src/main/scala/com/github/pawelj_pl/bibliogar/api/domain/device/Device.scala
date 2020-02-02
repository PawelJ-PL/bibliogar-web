package com.github.pawelj_pl.bibliogar.api.domain.device

import java.time.Instant

import io.chrisdavenport.fuuid.FUUID
import io.getquill.Embedded

final case class Device(device_id: FUUID, ownerId: FUUID, uniqueId: String, deviceDescription: DeviceDescription, createdAt: Instant, updatedAt: Instant)

final case class DeviceDescription(brand: String, deviceId: String, deviceName: String) extends Embedded {
  val asKeyDescription = s"$brand; $deviceId; $deviceName"
}
