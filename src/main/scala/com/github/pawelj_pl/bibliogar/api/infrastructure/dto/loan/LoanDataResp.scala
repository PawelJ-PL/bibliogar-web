package com.github.pawelj_pl.bibliogar.api.infrastructure.dto.loan

import java.time.Instant

import com.github.pawelj_pl.bibliogar.api.domain.loan.Loan
import io.chrisdavenport.fuuid.circe._
import io.chrisdavenport.fuuid.FUUID
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.scalaland.chimney.dsl._

final case class LoanDataResp(
  id: FUUID,
  libraryId: Option[FUUID],
  returnTo: Instant,
  returnedAt: Option[Instant],
  books: List[Option[FUUID]],
  version: String)

object LoanDataResp {
  def fromDomain(loan: Loan): LoanDataResp =
    loan.into[LoanDataResp].withFieldComputed(_.version, _.updatedAt.toEpochMilli.toString).transform

  implicit val decoder: Decoder[LoanDataResp] = deriveDecoder[LoanDataResp]
  implicit val encoder: Encoder[LoanDataResp] = deriveEncoder[LoanDataResp]
}
