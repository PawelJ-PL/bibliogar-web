package com.github.pawelj_pl.bibliogar.api.domain.user

import cats.data.OptionT
import io.chrisdavenport.fuuid.FUUID

trait ApiKeyRepositoryAlgebra[F[_]] {
  def create(apiKey: ApiKey): F[ApiKey]
  def find(key: String): OptionT[F, ApiKey]
  def findById(keyId: FUUID): OptionT[F, ApiKey]
}

object ApiKeyRepositoryAlgebra {
  def apply[F[_]](implicit ev: ApiKeyRepositoryAlgebra[F]): ApiKeyRepositoryAlgebra[F] = ev
}