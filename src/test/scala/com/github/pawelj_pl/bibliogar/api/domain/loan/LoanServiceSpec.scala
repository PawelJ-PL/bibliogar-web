package com.github.pawelj_pl.bibliogar.api.domain.loan

import java.time.Instant

import cats.{Monad, ~>}
import cats.data.{NonEmptyList, StateT}
import cats.effect.IO
import cats.mtl.instances.all._
import cats.mtl.MonadState
import cats.syntax.either._
import com.github.pawelj_pl.bibliogar.api.{CommonError, LibraryError, LoanError}
import com.github.pawelj_pl.bibliogar.api.constants.{BookConstants, LibraryConstants, LoanConstants, UserConstants}
import com.github.pawelj_pl.bibliogar.api.domain.book.BookRepositoryAlgebra
import com.github.pawelj_pl.bibliogar.api.domain.library.LibraryRepositoryAlgebra
import com.github.pawelj_pl.bibliogar.api.infrastructure.dto.loan.{EditLoanReq, NewLoanReq}
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.{RandomProvider, TimeProvider}
import com.github.pawelj_pl.bibliogar.api.testdoubles.repositories.{BookRepositoryFake, LibraryRepositoryFake, LoanRepositoryFake}
import com.github.pawelj_pl.bibliogar.api.testdoubles.utils.{RandomProviderFake, TimeProviderFake}
import com.olegpy.meow.hierarchy.deriveMonadState
import com.softwaremill.diffx.scalatest.DiffMatcher
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class LoanServiceSpec
    extends AnyWordSpec
    with Matchers
    with DiffMatcher
    with LoanConstants
    with UserConstants
    with LibraryConstants
    with BookConstants {
  case class TestState(
    timeProviderState: TimeProviderFake.TimeProviderState = TimeProviderFake.TimeProviderState(),
    randomState: RandomProviderFake.RandomState = RandomProviderFake.RandomState(),
    libraryRepoState: LibraryRepositoryFake.LibraryRepositoryState = LibraryRepositoryFake.LibraryRepositoryState(),
    loanRepoState: LoanRepositoryFake.LoanRepositoryState = LoanRepositoryFake.LoanRepositoryState(),
    bookRepoState: BookRepositoryFake.BookRepositoryState = BookRepositoryFake.BookRepositoryState())
  type TestEffect[A] = StateT[IO, TestState, A]

  val instance: LoanService[TestEffect] = {
    implicit def timeProvider[F[_]: Monad: MonadState[*[_], TestState]]: TimeProvider[F] = TimeProviderFake.instance[F]
    implicit def randomProvider[F[_]: Monad: MonadState[*[_], TestState]]: RandomProvider[F] = RandomProviderFake.instance[F]
    implicit def libraryRepo[F[_]: Monad: MonadState[*[_], TestState]]: LibraryRepositoryAlgebra[F] = LibraryRepositoryFake.instance[F]
    implicit def loanRepo[F[_]: Monad: MonadState[*[_], TestState]]: LoanRepositoryAlgebra[F] = LoanRepositoryFake.instance[F]
    implicit def bookRepo[F[_]: Monad: MonadState[*[_], TestState]]: BookRepositoryAlgebra[F] = BookRepositoryFake.instance[F]

    implicit def dbToApp: TestEffect ~> TestEffect = new (TestEffect ~> TestEffect) {
      override def apply[A](fa: TestEffect[A]): TestEffect[A] = fa
    }

    LoanService.withDb[TestEffect, TestEffect]()
  }

  final val ExampleManyBooks = NonEmptyList.fromListUnsafe(ExampleLoan.books) :+ None :+ Some(ExampleId1) :+ None

  "Create loan" should {
    val dto = NewLoanReq(ExampleLibrary.id, NonEmptyList.fromListUnsafe(ExampleLoan.books))

    "create and return loan" in {
      val expectedLoan = Loan(FirstRandomUuid,
                              ExampleUser.id,
                              Some(ExampleLibrary.id),
                              Instant.ofEpochMilli(1558125179888L),
                              None,
                              ExampleLoan.books,
                              Now,
                              Now)
      val initialState = TestState(
        libraryRepoState = LibraryRepositoryFake.LibraryRepositoryState(
          libraries = Set(ExampleLibrary)
        )
      )
      val (state, result) = instance.createLoanAs(dto, ExampleUser.id).value.run(initialState).unsafeRunSync()
      result should matchTo(expectedLoan.asRight[LoanError])
      state.loanRepoState.loans.toList should matchTo(List(expectedLoan))
    }

    "create loan with limit exceeded" in {
      val expectedLoan =
        Loan(FirstRandomUuid,
             ExampleUser.id,
             Some(ExampleLibrary.id),
             Instant.ofEpochMilli(1558125179888L),
             None,
             ExampleManyBooks.toList,
             Now,
             Now)
      val request = dto.copy(books = ExampleManyBooks)
      val initialState = TestState(
        libraryRepoState = LibraryRepositoryFake.LibraryRepositoryState(
          libraries = Set(ExampleLibrary)
        )
      )
      val (state, result) = instance.createLoanAs(request, ExampleUser.id, allowLimitOverrun = true).value.run(initialState).unsafeRunSync()
      result should matchTo(expectedLoan.asRight[LoanError])
      state.loanRepoState.loans.toList should matchTo(List(expectedLoan))
    }

    "increase book scores" in {
      val books = List(
        ExampleBook.copy(id = ExampleId1, score = None),
        ExampleBook.copy(id = ExampleId2, score = Some(0)),
        ExampleBook.copy(id = ExampleId3, score = None),
        ExampleBook.copy(id = ExampleId4, score = Some(10)),
        ExampleBook.copy(id = ExampleId5, score = Some(2)),
      )

      val requestedBooks = NonEmptyList.of(Some(ExampleId1), None, Some(ExampleId4), None, Some(ExampleId5))

      val expectedLoan = Loan(FirstRandomUuid,
                              ExampleUser.id,
                              Some(ExampleLibrary.id),
                              Instant.ofEpochMilli(1558125179888L),
                              None,
                              requestedBooks.toList,
                              Now,
                              Now)
      val initialState = TestState(
        libraryRepoState = LibraryRepositoryFake.LibraryRepositoryState(
          libraries = Set(ExampleLibrary)
        ),
        bookRepoState = BookRepositoryFake.BookRepositoryState(
          books = books.toSet
        )
      )
      val (state, result) = instance.createLoanAs(dto.copy(books = requestedBooks), ExampleUser.id).value.run(initialState).unsafeRunSync()
      result should matchTo(expectedLoan.asRight[LoanError])
      state.loanRepoState.loans.toList should matchTo(List(expectedLoan))
      state.bookRepoState.books should contain theSameElementsAs
        books
          .updated(3, ExampleBook.copy(id = ExampleId4, score = Some(11)))
          .updated(4, ExampleBook.copy(id = ExampleId5, score = Some(3)))
    }

    "fail" when {
      "library not found" in {
        val initialState = TestState(
          libraryRepoState = LibraryRepositoryFake.LibraryRepositoryState(
            libraries = Set.empty
          )
        )
        val (state, result) = instance.createLoanAs(dto, ExampleUser.id).value.run(initialState).unsafeRunSync()
        result should matchTo((LibraryError.LibraryIdNotFound(ExampleLibrary.id): LoanError).asLeft[Loan])
        state.loanRepoState should matchTo(initialState.loanRepoState)
      }
      "library not owned by user" in {
        val initialState = TestState(
          libraryRepoState = LibraryRepositoryFake.LibraryRepositoryState(
            libraries = Set(ExampleLibrary.copy(ownerId = ExampleId1))
          )
        )
        val (state, result) = instance.createLoanAs(dto, ExampleUser.id).value.run(initialState).unsafeRunSync()
        result should matchTo((LibraryError.LibraryNotOwnedByUser(ExampleLibrary.id, ExampleUser.id): LoanError).asLeft[Loan])
        state.loanRepoState should matchTo(initialState.loanRepoState)
      }
      "books limit exceeded" in {
        val books = dto.books :+ None :+ Some(ExampleId1) :+ None
        val request = dto.copy(books = books)
        val initialState = TestState(
          libraryRepoState = LibraryRepositoryFake.LibraryRepositoryState(
            libraries = Set(ExampleLibrary)
          )
        )
        val (state, result) =
          instance.createLoanAs(request, ExampleUser.id, allowLimitOverrun = false).value.run(initialState).unsafeRunSync()
        result should matchTo((LoanError.BooksLimitExceeded(ExampleLibrary, books.size): LoanError).asLeft[Loan])
        state.loanRepoState should matchTo(initialState.loanRepoState)
      }
    }
  }

  "Update loan" should {
    val dto = EditLoanReq(TestLibraryId, ExampleLoan.returnTo, None, NonEmptyList.fromListUnsafe(ExampleLoan.books), None)

    "update and return loan" when {
      "everything is ok" in {
        val expectedLoan = ExampleLoan.copy(returnTo = Instant.EPOCH)
        val initialState = TestState(
          loanRepoState = LoanRepositoryFake.LoanRepositoryState(
            loans = Set(ExampleLoan)
          ),
          libraryRepoState = LibraryRepositoryFake.LibraryRepositoryState(
            libraries = Set(ExampleLibrary)
          )
        )
        val (state, result) =
          instance.updateLoanAs(ExampleLoan.id, dto.copy(returnTo = Instant.EPOCH), ExampleUser.id).value.run(initialState).unsafeRunSync()
        result should matchTo(expectedLoan.asRight[LoanError])
        state.loanRepoState.loans.toList should matchTo(List(expectedLoan))
      }

      "books limit is exceeded" in {
        val expectedLoan = ExampleLoan.copy(books = ExampleManyBooks.toList)
        val initialState = TestState(
          loanRepoState = LoanRepositoryFake.LoanRepositoryState(
            loans = Set(ExampleLoan)
          ),
          libraryRepoState = LibraryRepositoryFake.LibraryRepositoryState(
            libraries = Set(ExampleLibrary)
          )
        )
        val (state, result) =
          instance
            .updateLoanAs(ExampleLoan.id, dto.copy(books = ExampleManyBooks), ExampleUser.id, allowLimitOverrun = true)
            .value
            .run(initialState)
            .unsafeRunSync()
        result should matchTo(expectedLoan.asRight[LoanError])
        state.loanRepoState.loans.toList should matchTo(List(expectedLoan))
      }

      "version match" in {
        val expectedLoan = ExampleLoan.copy(returnTo = Instant.EPOCH)
        val initialState = TestState(
          loanRepoState = LoanRepositoryFake.LoanRepositoryState(
            loans = Set(ExampleLoan)
          ),
          libraryRepoState = LibraryRepositoryFake.LibraryRepositoryState(
            libraries = Set(ExampleLibrary)
          )
        )
        val (state, result) =
          instance
            .updateLoanAs(ExampleLoan.id,
                          dto.copy(returnTo = Instant.EPOCH, version = Some(ExampleLoan.updatedAt.toEpochMilli.toString)),
                          ExampleUser.id)
            .value
            .run(initialState)
            .unsafeRunSync()
        result should matchTo(expectedLoan.asRight[LoanError])
        state.loanRepoState.loans.toList should matchTo(List(expectedLoan))
      }
    }

    "update books score" in {
      val books = List(
        ExampleBook.copy(id = ExampleId1, score = None),
        ExampleBook.copy(id = ExampleId2, score = Some(30)),
        ExampleBook.copy(id = ExampleId3, score = None),
        ExampleBook.copy(id = ExampleId4, score = Some(10)),
        ExampleBook.copy(id = ExampleId5, score = Some(2)),
      )

      val actualBooks = List(None, Some(ExampleId1), Some(ExampleId2), None)
      val requestedBooks = NonEmptyList.of(Some(ExampleId1), None, Some(ExampleId4), None, Some(ExampleId5))

      val expectedLoan = ExampleLoan.copy(books = requestedBooks.toList)
      val initialState = TestState(
        loanRepoState = LoanRepositoryFake.LoanRepositoryState(
          loans = Set(ExampleLoan.copy(books = actualBooks))
        ),
        libraryRepoState = LibraryRepositoryFake.LibraryRepositoryState(
          libraries = Set(ExampleLibrary)
        ),
        bookRepoState = BookRepositoryFake.BookRepositoryState(
          books = books.toSet
        )
      )
      val (state, result) =
        instance.updateLoanAs(ExampleLoan.id, dto.copy(books = requestedBooks), ExampleUser.id).value.run(initialState).unsafeRunSync()
      result should matchTo(expectedLoan.asRight[LoanError])
      state.loanRepoState.loans.toList should matchTo(List(expectedLoan))
      state.bookRepoState.books should contain theSameElementsAs
        books
          .updated(1, ExampleBook.copy(id = ExampleId2, score = Some(29)))
          .updated(3, ExampleBook.copy(id = ExampleId4, score = Some(11)))
          .updated(4, ExampleBook.copy(id = ExampleId5, score = Some(3)))
    }

    "fail" when {
      "loan not found" in {
        val initialState = TestState(
          loanRepoState = LoanRepositoryFake.LoanRepositoryState(
            loans = Set.empty
          ),
          libraryRepoState = LibraryRepositoryFake.LibraryRepositoryState(
            libraries = Set(ExampleLibrary)
          )
        )
        val (state, result) =
          instance.updateLoanAs(ExampleLoan.id, dto.copy(returnTo = Instant.EPOCH), ExampleUser.id).value.run(initialState).unsafeRunSync()
        result should matchTo((LoanError.LoanNotFound(ExampleLoan.id): LoanError).asLeft[Loan])
        state.loanRepoState.loans.toList should matchTo(initialState.loanRepoState.loans.toList)
      }

      "loan not owned by user" in {
        val initialState = TestState(
          loanRepoState = LoanRepositoryFake.LoanRepositoryState(
            loans = Set(ExampleLoan.copy(userId = ExampleId1))
          ),
          libraryRepoState = LibraryRepositoryFake.LibraryRepositoryState(
            libraries = Set(ExampleLibrary)
          )
        )
        val (state, result) =
          instance.updateLoanAs(ExampleLoan.id, dto.copy(returnTo = Instant.EPOCH), ExampleUser.id).value.run(initialState).unsafeRunSync()
        result should matchTo((LoanError.LoanNotOwnedByUser(ExampleLoan.id, ExampleUser.id): LoanError).asLeft[Loan])
        state.loanRepoState.loans.toList should matchTo(initialState.loanRepoState.loans.toList)
      }

      "version does not match" in {
        val initialState = TestState(
          loanRepoState = LoanRepositoryFake.LoanRepositoryState(
            loans = Set(ExampleLoan)
          ),
          libraryRepoState = LibraryRepositoryFake.LibraryRepositoryState(
            libraries = Set(ExampleLibrary)
          )
        )
        val (state, result) =
          instance
            .updateLoanAs(ExampleLoan.id, dto.copy(returnTo = Instant.EPOCH, version = Some("otherVersion")), ExampleUser.id)
            .value
            .run(initialState)
            .unsafeRunSync()
        result should matchTo(
          (CommonError.ResourceVersionDoesNotMatch(ExampleLoan.updatedAt.toEpochMilli.toString, "otherVersion"): LoanError).asLeft[Loan])
        state.loanRepoState.loans.toList should matchTo(initialState.loanRepoState.loans.toList)
      }

      "library not found" in {
        val initialState = TestState(
          loanRepoState = LoanRepositoryFake.LoanRepositoryState(
            loans = Set(ExampleLoan)
          ),
          libraryRepoState = LibraryRepositoryFake.LibraryRepositoryState(
            libraries = Set.empty
          )
        )
        val (state, result) =
          instance.updateLoanAs(ExampleLoan.id, dto.copy(libraryId = ExampleId1), ExampleUser.id).value.run(initialState).unsafeRunSync()
        result should matchTo((LibraryError.LibraryIdNotFound(ExampleId1): LoanError).asLeft[Loan])
        state.loanRepoState.loans.toList should matchTo(initialState.loanRepoState.loans.toList)
      }

      "library is owned by other user" in {
        val initialState = TestState(
          loanRepoState = LoanRepositoryFake.LoanRepositoryState(
            loans = Set(ExampleLoan)
          ),
          libraryRepoState = LibraryRepositoryFake.LibraryRepositoryState(
            libraries = Set(ExampleLibrary, ExampleLibrary.copy(id = ExampleId1, ownerId = ExampleId2))
          )
        )
        val (state, result) =
          instance.updateLoanAs(ExampleLoan.id, dto.copy(libraryId = ExampleId1), ExampleUser.id).value.run(initialState).unsafeRunSync()
        result should matchTo((LibraryError.LibraryNotOwnedByUser(ExampleId1, ExampleUser.id): LoanError).asLeft[Loan])
        state.loanRepoState.loans.toList should matchTo(initialState.loanRepoState.loans.toList)
      }

      "books limit exceeded" in {
        val initialState = TestState(
          loanRepoState = LoanRepositoryFake.LoanRepositoryState(
            loans = Set(ExampleLoan)
          ),
          libraryRepoState = LibraryRepositoryFake.LibraryRepositoryState(
            libraries = Set(ExampleLibrary)
          )
        )
        val (state, result) =
          instance
            .updateLoanAs(ExampleLoan.id, dto.copy(books = ExampleManyBooks), ExampleUser.id, allowLimitOverrun = false)
            .value
            .run(initialState)
            .unsafeRunSync()
        result should matchTo((LoanError.BooksLimitExceeded(ExampleLibrary, ExampleManyBooks.size): LoanError).asLeft[Loan])
        state.loanRepoState.loans.toList should matchTo(initialState.loanRepoState.loans.toList)
      }
    }
  }

  "Finish loan" should {
    "finish opened loan" in {
      val expectedLoan = ExampleLoan.copy(returnedAt = Some(Now))
      val initialState = TestState(
        loanRepoState = LoanRepositoryFake.LoanRepositoryState(
          loans = Set(ExampleLoan)
        )
      )
      val (state, result) = instance.finishLoanAs(ExampleLoan.id, ExampleUser.id).value.run(initialState).unsafeRunSync()
      result should matchTo(expectedLoan.asRight[LoanError])
      state.loanRepoState.loans.toList should matchTo(List(expectedLoan))
    }

    "fail" when {
      "loan not found" in {
        val initialState = TestState(
          loanRepoState = LoanRepositoryFake.LoanRepositoryState(
            loans = Set.empty
          )
        )
        val (state, result) = instance.finishLoanAs(ExampleLoan.id, ExampleUser.id).value.run(initialState).unsafeRunSync()
        result should matchTo((LoanError.LoanNotFound(ExampleLoan.id): LoanError).asLeft[Loan])
        state.loanRepoState.loans.toList should matchTo(initialState.loanRepoState.loans.toList)
      }
      "loan owned by other user" in {
        val initialState = TestState(
          loanRepoState = LoanRepositoryFake.LoanRepositoryState(
            loans = Set(ExampleLoan.copy(userId = ExampleId1))
          )
        )
        val (state, result) = instance.finishLoanAs(ExampleLoan.id, ExampleUser.id).value.run(initialState).unsafeRunSync()
        result should matchTo((LoanError.LoanNotOwnedByUser(ExampleLoan.id, ExampleUser.id): LoanError).asLeft[Loan])
        state.loanRepoState.loans.toList should matchTo(initialState.loanRepoState.loans.toList)
      }
      "loan already finished" in {
        val initialState = TestState(
          loanRepoState = LoanRepositoryFake.LoanRepositoryState(
            loans = Set(ExampleLoan.copy(returnedAt = Some(Instant.EPOCH)))
          )
        )
        val (state, result) = instance.finishLoanAs(ExampleLoan.id, ExampleUser.id).value.run(initialState).unsafeRunSync()
        result should matchTo((LoanError.LoanAlreadyFinished(ExampleLoan.id): LoanError).asLeft[Loan])
        state.loanRepoState.loans.toList should matchTo(initialState.loanRepoState.loans.toList)
      }
    }
  }

  "Get loan data" should {
    "return data" in {
      val initialState = TestState(
        loanRepoState = LoanRepositoryFake.LoanRepositoryState(
          loans = Set(ExampleLoan)
        )
      )
      val result = instance.getLoanDataAs(ExampleLoan.id, ExampleUser.id).value.runA(initialState).unsafeRunSync()
      result should matchTo(ExampleLoan.asRight[LoanError])
    }
    "fail" when {
      "loan owned by other user" in {
        val initialState = TestState(
          loanRepoState = LoanRepositoryFake.LoanRepositoryState(
            loans = Set(ExampleLoan)
          )
        )
        val result = instance.getLoanDataAs(ExampleLoan.id, ExampleId1).value.runA(initialState).unsafeRunSync()
        result should matchTo((LoanError.LoanNotOwnedByUser(ExampleLoan.id, ExampleId1): LoanError).asLeft[Loan])
      }
    }
  }

  "Get data by user" should {
    "return data taking into account page and page size" in {
      val initialState = TestState(
        loanRepoState = LoanRepositoryFake.LoanRepositoryState(
          loans = Set(
            ExampleLoan,
            ExampleLoan.copy(id = ExampleId1, createdAt = Now.minusSeconds(1)),
            ExampleLoan.copy(id = ExampleId2, createdAt = Now.minusSeconds(2)),
            ExampleLoan.copy(id = ExampleId9, userId = ExampleId13, createdAt = Now.minusSeconds(3)),
            ExampleLoan.copy(id = ExampleId3, createdAt = Now.minusSeconds(4)),
            ExampleLoan.copy(id = ExampleId4, createdAt = Now.minusSeconds(6)),
            ExampleLoan.copy(id = ExampleId5, createdAt = Now.minusSeconds(7)),
            ExampleLoan.copy(id = ExampleId11, userId = ExampleId13, createdAt = Now.minusSeconds(8)),
            ExampleLoan.copy(id = ExampleId6, createdAt = Now.minusSeconds(9)),
            ExampleLoan.copy(id = ExampleId10, userId = ExampleId13, createdAt = Now.minusSeconds(5)),
            ExampleLoan.copy(id = ExampleId7, createdAt = Now.minusSeconds(10)),
            ExampleLoan.copy(id = ExampleId12, userId = ExampleId13, createdAt = Now.minusSeconds(11)),
            ExampleLoan.copy(id = ExampleId8, createdAt = Now.minusSeconds(12))
          )
        )
      )
      val result = instance.getLoansOfUser(ExampleUser.id, 4, 4).runA(initialState).unsafeRunSync()
      result.map(_.id) should matchTo(List(ExampleId4, ExampleId5, ExampleId6, ExampleId7))
    }
  }

  "find active by user" should {
    "return loans" in {
      val initialState = TestState(
        loanRepoState = LoanRepositoryFake.LoanRepositoryState(
          loans = Set(
            ExampleLoan,
            ExampleLoan.copy(id = ExampleId1, userId = ExampleId2),
            ExampleLoan.copy(id = ExampleId3),
            ExampleLoan.copy(id = ExampleId4, returnedAt = Some(Instant.EPOCH)),
            ExampleLoan.copy(id = ExampleId5),
            ExampleLoan.copy(id = ExampleId6, userId = ExampleId2, returnedAt = Some(Instant.EPOCH))
          )
        )
      )
      val result = instance.getActiveLoansOfUser(ExampleUser.id).runA(initialState).unsafeRunSync()
      result.map(_.id) should contain theSameElementsAs List(ExampleLoan.id, ExampleId3, ExampleId5)
    }
  }
}
