package com.github.pawelj_pl.bibliogar.api.infrastructure.dto.user

import io.chrisdavenport.fuuid.circe._
import io.chrisdavenport.fuuid.FUUID
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

final case class SessionCheckResp(isValid: Boolean, sessionDetails: Option[SessionDetails])

object SessionCheckResp {
  implicit val encoder: Encoder[SessionCheckResp] = deriveEncoder[SessionCheckResp]
  implicit val decoder: Decoder[SessionCheckResp] = deriveDecoder[SessionCheckResp]
}

final case class SessionDetails(csrfToken: FUUID)

object SessionDetails {
  implicit val encoder: Encoder[SessionDetails] = deriveEncoder[SessionDetails]
  implicit val decoder: Decoder[SessionDetails] = deriveDecoder[SessionDetails]
}