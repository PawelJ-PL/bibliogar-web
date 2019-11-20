package com.github.pawelj_pl.bibliogar.api.infrastructure.utils

import cats.effect.Sync
import tsec.passwordhashers.PasswordHash
import tsec.passwordhashers.jca.BCrypt

trait CryptProvider[F[_]] {
  def bcryptHash(plainText: String): F[PasswordHash[BCrypt]]
  def bcryptCheckPw(password: String, hash: String): F[Boolean]
}

object CryptProvider {
  def apply[F[_]](implicit ev: CryptProvider[F]): CryptProvider[F] = ev
  def create[F[_]: Sync](rounds: Int): CryptProvider[F] = new CryptProvider[F] {
    override def bcryptHash(plainText: String): F[PasswordHash[BCrypt]] = BCrypt.hashpwWithRounds(plainText, rounds)

    override def bcryptCheckPw(password: String, hash: String): F[Boolean] =
      BCrypt.syncPasswordHasher[F].checkpwBool(password, PasswordHash[BCrypt](hash))
  }
}
