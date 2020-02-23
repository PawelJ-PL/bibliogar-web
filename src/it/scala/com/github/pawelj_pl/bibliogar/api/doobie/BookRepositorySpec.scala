package com.github.pawelj_pl.bibliogar.api.doobie

import cats.effect.IO
import com.github.pawelj_pl.bibliogar.api.domain.book.{Book, SourceType}
import com.github.pawelj_pl.bibliogar.api.doobie.setup.{TestDatabase, TestImplicits}
import com.github.pawelj_pl.bibliogar.api.infrastructure.repositories.{DoobieBookRepository, DoobieUserRepository}
import com.github.pawelj_pl.bibliogar.api.itconstants.{BookConstants, UserConstants}
import com.softwaremill.diffx.scalatest.DiffMatcher
import doobie.implicits._
import org.http4s.implicits._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class BookRepositorySpec
    extends AnyWordSpec
    with Matchers
    with DiffMatcher
    with BeforeAndAfterAll
    with TestDatabase
    with TestImplicits
    with BookConstants
    with UserConstants {
  private val repo = new DoobieBookRepository()
  private val userRepo = new DoobieUserRepository()
  private val transactor = tx[IO]

  override protected def afterAll(): Unit = closeDataSource()

  "Repository" should {
    "create and read books" in {
      val expectedBook = ExampleBook.copy(createdAt = RepoTimestamp, updatedAt = RepoTimestamp)
      val transaction = for {
        _       <- userRepo.create(ExampleUser)
        _       <- repo.create(ExampleBook.copy(id = ExampleId1, isbn = "1", sourceType = SourceType.GoogleBooks))
        _       <- repo.create(ExampleBook.copy(id = ExampleId2, isbn = "1"))
        book1   <- repo.create(ExampleBook)
        _       <- repo.create(ExampleBook.copy(id = ExampleId3, isbn = "1", sourceType = SourceType.OpenLibrary))
        _       <- repo.create(ExampleBook.copy(id = ExampleId4, isbn = "1"))
        result1 <- repo.findById(ExampleBook.id).value
        result2 <- repo.findNonUserDefinedBook("1")
      } yield {
        book1 should matchTo(expectedBook)
        result1 should matchTo[Option[Book]](Some(expectedBook))
        result2.map(_.id) should contain theSameElementsAs List(ExampleId1, ExampleId3)
      }
      transaction.transact(transactor).unsafeRunSync()
    }

    "find books with above average score" in {
      val transaction = for {
        _      <- userRepo.create(ExampleUser)
        _      <- repo.create(ExampleBook)
        _      <- repo.create(ExampleBook.copy(id = ExampleId1, isbn = "1"))
        _      <- repo.create(ExampleBook.copy(id = ExampleId2, score = None))
        _      <- repo.create(ExampleBook.copy(id = ExampleId3, isbn = "1", score = Some(30)))
        _      <- repo.create(ExampleBook.copy(id = ExampleId4, score = Some(3)))
        _      <- repo.create(ExampleBook.copy(id = ExampleId5, score = None))
        _      <- repo.create(ExampleBook.copy(id = ExampleId6, isbn = "1", score = Some(20)))
        _      <- repo.create(ExampleBook.copy(id = ExampleId7, score = Some(8)))
        _      <- repo.create(ExampleBook.copy(id = ExampleId8, score = Some(1)))
        _      <- repo.create(ExampleBook.copy(id = ExampleId9, score = Some(6)))
        _      <- repo.create(ExampleBook.copy(id = ExampleId10, score = Some(2)))
        result <- repo.findByIsbnWithScoreAboveOrEqualAverage(ExampleBook.isbn)
      } yield {
        result.map(_.id) should matchTo(List(ExampleId7, ExampleId9, ExampleBook.id))
      }
      transaction.transact(transactor).unsafeRunSync()
    }

    "find books by metadata" in {
      val transaction = for {
        _ <- userRepo.create(ExampleUser)
        _ <- repo.create(ExampleBook)
        _ <- repo.create(ExampleBook.copy(id = ExampleId1, title = "t1"))
        _ <- repo.create(ExampleBook.copy(id = ExampleId2, authors = Some("other authors")))
        _ <- repo.create(ExampleBook.copy(id = ExampleId3, score = Some(10)))
        _ <- repo.create(ExampleBook.copy(id = ExampleId4, cover = Some(uri"http://localhots:1111/cover.jpg")))
        _ <- repo.create(ExampleBook.copy(id = ExampleId5, sourceType = SourceType.OpenLibrary))
        _ <- repo.create(ExampleBook.copy(id = ExampleId6, isbn = "123456"))
        _ <- repo.create(ExampleBook.copy(id = ExampleId7, score = None))
        _ <- repo.create(ExampleBook.copy(id = ExampleId8, score = Some(2)))
        _ <- repo.create(ExampleBook.copy(id = ExampleId9, authors = None))
        _ <- repo.create(ExampleBook.copy(id = ExampleId10, cover = None))
        exampleResult <- repo.findByMetadata(ExampleBook.isbn,
                                             ExampleBook.title,
                                             ExampleBook.authors,
                                             ExampleBook.cover,
                                             ExampleBook.sourceType)
        emptyAuthorsResult <- repo.findByMetadata(ExampleBook.isbn, ExampleBook.title, None, ExampleBook.cover, ExampleBook.sourceType)
        emptyCoverResult   <- repo.findByMetadata(ExampleBook.isbn, ExampleBook.title, ExampleBook.authors, None, ExampleBook.sourceType)
      } yield {
        exampleResult.map(_.id) should matchTo(List(ExampleId7, ExampleId3, ExampleBook.id, ExampleId8))
        emptyAuthorsResult.map(_.id) should matchTo(List(ExampleId9))
        emptyCoverResult.map(_.id) should matchTo(List(ExampleId10))
      }
      transaction.transact(transactor).unsafeRunSync()
    }

    "increase score" in {
      val transaction = for {
        _           <- userRepo.create(ExampleUser)
        _           <- repo.create(ExampleBook)
        _           <- repo.create(ExampleBook.copy(id = ExampleId1, isbn = "1"))
        _           <- repo.create(ExampleBook.copy(id = ExampleId2, isbn = "2", score = None, sourceType = SourceType.OpenLibrary))
        _           <- repo.create(ExampleBook.copy(id = ExampleId3, isbn = "3"))
        book1Before <- repo.findByIsbnWithScoreAboveOrEqualAverage(ExampleBook.isbn)
        book2Before <- repo.findByIsbnWithScoreAboveOrEqualAverage("1")
        book3Before <- repo.findNonUserDefinedBook("2")
        book4Before <- repo.findByIsbnWithScoreAboveOrEqualAverage("3")
        _           <- repo.increaseScore(ExampleBook.id)
        _           <- repo.increaseScore(ExampleId2)
        _           <- repo.increaseScore(ExampleId3, 3)
        book1After  <- repo.findByIsbnWithScoreAboveOrEqualAverage(ExampleBook.isbn)
        book2After  <- repo.findByIsbnWithScoreAboveOrEqualAverage("1")
        book3After  <- repo.findNonUserDefinedBook("2")
        book4After  <- repo.findByIsbnWithScoreAboveOrEqualAverage("3")
      } yield {
        book1Before should matchTo(List(ExampleBook.copy(createdAt = RepoTimestamp, updatedAt = RepoTimestamp)))
        book1After should matchTo(List(ExampleBook.copy(createdAt = RepoTimestamp, updatedAt = RepoTimestamp, score = Some(5))))
        book2Before should matchTo(
          List(ExampleBook.copy(id = ExampleId1, isbn = "1", createdAt = RepoTimestamp, updatedAt = RepoTimestamp)))
        book2After should matchTo(List(ExampleBook.copy(id = ExampleId1, isbn = "1", createdAt = RepoTimestamp, updatedAt = RepoTimestamp)))
        book3Before should matchTo(
          List(
            ExampleBook.copy(id = ExampleId2,
                             isbn = "2",
                             score = None,
                             sourceType = SourceType.OpenLibrary,
                             createdAt = RepoTimestamp,
                             updatedAt = RepoTimestamp)))
        book3After should matchTo(
          List(
            ExampleBook.copy(id = ExampleId2,
                             isbn = "2",
                             score = None,
                             sourceType = SourceType.OpenLibrary,
                             createdAt = RepoTimestamp,
                             updatedAt = RepoTimestamp)))
        book4Before should matchTo(
          List(ExampleBook.copy(id = ExampleId3, isbn = "3", createdAt = RepoTimestamp, updatedAt = RepoTimestamp)))
        book4After should matchTo(
          List(ExampleBook.copy(id = ExampleId3, isbn = "3", score = Some(7), createdAt = RepoTimestamp, updatedAt = RepoTimestamp)))
      }
      transaction.transact(transactor).unsafeRunSync()
    }
  }
}
