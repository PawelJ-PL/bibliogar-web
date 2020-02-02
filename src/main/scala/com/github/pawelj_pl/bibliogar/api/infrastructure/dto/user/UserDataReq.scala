package com.github.pawelj_pl.bibliogar.api.infrastructure.dto.user

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

final case class UserDataReq(version: Option[String], nickName: NickName)

object UserDataReq extends NickNameImplicits {
  implicit val encode: Encoder[UserDataReq] = deriveEncoder[UserDataReq]
  implicit val decoder: Decoder[UserDataReq] = deriveDecoder[UserDataReq]
}