package com.github.pawelj_pl.bibliogar.api.doobie

import java.time.Instant

import cats.effect.IO
import cats.syntax.applicativeError._
import com.github.pawelj_pl.bibliogar.api.doobie.setup.{TestDatabase, TestImplicits}
import com.github.pawelj_pl.bibliogar.api.infrastructure.repositories.{DbError, DoobieBookRepository, DoobieLibraryRepository, DoobieLoanRepository, DoobieUserRepository}
import com.github.pawelj_pl.bibliogar.api.itconstants.{BookConstants, LibraryConstants, LoanConstants, UserConstants}
import com.softwaremill.diffx.scalatest.DiffMatcher
import doobie.implicits._
import org.scalatest.{BeforeAndAfterAll, EitherValues, OptionValues}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class LoanRepositorySpec
    extends AnyWordSpec
    with Matchers
    with DiffMatcher
    with EitherValues
    with OptionValues
    with BeforeAndAfterAll
    with TestDatabase
    with TestImplicits
    with UserConstants
    with LoanConstants
    with LibraryConstants
    with BookConstants {
  private val repo = new DoobieLoanRepository()
  private val userRepo = new DoobieUserRepository()
  private val libraryRepo = new DoobieLibraryRepository()
  private val bookRepo = new DoobieBookRepository()

  private val transactor = tx[IO]

  override protected def afterAll(): Unit = closeDataSource()

  "Repository" should {
    "save and read loans" in {
      val book2 = ExampleBook.copy(id = ExampleId1)
      val baseLoan = ExampleLoan.copy(books = ExampleLoan.books :+ Some(book2.id))
      val expectedLoan = baseLoan.copy(createdAt = RepoTimestamp, updatedAt = RepoTimestamp)
      val transaction = for {
        _                 <- userRepo.create(ExampleUser.copy(id = ExampleId2, email = "other@email"))
        _                 <- userRepo.create(ExampleUser)
        _                 <- libraryRepo.create(ExampleLibrary)
        _                 <- bookRepo.create(ExampleBook)
        _                 <- bookRepo.create(book2)
        _                 <- repo.create(ExampleLoan.copy(id = ExampleId3))
        created           <- repo.create(baseLoan)
        _                 <- repo.create(ExampleLoan.copy(id = ExampleId4, userId = ExampleId2))
        _                 <- repo.create(ExampleLoan.copy(id = ExampleId5, returnedAt = Some(Instant.EPOCH)))
        _                 <- repo.create(ExampleLoan.copy(id = ExampleId6, returnedAt = Some(Instant.EPOCH)))
        result            <- repo.findById(ExampleLoan.id).value
        byUser            <- repo.findByUser(ExampleUser.id, 0, 10)
        byUserLimit       <- repo.findByUser(ExampleUser.id, 0, 2)
        byUserNonReturned <- repo.findByUserAndEmptyReturnedAt(ExampleUser.id)
      } yield {
        created should matchTo(expectedLoan)
        result should matchTo(Option(expectedLoan))
        byUser.map(_.id) should contain theSameElementsAs List(ExampleId3, baseLoan.id, ExampleId5, ExampleId6)
        byUserLimit.size shouldBe 2
        byUserNonReturned.map(_.id) should contain theSameElementsAs List(ExampleId3, baseLoan.id)
      }
      transaction.transact(transactor).unsafeRunSync()
    }

    "return error on FK violation during loan creation" in {
      val transaction = for {
        result <- repo.create(ExampleLoan).attempt
      } yield {
        result.left.value shouldBe a[DbError.ForeignKeyViolation]
      }
      transaction.transact(transactor).unsafeRunSync()
    }

    "update loan" in {
      val book2 = ExampleBook.copy(id = ExampleId1)
      val book3 = ExampleBook.copy(id = ExampleId4)
      val baseLoan = ExampleLoan.copy(books = ExampleLoan.books :+ Some(book2.id))
      val loan2 = ExampleLoan.copy(id = ExampleId2)
      val loan3 = ExampleLoan.copy(id = ExampleId3, books = List(Some(ExampleBook.id)))
      val updatedLoan1 = baseLoan.copy(returnedAt = Some(Instant.EPOCH), books = List(Some(book3.id), None))
      val updatedLoan2 = loan2.copy(returnedAt = Some(Instant.EPOCH))
      val transaction = for {
        _  <- userRepo.create(ExampleUser)
        _  <- libraryRepo.create(ExampleLibrary)
        _  <- bookRepo.create(ExampleBook)
        _  <- bookRepo.create(ExampleBook.copy(id = book2.id))
        _  <- bookRepo.create(ExampleBook.copy(id = book3.id))
        _  <- repo.create(baseLoan)
        _  <- repo.create(loan2)
        _  <- repo.create(loan3)
        u1 <- repo.update(updatedLoan1).value
        u2 <- repo.update(updatedLoan2).value
        r1 <- repo.findById(ExampleLoan.id).value
        r2 <- repo.findById(loan2.id).value
        r3 <- repo.findById(loan3.id).value
      } yield {
        u1 should matchTo(Option(updatedLoan1.copy(updatedAt = RepoTimestamp)))
        u2 should matchTo(Option(updatedLoan2.copy(updatedAt = RepoTimestamp)))
        r1 should matchTo(Option(updatedLoan1.copy(updatedAt = RepoTimestamp)))
        r2 should matchTo(Option(updatedLoan2.copy(updatedAt = RepoTimestamp)))
        r3 should matchTo(Option(loan3.copy(createdAt = RepoTimestamp, updatedAt = RepoTimestamp)))
      }
      transaction.transact(transactor).unsafeRunSync()
    }
  }
}
