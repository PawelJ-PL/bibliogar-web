package com.github.pawelj_pl.bibliogar.api.testdoubles.domain.book

import cats.Functor
import cats.data.OptionT
import cats.mtl.MonadState
import cats.syntax.functor._
import com.github.pawelj_pl.bibliogar.api.constants.BookConstants
import com.github.pawelj_pl.bibliogar.api.domain.book.{Book, BookService}
import com.github.pawelj_pl.bibliogar.api.infrastructure.dto.book.BookReq
import io.chrisdavenport.fuuid.FUUID

object BookServiceFake extends BookConstants {
  final case class BookServiceState(book: Book = ExampleBook, maybeBook: Option[Book] = Some(ExampleBook))

  def instance[F[_]: Functor](implicit S: MonadState[F, BookServiceState]): BookService[F] = new BookService[F] {
    println(S)
    override def createBookAs(dto: BookReq, userId: FUUID): F[Book] = S.get.map(_.book)

    override def getBook(bookId: FUUID): OptionT[F, Book] = OptionT(S.get.map(_.maybeBook))

    override def getSuggestionForIsbn(isbn: String): F[List[Book]] = S.get.map(state => List(state.book))
  }
}
