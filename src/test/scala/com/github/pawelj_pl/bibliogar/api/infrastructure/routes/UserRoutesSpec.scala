package com.github.pawelj_pl.bibliogar.api.infrastructure.routes

import cats.{Applicative, Functor, Monad}
import cats.data.StateT
import cats.effect.{ContextShift, IO}
import cats.mtl.instances.all._
import cats.mtl.MonadState
import cats.syntax.either._
import com.github.pawelj_pl.bibliogar.api.{CommonError, UserError}
import com.github.pawelj_pl.bibliogar.api.constants.UserConstants
import com.github.pawelj_pl.bibliogar.api.domain.user.{AuthData, SessionRepositoryAlgebra, TokenType, User, UserService, UserSession}
import com.github.pawelj_pl.bibliogar.api.infrastructure.authorization.AuthInputs
import com.github.pawelj_pl.bibliogar.api.infrastructure.config.Config.CookieConfig
import com.github.pawelj_pl.bibliogar.api.infrastructure.dto.user.{
  ChangePasswordReq,
  Email,
  NickName,
  Password,
  ResetPasswordReq,
  SessionCheckResp,
  SessionDetails,
  UserDataReq,
  UserDataResp,
  UserLoginReq,
  UserRegistrationReq
}
import com.github.pawelj_pl.bibliogar.api.infrastructure.endpoints.UserEndpoints
import com.github.pawelj_pl.bibliogar.api.infrastructure.http.ApiEndpoint.latestApiVersion
import com.github.pawelj_pl.bibliogar.api.infrastructure.http.{ErrorResponse, PreconditionFailedReason}
import com.github.pawelj_pl.bibliogar.api.testdoubles.domain.user.UserServiceStub
import com.github.pawelj_pl.bibliogar.api.testdoubles.repositories.SessionRepositoryFake
import com.olegpy.meow.hierarchy.deriveMonadState
import io.chrisdavenport.fuuid.FUUID
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.implicits._
import org.http4s.util.CaseInsensitiveString
import org.http4s.{Header, HttpApp, Method, Request, ResponseCookie, Status, Uri}
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class UserRoutesSpec extends WordSpec with Matchers with UserConstants {
  case class TestState(
    userServiceState: UserServiceStub.UserServiceState = UserServiceStub.UserServiceState(),
    sessionRepoState: SessionRepositoryFake.SessionRepositoryState = SessionRepositoryFake.SessionRepositoryState())
  type TestEffect[A] = StateT[IO, TestState, A]

  final val ApiPrefix: Uri = Uri.unsafeFromString("api") / latestApiVersion

  def routes: HttpApp[TestEffect] = {
    implicit def userService[F[_]: Functor: Applicative: MonadState[*[_], TestState]]: UserService[F] = UserServiceStub.instance[F]
    implicit def sessionRepo[F[_]: Monad: MonadState[*[_], TestState]]: SessionRepositoryAlgebra[F] = SessionRepositoryFake.instance[F]
    implicit val IoCs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
    implicit val TestEffectCs: ContextShift[TestEffect] = ContextShift.deriveStateT[IO, TestState]

    val endpoints = new UserEndpoints(ExampleCookieConfig)

    new UserRoutes[TestEffect](endpoints, AuthToSession).routes.orNotFound
  }

  final val AuthToSession = (authInputs: AuthInputs) =>
    Applicative[TestEffect].pure(
      Either.cond(authInputs.headerApiKey.isDefined, ExampleUserSession, ErrorResponse.Unauthorized("unauthorized"): ErrorResponse))
  final val CookieName = "session"
  final val ExampleCookieMaxAge = 20.minutes
  final val ExampleCookieConfig = CookieConfig(ExampleCookieMaxAge, secure = true, httpOnly = true)

  "Create registration" should {
    val dto: UserRegistrationReq = UserRegistrationReq(Email(ExampleUser.email), NickName(ExampleUser.nickName), Password(ExamplePassword))
    "return 204" when {
      "all data valid" in {
        val initialState = TestState()
        val request = Request[TestEffect](Method.POST, uri = ApiPrefix / "users").withEntity(dto)
        val result = routes.run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.NoContent
      }
    }
    "return 409" when {
      "user already exists" in {
        val initialState = TestState(
          userServiceState =
            UserServiceStub.UserServiceState(userOrError = UserError.EmailAlreadyRegistered(ExampleUser.email).asLeft[User])
        )
        val request = Request[TestEffect](Method.POST, uri = ApiPrefix / "users").withEntity(dto)
        val result = routes.run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.Conflict
      }
    }
  }

  "Registration confirmation" should {
    val token = "someToken"
    "return 200 with user data" when {
      "token is ok" in {
        val initialState = TestState()
        val request = Request[TestEffect](Method.GET, uri = ApiPrefix / "registrations" / token)
        val result = routes.run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.Ok
        result.as[UserDataResp].runA(initialState).unsafeRunSync() shouldBe UserDataResp.fromDomain(ExampleUser)
      }
    }
    "return 404" when {
      "token not found" in {
        val initialState = TestState(
          userServiceState =
            UserServiceStub.UserServiceState(userOrError = UserError.TokenNotFound(token, TokenType.Registration).asLeft[User])
        )
        val request = Request[TestEffect](Method.GET, uri = ApiPrefix / "registrations" / token)
        val result = routes.run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.NotFound
        result.as[ErrorResponse.NotFound].runA(initialState).unsafeRunSync() shouldBe ErrorResponse.NotFound("Provided token is not valid")
      }
      "token is outdated" in {
        val initialState = TestState(
          userServiceState =
            UserServiceStub.UserServiceState(userOrError = UserError.OutdatedToken(token, TokenType.Registration, Now).asLeft[User])
        )
        val request = Request[TestEffect](Method.GET, uri = ApiPrefix / "registrations" / token)
        val result = routes.run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.NotFound
        result.as[ErrorResponse.NotFound].runA(initialState).unsafeRunSync() shouldBe ErrorResponse.NotFound("Provided token is not valid")
      }
    }
  }

  "User login" should {
    val dto = UserLoginReq(Email(ExampleUser.email), Password(ExamplePassword))
    val csrfTokenHeaderName = "X-CSRF-TOKEN"
    "return 200 save session and set cookie" when {
      "credentials are ok" in {
        val expectedCookie = ResponseCookie(
          name = CookieName,
          content = ExampleUserSession.sessionId.toString,
          maxAge = Some(ExampleCookieMaxAge.toSeconds),
          path = Some("/"),
          secure = true,
          httpOnly = true
        )
        val initialState = TestState()
        val request = Request[TestEffect](Method.POST, uri = ApiPrefix / "auth" / "login").withEntity(dto)
        val (state, result) = routes.run(request).run(initialState).unsafeRunSync()
        result.status shouldBe Status.Ok
        result.cookies.find(_.name == CookieName) shouldBe Some(expectedCookie)
        result.headers.get(CaseInsensitiveString(csrfTokenHeaderName)).map(_.value) shouldBe Some(ExampleUserSession.csrfToken.toString)
        state.sessionRepoState shouldBe initialState.sessionRepoState.copy(sessions = Set(ExampleUserSession))
      }
    }
    "return 401" when {
      "user not found" in {
        val initialState = TestState(
          userServiceState = UserServiceStub.UserServiceState(userOrError = UserError.UserEmailNotFound(ExampleUser.email).asLeft[User])
        )
        val request = Request[TestEffect](Method.POST, uri = ApiPrefix / "auth" / "login").withEntity(dto)
        val (state, result) = routes.run(request).run(initialState).unsafeRunSync()
        result.status shouldBe Status.Unauthorized
        result.cookies.find(_.name == CookieName) shouldBe None
        result.headers.get(CaseInsensitiveString(csrfTokenHeaderName)) shouldBe None
        state.sessionRepoState shouldBe initialState.sessionRepoState
        result.as[ErrorResponse.Unauthorized].runA(initialState).unsafeRunSync() shouldBe ErrorResponse.Unauthorized("Invalid credentials")
      }
      "user is not active" in {
        val initialState = TestState(
          userServiceState = UserServiceStub.UserServiceState(userOrError =
            UserError.UserNotActive(AuthData(ExampleUser.id, "someHash", confirmed = true, enabled = false, Now)).asLeft[User])
        )
        val request = Request[TestEffect](Method.POST, uri = ApiPrefix / "auth" / "login").withEntity(dto)
        val (state, result) = routes.run(request).run(initialState).unsafeRunSync()
        result.status shouldBe Status.Unauthorized
        result.cookies.find(_.name == CookieName) shouldBe None
        result.headers.get(CaseInsensitiveString(csrfTokenHeaderName)) shouldBe None
        state.sessionRepoState shouldBe initialState.sessionRepoState
        result.as[ErrorResponse.Unauthorized].runA(initialState).unsafeRunSync() shouldBe ErrorResponse.Unauthorized("Invalid credentials")
      }
      "credentials are invalid" in {
        val initialState = TestState(
          userServiceState = UserServiceStub.UserServiceState(userOrError = UserError.InvalidCredentials(ExampleUser.id).asLeft[User])
        )
        val request = Request[TestEffect](Method.POST, uri = ApiPrefix / "auth" / "login").withEntity(dto)
        val (state, result) = routes.run(request).run(initialState).unsafeRunSync()
        result.status shouldBe Status.Unauthorized
        result.cookies.find(_.name == CookieName) shouldBe None
        result.headers.get(CaseInsensitiveString(csrfTokenHeaderName)) shouldBe None
        state.sessionRepoState shouldBe initialState.sessionRepoState
        result.as[ErrorResponse.Unauthorized].runA(initialState).unsafeRunSync() shouldBe ErrorResponse.Unauthorized("Invalid credentials")
      }
    }
  }

  "Get user data" should {
    "return 200 and user data" when {
      "user logged in" in {
        val initialState = TestState()
        val request =
          Request[TestEffect](Method.GET, uri = ApiPrefix / "users" / "me" / "data").withHeaders(Header("X-Api-Key", "something"))
        val result = routes.run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.Ok
        result.as[UserDataResp].runA(initialState).unsafeRunSync() shouldBe UserDataResp.fromDomain(ExampleUser)
      }
    }
    "return 401" when {
      "user not logged in" in {
        val initialState = TestState()
        val request = Request[TestEffect](Method.GET, uri = ApiPrefix / "users" / "me" / "data")
        val result = routes.run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.Unauthorized
      }
    }
  }

  "Logout" should {
    "return 204, set invalid cookie and remove session" when {
      "user logged in" in {
        val expectedCookie = ResponseCookie(name = CookieName, content = "invalid", maxAge = Some(0), path = Some("/"))
        val initialState = TestState(
          sessionRepoState = SessionRepositoryFake.SessionRepositoryState(sessions = Set(ExampleUserSession))
        )
        val request = Request[TestEffect](Method.POST, uri = ApiPrefix / "auth" / "logout").withHeaders(Header("X-Api-Key", "something"))
        val (state, result) = routes.run(request).run(initialState).unsafeRunSync()
        result.status shouldBe Status.NoContent
        result.cookies.find(_.name == CookieName) shouldBe Some(expectedCookie)
        state.sessionRepoState shouldBe initialState.sessionRepoState.copy(sessions = Set.empty)
      }
    }
    "return 401" when {
      "unauthorized" in {
        val initialState = TestState(
          sessionRepoState = SessionRepositoryFake.SessionRepositoryState(sessions = Set(ExampleUserSession))
        )
        val request = Request[TestEffect](Method.POST, uri = ApiPrefix / "auth" / "logout")
        val (state, result) = routes.run(request).run(initialState).unsafeRunSync()
        result.status shouldBe Status.Unauthorized
        result.cookies.find(_.name == CookieName) shouldBe None
        state.sessionRepoState shouldBe initialState.sessionRepoState
      }
    }
  }

  "Check session" should {
    "return 200 and isValid true" when {
      "User logged in" in {
        val initialState = TestState()
        val request =
          Request[TestEffect](Method.GET, uri = ApiPrefix / "users" / "me" / "session").withHeaders(Header("X-Api-Key", "something"))
        val result = routes.run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.Ok
        result.as[SessionCheckResp].runA(initialState).unsafeRunSync() shouldBe SessionCheckResp(
          isValid = true,
          Some(SessionDetails(ExampleUserSession.csrfToken)))
      }
    }
    "return 200 and isValid false" when {
      "User not logged in" in {
        val initialState = TestState()
        val request =
          Request[TestEffect](Method.GET, uri = ApiPrefix / "users" / "me" / "session")
        val result = routes.run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.Ok
        result.as[SessionCheckResp].runA(initialState).unsafeRunSync() shouldBe SessionCheckResp(isValid = false, None)
      }
    }
  }

  "Set user data" should {
    val dto = UserDataReq(None, NickName(ExampleUser.nickName))
    "update user data" when {
      "user logged in" in {
        val initialState = TestState()
        val request = Request[TestEffect](Method.PUT, uri = ApiPrefix / "users" / "me" / "data")
          .withEntity(dto)
          .withHeaders(Header("X-Api-Key", "something"))
        val result = routes.run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.Ok
        result.as[UserDataResp].runA(initialState).unsafeRunSync() shouldBe UserDataResp(ExampleUser.id,
                                                                                         ExampleUser.updatedAt.toEpochMilli.toString,
                                                                                         ExampleUser.email,
                                                                                         ExampleUser.nickName)
      }
    }
    "return 401" when {
      "user not logged in" in {
        val initialState = TestState()
        val request = Request[TestEffect](Method.PUT, uri = ApiPrefix / "users" / "me" / "data")
          .withEntity(dto)
        val result = routes.run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.Unauthorized
      }
    }
    "return 412" when {
      "version mismatch" in {
        val initialState = TestState(
          userServiceState = UserServiceStub.UserServiceState(
            userOrError = CommonError.ResourceVersionDoesNotMatch("5", "2").asLeft[User]
          )
        )
        val request = Request[TestEffect](Method.PUT, uri = ApiPrefix / "users" / "me" / "data")
          .withEntity(dto)
          .withHeaders(Header("X-Api-Key", "something"))
        val result = routes.run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.PreconditionFailed
        result.as[ErrorResponse.PreconditionFailed].runA(initialState).unsafeRunSync() shouldBe ErrorResponse.PreconditionFailed(
          "Attempting to update resource from version 2, but current version is 5",
          Some(PreconditionFailedReason.ResourceErrorDoesNotMatch))
      }
    }
  }

  "Change password" should {
    val dto = ChangePasswordReq(Password(ExamplePassword), Password("newSecretPassword"))
    "change password, remove all user sessions and return 204" when {
      "possible" in {
        val s1 = UserSession(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f051"), None, ExampleUser.id, TestCsrfToken, Now)
        val s2 = UserSession(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f052"), None, ExampleId1, TestCsrfToken, Now)
        val s3 = UserSession(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f053"), None, ExampleUser.id, TestCsrfToken, Now)
        val s4 = UserSession(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f054"), None, ExampleUser.id, TestCsrfToken, Now)
        val s5 = UserSession(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f055"), None, ExampleId1, TestCsrfToken, Now)
        val initialState = TestState(
          sessionRepoState = SessionRepositoryFake.SessionRepositoryState(sessions = Set(s1, s2, s3, s4, s5))
        )
        val request = Request[TestEffect](Method.POST, uri = ApiPrefix / "users" / "me" / "password")
          .withHeaders(Header("X-Api-Key", "something"))
          .withEntity(dto)
        val (state, result) = routes.run(request).run(initialState).unsafeRunSync()
        result.status shouldBe Status.NoContent
        state.sessionRepoState shouldBe initialState.sessionRepoState.copy(sessions = Set(s2, s5))
      }
    }
    "return 422" when {
      "passwords are equal" in {
        val invalidDto = ChangePasswordReq(Password(ExamplePassword), Password(ExamplePassword))
        val s1 = UserSession(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f051"), None, ExampleUser.id, TestCsrfToken, Now)
        val s2 = UserSession(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f052"), None, ExampleId1, TestCsrfToken, Now)
        val s3 = UserSession(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f053"), None, ExampleUser.id, TestCsrfToken, Now)
        val s4 = UserSession(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f054"), None, ExampleUser.id, TestCsrfToken, Now)
        val s5 = UserSession(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f055"), None, ExampleId1, TestCsrfToken, Now)
        val initialState = TestState(
          userServiceState = UserServiceStub.UserServiceState(authDataOrError = UserError.NewAndOldPasswordAreEqual.asLeft[AuthData]),
          sessionRepoState = SessionRepositoryFake.SessionRepositoryState(sessions = Set(s1, s2, s3, s4, s5))
        )
        val request = Request[TestEffect](Method.POST, uri = ApiPrefix / "users" / "me" / "password")
          .withHeaders(Header("X-Api-Key", "something"))
          .withEntity(invalidDto)
        val (state, result) = routes.run(request).run(initialState).unsafeRunSync()
        result.status shouldBe Status.UnprocessableEntity
        state.sessionRepoState shouldBe initialState.sessionRepoState
      }
    }
    "return 403" when {
      "user is not active" in {
        val s1 = UserSession(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f051"), None, ExampleUser.id, TestCsrfToken, Now)
        val s2 = UserSession(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f052"), None, ExampleId1, TestCsrfToken, Now)
        val s3 = UserSession(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f053"), None, ExampleUser.id, TestCsrfToken, Now)
        val s4 = UserSession(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f054"), None, ExampleUser.id, TestCsrfToken, Now)
        val s5 = UserSession(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f055"), None, ExampleId1, TestCsrfToken, Now)
        val initialState = TestState(
          userServiceState = UserServiceStub.UserServiceState(
            authDataOrError =
              UserError.UserNotActive(AuthData(ExampleUser.id, "someHash", confirmed = true, enabled = false, Now)).asLeft[AuthData]),
          sessionRepoState = SessionRepositoryFake.SessionRepositoryState(sessions = Set(s1, s2, s3, s4, s5))
        )
        val request = Request[TestEffect](Method.POST, uri = ApiPrefix / "users" / "me" / "password")
          .withHeaders(Header("X-Api-Key", "something"))
          .withEntity(dto)
        val (state, result) = routes.run(request).run(initialState).unsafeRunSync()
        result.status shouldBe Status.Forbidden
        state.sessionRepoState shouldBe initialState.sessionRepoState
      }
      "return 401" when {
        "password is invalid" in {
          val s1 = UserSession(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f051"), None, ExampleUser.id, TestCsrfToken, Now)
          val s2 = UserSession(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f052"), None, ExampleId1, TestCsrfToken, Now)
          val s3 = UserSession(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f053"), None, ExampleUser.id, TestCsrfToken, Now)
          val s4 = UserSession(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f054"), None, ExampleUser.id, TestCsrfToken, Now)
          val s5 = UserSession(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f055"), None, ExampleId1, TestCsrfToken, Now)
          val initialState = TestState(
            userServiceState =
              UserServiceStub.UserServiceState(authDataOrError = UserError.InvalidCredentials(ExampleUser.id).asLeft[AuthData]),
            sessionRepoState = SessionRepositoryFake.SessionRepositoryState(sessions = Set(s1, s2, s3, s4, s5))
          )
          val request = Request[TestEffect](Method.POST, uri = ApiPrefix / "users" / "me" / "password")
            .withHeaders(Header("X-Api-Key", "something"))
            .withEntity(dto)
          val (state, result) = routes.run(request).run(initialState).unsafeRunSync()
          result.status shouldBe Status.Unauthorized
          state.sessionRepoState shouldBe initialState.sessionRepoState
        }
        "user not logged in" in {
          val s1 = UserSession(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f051"), None, ExampleUser.id, TestCsrfToken, Now)
          val s2 = UserSession(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f052"), None, ExampleId1, TestCsrfToken, Now)
          val s3 = UserSession(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f053"), None, ExampleUser.id, TestCsrfToken, Now)
          val s4 = UserSession(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f054"), None, ExampleUser.id, TestCsrfToken, Now)
          val s5 = UserSession(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f055"), None, ExampleId1, TestCsrfToken, Now)
          val initialState = TestState(
            sessionRepoState = SessionRepositoryFake.SessionRepositoryState(sessions = Set(s1, s2, s3, s4, s5))
          )
          val request = Request[TestEffect](Method.POST, uri = ApiPrefix / "users" / "me" / "password")
            .withEntity(dto)
          val (state, result) = routes.run(request).run(initialState).unsafeRunSync()
          result.status shouldBe Status.Unauthorized
          state.sessionRepoState shouldBe initialState.sessionRepoState
        }
      }
    }
  }

  "Request password reset" should {
    "return 204" when {
      "user found" in {
        val initialState = TestState()
        val request = Request[TestEffect](Method.GET, uri = ApiPrefix / "passwords" / ExampleUser.email)
        val result = routes.run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.NoContent
      }
      "user not found" in {
        val initialState = TestState(
          userServiceState = UserServiceStub.UserServiceState(maybeUserToken = None)
        )
        val request = Request[TestEffect](Method.GET, uri = ApiPrefix / "passwords" / ExampleUser.email)
        val result = routes.run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.NoContent
      }
    }
  }

  "Reset password" should {
    val dto = ResetPasswordReq(Password("newSecretPassword"))
    val token = "xyz"
    "reset password and delete all user sessions" when {
      "token is valid" in {
        val s1 = UserSession(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f051"), None, ExampleUser.id, TestCsrfToken, Now)
        val s2 = UserSession(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f052"), None, ExampleId1, TestCsrfToken, Now)
        val s3 = UserSession(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f053"), None, ExampleUser.id, TestCsrfToken, Now)
        val s4 = UserSession(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f054"), None, ExampleUser.id, TestCsrfToken, Now)
        val s5 = UserSession(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f055"), None, ExampleId1, TestCsrfToken, Now)
        val initialState = TestState(
          sessionRepoState = SessionRepositoryFake.SessionRepositoryState(sessions = Set(s1, s2, s3, s4, s5))
        )
        val request = Request[TestEffect](Method.POST, uri = ApiPrefix / "passwords" / token).withEntity(dto)
        val (state, result) = routes.run(request).run(initialState).unsafeRunSync()
        result.status shouldBe Status.NoContent
        state.sessionRepoState shouldBe initialState.sessionRepoState.copy(sessions = Set(s2, s5))
      }
    }
    "return 404" when {
      "token not found" in {
        val s1 = UserSession(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f051"), None, ExampleUser.id, TestCsrfToken, Now)
        val s2 = UserSession(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f052"), None, ExampleId1, TestCsrfToken, Now)
        val s3 = UserSession(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f053"), None, ExampleUser.id, TestCsrfToken, Now)
        val s4 = UserSession(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f054"), None, ExampleUser.id, TestCsrfToken, Now)
        val s5 = UserSession(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f055"), None, ExampleId1, TestCsrfToken, Now)
        val initialState = TestState(
          userServiceState =
            UserServiceStub.UserServiceState(authDataOrError = UserError.TokenNotFound(token, TokenType.PasswordReset).asLeft[AuthData]),
          sessionRepoState = SessionRepositoryFake.SessionRepositoryState(sessions = Set(s1, s2, s3, s4, s5))
        )
        val request = Request[TestEffect](Method.POST, uri = ApiPrefix / "passwords" / token).withEntity(dto)
        val (state, result) = routes.run(request).run(initialState).unsafeRunSync()
        result.status shouldBe Status.NotFound
        state.sessionRepoState shouldBe initialState.sessionRepoState
      }
      "token is outdated" in {
        val s1 = UserSession(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f051"), None, ExampleUser.id, TestCsrfToken, Now)
        val s2 = UserSession(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f052"), None, ExampleId1, TestCsrfToken, Now)
        val s3 = UserSession(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f053"), None, ExampleUser.id, TestCsrfToken, Now)
        val s4 = UserSession(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f054"), None, ExampleUser.id, TestCsrfToken, Now)
        val s5 = UserSession(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f055"), None, ExampleId1, TestCsrfToken, Now)
        val initialState = TestState(
          userServiceState = UserServiceStub.UserServiceState(
            authDataOrError = UserError.OutdatedToken(token, TokenType.PasswordReset, Now).asLeft[AuthData]),
          sessionRepoState = SessionRepositoryFake.SessionRepositoryState(sessions = Set(s1, s2, s3, s4, s5))
        )
        val request = Request[TestEffect](Method.POST, uri = ApiPrefix / "passwords" / token).withEntity(dto)
        val (state, result) = routes.run(request).run(initialState).unsafeRunSync()
        result.status shouldBe Status.NotFound
        state.sessionRepoState shouldBe initialState.sessionRepoState
      }
      "user is not active" in {
        val s1 = UserSession(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f051"), None, ExampleUser.id, TestCsrfToken, Now)
        val s2 = UserSession(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f052"), None, ExampleId1, TestCsrfToken, Now)
        val s3 = UserSession(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f053"), None, ExampleUser.id, TestCsrfToken, Now)
        val s4 = UserSession(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f054"), None, ExampleUser.id, TestCsrfToken, Now)
        val s5 = UserSession(FUUID.fuuid("986e3548-07c3-43eb-b789-4ec21a27f055"), None, ExampleId1, TestCsrfToken, Now)
        val initialState = TestState(
          userServiceState = UserServiceStub.UserServiceState(
            authDataOrError =
              UserError.UserNotActive(AuthData(ExampleUser.id, "someHash", confirmed = true, enabled = false, Now)).asLeft[AuthData]),
          sessionRepoState = SessionRepositoryFake.SessionRepositoryState(sessions = Set(s1, s2, s3, s4, s5))
        )
        val request = Request[TestEffect](Method.POST, uri = ApiPrefix / "passwords" / token).withEntity(dto)
        val (state, result) = routes.run(request).run(initialState).unsafeRunSync()
        result.status shouldBe Status.NotFound
        state.sessionRepoState shouldBe initialState.sessionRepoState
      }
    }
  }

}
