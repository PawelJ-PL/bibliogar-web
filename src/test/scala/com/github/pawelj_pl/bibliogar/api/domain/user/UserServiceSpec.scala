package com.github.pawelj_pl.bibliogar.api.domain.user

import java.time.Instant
import java.time.temporal.ChronoUnit

import cats.{Applicative, Monad, ~>}
import cats.data.{Chain, StateT}
import cats.effect.IO
import cats.mtl.instances.all._
import cats.mtl.MonadState
import com.github.pawelj_pl.bibliogar.api.UserError
import com.github.pawelj_pl.bibliogar.api.constants.UserConstants
import com.github.pawelj_pl.bibliogar.api.infrastructure.config.Config
import com.github.pawelj_pl.bibliogar.api.infrastructure.dto.user.{ChangePasswordReq, Email, NickName, Password, UserDataReq, UserLoginReq, UserRegistrationReq}
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.{Correspondence, CryptProvider, MessageComposer, RandomProvider, TimeProvider}
import com.github.pawelj_pl.bibliogar.api.testdoubles.repositories.{UserRepositoryFake, UserTokenRepositoryFake}
import com.github.pawelj_pl.bibliogar.api.testdoubles.utils.{CorrespondenceMock, CryptProviderFake, MessageComposerMock, RandomProviderFake, TimeProviderFake}
import com.olegpy.meow.hierarchy.deriveMonadState
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.duration._

class UserServiceSpec extends WordSpec with UserConstants with Matchers {
  type TestEffect[A] = StateT[IO, TestState, A]

  case class TestState(
    userRepoState: UserRepositoryFake.UserRepositoryState = UserRepositoryFake.UserRepositoryState(),
    tokenRepoState: UserTokenRepositoryFake.TokenRepositoryState = UserTokenRepositoryFake.TokenRepositoryState(),
    timeProviderState: TimeProviderFake.TimeProviderState = TimeProviderFake.TimeProviderState(),
    randomState: RandomProviderFake.RandomState = RandomProviderFake.RandomState(),
    notificationState: CorrespondenceMock.CorrespondenceState = CorrespondenceMock.CorrespondenceState())

  def instance: UserService[TestEffect] = {
    implicit def userRepo[F[_]: Monad: MonadState[*[_], TestState]]: UserRepositoryAlgebra[F] = UserRepositoryFake.instance[F]
    implicit def tokenRepo[F[_]: Monad: MonadState[*[_], TestState]]: UserTokenRepositoryAlgebra[F] = UserTokenRepositoryFake.instance[F]

    implicit def timeProvider[F[_]: Monad: MonadState[*[_], TestState]]: TimeProvider[F] = TimeProviderFake.instance[F]
    implicit def randomProvider[F[_]: Monad: MonadState[*[_], TestState]]: RandomProvider[F] = RandomProviderFake.instance[F]
    implicit def cryptProvider[F[_]: Applicative]: CryptProvider[F] = CryptProviderFake.instance[F]
    implicit def notification[F[_]: MonadState[*[_], TestState]]: Correspondence[F] = CorrespondenceMock.instance[F]
    implicit def messageComposer[F[_]: Applicative]: MessageComposer[F] = MessageComposerMock.instance[F]

    implicit def dbToApp: TestEffect ~> TestEffect = new (TestEffect ~> TestEffect) {
      override def apply[A](fa: TestEffect[A]): TestEffect[A] = fa
    }

    UserService.withDb[TestEffect, TestEffect](ExampleAuthConfig)
  }

  final val ExampleRegistrationDto =
    UserRegistrationReq(Email(ExampleUser.email), NickName(ExampleUser.nickName), Password(ExamplePassword))
  final val ExampleRegistrationConfig = Config.RegistrationConfig(30.minutes)
  final val ExamplePasswordResetConfig = Config.ResetPasswordConfig(30.minutes)
  final val ExampleAuthConfig = Config.AuthConfig(1, ExampleRegistrationConfig, ExamplePasswordResetConfig, null, "dummyHash")
  final val ExampleCredentials = UserLoginReq(Email(ExampleUser.email), Password(ExamplePassword))

  "Registration" should {
    "be finished" when {
      "all data correct" in {
        val (state, result) = instance.registerUser(ExampleRegistrationDto).value.run(TestState()).unsafeRunSync()
        result shouldBe Right(ExampleUser)
        val expectedAuthData = AuthData(ExampleUser.id, s"bcrypt($ExamplePassword)", confirmed = false, enabled = true, Now)
        val expectedToken = UserToken("124", ExampleUser.id, TokenType.Registration, Instant.EPOCH, Instant.EPOCH)
        val expectedMessage =
          CorrespondenceMock.Message(ExampleUser.email, "Rejestracja w systemie Bibliogar", "newRegistration - token: 124")
        state.userRepoState shouldBe UserRepositoryFake.UserRepositoryState(users = Set(ExampleUser), authData = Set(expectedAuthData))
        state.tokenRepoState shouldBe UserTokenRepositoryFake.TokenRepositoryState(tokens = Set(expectedToken))
        state.notificationState shouldBe CorrespondenceMock.CorrespondenceState(sentMessages = Chain(expectedMessage))
      }
    }

    "fail" when {
      "email already registered" in {
        val existingUser = ExampleUser.copy(createdAt = Now.minus(3, ChronoUnit.HOURS))
        val initialState = TestState(userRepoState = UserRepositoryFake.UserRepositoryState(users = Set(existingUser)))
        val (state, result) = instance.registerUser(ExampleRegistrationDto).value.run(initialState).unsafeRunSync()
        result shouldBe Left(UserError.EmailAlreadyRegistered(ExampleUser.email))
        state.userRepoState shouldBe initialState.userRepoState
        state.tokenRepoState shouldBe initialState.tokenRepoState
        state.notificationState.sentMessages.size shouldBe 0
      }
    }
  }

  "Registration confirmation" should {
    "succeed" when {
      "Token is valid" in {
        val existingAuthData = AuthData(ExampleUser.id, "abc", confirmed = false, enabled = true, Now.minusSeconds(60))
        val existingToken = UserToken("validToken", ExampleUser.id, TokenType.Registration, Now.minusSeconds(60), Now.minusSeconds(60))
        val initialState = TestState(
          userRepoState = UserRepositoryFake.UserRepositoryState(users = Set(ExampleUser), authData = Set(existingAuthData)),
          tokenRepoState = UserTokenRepositoryFake.TokenRepositoryState(tokens = Set(existingToken))
        )
        val (state, result) = instance.confirmRegistration("validToken").value.run(initialState).unsafeRunSync()
        result shouldBe Right(ExampleUser)
        state.userRepoState shouldBe initialState.userRepoState.copy(authData = Set(existingAuthData.copy(confirmed = true)))
        state.tokenRepoState shouldBe initialState.tokenRepoState.copy(tokens = Set.empty)
      }
    }
    "fail" when {
      "token doesn't exist" in {
        val initialState = TestState()
        val (state, result) = instance.confirmRegistration("invalidToken").value.run(initialState).unsafeRunSync()
        result shouldBe Left(UserError.TokenNotFound("invalidToken", TokenType.Registration))
        state.userRepoState shouldBe initialState.userRepoState
        state.tokenRepoState shouldBe initialState.tokenRepoState
      }
      "token exists with differentType" in {
        val existingToken = UserToken("someToken", ExampleUser.id, TokenType.PasswordReset, Now.minusSeconds(60), Now.minusSeconds(60))
        val initialState = TestState(
          tokenRepoState = UserTokenRepositoryFake.TokenRepositoryState(tokens = Set(existingToken))
        )
        val (state, result) = instance.confirmRegistration("someToken").value.run(initialState).unsafeRunSync()
        result shouldBe Left(UserError.TokenNotFound("someToken", TokenType.Registration))
        state.userRepoState shouldBe initialState.userRepoState
        state.tokenRepoState shouldBe initialState.tokenRepoState
      }
      "token is outdated" in {
        val existingToken =
          UserToken("someToken", ExampleUser.id, TokenType.Registration, Now.minus(10, ChronoUnit.DAYS), Now.minusSeconds(60))
        val initialState = TestState(
          tokenRepoState = UserTokenRepositoryFake.TokenRepositoryState(tokens = Set(existingToken))
        )
        val (state, result) = instance.confirmRegistration("someToken").value.run(initialState).unsafeRunSync()
        result shouldBe Left(UserError.OutdatedToken("someToken", TokenType.Registration, Now.minus(10, ChronoUnit.DAYS)))
        state.userRepoState shouldBe initialState.userRepoState
        state.tokenRepoState shouldBe initialState.tokenRepoState.copy(tokens = Set.empty)
      }
      "account related to token doesn't exist" in {
        val existingToken = UserToken("someToken", ExampleUser.id, TokenType.Registration, Now.minusSeconds(60), Now.minusSeconds(60))
        val initialState = TestState(
          tokenRepoState = UserTokenRepositoryFake.TokenRepositoryState(tokens = Set(existingToken)),
          userRepoState = UserRepositoryFake.UserRepositoryState(users = Set.empty)
        )
        val (state, result) = instance.confirmRegistration("someToken").value.run(initialState).unsafeRunSync()
        result shouldBe Left(UserError.UserIdNotFound(ExampleUser.id))
        state.userRepoState shouldBe initialState.userRepoState
        state.tokenRepoState shouldBe initialState.tokenRepoState.copy(tokens = Set.empty)
      }
    }
  }

  "Verify credentials" should {
    "succeed" when {
      "credentials are valid" in {
        val exampleAuth = AuthData(ExampleUser.id, s"bcrypt($ExamplePassword)", confirmed = true, enabled = true, Now)
        val initialState = TestState(
          userRepoState = UserRepositoryFake.UserRepositoryState(users = Set(ExampleUser), authData = Set(exampleAuth))
        )
        val (state, result) = instance.verifyCredentials(ExampleCredentials).value.run(initialState).unsafeRunSync()
        result shouldBe Right(ExampleUser)
        state shouldBe initialState
      }
    }
    "failed" when {
      "user doesn't exist" in {
        val exampleAuth = AuthData(ExampleUser.id, s"bcrypt($ExamplePassword)", confirmed = true, enabled = true, Now)
        val initialState = TestState(
          userRepoState = UserRepositoryFake.UserRepositoryState(users = Set.empty, authData = Set(exampleAuth))
        )
        val (state, result) = instance.verifyCredentials(ExampleCredentials).value.run(initialState).unsafeRunSync()
        result shouldBe Left(UserError.UserEmailNotFound(ExampleUser.email))
        state shouldBe initialState
      }
      "auth data doesn't exist" in {
        val initialState = TestState(
          userRepoState = UserRepositoryFake.UserRepositoryState(users = Set(ExampleUser), authData = Set.empty)
        )
        val (state, result) = instance.verifyCredentials(ExampleCredentials).value.run(initialState).unsafeRunSync()
        result shouldBe Left(UserError.UserEmailNotFound(ExampleUser.email))
        state shouldBe initialState
      }
      "user is not active" in {
        val exampleAuth = AuthData(ExampleUser.id, s"bcrypt($ExamplePassword)", confirmed = false, enabled = true, Now)
        val initialState = TestState(
          userRepoState = UserRepositoryFake.UserRepositoryState(users = Set(ExampleUser), authData = Set(exampleAuth))
        )
        val (state, result) = instance.verifyCredentials(ExampleCredentials).value.run(initialState).unsafeRunSync()
        result shouldBe Left(UserError.UserNotActive(exampleAuth))
        state shouldBe initialState
      }
      "password is invalid" in {
        val exampleAuth = AuthData(ExampleUser.id, s"bcrypt(otherPassword)", confirmed = true, enabled = true, Now)
        val initialState = TestState(
          userRepoState = UserRepositoryFake.UserRepositoryState(users = Set(ExampleUser), authData = Set(exampleAuth))
        )
        val (state, result) = instance.verifyCredentials(ExampleCredentials).value.run(initialState).unsafeRunSync()
        result shouldBe Left(UserError.InvalidCredentials(ExampleUser.id))
        state shouldBe initialState
      }
    }
  }

  "Get user" should {
    "return user data" when {
      "user exists" in {
        val initialState = TestState(
          userRepoState = UserRepositoryFake.UserRepositoryState(users = Set(ExampleUser))
        )
        val (state, result) = instance.getUser(ExampleUser.id).value.run(initialState).unsafeRunSync()
        result shouldBe Some(ExampleUser)
        state shouldBe initialState
      }
    }
    "return None" when {
      "user doesn't exist" in {
        val initialState = TestState(
          userRepoState = UserRepositoryFake.UserRepositoryState(users = Set.empty)
        )
        val (state, result) = instance.getUser(ExampleUser.id).value.run(initialState).unsafeRunSync()
        result shouldBe None
        state shouldBe initialState
      }
    }
  }

  "Update user" should {
    val Dto = UserDataReq(NickName("newNick"))

    "update and return user" when {
      "user exists" in {
        val initialState = TestState(
          userRepoState = UserRepositoryFake.UserRepositoryState(users = Set(ExampleUser))
        )
        val (state, result) =
          instance.updateUser(ExampleUser.id, Dto).value.run(initialState).unsafeRunSync()
        result shouldBe Some(ExampleUser.copy(nickName = "newNick"))
        state.userRepoState shouldBe initialState.userRepoState.copy(users = Set(ExampleUser.copy(nickName = "newNick")))
      }
    }
    "return None" when {
      "user doesn't exist" in {
        val initialState = TestState(
          userRepoState = UserRepositoryFake.UserRepositoryState(users = Set.empty)
        )
        val (state, result) =
          instance.updateUser(ExampleUser.id, Dto).value.run(initialState).unsafeRunSync()
        result shouldBe None
        state shouldBe initialState
      }
    }
  }

  "Change password" should {
    "succeed" in {
      val authData = AuthData(ExampleUser.id, "bcrypt(oldPassword)", confirmed = true, enabled = true, Now)
      val dto = ChangePasswordReq(Password("oldPassword"), Password("newPassword"))
      val initialState = TestState(
        userRepoState = UserRepositoryFake.UserRepositoryState(authData = Set(authData))
      )
      val (state, result) = instance.changePassword(dto, ExampleUser.id).value.run(initialState).unsafeRunSync()
      result shouldBe Right(authData.copy(passwordHash = "bcrypt(newPassword)"))
      state.userRepoState shouldBe initialState.userRepoState.copy(authData = Set(authData.copy(passwordHash = "bcrypt(newPassword)")))
    }
    "fail" when {
      "old and new password are the same" in {
        val authData = AuthData(ExampleUser.id, "bcrypt(oldPassword)", confirmed = true, enabled = true, Now)
        val dto = ChangePasswordReq(Password("oldPassword"), Password("oldPassword"))
        val initialState = TestState(
          userRepoState = UserRepositoryFake.UserRepositoryState(authData = Set(authData))
        )
        val (state, result) = instance.changePassword(dto, ExampleUser.id).value.run(initialState).unsafeRunSync()
        result shouldBe Left(UserError.NewAndOldPasswordAreEqual)
        state shouldBe initialState
      }
      "user is not active" in {
        val authData = AuthData(ExampleUser.id, "bcrypt(oldPassword)", confirmed = true, enabled = false, Now)
        val dto = ChangePasswordReq(Password("oldPassword"), Password("newPassword"))
        val initialState = TestState(
          userRepoState = UserRepositoryFake.UserRepositoryState(authData = Set(authData))
        )
        val (state, result) = instance.changePassword(dto, ExampleUser.id).value.run(initialState).unsafeRunSync()
        result shouldBe Left(UserError.UserNotActive(authData))
        state shouldBe initialState
      }
      "credentials are invalid" in {
        val authData = AuthData(ExampleUser.id, "bcrypt(oldPassword)", confirmed = true, enabled = true, Now)
        val dto = ChangePasswordReq(Password("invalidPassword"), Password("newPassword"))
        val initialState = TestState(
          userRepoState = UserRepositoryFake.UserRepositoryState(authData = Set(authData))
        )
        val (state, result) = instance.changePassword(dto, ExampleUser.id).value.run(initialState).unsafeRunSync()
        result shouldBe Left(UserError.InvalidCredentials(ExampleUser.id))
        state shouldBe initialState
      }
    }
  }

  "Request password reset" should {
    "generate token" in {
      val expectedToken = UserToken("124", ExampleUser.id, TokenType.PasswordReset, Instant.EPOCH, Instant.EPOCH)
      val expectedMessage = CorrespondenceMock.Message(
        to = ExampleUser.email,
        subject = "Bibliogar - reset hasła",
        message = "resetPassword - token: 124"
      )
      val initialState = TestState(
        userRepoState = UserRepositoryFake.UserRepositoryState(users = Set(ExampleUser))
      )
      val (state, result) = instance.requestPasswordReset(ExampleUser.email).value.run(initialState).unsafeRunSync()
      result shouldBe Some(expectedToken)
      state.userRepoState shouldBe initialState.userRepoState
      state.tokenRepoState shouldBe initialState.tokenRepoState.copy(tokens = Set(expectedToken))
      state.notificationState shouldBe CorrespondenceMock.CorrespondenceState(sentMessages = Chain(expectedMessage))
    }
    "return None" when {
      "user not found" in {
        val initialState = TestState(
          userRepoState = UserRepositoryFake.UserRepositoryState(users = Set.empty)
        )
        val (state, result) = instance.requestPasswordReset(ExampleUser.email).value.run(initialState).unsafeRunSync()
        result shouldBe None
        state.userRepoState shouldBe initialState.userRepoState
        state.tokenRepoState shouldBe initialState.tokenRepoState
        state.notificationState shouldBe CorrespondenceMock.CorrespondenceState(sentMessages = Chain.empty)
      }
    }
  }

  "Reset password" should {
    "change password" when {
      "token is valid" in {
        val existingToken1 = UserToken("validToken", ExampleUser.id, TokenType.PasswordReset, Now.minusSeconds(60), Now.minusSeconds(60))
        val existingToken2 = UserToken("t1", ExampleUser.id, TokenType.PasswordReset, Now.minusSeconds(60), Now.minusSeconds(60))
        val existingToken3 = UserToken("t2", ExampleId1, TokenType.PasswordReset, Now.minusSeconds(60), Now.minusSeconds(60))
        val existingToken4 = UserToken("t3", ExampleUser.id, TokenType.PasswordReset, Now.minusSeconds(60), Now.minusSeconds(60))
        val existingToken5 = UserToken("t4", ExampleUser.id, TokenType.Registration, Now.minusSeconds(60), Now.minusSeconds(60))
        val existingAuthData = AuthData(ExampleUser.id, "oldPasswordHash", confirmed = true, enabled = true, Now.minusSeconds(120))
        val initialState = TestState(
          tokenRepoState = UserTokenRepositoryFake.TokenRepositoryState(
            tokens = Set(existingToken1, existingToken2, existingToken3, existingToken4, existingToken5)),
          userRepoState = UserRepositoryFake.UserRepositoryState(authData = Set(existingAuthData))
        )
        val (state, result) = instance.resetPassword("validToken", "newPassword").value.run(initialState).unsafeRunSync()
        result shouldBe Right(existingAuthData)
        state.userRepoState shouldBe initialState.userRepoState.copy(
          authData = Set(existingAuthData.copy(passwordHash = "bcrypt(newPassword)"))
        )
        state.tokenRepoState shouldBe initialState.tokenRepoState.copy(tokens = Set(existingToken3, existingToken5))
      }
    }
    "fail" when {
      "token not found" in {
        val initialState = TestState(
          tokenRepoState = UserTokenRepositoryFake.TokenRepositoryState(tokens = Set.empty)
        )
        val (state, result) = instance.resetPassword("invalidToken", "newPassword").value.run(initialState).unsafeRunSync()
        result shouldBe Left(UserError.TokenNotFound("invalidToken", TokenType.PasswordReset))
        state shouldBe initialState
      }
      "token is outdated" in {
        val existingToken = UserToken("someToken", ExampleUser.id, TokenType.PasswordReset, Now.minus(3, ChronoUnit.DAYS), Now)
        val initialState = TestState(
          tokenRepoState = UserTokenRepositoryFake.TokenRepositoryState(tokens = Set(existingToken))
        )
        val (state, result) = instance.resetPassword("someToken", "newPassword").value.run(initialState).unsafeRunSync()
        result shouldBe Left(UserError.OutdatedToken("someToken", TokenType.PasswordReset, Now.minus(3, ChronoUnit.DAYS)))
        state.tokenRepoState shouldBe initialState.tokenRepoState.copy(tokens = Set.empty)
      }
      "token has different type" in {
        val existingToken = UserToken("someToken", ExampleUser.id, TokenType.Registration, Now.minus(3, ChronoUnit.DAYS), Now)
        val initialState = TestState(
          tokenRepoState = UserTokenRepositoryFake.TokenRepositoryState(tokens = Set(existingToken))
        )
        val (state, result) = instance.resetPassword("someToken", "newPassword").value.run(initialState).unsafeRunSync()
        result shouldBe Left(UserError.TokenNotFound("someToken", TokenType.PasswordReset))
        state.tokenRepoState shouldBe initialState.tokenRepoState
      }
      "user is not active" in {
        val existingToken = UserToken("validToken", ExampleUser.id, TokenType.PasswordReset, Now.minusSeconds(60), Now.minusSeconds(60))
        val existingAuthData = AuthData(ExampleUser.id, "oldPasswordHash", confirmed = true, enabled = false, Now.minusSeconds(120))
        val initialState = TestState(
          tokenRepoState = UserTokenRepositoryFake.TokenRepositoryState(tokens = Set(existingToken)),
          userRepoState = UserRepositoryFake.UserRepositoryState(authData = Set(existingAuthData))
        )
        val (state, result) = instance.resetPassword("validToken", "newPassword").value.run(initialState).unsafeRunSync()
        result shouldBe Left(UserError.UserNotActive(existingAuthData))
        state.userRepoState shouldBe initialState.userRepoState
        state.tokenRepoState shouldBe initialState.tokenRepoState.copy(tokens = Set.empty)
      }
    }
  }
}
