package com.github.pawelj_pl.bibliogar.api.domain.book

import java.time.Instant

import cats.{Eq, Show}
import enumeratum._
import io.chrisdavenport.fuuid.FUUID
import org.http4s.Uri

final case class Book(
  id: FUUID,
  isbn: String,
  title: String,
  authors: Option[String],
  cover: Option[Uri],
  score: Option[Int],
  sourceType: SourceType,
  createdBy: Option[FUUID],
  createdAt: Instant,
  updatedAt: Instant)

object Book {
  implicit val showInstance: Show[Book] = Show.fromToString
}

sealed trait SourceType extends EnumEntry

object SourceType extends Enum[SourceType] {
  val values = findValues

  case object OpenLibrary extends SourceType
  case object GoogleBooks extends SourceType
  case object BibliotekaNarodowa extends SourceType
  case object User extends SourceType

  implicit val eqInstance: Eq[SourceType] = Eq.fromUniversalEquals
}
