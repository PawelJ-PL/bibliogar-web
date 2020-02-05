package com.github.pawelj_pl.bibliogar.api.infrastructure.dto.library

import com.github.pawelj_pl.bibliogar.api.domain.library.{Library, LoanDurationUnit}
import io.chrisdavenport.fuuid.circe._
import io.chrisdavenport.fuuid.FUUID
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import io.scalaland.chimney.dsl._

final case class LibraryDataResp(version: String, id: FUUID, name: String, loanDurationValue: Int, loanDurationUnit: LoanDurationUnit)

object LibraryDataResp {
  def fromDomain(library: Library): LibraryDataResp =
    library.into[LibraryDataResp].withFieldComputed(_.version, _.updatedAt.toEpochMilli.toString).transform

  implicit val encoder: Encoder[LibraryDataResp] = deriveEncoder[LibraryDataResp]
  implicit val decoder: Decoder[LibraryDataResp] = deriveDecoder[LibraryDataResp]
}
