package com.github.pawelj_pl.bibliogar.api.testdoubles.repositories

import cats.Monad
import cats.mtl.MonadState
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.github.pawelj_pl.bibliogar.api.constants.UserConstants
import com.github.pawelj_pl.bibliogar.api.domain.user.{SessionRepositoryAlgebra, UserSession}
import io.chrisdavenport.fuuid.FUUID

object SessionRepositoryFake extends UserConstants{
  case class SessionRepositoryState(sessions: Set[UserSession] = Set.empty)

  def instance[F[_]: Monad](implicit S: MonadState[F, SessionRepositoryState]): SessionRepositoryAlgebra[F] =  new SessionRepositoryAlgebra[F] {
    override def createFor(userId: FUUID, apiKeyId: Option[FUUID]): F[UserSession] = {
      val newSession = ExampleUserSession.copy(userId = userId, apiKeyId = apiKeyId)
      S.modify(state => state.copy(sessions = state.sessions + newSession)).map(_ => newSession)
    }

    override def getSession(sessionId: FUUID): F[Option[UserSession]] = S.get.map(_.sessions.find(_.sessionId == sessionId))

    override def deleteSession(sessionId: FUUID): F[Option[UserSession]] = {
      val transform = for {
        origState <- S.get
        _ <- S.modify(state => {
          state.sessions.find(_.sessionId == sessionId) match {
            case None    => state
            case Some(s) => state.copy(sessions = state.sessions - s)
          }
        })
      } yield origState.sessions.find(_.sessionId == sessionId)
      transform
    }

    override def deleteAllUserSessions(userId: FUUID): F[List[FUUID]] = {
      for {
        origState <- S.get
        _ <- S.modify(state => {
          val toDelete = state.sessions.filter(_.userId == userId)
          state.copy(state.sessions -- toDelete)
        })
      } yield origState.sessions.filter(_.userId == userId).map(_.sessionId).toList
    }
  }
}
