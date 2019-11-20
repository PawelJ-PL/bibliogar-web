package com.github.pawelj_pl.bibliogar.api.doobie

import cats.effect.IO
import cats.syntax.apply._
import com.github.pawelj_pl.bibliogar.api.domain.user.TokenType
import com.github.pawelj_pl.bibliogar.api.doobie.setup.{TestDatabase, TestImplicits}
import com.github.pawelj_pl.bibliogar.api.infrastructure.repositories.{DoobieUserRepository, DoobieUserTokenRepository}
import com.github.pawelj_pl.bibliogar.api.itconstants.UserConstants
import doobie.implicits._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

class UserTokenRepositorySpec
    extends WordSpec
    with Matchers
    with BeforeAndAfterAll
    with TestDatabase
    with UserConstants
    with TestImplicits {
  private val transactor = tx[IO]
  private val userRepo = new DoobieUserRepository
  private val repo = new DoobieUserTokenRepository

  override protected def afterAll(): Unit = closeDataSource()

  "Repository" should {
    "create and read token" when {
      "requested token has valid type" in {
        val transaction = for {
          _     <- userRepo.create(ExampleUser)
          saved <- repo.create(ExampleUserToken)
          read  <- repo.get(ExampleUserToken.token, ExampleUserToken.tokenType).value
        } yield {
          val expectedToken = ExampleUserToken.copy(createdAt = RepoTimestamp, updatedAt = RepoTimestamp)
          saved shouldBe expectedToken
          read shouldBe Some(expectedToken)
        }
        transaction.transact(transactor).unsafeRunSync()
      }
      "requested token has different type" in {
        val transaction = for {
          _     <- userRepo.create(ExampleUser)
          saved <- repo.create(ExampleUserToken)
          read  <- repo.get(ExampleUserToken.token, TokenType.PasswordReset).value
        } yield {
          val expectedToken = ExampleUserToken.copy(createdAt = RepoTimestamp, updatedAt = RepoTimestamp)
          saved shouldBe expectedToken
          read shouldBe None
        }
        transaction.transact(transactor).unsafeRunSync()
      }
    }
    "delete by token value" in {
      val transaction = for {
        _      <- userRepo.create(ExampleUser)
        _      <- repo.create(ExampleUserToken)
        before <- repo.get(ExampleUserToken.token, ExampleUserToken.tokenType).value
        _      <- repo.delete(ExampleUserToken.token)
        after  <- repo.get(ExampleUserToken.token, ExampleUserToken.tokenType).value
      } yield {
        val expectedToken = ExampleUserToken.copy(createdAt = RepoTimestamp, updatedAt = RepoTimestamp)
        before shouldBe Some(expectedToken)
        after shouldBe None
      }
      transaction.transact(transactor).unsafeRunSync()
    }
    "delete tokens by user and type" in {
      val transaction = for {
        _ <- userRepo.create(ExampleUser)
        _ <- userRepo.create(ExampleUser.copy(id = ExampleId1, email = "1@x.pl"))
        _ <- repo.create(ExampleUserToken.copy(token = "t1"))
        _ <- repo.create(ExampleUserToken.copy(token = "t2", tokenType = TokenType.PasswordReset))
        _ <- repo.create(ExampleUserToken.copy(token = "t3", account = ExampleId1))
        _ <- repo.create(ExampleUserToken.copy(token = "t4"))
        _ <- repo.create(ExampleUserToken.copy(token = "t5", tokenType = TokenType.PasswordReset, account = ExampleId1))
        _ <- repo.create(ExampleUserToken.copy(token = "t6"))
        _ <- repo.create(ExampleUserToken.copy(token = "t7", tokenType = TokenType.PasswordReset))
        before <- (
          repo.get("t1", TokenType.Registration).value,
          repo.get("t2", TokenType.PasswordReset).value,
          repo.get("t3", TokenType.Registration).value,
          repo.get("t4", TokenType.Registration).value,
          repo.get("t5", TokenType.PasswordReset).value,
          repo.get("t6", TokenType.Registration).value,
          repo.get("t7", TokenType.PasswordReset).value,
        ).mapN((a, b, c, d, e, f, g) => List(a, b, c, d, e, f, g))
        _ <- repo.deleteByAccountAndType(ExampleUser.id, TokenType.PasswordReset)
        after <- (
          repo.get("t1", TokenType.Registration).value,
          repo.get("t2", TokenType.PasswordReset).value,
          repo.get("t3", TokenType.Registration).value,
          repo.get("t4", TokenType.Registration).value,
          repo.get("t5", TokenType.PasswordReset).value,
          repo.get("t6", TokenType.Registration).value,
          repo.get("t7", TokenType.PasswordReset).value
        ).mapN((a, b, c, d, e, f, g) => List(a, b, c, d, e, f, g))
      } yield {
        before.map(_.map(_.token)) shouldBe List(Some("t1"), Some("t2"), Some("t3"), Some("t4"), Some("t5"), Some("t6"), Some("t7"))
        after.map(_.map(_.token)) shouldBe List(Some("t1"), None, Some("t3"), Some("t4"), Some("t5"), Some("t6"), None)
      }
      transaction.transact(transactor).unsafeRunSync()
    }
  }
}
