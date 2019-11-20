package com.github.pawelj_pl.bibliogar.api.domain.user

import java.time.Instant

import io.chrisdavenport.fuuid.FUUID
import enumeratum._

final case class UserToken(token: String, account: FUUID, tokenType: TokenType, createdAt: Instant, updatedAt: Instant)

sealed trait TokenType extends EnumEntry

case object TokenType extends Enum[TokenType] with CirceEnum[TokenType] {
  case object Registration extends TokenType
  case object PasswordReset extends TokenType

  val values = findValues
}