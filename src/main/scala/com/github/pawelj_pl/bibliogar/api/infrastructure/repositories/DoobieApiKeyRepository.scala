package com.github.pawelj_pl.bibliogar.api.infrastructure.repositories

import cats.data.OptionT
import com.github.pawelj_pl.bibliogar.api.DB
import com.github.pawelj_pl.bibliogar.api.domain.user.{ApiKey, ApiKeyRepositoryAlgebra, KeyType}
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.TimeProvider
import io.chrisdavenport.fuuid.FUUID

class DoobieApiKeyRepository(implicit timeProvider: TimeProvider[DB]) extends ApiKeyRepositoryAlgebra[DB] with BasePostgresRepository {
  import doobieContext._
  import dbImplicits._

  implicit val encodeTokenType: MappedEncoding[KeyType, String] = MappedEncoding[KeyType, String](_.entryName)
  implicit val decodeTokenType: MappedEncoding[String, KeyType] = MappedEncoding[String, KeyType](KeyType.withName)

  override def create(apiKey: ApiKey): DB[ApiKey] =
    for {
      now <- TimeProvider[DB].now
      updatedKey = apiKey.copy(updatedAt = now, createdAt = now)
      _ <- run(quote(apiKeys.insert(lift(updatedKey))))
    } yield updatedKey

  override def find(key: String): OptionT[DB, ApiKey] = OptionT(
    run(quote(apiKeys.filter(_.apiKey == lift(key)))).map(_.headOption)
  )

  override def findById(keyId: FUUID): OptionT[DB, ApiKey] = OptionT(
    run(quote(apiKeys.filter(_.keyId == lift(keyId)))).map(_.headOption)
  )

  private val apiKeys = quote {
    querySchema[ApiKey]("api_keys")
  }
}
