package com.github.pawelj_pl.bibliogar.api.testdoubles.domain.book

import cats.Functor
import cats.instances.string._
import cats.syntax.eq._
import cats.mtl.MonadState
import cats.syntax.functor._
import com.github.pawelj_pl.bibliogar.api.constants.BookConstants
import com.github.pawelj_pl.bibliogar.api.domain.book.{Book, IsbnService}

object IsbnServiceFake extends BookConstants {
  final case class IsbnServiceState(books: List[Book] = List(ExampleBook))

  def instance[F[_]: Functor](implicit S: MonadState[F, IsbnServiceState]): IsbnService[F] =
    (isbn: String) => S.get.map(_.books.filter(_.isbn === isbn))
}
