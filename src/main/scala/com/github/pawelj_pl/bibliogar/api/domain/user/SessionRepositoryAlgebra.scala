package com.github.pawelj_pl.bibliogar.api.domain.user

import io.chrisdavenport.fuuid.FUUID

trait SessionRepositoryAlgebra[F[_]] {
  def createFor(userId: FUUID, apiKeyId: Option[FUUID]): F[UserSession]
  def getSession(sessionId: FUUID): F[Option[UserSession]]
  def deleteSession(sessionId: FUUID): F[Option[UserSession]]
  def deleteAllUserSessions(userId: FUUID): F[List[FUUID]]
}

object SessionRepositoryAlgebra {
  def apply[F[_]](implicit ev: SessionRepositoryAlgebra[F]): SessionRepositoryAlgebra[F] = ev
}