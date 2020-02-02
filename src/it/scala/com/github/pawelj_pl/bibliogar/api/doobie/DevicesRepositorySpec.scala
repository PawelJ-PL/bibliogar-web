package com.github.pawelj_pl.bibliogar.api.doobie

import cats.effect.IO
import cats.syntax.apply._
import com.github.pawelj_pl.bibliogar.api.domain.user.User
import com.github.pawelj_pl.bibliogar.api.doobie.setup.{TestDatabase, TestImplicits}
import com.github.pawelj_pl.bibliogar.api.infrastructure.repositories.{DoobieDevicesRepository, DoobieUserRepository}
import com.github.pawelj_pl.bibliogar.api.itconstants.DeviceConstants
import doobie.implicits._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

class DevicesRepositorySpec
    extends WordSpec
    with Matchers
    with BeforeAndAfterAll
    with TestDatabase
    with TestImplicits
    with DeviceConstants {
  private val transactor = tx[IO]
  private val repo = new DoobieDevicesRepository
  private val userRepo = new DoobieUserRepository

  "Repository" should {
    "create and read device data" in {
      val expectedDevice = ExampleDevice.copy(createdAt = RepoTimestamp, updatedAt = RepoTimestamp)
      val transaction = for {
        _     <- userRepo.create(ExampleUser)
        saved <- repo.create(ExampleDevice)
        read  <- repo.findById(ExampleDevice.device_id).value
      } yield {
        saved shouldBe expectedDevice
        read shouldBe Some(expectedDevice)
      }
      transaction.transact(transactor).unsafeRunSync()
    }
    "delete devices" in {
      val transaction = for {
        _ <- userRepo.create(ExampleUser)
        _ <- repo.create(ExampleDevice)
        _ <- repo.create(ExampleDevice.copy(device_id = ExampleId1, uniqueId = "id1"))
        _ <- repo.create(ExampleDevice.copy(device_id = ExampleId2, uniqueId = "id2"))
        _ <- repo.create(ExampleDevice.copy(device_id = ExampleId3, uniqueId = "id3"))
        before <- (
          repo.findById(ExampleDevice.device_id).value,
          repo.findById(ExampleId1).value,
          repo.findById(ExampleId2).value,
          repo.findById(ExampleId3).value,
        ).mapN((a, b, c, d) => List(a, b, c, d))
        _ <- repo.delete(ExampleDevice.device_id, ExampleId2)
        after <- (
          repo.findById(ExampleDevice.device_id).value,
          repo.findById(ExampleId1).value,
          repo.findById(ExampleId2).value,
          repo.findById(ExampleId3).value,
        ).mapN((a, b, c, d) => List(a, b, c, d))
      } yield {
        before.map(_.map(_.device_id)) should contain theSameElementsAs List(Some(ExampleDevice.device_id),
                                                                             Some(ExampleId1),
                                                                             Some(ExampleId2),
                                                                             Some(ExampleId3))
        after.map(_.map(_.device_id)) should contain theSameElementsAs List(None, Some(ExampleId1), None, Some(ExampleId3))
      }
      transaction.transact(transactor).unsafeRunSync()
    }
    "find devices by unique ID and user" in {
      val otherUser = User(ExampleId1, "x@y.z", "My nickname", Now, Now)
      val transaction = for {
        _      <- userRepo.create(ExampleUser)
        _      <- userRepo.create(otherUser)
        _      <- repo.create(ExampleDevice)
        _      <- repo.create(ExampleDevice.copy(device_id = ExampleId2, uniqueId = "otherId"))
        _      <- repo.create(ExampleDevice.copy(device_id = ExampleId4, ownerId = ExampleId1))
        _      <- repo.create(ExampleDevice.copy(device_id = ExampleId6, uniqueId = "anotherId", ownerId = ExampleId1))
        _      <- repo.create(ExampleDevice.copy(device_id = ExampleId7, uniqueId = "anotherId"))
        result <- repo.findByUniqueIdAndUser(ExampleDevice.uniqueId, ExampleDevice.ownerId)
      } yield {
        result.map(_.device_id) should contain theSameElementsAs List(ExampleDevice.device_id)
      }
      transaction.transact(transactor).unsafeRunSync()
    }
  }
}
