package com.github.pawelj_pl.bibliogar.api.domain.user

import java.time.Instant

import cats.Applicative
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.CryptProvider
import io.chrisdavenport.fuuid.FUUID

final case class AuthData(userId: FUUID, passwordHash: String, confirmed: Boolean, enabled: Boolean, updatedAt: Instant) {
  def isActive[F[_]: Applicative]: F[Boolean] = Applicative[F].pure(confirmed && enabled)
  def checkPassword[F[_]: CryptProvider](plainText: String): F[Boolean] = CryptProvider[F].bcryptCheckPw(plainText, passwordHash)
}
