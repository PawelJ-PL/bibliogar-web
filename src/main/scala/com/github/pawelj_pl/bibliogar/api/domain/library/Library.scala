package com.github.pawelj_pl.bibliogar.api.domain.library

import java.time.Instant

import cats.Show
import enumeratum._
import io.chrisdavenport.fuuid.FUUID


final case class Library(
  id: FUUID,
  ownerId: FUUID,
  name: String,
  loanDurationValue: Int,
  loanDurationUnit: LoanDurationUnit,
  createdAt: Instant,
  updatedAt: Instant) {
  val version: String = updatedAt.toEpochMilli.toString
}

object Library {
  implicit val show: Show[Library] = Show.fromToString
}

sealed trait LoanDurationUnit extends EnumEntry

object LoanDurationUnit extends Enum[LoanDurationUnit] with CirceEnum[LoanDurationUnit] {
  val values = findValues

  case object Day extends LoanDurationUnit
  case object Week extends LoanDurationUnit
  case object Month extends LoanDurationUnit
  case object Year extends LoanDurationUnit
}
