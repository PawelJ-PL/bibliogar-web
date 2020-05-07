package com.github.pawelj_pl.bibliogar.api.infrastructure.repositories

import cats.data.OptionT
import cats.syntax.functor._
import com.github.pawelj_pl.bibliogar.api.DB
import com.github.pawelj_pl.bibliogar.api.domain.device.{Device, DevicesRepositoryAlgebra, NotificationToken}
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

  override def delete(deviceIds: FUUID*): DB[Unit] =
    run {
      quote {
        devices.filter(d => liftQuery(deviceIds).contains(d.device_id)).delete
      }
    }.void

  override def findByUniqueIdAndUser(uniqueId: String, ownerId: FUUID): DB[List[Device]] = run {
    quote {
      devices.filter(d => d.uniqueId == lift(uniqueId) && d.ownerId == lift(ownerId))
    }
  }

  override def create(notificationToken: NotificationToken): DB[NotificationToken] =
    for {
      now <- TimeProvider[DB].now
      updatedToken = notificationToken.copy(createdAt = now, updatedAt = now)
      _ <- run(
        quote(
          notificationTokens
            .insert(lift(updatedToken))
            .onConflictUpdate(_.token)((t, e) => t.deviceId -> e.deviceId, (t, e) => t.updatedAt -> e.updatedAt)))
    } yield updatedToken

  override def findAllNotificationTokensOwnedByUser(userId: FUUID): DB[List[NotificationToken]] = run {
    quote {
      for {
        ownedDevices <- devices.filter(_.ownerId == lift(userId))
        token        <- notificationTokens.join(t => t.deviceId == ownedDevices.device_id)
      } yield token
    }
  }

  private val devices = quote {
    querySchema[Device](
      "devices",
      _.deviceDescription.brand -> "brand",
      _.deviceDescription.deviceId -> "device_info_id",
      _.deviceDescription.deviceName -> "device_name"
    )
  }

  private val notificationTokens = quote {
    querySchema[NotificationToken]("notification_tokens")
  }
}
