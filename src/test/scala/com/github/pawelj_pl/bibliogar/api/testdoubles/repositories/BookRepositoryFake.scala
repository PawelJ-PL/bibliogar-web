package com.github.pawelj_pl.bibliogar.api.testdoubles.repositories

import cats.Monad
import cats.syntax.eq._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.data.OptionT
import cats.mtl.MonadState
import com.github.pawelj_pl.bibliogar.api.domain.book.{Book, BookRepositoryAlgebra, SourceType}
import io.chrisdavenport.fuuid.FUUID

object BookRepositoryFake {
  final case class BookRepositoryState(books: Set[Book] = Set.empty)

  def instance[F[_]: Monad](implicit S: MonadState[F, BookRepositoryState]): BookRepositoryAlgebra[F] = new BookRepositoryAlgebra[F] {
    override def create(book: Book): F[Book] = S.modify(state => state.copy(books = state.books + book)).map(_ => book)

    override def findById(bookId: FUUID): OptionT[F, Book] = OptionT(S.get.map(_.books.find(_.id === bookId)))

    override def findByIsbnWithScoreAboveOrEqualAverage(isbn: String): F[List[Book]] =
      for {
        scores <- S.get.map(_.books.map(_.score).collect {
          case Some(v) => v
        })
        avg = if (scores.nonEmpty) scores.sum.toDouble / scores.size else 0D
        result <- S.get.map(_.books.filter(book => book.score.exists(x => x.toDouble >= avg) && book.score.isDefined))
      } yield result.toList.sortBy(_.score.getOrElse(0)).reverse

    override def findNonUserDefinedBook(isbn: String): F[List[Book]] =
      S.get.map(_.books.filter(_.sourceType != SourceType.User).toList)

    override def increaseScore(bookId: FUUID, number: Int): F[Unit] = ???
  }
}
