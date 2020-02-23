package com.github.pawelj_pl.bibliogar.api.domain.book

import cats.{Functor, Monad, ~>}
import cats.data.StateT
import cats.effect.IO
import cats.mtl.instances.all._
import cats.mtl.MonadState
import com.github.pawelj_pl.bibliogar.api.constants.{BookConstants, UserConstants}
import com.github.pawelj_pl.bibliogar.api.infrastructure.dto.book.{Authors, BookReq, Cover, Isbn, Title}
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.{RandomProvider, TimeProvider}
import com.github.pawelj_pl.bibliogar.api.testdoubles.domain.book.IsbnServiceFake
import com.github.pawelj_pl.bibliogar.api.testdoubles.repositories.BookRepositoryFake
import com.github.pawelj_pl.bibliogar.api.testdoubles.utils.{RandomProviderFake, TimeProviderFake}
import com.softwaremill.diffx.scalatest.DiffMatcher
import com.olegpy.meow.hierarchy.deriveMonadState
import com.softwaremill.diffx.{Derived, Diff}
import org.http4s.Uri
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class BookServiceSpec extends AnyWordSpec with Matchers with DiffMatcher with BookConstants with UserConstants {
  case class TestState(
    timeProviderState: TimeProviderFake.TimeProviderState = TimeProviderFake.TimeProviderState(),
    randomState: RandomProviderFake.RandomState = RandomProviderFake.RandomState(),
    isbnServiceState: IsbnServiceFake.IsbnServiceState = IsbnServiceFake.IsbnServiceState(),
    bookRepoState: BookRepositoryFake.BookRepositoryState = BookRepositoryFake.BookRepositoryState())

  type TestEffect[A] = StateT[IO, TestState, A]

  implicit val uriDiff: Derived[Diff[Uri]] = Derived(Diff[String].contramap[Uri](_.renderString))

  val instance: BookService[TestEffect] = {
    implicit def timeProvider[F[_]: Monad: MonadState[*[_], TestState]]: TimeProvider[F] = TimeProviderFake.instance[F]
    implicit def randomProvider[F[_]: Monad: MonadState[*[_], TestState]]: RandomProvider[F] = RandomProviderFake.instance[F]
    implicit def isbnService[F[_]: Functor: MonadState[*[_], TestState]]: IsbnService[F] = IsbnServiceFake.instance[F]
    implicit def bookRepo[F[_]: Monad: MonadState[*[_], TestState]]: BookRepositoryAlgebra[F] = BookRepositoryFake.instance[F]

    implicit def dbToApp: TestEffect ~> TestEffect = new (TestEffect ~> TestEffect) {
      override def apply[A](fa: TestEffect[A]): TestEffect[A] = fa
    }

    BookService.withDb[TestEffect, TestEffect]()
  }

  "Create book" should {
    val expectedBook = ExampleBook.copy(id = FirstRandomUuid, score = Some(0))
    val testRequest =
      BookReq(Isbn(ExampleBook.isbn), Title(ExampleBook.title), ExampleBook.authors.map(Authors), ExampleBook.cover.map(Cover))
    "create new book" in {
      val initialState = TestState()
      val (state, result) = instance.createBookAs(testRequest, ExampleUser.id).run(initialState).unsafeRunSync()
      result should matchTo(expectedBook)
      state.bookRepoState.books.toList should matchTo(List(expectedBook))
    }
    "reuse existing book with the same metadata" in {
      val selectedBook = ExampleBook.copy(ExampleId2, score = Some(10))
      val initialState = TestState(
        bookRepoState = BookRepositoryFake.BookRepositoryState(
          books = Set(
            ExampleBook.copy(ExampleId1, score = Some(2)),
            selectedBook,
            ExampleBook.copy(ExampleId2, score = Some(6))
          )
        ))
      val (state, result) = instance.createBookAs(testRequest, ExampleUser.id).run(initialState).unsafeRunSync()
      result should matchTo(selectedBook)
      state.bookRepoState.books.toList should matchTo(initialState.bookRepoState.books.toList)
    }
  }

  "Get book" should {
    "return book" in {
      val initialState = TestState(
        bookRepoState = BookRepositoryFake.BookRepositoryState(
          books = Set(ExampleBook)
        ))
      val result = instance.getBook(ExampleBook.id).value.runA(initialState).unsafeRunSync()
      result shouldBe Option(ExampleBook)
    }
  }

  "Get suggestion for isbn" should {
    "return saved data" in {
      val initialState = TestState(
        bookRepoState = BookRepositoryFake.BookRepositoryState(
          books = Set(ExampleBook, ExampleBook.copy(id = ExampleId1, score = None, sourceType = SourceType.BibliotekaNarodowa))
        ),
        isbnServiceState = IsbnServiceFake.IsbnServiceState(
          books = List(ExampleBook.copy(id = ExampleId2, score = None, sourceType = SourceType.GoogleBooks))
        )
      )
      val (state, result) = instance.getSuggestionForIsbn(ExampleBook.isbn).run(initialState).unsafeRunSync()
      result.map(_.id) should matchTo(List(ExampleId1, ExampleBook.id))
      state.bookRepoState.books.toList should matchTo(initialState.bookRepoState.books.toList)
    }
    "fetch info from remote API" when {
      "not fetched yet" in {
        val initialState = TestState(
          bookRepoState = BookRepositoryFake.BookRepositoryState(
            books = Set(
              ExampleBook,
              ExampleBook.copy(id = ExampleId1, score = Some(5), sourceType = SourceType.User),
              ExampleBook.copy(id = ExampleId3, score = Some(1), sourceType = SourceType.User)
            )
          ),
          isbnServiceState = IsbnServiceFake.IsbnServiceState(
            books = List(ExampleBook.copy(id = ExampleId2, score = None, sourceType = SourceType.GoogleBooks))
          )
        )
        val (state, result) = instance.getSuggestionForIsbn(ExampleBook.isbn).run(initialState).unsafeRunSync()
        result.map(_.id) should matchTo(List(ExampleId2, ExampleId1, ExampleBook.id))
        state.bookRepoState.books.map(_.id) should contain theSameElementsAs List(ExampleBook.id, ExampleId1, ExampleId2, ExampleId3)
      }
    }
  }
}
