package com.github.pawelj_pl.bibliogar.api.domain.device

import cats.data.OptionT
import io.chrisdavenport.fuuid.FUUID

trait DevicesRepositoryAlgebra[F[_]] {
  def create(device: Device): F[Device]
  def findById(deviceId: FUUID): OptionT[F, Device]
  def findByUniqueIdAndUser(uniqueId: String, ownerId: FUUID): F[List[Device]]
  def delete(deviceIds: FUUID*): F[Unit]

  def create(notificationToken: NotificationToken): F[NotificationToken]
  def findAllNotificationTokensOwnedByUser(userId: FUUID): F[List[NotificationToken]]
}

object DevicesRepositoryAlgebra {
  def apply[F[_]](implicit ev: DevicesRepositoryAlgebra[F]): DevicesRepositoryAlgebra[F] = ev
}