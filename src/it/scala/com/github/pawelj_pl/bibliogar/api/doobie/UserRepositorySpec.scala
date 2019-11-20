package com.github.pawelj_pl.bibliogar.api.doobie

import java.time.Instant

import cats.effect.IO
import cats.syntax.apply._
import com.github.pawelj_pl.bibliogar.api.doobie.setup.{TestDatabase, TestImplicits}
import com.github.pawelj_pl.bibliogar.api.infrastructure.repositories.DoobieUserRepository
import com.github.pawelj_pl.bibliogar.api.itconstants.UserConstants
import doobie.implicits._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

class UserRepositorySpec extends WordSpec with Matchers with BeforeAndAfterAll with TestDatabase with UserConstants with TestImplicits {
  private val repo = new DoobieUserRepository
  private val transactor = tx[IO]

  override protected def afterAll(): Unit = closeDataSource()

  "Repository" should {
    "create and read user" in {
      val transaction = for {
        empty1 <- repo.findUserByEmail(ExampleUser.email).value
        empty2 <- repo.findUserById(ExampleUser.id).value
        saved  <- repo.create(ExampleUser)
        read1  <- repo.findUserByEmail(ExampleUser.email).value
        read2  <- repo.findUserById(ExampleUser.id).value
      } yield {
        val expectedUser = ExampleUser.copy(createdAt = RepoTimestamp, updatedAt = RepoTimestamp)
        empty1 shouldBe None
        empty2 shouldBe None
        saved shouldBe expectedUser
        read1 shouldBe Some(expectedUser)
        read2 shouldBe Some(expectedUser)
      }
      transaction.transact(transactor).unsafeRunSync()
    }

    "create and read user and auth" in {
      val transaction = for {
        empty1    <- repo.findUserWithAuthById(ExampleUser.id).value
        empty2    <- repo.findAuthDataFor(ExampleUser.id).value
        _         <- repo.create(ExampleUser.copy(id = ExampleId1, email = "other@example.org"))
        savedUser <- repo.create(ExampleUser)
        _         <- repo.create(ExampleAuthData.copy(userId = ExampleId1, enabled = false))
        savedAuth <- repo.create(ExampleAuthData)
        read1     <- repo.findUserWithAuthById(ExampleUser.id).value
        read2     <- repo.findUserWithAuthByEmail(ExampleUser.email).value
        read3     <- repo.findAuthDataFor(ExampleUser.id).value
      } yield {
        val expectedUser = ExampleUser.copy(createdAt = RepoTimestamp, updatedAt = RepoTimestamp)
        val expectedAuth = ExampleAuthData.copy(updatedAt = RepoTimestamp)
        empty1 shouldBe None
        empty2 shouldBe None
        savedUser shouldBe expectedUser
        savedAuth shouldBe expectedAuth
        read1 shouldBe Some((expectedUser, expectedAuth))
        read2 shouldBe Some((expectedUser, expectedAuth))
        read3 shouldBe Some(expectedAuth)
      }
      transaction.transact(transactor).unsafeRunSync()
    }

    "delete users" in {
      val user2 = ExampleUser.copy(id = ExampleId1, email = "1@x.pl", updatedAt = RepoTimestamp, createdAt = RepoTimestamp)
      val user3 = ExampleUser.copy(id = ExampleId2, email = "2@x.pl", updatedAt = RepoTimestamp, createdAt = RepoTimestamp)
      val transaction = for {
        _      <- repo.create(ExampleUser)
        _      <- repo.create(user2)
        _      <- repo.create(user3)
        before <- (repo.findUserById(ExampleUser.id).value, repo.findUserById(ExampleId1).value, repo.findUserById(ExampleId2).value).tupled
        _      <- repo.deleteByIds(ExampleUser.id, ExampleId2, ExampleId3)
        after  <- (repo.findUserById(ExampleUser.id).value, repo.findUserById(ExampleId1).value, repo.findUserById(ExampleId2).value).tupled
      } yield {
        before shouldBe (Some(ExampleUser.copy(createdAt = RepoTimestamp, updatedAt = RepoTimestamp)), Some(user2), Some(user3))
        after shouldBe (None, Some(user2), None)
      }
      transaction.transact(transactor).unsafeRunSync()
    }

    "update user and return new data" when {
      "user exists" in {
        val transaction = for {
          _       <- repo.create(ExampleUser)
          updated <- repo.update(ExampleUser.copy(email = "new@example.org", updatedAt = Instant.EPOCH)).value
          after   <- repo.findUserById(ExampleUser.id).value
        } yield {
          val expectedUser = ExampleUser.copy(email = "new@example.org", updatedAt = RepoTimestamp)
          updated shouldBe Some(expectedUser)
          after shouldBe Some(expectedUser)
        }
        transaction.transact(transactor).unsafeRunSync()
      }
    }

    "update user and return None" when {
      "user does not exist" in {
        val transaction = for {
          updated <- repo.update(ExampleUser.copy(email = "new@example.org", updatedAt = Instant.EPOCH)).value
          after   <- repo.findUserById(ExampleUser.id).value
        } yield {
          updated shouldBe None
          after shouldBe None
        }
        transaction.transact(transactor).unsafeRunSync()
      }
    }

    "find outdated registrations" when {
      "time after modification timestamp" in {
        val user2 = ExampleUser.copy(id = ExampleId1, email = "1@x.pl", updatedAt = RepoTimestamp, createdAt = RepoTimestamp)
        val user3 = ExampleUser.copy(id = ExampleId2, email = "2@x.pl", updatedAt = RepoTimestamp, createdAt = RepoTimestamp)
        val user4 = ExampleUser.copy(id = ExampleId3, email = "3@x.pl", updatedAt = RepoTimestamp, createdAt = RepoTimestamp)
        val transaction = for {
          _      <- repo.create(ExampleUser)
          _      <- repo.create(ExampleAuthData)
          _      <- repo.create(user2)
          _      <- repo.create(ExampleAuthData.copy(userId = ExampleId1, confirmed = false))
          _      <- repo.create(user3)
          _      <- repo.create(ExampleAuthData.copy(userId = ExampleId2))
          _      <- repo.create(user4)
          _      <- repo.create(ExampleAuthData.copy(userId = ExampleId3, confirmed = false))
          result <- repo.findNotConfirmedAuthDataOlderThan(RepoTimestamp.plusSeconds(60))
        } yield {
          result.map(_.userId) should contain theSameElementsAs Vector(ExampleId1, ExampleId3)
        }
        transaction.transact(transactor).unsafeRunSync()
      }
      "time before modification timestamp" in {
        val user2 = ExampleUser.copy(id = ExampleId1, email = "1@x.pl", updatedAt = RepoTimestamp, createdAt = RepoTimestamp)
        val user3 = ExampleUser.copy(id = ExampleId2, email = "2@x.pl", updatedAt = RepoTimestamp, createdAt = RepoTimestamp)
        val user4 = ExampleUser.copy(id = ExampleId3, email = "3@x.pl", updatedAt = RepoTimestamp, createdAt = RepoTimestamp)
        val transaction = for {
          _      <- repo.create(ExampleUser)
          _      <- repo.create(ExampleAuthData)
          _      <- repo.create(user2)
          _      <- repo.create(ExampleAuthData.copy(userId = ExampleId1, confirmed = false))
          _      <- repo.create(user3)
          _      <- repo.create(ExampleAuthData.copy(userId = ExampleId2))
          _      <- repo.create(user4)
          _      <- repo.create(ExampleAuthData.copy(userId = ExampleId3, confirmed = false))
          result <- repo.findNotConfirmedAuthDataOlderThan(RepoTimestamp.minusSeconds(60))
        } yield {
          result.map(_.userId) shouldBe empty
        }
        transaction.transact(transactor).unsafeRunSync()
      }
    }

    "update auth data" when {
      "auth data exists" in {
        val transaction = for {
          _       <- repo.create(ExampleUser)
          _       <- repo.create(ExampleAuthData)
          updated <- repo.update(ExampleAuthData.copy(confirmed = false, updatedAt = Instant.EPOCH)).value
          read    <- repo.findAuthDataFor(ExampleUser.id).value
        } yield {
          val expectedAuth = ExampleAuthData.copy(confirmed = false, updatedAt = RepoTimestamp)
          updated shouldBe Some(expectedAuth)
          read shouldBe Some(expectedAuth)
        }
        transaction.transact(transactor).unsafeRunSync()
      }
      "auth data does not exist" in {
        val transaction = for {
          _       <- repo.create(ExampleUser)
          updated <- repo.update(ExampleAuthData.copy(confirmed = false, updatedAt = Instant.EPOCH)).value
          read    <- repo.findAuthDataFor(ExampleUser.id).value
        } yield {
          updated shouldBe None
          read shouldBe None
        }
        transaction.transact(transactor).unsafeRunSync()
      }
    }
  }
}
