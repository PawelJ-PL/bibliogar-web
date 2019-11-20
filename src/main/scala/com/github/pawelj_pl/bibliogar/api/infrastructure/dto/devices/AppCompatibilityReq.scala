package com.github.pawelj_pl.bibliogar.api.infrastructure.dto.devices

import com.github.pawelj_pl.bibliogar.api.infrastructure.http.Implicits.Semver._
import com.vdurmont.semver4j.Semver
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

final case class AppCompatibilityReq(appVersion: Semver)

object AppCompatibilityReq {
  implicit val encoder: Encoder[AppCompatibilityReq] = deriveEncoder[AppCompatibilityReq]
  implicit val decoder: Decoder[AppCompatibilityReq] = deriveDecoder[AppCompatibilityReq]
}
