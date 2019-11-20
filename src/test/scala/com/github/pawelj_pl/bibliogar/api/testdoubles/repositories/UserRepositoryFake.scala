package com.github.pawelj_pl.bibliogar.api.testdoubles.repositories

import java.time.Instant

import cats.Monad
import cats.data.OptionT
import cats.mtl.MonadState
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.github.pawelj_pl.bibliogar.api.domain.user.{AuthData, User, UserRepositoryAlgebra}
import io.chrisdavenport.fuuid.FUUID

object UserRepositoryFake {
  case class UserRepositoryState(users: Set[User] = Set.empty, authData: Set[AuthData] = Set.empty)

  def instance[F[_]: Monad](implicit S: MonadState[F, UserRepositoryState]): UserRepositoryAlgebra[F] = new UserRepositoryAlgebra[F] {
    override def findUserByEmail(email: String): OptionT[F, User] = OptionT(S.get.map(_.users.find(_.email == email)))

    override def findUserById(id: FUUID): OptionT[F, User] = OptionT(S.get.map(_.users.find(_.id == id)))

    override def create(user: User): F[User] = S.modify(state => state.copy(users = state.users + user)).map(_ => user)

    override def deleteByIds(ids: FUUID*): F[Long] =
      for {
        origState <- S.get
        toDelete = ids.flatMap(u => origState.users.find(_.id == u))
        _ <- S.modify(state => state.copy(users = state.users -- toDelete))
      } yield toDelete.length.toLong

    override def findUserWithAuthById(id: FUUID): OptionT[F, (User, AuthData)] =
      OptionT(S.get.map(state => {
        for {
          user     <- state.users.find(_.id == id)
          authData <- state.authData.find(_.userId == id)
        } yield (user, authData)
      }))

    override def findUserWithAuthByEmail(email: String): OptionT[F, (User, AuthData)] =
      OptionT(S.get.map(state => {
        for {
          user     <- state.users.find(_.email == email)
          authData <- state.authData.find(_.userId == user.id)
        } yield (user, authData)
      }))

    override def update(user: User): OptionT[F, User] =
      OptionT({
        val transform = for {
          origState <- S.get
          _ <- S.modify(state => {
            state.users.find(_.id == user.id) match {
              case None => state
              case Some(u) =>
                val removed = state.users - u
                state.copy(users = removed + user)
            }
          })
          newState <- S.get
        } yield origState != newState
        transform.map(changed => if (changed) Some(user) else None)
      })

    override def findAuthDataFor(userId: FUUID): OptionT[F, AuthData] = OptionT(S.get.map(state => state.authData.find(_.userId == userId)))

    override def create(authData: AuthData): F[AuthData] =
      S.modify(state => state.copy(authData = state.authData + authData)).map(_ => authData)

    override def update(authData: AuthData): OptionT[F, AuthData] =
      OptionT({
        val transform = for {
          origState <- S.get
          _ <- S.modify(state => {
            state.authData.find(_.userId == authData.userId) match {
              case None => state
              case Some(auth) =>
                val removed = state.authData - auth
                state.copy(authData = removed + authData)
            }
          })
          newState <- S.get
        } yield origState != newState
        transform.map(changed => if (changed) Some(authData) else None)
      })

    override def findNotConfirmedAuthDataOlderThan(when: Instant): F[List[AuthData]] =
      S.get.map(_.authData.filter(a => a.updatedAt.isBefore(when) && !a.confirmed).toList)
  }
}
