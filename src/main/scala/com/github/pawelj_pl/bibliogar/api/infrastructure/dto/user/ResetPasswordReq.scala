package com.github.pawelj_pl.bibliogar.api.infrastructure.dto.user

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

final case class ResetPasswordReq(password: Password)

object ResetPasswordReq extends PasswordImplicits {
  implicit val encode: Encoder[ResetPasswordReq] = deriveEncoder[ResetPasswordReq]
  implicit val decoder: Decoder[ResetPasswordReq] = deriveDecoder[ResetPasswordReq]
}
