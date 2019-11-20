package com.github.pawelj_pl.bibliogar.api.infrastructure.repositories

import cats.data.OptionT
import cats.syntax.functor._
import com.github.pawelj_pl.bibliogar.api.DB
import com.github.pawelj_pl.bibliogar.api.domain.user.{TokenType, UserToken, UserTokenRepositoryAlgebra}
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.TimeProvider
import io.chrisdavenport.fuuid.FUUID

class DoobieUserTokenRepository(implicit timeProvider: TimeProvider[DB])
    extends UserTokenRepositoryAlgebra[DB]
    with BasePostgresRepository {
  import doobieContext._
  import dbImplicits._

  implicit val encodeTokenType: MappedEncoding[TokenType, String] = MappedEncoding[TokenType, String](_.entryName)
  implicit val decodeTokenType: MappedEncoding[String, TokenType] = MappedEncoding[String, TokenType](TokenType.withName)

  override def create(token: UserToken): DB[UserToken] =
    for {
      now <- TimeProvider[DB].now
      updatedToken = token.copy(createdAt = now, updatedAt = now)
      _ <- run(quote(tokens.insert(lift(updatedToken))))
    } yield updatedToken

  override def get(token: String, tokenType: TokenType): OptionT[DB, UserToken] = OptionT(
    run(quote(tokens.filter(t => t.token == lift(token) && t.tokenType == lift(tokenType)))).map(_.headOption)
  )

  override def delete(token: String): DB[Unit] = run(quote(tokens.filter(_.token == lift(token)).delete)).void

  override def deleteByAccountAndType(account: FUUID, tokenType: TokenType): DB[Unit] =
    run(
      quote(
        tokens.filter(t => t.account == lift(account) && t.tokenType == lift(tokenType)).delete
      )
    ).void

  private val tokens = quote {
    querySchema[UserToken]("user_tokens", _.tokenType -> "type")
  }
}
