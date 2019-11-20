package com.github.pawelj_pl.bibliogar.api.infrastructure.dto.user

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.deriveEncoder
import io.circe.generic.semiauto.deriveDecoder

final case class UserLoginReq(email: Email, password: Password)

object UserLoginReq extends PasswordImplicits with EmailImplicits {
  implicit val encoder: Encoder[UserLoginReq] = deriveEncoder[UserLoginReq]
  implicit val decoder: Decoder[UserLoginReq] = deriveDecoder[UserLoginReq]
}
