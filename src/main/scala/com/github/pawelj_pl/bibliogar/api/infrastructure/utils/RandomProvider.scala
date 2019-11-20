package com.github.pawelj_pl.bibliogar.api.infrastructure.utils

import cats.effect.Sync
import io.chrisdavenport.fuuid.FUUID
import tsec.common.{SecureRandomId, SecureRandomIdGenerator}

trait RandomProvider[F[_]] {
  def secureRandomString(size: Int): F[SecureRandomId]
  def randomFuuid: F[FUUID]
}

object RandomProvider {
  def apply[F[_]](implicit  ev: RandomProvider[F]): RandomProvider[F] = ev
  def create[F[_]: Sync]: RandomProvider[F] = new RandomProvider[F] {
    override def secureRandomString(size: Int): F[SecureRandomId] = SecureRandomIdGenerator(size).generateF[F]
    override def randomFuuid: F[FUUID] = FUUID.randomFUUID
  }
}
