package com.github.pawelj_pl.bibliogar.api.testdoubles.repositories

import cats.Monad
import cats.data.OptionT
import cats.syntax.functor._
import cats.mtl.MonadState
import com.github.pawelj_pl.bibliogar.api.domain.device.{Device, DevicesRepositoryAlgebra}
import io.chrisdavenport.fuuid.FUUID

object DevicesRepositoryFake {
  final case class DevicesRepositoryState(devices: Set[Device] = Set.empty)

  def instance[F[_]: Monad](implicit S: MonadState[F, DevicesRepositoryState]): DevicesRepositoryAlgebra[F] =
    new DevicesRepositoryAlgebra[F] {
      override def create(device: Device): F[Device] = S.modify(state => state.copy(devices = state.devices + device)).map(_ => device)

      override def findById(deviceId: FUUID): OptionT[F, Device] = OptionT(S.get.map(_.devices.find(_.device_id == deviceId)))

      override def delete(deviceId: FUUID): F[Unit] = S.modify(state => state.copy(devices = state.devices.filter(_.device_id != deviceId)))
    }
}
