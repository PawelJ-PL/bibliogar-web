package com.github.pawelj_pl.bibliogar.api.domain.user

import java.time.Instant

import cats.data.OptionT
import io.chrisdavenport.fuuid.FUUID

trait UserRepositoryAlgebra[F[_]] {
  def findUserByEmail(email: String): OptionT[F, User]
  def findUserById(id: FUUID): OptionT[F, User]
  def create(user: User): F[User]
  def deleteByIds(ids: FUUID*): F[Long]
  def findUserWithAuthById(id: FUUID): OptionT[F, (User, AuthData)]
  def findUserWithAuthByEmail(email: String): OptionT[F, (User, AuthData)]
  def update(user: User): OptionT[F, User]

  def findAuthDataFor(userId: FUUID): OptionT[F, AuthData]
  def create(authData: AuthData): F[AuthData]
  def update(authData: AuthData): OptionT[F, AuthData]
  def findNotConfirmedAuthDataOlderThan(when: Instant): F[List[AuthData]]
}

object UserRepositoryAlgebra {
  def apply[F[_]](implicit ev: UserRepositoryAlgebra[F]): UserRepositoryAlgebra[F] = ev
}
