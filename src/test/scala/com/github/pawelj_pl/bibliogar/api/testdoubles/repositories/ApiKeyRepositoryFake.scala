package com.github.pawelj_pl.bibliogar.api.testdoubles.repositories

import cats.Monad
import cats.data.OptionT
import cats.mtl.MonadState
import cats.syntax.functor._
import com.github.pawelj_pl.bibliogar.api.domain.user.{ApiKey, ApiKeyRepositoryAlgebra}
import io.chrisdavenport.fuuid.FUUID

object ApiKeyRepositoryFake {
  case class ApiKeyRepositoryState(keys: Set[ApiKey] = Set.empty)

  def instance[F[_]: Monad](implicit S: MonadState[F, ApiKeyRepositoryState]): ApiKeyRepositoryAlgebra[F] = new ApiKeyRepositoryAlgebra[F] {
    override def create(apiKey: ApiKey): F[ApiKey] = S.modify(state => state.copy(keys = state.keys + apiKey)).map(_ => apiKey)

    override def find(key: String): OptionT[F, ApiKey] = OptionT(S.get.map(_.keys.find(_.apiKey == key)))

    override def findById(keyId: FUUID): OptionT[F, ApiKey] = OptionT(S.get.map(state => state.keys.find(_.keyId == keyId)))
  }
}
