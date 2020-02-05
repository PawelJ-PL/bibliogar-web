package com.github.pawelj_pl.bibliogar.api.domain.device

import cats.{Monad, ~>}
import cats.data.StateT
import cats.effect.IO
import cats.mtl.instances.all._
import cats.mtl.MonadState
import com.github.pawelj_pl.bibliogar.api.DeviceError
import com.github.pawelj_pl.bibliogar.api.constants.{DeviceConstants, UserConstants}
import com.github.pawelj_pl.bibliogar.api.domain.user.{ApiKey, ApiKeyRepositoryAlgebra, KeyType}
import com.github.pawelj_pl.bibliogar.api.infrastructure.config.Config.MobileAppConfig
import com.github.pawelj_pl.bibliogar.api.infrastructure.dto.devices.DeviceRegistrationReq
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.{RandomProvider, TimeProvider}
import com.github.pawelj_pl.bibliogar.api.testdoubles.repositories.{ApiKeyRepositoryFake, DevicesRepositoryFake}
import com.github.pawelj_pl.bibliogar.api.testdoubles.utils.{RandomProviderFake, TimeProviderFake}
import com.vdurmont.semver4j.Semver
import com.olegpy.meow.hierarchy.deriveMonadState
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class DevicesServiceSpec extends AnyWordSpec with Matchers with UserConstants with DeviceConstants {
  case class TestState(
    timeProviderState: TimeProviderFake.TimeProviderState = TimeProviderFake.TimeProviderState(),
    randomState: RandomProviderFake.RandomState = RandomProviderFake.RandomState(),
    apiKeyRepoState: ApiKeyRepositoryFake.ApiKeyRepositoryState = ApiKeyRepositoryFake.ApiKeyRepositoryState(),
    devicesRepoState: DevicesRepositoryFake.DevicesRepositoryState = DevicesRepositoryFake.DevicesRepositoryState())
  type TestEffect[A] = StateT[IO, TestState, A]

  final val ExampleMobileAppConfig = MobileAppConfig(2)

  def instance: DevicesService[TestEffect] = {
    implicit def timeProvider[F[_]: Monad: MonadState[*[_], TestState]]: TimeProvider[F] = TimeProviderFake.instance[F]
    implicit def randomProvider[F[_]: Monad: MonadState[*[_], TestState]]: RandomProvider[F] = RandomProviderFake.instance[F]
    implicit def apiKeyRepo[F[_]: Monad: MonadState[*[_], TestState]]: ApiKeyRepositoryAlgebra[F] = ApiKeyRepositoryFake.instance[F]
    implicit def devicesRepo[F[_]: Monad: MonadState[*[_], TestState]]: DevicesRepositoryAlgebra[F] = DevicesRepositoryFake.instance[F]

    implicit def dbToApp: TestEffect ~> TestEffect = new (TestEffect ~> TestEffect) {
      override def apply[A](fa: TestEffect[A]): TestEffect[A] = fa
    }

    DevicesService.withDb[TestEffect, TestEffect](ExampleMobileAppConfig)
  }

  "Check compatibility" should {
    "return true" when {
      "app with the same major version" in {
        val result = instance.isAppCompatibleWithApi(new Semver("2.0.0")).runA(TestState()).unsafeRunSync()
        result shouldBe true
      }
      "app with higher major version" in {
        val result = instance.isAppCompatibleWithApi(new Semver("3.7.7")).runA(TestState()).unsafeRunSync()
        result shouldBe true
      }
    }
    "return false" when {
      "app with lower major version" in {
        val result = instance.isAppCompatibleWithApi(new Semver("1.99.99")).runA(TestState()).unsafeRunSync()
        result shouldBe false
      }
    }
  }

  "Register device" should {
    val dto = DeviceRegistrationReq(ExampleDevice.uniqueId, ExampleDevice.deviceDescription)
    "create and return device and API key" in {
      val d1 = ExampleDevice.copy(device_id = ExampleId1)
      val d2 = ExampleDevice.copy(device_id = ExampleId2, ownerId = ExampleId3)
      val d3 = ExampleDevice.copy(device_id = ExampleId4, uniqueId = "otherId")
      val d4 = ExampleDevice.copy(device_id = ExampleId5)
      val initialState = TestState(
        devicesRepoState = DevicesRepositoryFake.DevicesRepositoryState(
          devices = Set(d1, d2, d3, d4)
        )
      )
      val (state, result) = instance.registerDevice(ExampleUser.id, dto).run(initialState).unsafeRunSync()
      val expectedDevice = ExampleDevice.copy(device_id = FirstRandomUuid)
      val expectedKey = ApiKey(
        SecondRandomUuid,
        "124",
        ExampleUser.id,
        Some(expectedDevice.device_id),
        KeyType.Device,
        Some("myBrand; someDescriptionId; myDeviceName"),
        enabled = true,
        None,
        Now.plusSeconds(1),
        Now.plusSeconds(1)
      )
      result shouldBe (expectedDevice, expectedKey)
      state.devicesRepoState shouldBe initialState.devicesRepoState.copy(devices = Set(expectedDevice, d2, d3))
      state.apiKeyRepoState shouldBe initialState.apiKeyRepoState.copy(keys = Set(expectedKey))
    }
  }

  "Unregister device" should {
    "remove device" in {
      val existingKey = ExampleApiKey.copy(keyType = KeyType.Device)
      val initialState = TestState(
        devicesRepoState = DevicesRepositoryFake.DevicesRepositoryState(Set(ExampleDevice)),
        apiKeyRepoState = ApiKeyRepositoryFake.ApiKeyRepositoryState(Set(existingKey))
      )
      val (state, result) = instance.unregisterDeviceAs(ExampleUser.id, existingKey.keyId).value.run(initialState).unsafeRunSync()
      result shouldBe Right((): Unit)
      state.devicesRepoState.devices shouldBe Set.empty
    }
    "fail" when {
      "key with id doesn't exist" in {
        val initialState = TestState(
          devicesRepoState = DevicesRepositoryFake.DevicesRepositoryState(Set(ExampleDevice))
        )
        val (state, result) = instance.unregisterDeviceAs(ExampleUser.id, ExampleId1).value.run(initialState).unsafeRunSync()
        result shouldBe Left(DeviceError.DeviceIdNotFound(ExampleId1))
        state.devicesRepoState.devices shouldBe state.devicesRepoState.devices
      }
      "key is related to user not device" in {
        val existingKey = ExampleApiKey.copy(keyType = KeyType.User)
        val initialState = TestState(
          devicesRepoState = DevicesRepositoryFake.DevicesRepositoryState(Set(ExampleDevice)),
          apiKeyRepoState = ApiKeyRepositoryFake.ApiKeyRepositoryState(Set(existingKey))
        )
        val (state, result) = instance.unregisterDeviceAs(ExampleUser.id, existingKey.keyId).value.run(initialState).unsafeRunSync()
        result shouldBe Left(DeviceError.ApiKeyIsNotDeviceType(ExampleApiKey.keyId, KeyType.User))
        state.devicesRepoState.devices shouldBe state.devicesRepoState.devices
      }
      "key has no device assigned" in {
        val existingKey = ExampleApiKey.copy(keyType = KeyType.Device, deviceId = None)
        val initialState = TestState(
          devicesRepoState = DevicesRepositoryFake.DevicesRepositoryState(Set(ExampleDevice)),
          apiKeyRepoState = ApiKeyRepositoryFake.ApiKeyRepositoryState(Set(existingKey))
        )
        val (state, result) = instance.unregisterDeviceAs(ExampleUser.id, existingKey.keyId).value.run(initialState).unsafeRunSync()
        result shouldBe Left(DeviceError.ApiKeyNotRelatedToAnyDevice(ExampleApiKey.keyId))
        state.devicesRepoState.devices shouldBe state.devicesRepoState.devices
      }
      "key owned by other user" in {
        val existingKey = ExampleApiKey.copy(keyType = KeyType.Device)
        val initialState = TestState(
          devicesRepoState = DevicesRepositoryFake.DevicesRepositoryState(Set(ExampleDevice)),
          apiKeyRepoState = ApiKeyRepositoryFake.ApiKeyRepositoryState(Set(existingKey))
        )
        val (state, result) = instance.unregisterDeviceAs(ExampleId1, existingKey.keyId).value.run(initialState).unsafeRunSync()
        result shouldBe Left(DeviceError.DeviceNotOwnedByUser(ExampleDevice.device_id, ExampleId1))
        state.devicesRepoState.devices shouldBe state.devicesRepoState.devices
      }
    }
  }
}
