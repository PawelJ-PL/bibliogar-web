package com.github.pawelj_pl.bibliogar.api.infrastructure.dto.user

import com.github.pawelj_pl.bibliogar.api.domain.user.User
import io.chrisdavenport.fuuid.circe._
import io.chrisdavenport.fuuid.FUUID
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.scalaland.chimney.dsl._

final case class UserDataResp(id: FUUID, email: String, nickName: String)

object UserDataResp {
  def fromDomain(user: User): UserDataResp = user.transformInto[UserDataResp]

  implicit val encoder: Encoder[UserDataResp] = deriveEncoder[UserDataResp]
  implicit val decoder: Decoder[UserDataResp] = deriveDecoder[UserDataResp]
}