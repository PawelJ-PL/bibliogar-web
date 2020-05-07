package com.github.pawelj_pl.bibliogar.api.infrastructure.tasks

import java.time.temporal.ChronoUnit

import cats.{Monad, ~>}
import cats.data.StateT
import cats.effect.IO
import cats.mtl.MonadState
import cats.mtl.instances.all._
import com.github.pawelj_pl.bibliogar.api.constants.UserConstants
import com.github.pawelj_pl.bibliogar.api.domain.user.{AuthData, User, UserRepositoryAlgebra}
import com.github.pawelj_pl.bibliogar.api.infrastructure.config.Config
import com.github.pawelj_pl.bibliogar.api.infrastructure.config.Config.{AuthConfig, RegistrationCleanerConfig, RegistrationConfig, ResetPasswordConfig, TasksConfig}
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.TimeProvider
import com.github.pawelj_pl.bibliogar.api.testdoubles.repositories.UserRepositoryFake
import com.github.pawelj_pl.bibliogar.api.testdoubles.utils.TimeProviderFake
import com.olegpy.meow.hierarchy.deriveMonadState
import cron4s.Cron
import io.chrisdavenport.fuuid.FUUID
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration._

class RegistrationCleanerSpec extends AnyWordSpec with Matchers with UserConstants {
  case class TestState(
    timeProviderState: TimeProviderFake.TimeProviderState = TimeProviderFake.TimeProviderState(),
    userRepoState: UserRepositoryFake.UserRepositoryState = UserRepositoryFake.UserRepositoryState())
  type TestEffect[A] = StateT[IO, TestState, A]

  def instance: TaskDefinition[TestEffect] = {
    implicit def timeProvider[F[_]: Monad: MonadState[*[_], TestState]]: TimeProvider[F] = TimeProviderFake.instance[F]
    implicit def userRepo[F[_]: Monad: MonadState[*[_], TestState]]: UserRepositoryAlgebra[F] = UserRepositoryFake.instance[F]

    implicit def dbToApp: TestEffect ~> TestEffect = new (TestEffect ~> TestEffect) {
      override def apply[A](fa: TestEffect[A]): TestEffect[A] = fa
    }

    new RegistrationCleaner[TestEffect, TestEffect](ExampleConfig)
  }

  final val ExampleRegistrationConfig = RegistrationConfig(30.minutes)
  final val ExampleResetPasswordConfig = ResetPasswordConfig(30.minutes)
  final val ExampleAuthConfig = AuthConfig(1, ExampleRegistrationConfig, ExampleResetPasswordConfig, null, "dummyHash")
  final val ExampleRegistrationCleanerConf = RegistrationCleanerConfig(Cron.unsafeParse("0 */10 * ? * *"))
  final val ExampleTasksConf = TasksConfig(ExampleRegistrationCleanerConf)
  final val ExampleConfig = Config(null, null, ExampleAuthConfig, null, ExampleTasksConf, null, null)

  "Registration cleaner" should {
    "remove outdated, non confirmed registrations" in {
      val a1 = AuthData(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f051"),
                        "h",
                        confirmed = true,
                        enabled = true,
                        Now.minus(10, ChronoUnit.MINUTES))
      val u1 = User(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f051"), "e1", "n", Now, Now)
      val a2 = AuthData(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f052"),
                        "h",
                        confirmed = false,
                        enabled = true,
                        Now.minus(60, ChronoUnit.MINUTES))
      val u2 = User(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f052"), "e2", "n", Now, Now)
      val a3 = AuthData(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f053"),
                        "h",
                        confirmed = true,
                        enabled = true,
                        Now.minus(60, ChronoUnit.MINUTES))
      val u3 = User(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f053"), "e3", "n", Now, Now)
      val a4 = AuthData(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f054"),
                        "h",
                        confirmed = false,
                        enabled = true,
                        Now.minus(60, ChronoUnit.MINUTES))
      val u4 = User(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f054"), "e4", "n", Now, Now)
      val a5 = AuthData(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f055"),
                        "h",
                        confirmed = false,
                        enabled = true,
                        Now.minus(10, ChronoUnit.MINUTES))
      val u5 = User(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f055"), "e5", "n", Now, Now)
      val a6 = AuthData(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f056"),
                        "h",
                        confirmed = false,
                        enabled = true,
                        Now.minus(60, ChronoUnit.MINUTES))
      val u6 = User(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f056"), "e6", "n", Now, Now)
      val a7 = AuthData(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f057"),
                        "h",
                        confirmed = true,
                        enabled = true,
                        Now.minus(10, ChronoUnit.MINUTES))
      val u7 = User(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f057"), "e7", "n", Now, Now)
      val initialState = TestState(
        userRepoState = UserRepositoryFake.UserRepositoryState(
          users = Set(u1, u2, u3, u4, u5, u6, u7),
          authData = Set(a1, a2, a3, a4, a5, a6, a7)
        )
      )
      val state = instance.task.runS(initialState).unsafeRunSync()
      state.userRepoState shouldBe initialState.userRepoState.copy(users = Set(u1, u3, u5, u7))
    }
  }
}
