package com.github.pawelj_pl.bibliogar.api.domain.loan

import java.time.Instant

import io.chrisdavenport.fuuid.FUUID

final case class Loan(
  id: FUUID,
  userId: FUUID,
  libraryId: Option[FUUID],
  returnTo: Instant,
  returnedAt: Option[Instant],
  books: List[Option[FUUID]],
  createdAt: Instant,
  updatedAt: Instant) {
  val version: String = updatedAt.toEpochMilli.toString
}
