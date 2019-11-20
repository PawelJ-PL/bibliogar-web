package com.github.pawelj_pl.bibliogar.api.doobie

import cats.effect.IO
import com.github.pawelj_pl.bibliogar.api.doobie.setup.{TestDatabase, TestImplicits}
import com.github.pawelj_pl.bibliogar.api.infrastructure.repositories.{DoobieDevicesRepository}
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

  "Repository" should {
    "create and read device data" in {
      val expectedDevice = ExampleDevice.copy(createdAt = RepoTimestamp, updatedAt = RepoTimestamp)
      val transaction = for {
        saved <- repo.create(ExampleDevice)
        read  <- repo.findById(ExampleDevice.device_id).value
      } yield {
        saved shouldBe expectedDevice
        read shouldBe Some(expectedDevice)
      }
      transaction.transact(transactor).unsafeRunSync()
    }
    "delete device" in {
      val expectedDevice = ExampleDevice.copy(createdAt = RepoTimestamp, updatedAt = RepoTimestamp)
      val transaction = for {
        _      <- repo.create(ExampleDevice)
        before <- repo.findById(ExampleDevice.device_id).value
        _      <- repo.delete(ExampleDevice.device_id)
        after  <- repo.findById(ExampleDevice.device_id).value
      } yield {
        before shouldBe Some(expectedDevice)
        after shouldBe None
      }
      transaction.transact(transactor).unsafeRunSync()
    }
  }
}
