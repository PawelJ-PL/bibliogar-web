package com.github.pawelj_pl.bibliogar.api.domain.user

import cats.data.OptionT
import io.chrisdavenport.fuuid.FUUID

trait UserTokenRepositoryAlgebra[F[_]] {
  def create(token: UserToken): F[UserToken]
  def get(token: String, tokenType: TokenType): OptionT[F, UserToken]
  def delete(token: String): F[Unit]
  def deleteByAccountAndType(account: FUUID, tokenType: TokenType): F[Unit]
}

object UserTokenRepositoryAlgebra {
  def apply[F[_]](implicit ev: UserTokenRepositoryAlgebra[F]): UserTokenRepositoryAlgebra[F] = ev
}