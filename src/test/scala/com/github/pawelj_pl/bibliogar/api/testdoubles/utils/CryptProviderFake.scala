package com.github.pawelj_pl.bibliogar.api.testdoubles.utils

import cats.Applicative
import cats.syntax.applicative._
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.CryptProvider
import tsec.passwordhashers.PasswordHash
import tsec.passwordhashers.jca.BCrypt

object CryptProviderFake {
  def instance[F[_]: Applicative]: CryptProvider[F] = new CryptProvider[F] {
    override def bcryptHash(plainText: String): F[PasswordHash[BCrypt]] = PasswordHash.apply[BCrypt](s"bcrypt($plainText)").pure[F]

    override def bcryptCheckPw(password: String, hash: String): F[Boolean] = (hash == s"bcrypt($password)").pure[F]
  }
}
