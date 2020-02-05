package com.github.pawelj_pl.bibliogar.api.infrastructure.dto.user

import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.Misc.resourceVersion.VersionExtractor
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

final case class UserDataReq(version: Option[String], nickName: NickName)

object UserDataReq extends NickNameImplicits {
  implicit val versionExtractor: VersionExtractor[UserDataReq] = VersionExtractor.of[UserDataReq](_.version)

  implicit val encode: Encoder[UserDataReq] = deriveEncoder[UserDataReq]
  implicit val decoder: Decoder[UserDataReq] = deriveDecoder[UserDataReq]
}