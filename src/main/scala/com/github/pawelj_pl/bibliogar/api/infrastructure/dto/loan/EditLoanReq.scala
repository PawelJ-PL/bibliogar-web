package com.github.pawelj_pl.bibliogar.api.infrastructure.dto.loan

import java.time.Instant

import cats.data.NonEmptyList
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.Misc.resourceVersion.VersionExtractor
import io.chrisdavenport.fuuid.circe._
import io.chrisdavenport.fuuid.FUUID
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

final case class EditLoanReq(
  libraryId: FUUID,
  returnTo: Instant,
  returnedAt: Option[Instant],
  books: NonEmptyList[Option[FUUID]],
  version: Option[String])

object EditLoanReq {
  implicit val versionExtractor: VersionExtractor[EditLoanReq] = VersionExtractor.of[EditLoanReq](_.version)

  implicit val decoder: Decoder[EditLoanReq] = deriveDecoder[EditLoanReq]
  implicit val encoder: Encoder[EditLoanReq] = deriveEncoder[EditLoanReq]
}
