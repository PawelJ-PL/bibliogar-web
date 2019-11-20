package com.github.pawelj_pl.bibliogar.api.testdoubles.repositories

import cats.Monad
import cats.data.OptionT
import cats.mtl.MonadState
import cats.syntax.functor._
import com.github.pawelj_pl.bibliogar.api.domain.user.{TokenType, UserToken, UserTokenRepositoryAlgebra}
import io.chrisdavenport.fuuid.FUUID

object UserTokenRepositoryFake {
  case class TokenRepositoryState(tokens: Set[UserToken] = Set.empty)

  def instance[F[_]: Monad](implicit S: MonadState[F, TokenRepositoryState]): UserTokenRepositoryAlgebra[F] =
    new UserTokenRepositoryAlgebra[F] {
      override def create(token: UserToken): F[UserToken] = S.modify(state => state.copy(tokens = state.tokens + token)).map(_ => token)

      override def get(token: String, tokenType: TokenType): OptionT[F, UserToken] =
        OptionT(S.get.map(state => state.tokens.find(t => t.token == token && t.tokenType == tokenType)))

      override def delete(token: String): F[Unit] = S.modify(state => state.copy(tokens = state.tokens.filter(_.token != token)))

      override def deleteByAccountAndType(account: FUUID, tokenType: TokenType): F[Unit] =
        S.modify(state => {
          val tokensToDelete = state.tokens.filter(token => token.account == account && token.tokenType == tokenType)
          state.copy(tokens = state.tokens -- tokensToDelete)
        })
    }
}
