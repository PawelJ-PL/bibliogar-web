package com.github.pawelj_pl.bibliogar.api.doobie

import cats.effect.IO
import com.github.pawelj_pl.bibliogar.api.doobie.setup.{TestDatabase, TestImplicits}
import com.github.pawelj_pl.bibliogar.api.infrastructure.repositories.{DoobieApiKeyRepository, DoobieUserRepository}
import com.github.pawelj_pl.bibliogar.api.itconstants.UserConstants
import doobie.implicits._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers

class ApiKeyRepositorySpec extends AnyWordSpec with Matchers with BeforeAndAfterAll with TestDatabase with UserConstants with TestImplicits {
  private val transactor = tx[IO]
  private val userRepo = new DoobieUserRepository
  private val repo = new DoobieApiKeyRepository

  override protected def afterAll(): Unit = closeDataSource()

  "Repository" should {
    "create and read key" in {
      val transaction = for {
        _     <- userRepo.create(ExampleUser)
        saved <- repo.create(ExampleApiKey)
        read1 <- repo.find(ExampleApiKey.apiKey).value
        read2 <- repo.findById(ExampleApiKey.keyId).value
      } yield {
        val expectedKey = ExampleApiKey.copy(createdAt = RepoTimestamp, updatedAt = RepoTimestamp)
        saved shouldBe expectedKey
        read1 shouldBe Some(expectedKey)
        read2 shouldBe Some(expectedKey)
      }
      transaction.transact(transactor).unsafeRunSync()
    }
  }
}
