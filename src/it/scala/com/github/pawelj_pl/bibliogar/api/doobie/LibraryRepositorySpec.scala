package com.github.pawelj_pl.bibliogar.api.doobie

import cats.effect.IO
import cats.syntax.apply._
import com.github.pawelj_pl.bibliogar.api.domain.library.Library
import com.github.pawelj_pl.bibliogar.api.doobie.setup.{TestDatabase, TestImplicits}
import com.github.pawelj_pl.bibliogar.api.infrastructure.repositories.{DoobieLibraryRepository, DoobieUserRepository}
import com.github.pawelj_pl.bibliogar.api.itconstants.{LibraryConstants, UserConstants}
import com.softwaremill.diffx.scalatest.DiffMatcher
import doobie.implicits._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers

class LibraryRepositorySpec
    extends AnyWordSpec
    with Matchers
    with DiffMatcher
    with BeforeAndAfterAll
    with TestDatabase
    with TestImplicits
    with LibraryConstants
    with UserConstants {
  private val repo = new DoobieLibraryRepository()
  private val userRepo = new DoobieUserRepository()
  private val transactor = tx[IO]

  override protected def afterAll(): Unit = closeDataSource()

  "Repository" should {
    "create and read library" in {
      val expectedLibrary = ExampleLibrary.copy(createdAt = RepoTimestamp, updatedAt = RepoTimestamp)
      val transaction = for {
        _       <- userRepo.create(ExampleUser)
        _       <- userRepo.create(ExampleUser.copy(id = ExampleId3, email = "foo@bar"))
        before  <- repo.findById(ExampleLibrary.id).value
        _       <- repo.create(ExampleLibrary.copy(id = ExampleId1))
        saved   <- repo.create(ExampleLibrary)
        _       <- repo.create(ExampleLibrary.copy(id = ExampleId2, ownerId = ExampleId3))
        _       <- repo.create(ExampleLibrary.copy(id = ExampleId4))
        after   <- repo.findById(ExampleLibrary.id).value
        ownedBy <- repo.findByOwner(ExampleUser.id)
      } yield {
        before should matchTo[Option[Library]](None)
        saved should matchTo(expectedLibrary)
        after should matchTo[Option[Library]](Some(expectedLibrary))
        ownedBy.map(_.id) should contain theSameElementsAs List(ExampleLibrary.id, ExampleId1, ExampleId4)
      }
      transaction.transact(transactor).unsafeRunSync()
    }
    "delete libraries" in {
      val transaction = for {
        _ <- userRepo.create(ExampleUser)
        _ <- repo.create(ExampleLibrary)
        _ <- repo.create(ExampleLibrary.copy(id = ExampleId1))
        _ <- repo.create(ExampleLibrary.copy(id = ExampleId2))
        _ <- repo.create(ExampleLibrary.copy(id = ExampleId3))
        _ <- repo.create(ExampleLibrary.copy(id = ExampleId4))
        before <- (
          repo.findById(ExampleLibrary.id).value,
          repo.findById(ExampleId1).value,
          repo.findById(ExampleId2).value,
          repo.findById(ExampleId3).value,
          repo.findById(ExampleId4).value
        ).mapN((a, b, c, d, e) => List(a, b, c, d, e))
        result <- repo.delete(ExampleLibrary.id, ExampleId2, ExampleId4)
        after <- (
          repo.findById(ExampleLibrary.id).value,
          repo.findById(ExampleId1).value,
          repo.findById(ExampleId2).value,
          repo.findById(ExampleId3).value,
          repo.findById(ExampleId4).value
        ).mapN((a, b, c, d, e) => List(a, b, c, d, e))
      } yield {
        before.map(_.map(_.id)) should contain theSameElementsAs List(Some(ExampleLibrary.id),
                                                                      Some(ExampleId1),
                                                                      Some(ExampleId2),
                                                                      Some(ExampleId3),
                                                                      Some(ExampleId4))
        result shouldBe 3
        after.map(_.map(_.id)) should contain theSameElementsAs List(None, Some(ExampleId1), None, Some(ExampleId3), None)
      }
      transaction.transact(transactor).unsafeRunSync()
    }
    "update library" in {
      val transaction = for {
        _           <- userRepo.create(ExampleUser)
        _           <- repo.create(ExampleLibrary)
        before      <- repo.findById(ExampleLibrary.id).value
        existing    <- repo.update(ExampleLibrary.copy(name = "newName")).value
        nonExisting <- repo.update(ExampleLibrary.copy(id = ExampleId1, name = "nxName")).value
        after       <- repo.findById(ExampleLibrary.id).value
      } yield {
        before should matchTo[Option[Library]](Some(ExampleLibrary.copy(updatedAt = RepoTimestamp, createdAt = RepoTimestamp)))
        existing should matchTo[Option[Library]](Some(ExampleLibrary.copy(name = "newName", updatedAt = RepoTimestamp)))
        nonExisting shouldBe None
        after should matchTo[Option[Library]](Some(ExampleLibrary.copy(name = "newName", updatedAt = RepoTimestamp)))
      }
      transaction.transact(transactor).unsafeRunSync()
    }
  }
}
