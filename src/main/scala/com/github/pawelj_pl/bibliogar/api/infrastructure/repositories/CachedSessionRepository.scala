package com.github.pawelj_pl.bibliogar.api.infrastructure.repositories

import cats.Monad
import cats.data.OptionT
import cats.instances.list._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.traverse._
import com.github.pawelj_pl.bibliogar.api.domain.user.{SessionRepositoryAlgebra, UserSession}
import com.github.pawelj_pl.bibliogar.api.infrastructure.config.Config.CookieConfig
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.{RandomProvider, TimeProvider}
import io.chrisdavenport.fuuid.FUUID
import scalacache.{Cache, Mode, get, put, remove}

import scala.concurrent.duration._

class CachedSessionRepository[F[_]: Monad: TimeProvider: RandomProvider: Mode](
  cookieConfig: CookieConfig
)(implicit cache: Cache[UserSession],
  userIdMappingCache: Cache[Set[FUUID]])
    extends SessionRepositoryAlgebra[F] {
  override def createFor(userId: FUUID, apiKeyId: Option[FUUID]): F[UserSession] = synchronized {
    for {
      now          <- TimeProvider[F].now
      sessionId    <- RandomProvider[F].randomFuuid
      csrfToken    <- RandomProvider[F].randomFuuid
      userSessions <- get[F, Set[FUUID]](userId)
      _            <- put(userId)(userSessions.map(_ + sessionId).getOrElse(Set(sessionId)), Some(cookieConfig.maxAge.plus(1.minute)))
      session = UserSession(sessionId, apiKeyId, userId, csrfToken, now)
      _ <- put(sessionId)(session, Some(cookieConfig.maxAge))
    } yield session
  }

  override def getSession(sessionId: FUUID): F[Option[UserSession]] = get(sessionId)

  override def deleteSession(sessionId: FUUID): F[Option[UserSession]] = synchronized {
    (for {
      session      <- OptionT(get[F, UserSession](sessionId))
      userSessions <- OptionT(get[F, Set[FUUID]](session.userId))
      _            <- OptionT.liftF(remove[F, UserSession](sessionId))
      _            <- OptionT.liftF(put(session.userId)(userSessions - sessionId, Some(cookieConfig.maxAge.plus(1.minute))).void)
    } yield session).value
  }

  override def deleteAllUserSessions(userId: FUUID): F[List[FUUID]] = synchronized {
    for {
      userSessions <- get[F, Set[FUUID]](userId).map(_.getOrElse(Set.empty))
      removed      <- userSessions.toList.traverse(deleteSession).map(_.flatten)
    } yield removed.map(_.sessionId)
  }
}
