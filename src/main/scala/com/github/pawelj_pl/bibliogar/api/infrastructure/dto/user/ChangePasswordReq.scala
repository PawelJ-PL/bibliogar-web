package com.github.pawelj_pl.bibliogar.api.infrastructure.dto.user

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

final case class ChangePasswordReq(oldPassword: Password, newPassword: Password)

object ChangePasswordReq extends PasswordImplicits {
  implicit val encode: Encoder[ChangePasswordReq] = deriveEncoder[ChangePasswordReq]
  implicit val decoder: Decoder[ChangePasswordReq] = deriveDecoder[ChangePasswordReq]
}
