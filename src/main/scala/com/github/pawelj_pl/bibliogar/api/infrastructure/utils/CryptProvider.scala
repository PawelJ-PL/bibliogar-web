package com.github.pawelj_pl.bibliogar.api.infrastructure.utils

import cats.effect.Sync
import tsec.common._
import tsec.passwordhashers.PasswordHash
import tsec.passwordhashers.jca.BCrypt
import tsec.hashing.jca._

trait CryptProvider[F[_]] {
  def bcryptHash(plainText: String): F[PasswordHash[BCrypt]]
  def bcryptCheckPw(password: String, hash: String): F[Boolean]
  def encodeSha256(plain: String): String
}

object CryptProvider {
  def apply[F[_]](implicit ev: CryptProvider[F]): CryptProvider[F] = ev
  def create[F[_]: Sync](rounds: Int): CryptProvider[F] = new CryptProvider[F] {
    override def bcryptHash(plainText: String): F[PasswordHash[BCrypt]] = BCrypt.hashpwWithRounds(plainText, rounds)

    override def bcryptCheckPw(password: String, hash: String): F[Boolean] =
      BCrypt.syncPasswordHasher[F].checkpwBool(password, PasswordHash[BCrypt](hash))

    override def encodeSha256(plain: String): String = plain.utf8Bytes.hash[SHA256].toHexString
  }
}
