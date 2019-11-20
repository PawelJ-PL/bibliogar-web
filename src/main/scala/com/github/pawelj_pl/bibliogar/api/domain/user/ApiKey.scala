package com.github.pawelj_pl.bibliogar.api.domain.user

import java.time.Instant

import cats.Functor
import cats.syntax.functor._
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.TimeProvider
import enumeratum._
import io.chrisdavenport.fuuid.FUUID

final case class ApiKey(
  keyId: FUUID,
  apiKey: String,
  userId: FUUID,
  deviceId: Option[FUUID],
  keyType: KeyType,
  description: Option[String],
  enabled: Boolean,
  validTo: Option[Instant],
  createdAt: Instant,
  updatedAt: Instant) {
  def isActive[F[_]: Functor: TimeProvider]: F[Boolean] =
    for {
      now <- TimeProvider[F].now
    } yield enabled && validTo.forall(_.isAfter(now))
}

sealed trait KeyType extends EnumEntry

case object KeyType extends Enum[KeyType] {
  case object Device extends KeyType
  case object User extends KeyType

  val values = findValues
}
