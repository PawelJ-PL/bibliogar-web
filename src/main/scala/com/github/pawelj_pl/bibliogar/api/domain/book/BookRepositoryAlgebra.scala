package com.github.pawelj_pl.bibliogar.api.domain.book

import cats.data.OptionT
import io.chrisdavenport.fuuid.FUUID
import org.http4s.Uri

trait BookRepositoryAlgebra[F[_]] {
  def create(book: Book): F[Book]
  def findById(bookId: FUUID): OptionT[F, Book]
  def findByIsbnWithScoreAboveOrEqualAverage(isbn: String): F[List[Book]]
  def findNonUserDefinedBook(isbn: String): F[List[Book]]
  def findByMetadata(isbn: String, title: String, authors: Option[String], cover: Option[Uri], sourceType: SourceType): F[List[Book]]
  def increaseScore(bookIds: List[FUUID], number: Int = 1): F[Unit]
}

object BookRepositoryAlgebra {
  def apply[F[_]](implicit ev: BookRepositoryAlgebra[F]): BookRepositoryAlgebra[F] = ev
}
