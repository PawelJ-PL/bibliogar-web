package com.github.pawelj_pl.bibliogar.api.infrastructure.repositories

import java.time.Instant

import cats.data.OptionT
import com.github.pawelj_pl.bibliogar.api.DB
import com.github.pawelj_pl.bibliogar.api.domain.user.{AuthData, User, UserRepositoryAlgebra}
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.TimeProvider
import io.chrisdavenport.fuuid.FUUID

class DoobieUserRepository(implicit timeProvider: TimeProvider[DB]) extends UserRepositoryAlgebra[DB] with BasePostgresRepository {
  import doobieContext._
  import dbImplicits._

  override def findUserByEmail(email: String): OptionT[DB, User] = OptionT(
    run(quote(users.filter(_.email.toLowerCase == lift(email.toLowerCase)))).map(_.headOption)
  )

  override def findUserById(id: FUUID): OptionT[DB, User] = OptionT(
    run(quote(users.filter(_.id == lift(id)))).map(_.headOption)
  )

  override def create(user: User): DB[User] =
    for {
      now <- timeProvider.now
      updatedUser = user.copy(createdAt = now, updatedAt = now)
      _ <- run(quote(users.insert(lift(updatedUser))))
    } yield updatedUser

  override def deleteByIds(ids: FUUID*): DB[Long] =
    run {
      quote {
        users.filter(a => liftQuery(ids).contains(a.id)).delete
      }
    }

  override def findUserWithAuthByEmail(email: String): OptionT[DB, (User, AuthData)] =
    OptionT(run {
      quote {
        for {
          u <- users.filter(_.email.toLowerCase == lift(email.toLowerCase))
          a <- userAuths.join(_.userId == u.id)
        } yield (u, a)
      }
    }.map(_.headOption))

  override def update(user: User): OptionT[DB, User] =
    OptionT(for {
      now <- TimeProvider[DB].now
      updated = user.copy(updatedAt = now)
      count <- run(quote(users.filter(_.id == lift(user.id)).update(lift(updated))))
      result = if (count > 0) Some(updated) else None
    } yield result)

  override def findUserWithAuthById(id: FUUID): OptionT[DB, (User, AuthData)] =
    OptionT(run {
      quote {
        for {
          u <- users.filter(_.id == lift(id))
          a <- userAuths.join(_.userId == u.id)
        } yield (u, a)
      }
    }.map(_.headOption))

  override def findAuthDataFor(userId: FUUID): OptionT[DB, AuthData] = OptionT(
    run(quote(userAuths.filter(_.userId == lift(userId)))).map(_.headOption)
  )

  override def create(authData: AuthData): DB[AuthData] =
    for {
      now <- TimeProvider[DB].now
      updatedAuth = authData.copy(updatedAt = now)
      _ <- run(quote(userAuths.insert(lift(updatedAuth))))
    } yield updatedAuth

  override def update(authData: AuthData): OptionT[DB, AuthData] =
    OptionT(for {
      now <- TimeProvider[DB].now
      updated = authData.copy(updatedAt = now)
      count <- run(quote(userAuths.filter(_.userId == lift(authData.userId)).update(lift(updated))))
    } yield if (count > 0) Some(updated) else None)

  override def findNotConfirmedAuthDataOlderThan(when: Instant): DB[List[AuthData]] =
    run(quote(userAuths.filter(a => !a.confirmed && a.updatedAt < lift(when))))

  private val users = quote {
    querySchema[User]("users", _.id -> "user_id")
  }

  private val userAuths = quote {
    querySchema[AuthData]("user_auth")
  }
}
