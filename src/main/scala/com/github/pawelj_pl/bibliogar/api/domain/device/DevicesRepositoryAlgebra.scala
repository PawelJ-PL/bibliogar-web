package com.github.pawelj_pl.bibliogar.api.domain.device

import cats.data.OptionT
import io.chrisdavenport.fuuid.FUUID

trait DevicesRepositoryAlgebra[F[_]] {
  def create(device: Device): F[Device]
  def findById(deviceId: FUUID): OptionT[F, Device]
  def delete(deviceId: FUUID): F[Unit]
}

object DevicesRepositoryAlgebra {
  def apply[F[_]](implicit ev: DevicesRepositoryAlgebra[F]): DevicesRepositoryAlgebra[F] = ev
}