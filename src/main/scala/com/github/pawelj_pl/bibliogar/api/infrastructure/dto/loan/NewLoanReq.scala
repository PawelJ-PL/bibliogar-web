package com.github.pawelj_pl.bibliogar.api.infrastructure.dto.loan

import cats.data.NonEmptyList
import io.chrisdavenport.fuuid.circe._
import io.chrisdavenport.fuuid.FUUID
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

final case class NewLoanReq(libraryId: FUUID, books: NonEmptyList[Option[FUUID]])

object NewLoanReq {
  implicit val decoder: Decoder[NewLoanReq] = deriveDecoder[NewLoanReq]
  implicit val encoder: Encoder[NewLoanReq] = deriveEncoder[NewLoanReq]
}
