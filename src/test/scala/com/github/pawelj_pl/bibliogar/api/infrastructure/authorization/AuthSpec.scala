package com.github.pawelj_pl.bibliogar.api.infrastructure.authorization

import cats.{Monad, ~>}
import cats.data.StateT
import cats.effect.IO
import cats.mtl.instances.all._
import cats.mtl.MonadState
import com.github.pawelj_pl.bibliogar.api.constants.UserConstants
import com.github.pawelj_pl.bibliogar.api.domain.user.{ApiKey, ApiKeyRepositoryAlgebra, AuthData, KeyType, SessionRepositoryAlgebra, UserRepositoryAlgebra, UserSession}
import com.github.pawelj_pl.bibliogar.api.infrastructure.http.ErrorResponse.Unauthorized
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.{RandomProvider, TimeProvider}
import com.github.pawelj_pl.bibliogar.api.testdoubles.repositories.{ApiKeyRepositoryFake, SessionRepositoryFake, UserRepositoryFake}
import com.github.pawelj_pl.bibliogar.api.testdoubles.utils.{RandomProviderFake, TimeProviderFake}
import com.olegpy.meow.hierarchy.deriveMonadState
import io.chrisdavenport.fuuid.FUUID
import org.scalatest.{Matchers, WordSpec}

class AuthSpec extends WordSpec with Matchers with UserConstants {
  case class TestState(
    sessionRepoState: SessionRepositoryFake.SessionRepositoryState = SessionRepositoryFake.SessionRepositoryState(),
    apiKeyRepoState: ApiKeyRepositoryFake.ApiKeyRepositoryState = ApiKeyRepositoryFake.ApiKeyRepositoryState(),
    userRepoState: UserRepositoryFake.UserRepositoryState = UserRepositoryFake.UserRepositoryState(),
    timeProviderState: TimeProviderFake.TimeProviderState = TimeProviderFake.TimeProviderState(),
    randomState: RandomProviderFake.RandomState = RandomProviderFake.RandomState())
  type TestEffect[A] = StateT[IO, TestState, A]

  def instance: Auth[TestEffect] = {
    implicit def timeProvider[F[_]: Monad: MonadState[*[_], TestState]]: TimeProvider[F] = TimeProviderFake.instance[F]
    implicit def randomProvider[F[_]: Monad: MonadState[*[_], TestState]]: RandomProvider[F] = RandomProviderFake.instance[F]
    implicit def sessionRepo[F[_]: Monad: MonadState[*[_], TestState]]: SessionRepositoryAlgebra[F] = SessionRepositoryFake.instance[F]
    implicit def apiKeyRepo[F[_]: Monad: MonadState[*[_], TestState]]: ApiKeyRepositoryAlgebra[F] = ApiKeyRepositoryFake.instance[F]
    implicit def userRepo[F[_]: Monad: MonadState[*[_], TestState]]: UserRepositoryAlgebra[F] = UserRepositoryFake.instance[F]

    implicit def dbToApp: TestEffect ~> TestEffect = new (TestEffect ~> TestEffect) {
      override def apply[A](fa: TestEffect[A]): TestEffect[A] = fa
    }
    Auth.create[TestEffect, TestEffect]
  }

  "Auth to session" should {
    "return session" when {
      "valid API key found in header" in {
        val input = AuthInputs(None, None, "POST", Some("testKey"), None)
        val existingKey = ApiKey(ExampleId1, "testKey", ExampleId2, None, KeyType.User, None, enabled = true, None, Now, Now)
        val existingAuthData = AuthData(ExampleId2, "someHash", confirmed = true, enabled = true, Now)
        val expectedSession = UserSession(FirstRandomUuid, Some(existingKey.keyId), ExampleId2, FirstRandomUuid, Now.plusSeconds(1))
        val initialState = TestState(
          apiKeyRepoState = ApiKeyRepositoryFake.ApiKeyRepositoryState(keys = Set(existingKey)),
          userRepoState = UserRepositoryFake.UserRepositoryState(authData = Set(existingAuthData))
        )
        val result = instance.authToSession(input).runA(initialState).unsafeRunSync()
        result shouldBe Right(expectedSession)
      }
      "valid API key found in param" in {
        val input = AuthInputs(None, None, "POST", None, Some("testKey"))
        val existingKey = ApiKey(ExampleId1, "testKey", ExampleId2, None, KeyType.User, None, enabled = true, None, Now, Now)
        val existingAuthData = AuthData(ExampleId2, "someHash", confirmed = true, enabled = true, Now)
        val expectedSession = UserSession(FirstRandomUuid, Some(existingKey.keyId), ExampleId2, FirstRandomUuid, Now.plusSeconds(1))
        val initialState = TestState(
          apiKeyRepoState = ApiKeyRepositoryFake.ApiKeyRepositoryState(keys = Set(existingKey)),
          userRepoState = UserRepositoryFake.UserRepositoryState(authData = Set(existingAuthData))
        )
        val result = instance.authToSession(input).runA(initialState).unsafeRunSync()
        result shouldBe Right(expectedSession)
      }
      "valid session cookie found" in {
        val input = AuthInputs(Some(ExampleId1.toString), Some(TestCsrfToken), "POST", None, None)
        val existingSession = UserSession(ExampleId1, None, ExampleId2, TestCsrfToken, Now)
        val initialState = TestState(
          sessionRepoState = SessionRepositoryFake.SessionRepositoryState(sessions = Set(existingSession))
        )
        val result = instance.authToSession(input).runA(initialState).unsafeRunSync()
        result shouldBe Right(existingSession)
      }
      "csrf token is missing for safe method" in {
        val input = AuthInputs(Some(ExampleId1.toString), None, "GEt", None, None)
        val existingSession = UserSession(ExampleId1, None, ExampleId2, TestCsrfToken, Now)
        val initialState = TestState(
          sessionRepoState = SessionRepositoryFake.SessionRepositoryState(sessions = Set(existingSession))
        )
        val result = instance.authToSession(input).runA(initialState).unsafeRunSync()
        result shouldBe Right(existingSession)
      }
      "csrf token doesn't match for safe method" in {
        val input = AuthInputs(Some(ExampleId1.toString), Some(FUUID.fuuid("4ba238c6-289b-457d-9e4b-1d8ff414b270")), "GEt", None, None)
        val existingSession = UserSession(ExampleId1, None, ExampleId2, TestCsrfToken, Now)
        val initialState = TestState(
          sessionRepoState = SessionRepositoryFake.SessionRepositoryState(sessions = Set(existingSession))
        )
        val result = instance.authToSession(input).runA(initialState).unsafeRunSync()
        result shouldBe Right(existingSession)
      }
    }
    "return error response" when {
      "no auth input provided" in {
        val input = AuthInputs(None, None, "POST", None, None)
        val initialState = TestState()
        val result = instance.authToSession(input).runA(initialState).unsafeRunSync()
        result shouldBe Left(Unauthorized("Authentication failed"))
      }
      "api key not found" in {
        val input = AuthInputs(None, None, "POST", Some("notExistingKey"), None)
        val initialState = TestState(
          apiKeyRepoState = ApiKeyRepositoryFake.ApiKeyRepositoryState(keys = Set.empty)
        )
        val result = instance.authToSession(input).runA(initialState).unsafeRunSync()
        result shouldBe Left(Unauthorized("Authentication failed"))
      }
      "api key is not valid" in {
        val input = AuthInputs(None, None, "POST", Some("invalidKey"), None)
        val existingKey = ApiKey(ExampleId1, "invalidKey", ExampleId2, None, KeyType.User, None, enabled = false, None, Now, Now)
        val initialState = TestState(
          apiKeyRepoState = ApiKeyRepositoryFake.ApiKeyRepositoryState(keys = Set(existingKey))
        )
        val result = instance.authToSession(input).runA(initialState).unsafeRunSync()
        result shouldBe Left(Unauthorized("Authentication failed"))
      }
      "auth data for api key not found" in {
        val input = AuthInputs(None, None, "POST", Some("apiKey"), None)
        val existingKey = ApiKey(ExampleId1, "apiKey", ExampleId2, None, KeyType.User, None, enabled = true, None, Now, Now)
        val initialState = TestState(
          apiKeyRepoState = ApiKeyRepositoryFake.ApiKeyRepositoryState(keys = Set(existingKey)),
          userRepoState = UserRepositoryFake.UserRepositoryState(authData = Set.empty)
        )
        val result = instance.authToSession(input).runA(initialState).unsafeRunSync()
        result shouldBe Left(Unauthorized("Authentication failed"))
      }
      "user for api key is not active" in {
        val input = AuthInputs(None, None, "POST", Some("testKey"), None)
        val existingKey = ApiKey(ExampleId1, "testKey", ExampleId2, None, KeyType.User, None, enabled = true, None, Now, Now)
        val existingAuthData = AuthData(ExampleId2, "someHash", confirmed = true, enabled = false, Now)
        val initialState = TestState(
          apiKeyRepoState = ApiKeyRepositoryFake.ApiKeyRepositoryState(keys = Set(existingKey)),
          userRepoState = UserRepositoryFake.UserRepositoryState(authData = Set(existingAuthData))
        )
        val result = instance.authToSession(input).runA(initialState).unsafeRunSync()
        result shouldBe Left(Unauthorized("Authentication failed"))
      }
      "session id is not valid uuid" in {
        val input = AuthInputs(Some("invalidSessionId"), Some(TestCsrfToken), "POST", None, None)
        val initialState = TestState()
        val result = instance.authToSession(input).runA(initialState).unsafeRunSync()
        result shouldBe Left(Unauthorized("Authentication failed"))
      }
      "session not found" in {
        val input = AuthInputs(Some(ExampleId1.toString), Some(TestCsrfToken), "POST", None, None)
        val initialState = TestState(
          sessionRepoState = SessionRepositoryFake.SessionRepositoryState(sessions = Set.empty)
        )
        val result = instance.authToSession(input).runA(initialState).unsafeRunSync()
        result shouldBe Left(Unauthorized("Authentication failed"))
      }
      "csrf token is missing for unsafe method" in {
        val input = AuthInputs(Some(ExampleId1.toString), None, "PoSt", None, None)
        val existingSession = UserSession(ExampleId1, None, ExampleId2, TestCsrfToken, Now)
        val initialState = TestState(
          sessionRepoState = SessionRepositoryFake.SessionRepositoryState(sessions = Set(existingSession))
        )
        val result = instance.authToSession(input).runA(initialState).unsafeRunSync()
        result shouldBe Left(Unauthorized("Authentication failed"))
      }
      "csrf doesn't match for unsafe method" in {
        val input = AuthInputs(Some(ExampleId1.toString), Some(FUUID.fuuid("4ba238c6-289b-457d-9e4b-1d8ff414b270")), "POST", None, None)
        val existingSession = UserSession(ExampleId1, None, ExampleId2, TestCsrfToken, Now)
        val initialState = TestState(
          sessionRepoState = SessionRepositoryFake.SessionRepositoryState(sessions = Set(existingSession))
        )
        val result = instance.authToSession(input).runA(initialState).unsafeRunSync()
        result shouldBe Left(Unauthorized("Authentication failed"))
      }

    }
  }

}
