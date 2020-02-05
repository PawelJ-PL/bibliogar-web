package com.github.pawelj_pl.bibliogar.api.domain.user

import cats.Monad
import cats.data.StateT
import cats.effect.IO
import cats.mtl.instances.all._
import cats.mtl.MonadState
import com.github.pawelj_pl.bibliogar.api.constants.UserConstants
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.TimeProvider
import com.github.pawelj_pl.bibliogar.api.testdoubles.utils.TimeProviderFake
import com.olegpy.meow.hierarchy.deriveMonadState
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ApiKeySpec extends AnyWordSpec with Matchers with UserConstants {
  case class TestState(timeProviderState: TimeProviderFake.TimeProviderState = TimeProviderFake.TimeProviderState())
  type TestEffect[A] = StateT[IO, TestState, A]

  implicit def timeProvider[F[_]: Monad: MonadState[*[_], TestState]]: TimeProvider[F] = TimeProviderFake.instance[F]

  "Is active" should {
    "be true" when {
      "validTo not defined" in {
        val input = ApiKey(ExampleId1, "123", ExampleUser.id, None, KeyType.User, Some("Some key"), enabled = true, None, Now, Now)
        val result = input.isActive[TestEffect].runA(TestState()).unsafeRunSync()
        result shouldBe true
      }
      "validTo is in future" in {
        val input = ApiKey(ExampleId1,
                           "123",
                           ExampleUser.id,
                           None,
                           KeyType.User,
                           Some("Some key"),
                           enabled = true,
                           Some(Now.plusSeconds(60)),
                           Now,
                           Now)
        val result = input.isActive[TestEffect].runA(TestState()).unsafeRunSync()
        result shouldBe true
      }
    }
    "be false" when {
      "enabled is false" in {
        val input = ApiKey(ExampleId1, "123", ExampleUser.id, None, KeyType.User, Some("Some key"), enabled = false, None, Now, Now)
        val result = input.isActive[TestEffect].runA(TestState()).unsafeRunSync()
        result shouldBe false
      }
      "validTo is in past" in {
        val input = ApiKey(ExampleId1,
                           "123",
                           ExampleUser.id,
                           None,
                           KeyType.User,
                           Some("Some key"),
                           enabled = true,
                           Some(Now.minusSeconds(60)),
                           Now,
                           Now)
        val result = input.isActive[TestEffect].runA(TestState()).unsafeRunSync()
        result shouldBe false
      }
    }
  }
}
