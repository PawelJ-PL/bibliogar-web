package com.github.pawelj_pl.bibliogar.api.infrastructure.repositories

import cats.data.OptionT
import cats.syntax.functor._
import com.github.pawelj_pl.bibliogar.api.DB
import com.github.pawelj_pl.bibliogar.api.domain.device.{Device, DevicesRepositoryAlgebra}
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.TimeProvider
import io.chrisdavenport.fuuid.FUUID

class DoobieDevicesRepository(implicit timeProvider: TimeProvider[DB]) extends DevicesRepositoryAlgebra[DB] with BasePostgresRepository {
  import doobieContext._
  import dbImplicits._

  override def create(device: Device): DB[Device] =
    for {
      now <- TimeProvider[DB].now
      updatedDevice = device.copy(createdAt = now, updatedAt = now)
      _ <- run(quote(devices.insert(lift(updatedDevice))))
    } yield updatedDevice

  override def findById(deviceId: FUUID): OptionT[DB, Device] =
    OptionT(run(quote(devices.filter(_.device_id == lift(deviceId)))).map(_.headOption))

  override def delete(deviceId: FUUID): DB[Unit] =
    run {
      quote {
        devices.filter(_.device_id == lift(deviceId)).delete
      }
    }.void

  private val devices = quote {
    querySchema[Device](
      "devices",
      _.deviceDescription.brand -> "brand",
      _.deviceDescription.deviceId -> "device_info_id",
      _.deviceDescription.deviceName -> "device_name"
    )
  }
}
