package com.github.pawelj_pl.bibliogar.api.domain.library

import java.time.{Instant, Period}

import cats.Show
import enumeratum._
import io.chrisdavenport.fuuid.FUUID

final case class Library(
  id: FUUID,
  ownerId: FUUID,
  name: String,
  loanDurationValue: Int,
  loanDurationUnit: LoanDurationUnit,
  booksLimit: Option[Int],
  createdAt: Instant,
  updatedAt: Instant) {
  val version: String = updatedAt.toEpochMilli.toString
  val loanPeriod: Period = loanDurationUnit match {
    case LoanDurationUnit.Day   => Period.ofDays(loanDurationValue)
    case LoanDurationUnit.Week  => Period.ofWeeks(loanDurationValue)
    case LoanDurationUnit.Month => Period.ofMonths(loanDurationValue)
    case LoanDurationUnit.Year  => Period.ofYears(loanDurationValue)
  }
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
