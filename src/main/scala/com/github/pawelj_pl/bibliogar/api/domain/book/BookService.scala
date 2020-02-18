package com.github.pawelj_pl.bibliogar.api.domain.book

import cats.data.{NonEmptyList, OptionT}
import cats.effect.Sync
import cats.{Monad, ~>}
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.show._
import com.github.pawelj_pl.bibliogar.api.infrastructure.dto.book.BookReq
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.{RandomProvider, TimeProvider}
import io.chrisdavenport.fuuid.FUUID
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

trait BookService[F[_]] {
  def createBookAs(dto: BookReq, userId: FUUID): F[Book]
  def getBook(bookId: FUUID): OptionT[F, Book]
  def getSuggestionForIsbn(isbn: String): F[List[Book]]
}

object BookService {
  def apply[F[_]](implicit ev: BookService[F]): BookService[F] = ev

  def withDb[F[_]: IsbnService, D[_]: Monad: Sync: BookRepositoryAlgebra: TimeProvider: RandomProvider](
  )(implicit dbToF: D ~> F,
    liftF: F ~> D
  ): BookService[F] =
    new BookService[F] {
      private val log: Logger[D] = Slf4jLogger.getLogger[D]

      override def createBookAs(dto: BookReq, userId: FUUID): F[Book] = dbToF(
        for {
          book  <- dto.toDomain[D](userId)
          saved <- BookRepositoryAlgebra[D].create(book)
          _     <- log.info(show"created new book: $saved")
        } yield saved
      )

      override def getBook(bookId: FUUID): OptionT[F, Book] = BookRepositoryAlgebra[D].findById(bookId).mapK(dbToF)

      override def getSuggestionForIsbn(isbn: String): F[List[Book]] = dbToF(
        for {
          externalInfo <- OptionT(BookRepositoryAlgebra[D].findNonUserDefinedBook(isbn).map(NonEmptyList.fromList))
            .map(_.toList)
            .getOrElseF(fetchAndSaveBookInfos(isbn).map(book => List(book)).getOrElse(List.empty))
          infoFromUser <- BookRepositoryAlgebra[D].findByIsbnWithScoreAboveOrEqualAverage(isbn)
        } yield externalInfo ++ infoFromUser
      )

      private def fetchAndSaveBookInfos(isbn: String): OptionT[D, Book] =
        for {
          book  <- IsbnService[F].find(isbn).mapK(liftF)
          saved <- OptionT.liftF(BookRepositoryAlgebra[D].create(book))
        } yield saved
    }
}
