package com.github.pawelj_pl.bibliogar.api.testdoubles.repositories

import cats.Monad
import cats.data.OptionT
import cats.instances.string._
import cats.syntax.eq._
import cats.syntax.functor._
import cats.mtl.MonadState
import com.github.pawelj_pl.bibliogar.api.domain.device.{Device, DevicesRepositoryAlgebra, NotificationToken}
import io.chrisdavenport.fuuid.FUUID

object DevicesRepositoryFake {
  final case class DevicesRepositoryState(devices: Set[Device] = Set.empty, notificationTokens: Set[NotificationToken] = Set.empty)

  def instance[F[_]: Monad](implicit S: MonadState[F, DevicesRepositoryState]): DevicesRepositoryAlgebra[F] =
    new DevicesRepositoryAlgebra[F] {
      override def create(device: Device): F[Device] = S.modify(state => state.copy(devices = state.devices + device)).map(_ => device)

      override def findById(deviceId: FUUID): OptionT[F, Device] = OptionT(S.get.map(_.devices.find(_.device_id == deviceId)))

      override def delete(deviceIds: FUUID*): F[Unit] =
        S.modify(state => state.copy(devices = state.devices.filter(d => !deviceIds.contains(d.device_id))))

      override def findByUniqueIdAndUser(uniqueId: String, ownerId: FUUID): F[List[Device]] =
        S.get.map(_.devices.filter(d => d.uniqueId === uniqueId && d.ownerId === ownerId).toList)

      override def create(notificationToken: NotificationToken): F[NotificationToken] =
        S.modify(state => state.copy(notificationTokens = state.notificationTokens + notificationToken)).map(_ => notificationToken)

      override def findAllNotificationTokensOwnedByUser(userId: FUUID): F[List[NotificationToken]] =
        S.get.map(state => {
          val matchingDevices = state.devices.filter(_.ownerId === userId)
          state.notificationTokens.filter(t => matchingDevices.map(_.device_id).contains(t.deviceId)).toList
        })
    }
}
