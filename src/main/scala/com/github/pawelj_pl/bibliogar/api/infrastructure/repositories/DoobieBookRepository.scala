package com.github.pawelj_pl.bibliogar.api.infrastructure.repositories

import cats.data.OptionT
import com.github.pawelj_pl.bibliogar.api.DB
import com.github.pawelj_pl.bibliogar.api.domain.book.{Book, BookRepositoryAlgebra, SourceType}
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.TimeProvider
import io.chrisdavenport.fuuid.FUUID

class DoobieBookRepository(implicit timeProvider: TimeProvider[DB]) extends BookRepositoryAlgebra[DB] with BasePostgresRepository {
  import doobieContext._
  import dbImplicits._

  implicit val encodeSourceType: MappedEncoding[SourceType, String] = MappedEncoding[SourceType, String](_.entryName)
  implicit val decodeSourceType: MappedEncoding[String, SourceType] = MappedEncoding[String, SourceType](SourceType.withName)

  override def create(book: Book): DB[Book] =
    for {
      now <- timeProvider.now
      updatedBook = book.copy(createdAt = now, updatedAt = now)
      _ <- run(quote(books.insert(lift(updatedBook))))
    } yield updatedBook

  override def findById(bookId: FUUID): OptionT[DB, Book] =
    OptionT(run {
      quote {
        books.filter(_.id == lift(bookId))
      }
    }.map(_.headOption))

  override def findByIsbnWithScoreAboveOrEqualAverage(isbn: String): DB[List[Book]] = run {
    quote {
      books
        .filter(book =>
          book.isbn == lift(isbn) && book.score.getOrNull >=
            books.filter(book => book.isbn == lift(isbn) && book.score.isDefined).map(_.score.getOrNull).avg.getOrNull)
        .sortBy(_.score)(Ord.desc)
    }
  }

  override def findNonUserDefinedBook(isbn: String): DB[List[Book]] = {
    val userSource: SourceType = SourceType.User
    run(quote(books.filter(b => b.isbn == lift(isbn) && b.sourceType != lift(userSource))))
  }

  override def increaseScore(bookId: FUUID, number: Int = 1): DB[Unit] =
    for {
      now <- timeProvider.now
      _ <- run(
        quote(
          books
            .filter(book => book.id == lift(bookId) && book.score.isDefined)
            .update(
              b => b.score -> b.score.map(_ + lift(number)),
              b => b.updatedAt -> lift(now)
            )
        )
      )
    } yield ()

  private val books = quote {
    querySchema[Book]("books")
  }
}
