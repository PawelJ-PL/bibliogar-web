package com.github.pawelj_pl.bibliogar.api.infrastructure.routes

import cats.{Applicative, Functor, Monad}
import com.github.pawelj_pl.bibliogar.api.infrastructure.http.ApiEndpoint.latestApiVersion
import cats.data.StateT
import cats.effect.{ContextShift, IO}
import cats.mtl.MonadState
import cats.mtl.instances.all._
import com.github.pawelj_pl.bibliogar.api.DeviceError
import com.github.pawelj_pl.bibliogar.api.constants.{DeviceConstants, UserConstants}
import com.github.pawelj_pl.bibliogar.api.domain.device.DevicesService
import com.github.pawelj_pl.bibliogar.api.domain.user.{KeyType, SessionRepositoryAlgebra, UserSession}
import com.github.pawelj_pl.bibliogar.api.infrastructure.authorization.AuthInputs
import com.github.pawelj_pl.bibliogar.api.infrastructure.dto.devices.{AppCompatibilityReq, DeviceRegistrationReq, DeviceRegistrationResp}
import com.github.pawelj_pl.bibliogar.api.infrastructure.http.{ErrorResponse, PreconditionFailedReason}
import com.github.pawelj_pl.bibliogar.api.testdoubles.domain.device.DevicesServiceStub
import com.github.pawelj_pl.bibliogar.api.testdoubles.repositories.SessionRepositoryFake
import com.olegpy.meow.hierarchy.deriveMonadState
import com.vdurmont.semver4j.Semver
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.{Header, HttpApp, Method, Request, ResponseCookie, Status, Uri}
import org.http4s.implicits._
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.ExecutionContext

class DevicesRoutesSpec extends WordSpec with Matchers with UserConstants with DeviceConstants {
  case class TestState(
    devicesServiceState: DevicesServiceStub.DevicesServiceState = DevicesServiceStub.DevicesServiceState(),
    sessionRepoState: SessionRepositoryFake.SessionRepositoryState = SessionRepositoryFake.SessionRepositoryState())
  type TestEffect[A] = StateT[IO, TestState, A]

  final val ApiPrefix: Uri = Uri.unsafeFromString("api") / latestApiVersion

  private def authToSession(session: UserSession): AuthInputs => TestEffect[Either[ErrorResponse, UserSession]] =
    (authInputs: AuthInputs) =>
      Applicative[TestEffect].pure(
        Either.cond(authInputs.headerApiKey.isDefined, session, ErrorResponse.Unauthorized("unauthorized"): ErrorResponse))

  private def routes: HttpApp[TestEffect] = routes(ExampleUserSession)

  private def routes(session: UserSession): HttpApp[TestEffect] = {
    implicit def devicesService[F[_]: Functor: Applicative: MonadState[*[_], TestState]]: DevicesService[F] = DevicesServiceStub.instance[F]
    implicit def sessionRepo[F[_]: Monad: MonadState[*[_], TestState]]: SessionRepositoryAlgebra[F] = SessionRepositoryFake.instance[F]
    implicit val IoCs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
    implicit val TestEffectCs: ContextShift[TestEffect] = ContextShift.deriveStateT[IO, TestState]

    new DevicesRoutes[TestEffect](authToSession(session)).routes.orNotFound
  }

  "Check version compatibility" should {
    val dto = AppCompatibilityReq(new Semver("1.0.0"))
    "return 200" when {
      "version is compatible" in {
        val initialState = TestState()
        val request = Request[TestEffect](Method.POST, uri = ApiPrefix / "devices" / "compatibility").withEntity(dto)
        val result = routes.run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.Ok
      }
    }
    "return 412" when {
      "version is incompatible" in {
        val initialState = TestState(devicesServiceState = DevicesServiceStub.DevicesServiceState(isAppCompatible = false))
        val request = Request[TestEffect](Method.POST, uri = ApiPrefix / "devices" / "compatibility").withEntity(dto)
        val result = routes.run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.PreconditionFailed
      }
    }
  }

  "Register device" should {
    val dto = DeviceRegistrationReq(ExampleDevice.uniqueId, ExampleDevice.deviceDescription)
    "return data and logout cookie user" in {
      val otherSession = ExampleUserSession.copy(sessionId = ExampleId1)
      val initialState = TestState(
        sessionRepoState = SessionRepositoryFake.SessionRepositoryState(
          Set(
            ExampleUserSession,
            otherSession
          )
        )
      )
      val request =
        Request[TestEffect](Method.POST, uri = ApiPrefix / "devices").withEntity(dto).withHeaders(Header("X-Api-Key", "something"))
      val (state, result) = routes.run(request).run(initialState).unsafeRunSync()
      result.status shouldBe Status.Ok
      result.cookies.find(_.name == "session") shouldBe Some(
        ResponseCookie(name = "session", content = "invalid", maxAge = Some(0), path = Some("/")))
      result.as[DeviceRegistrationResp].runA(initialState).unsafeRunSync() shouldBe DeviceRegistrationResp(ExampleDevice.device_id,
                                                                                                           ExampleApiKey.apiKey)
      state.shouldBe(initialState.copy(sessionRepoState = SessionRepositoryFake.SessionRepositoryState(Set(otherSession))))
    }
    "return 401" when {
      "user not logged in" in {
        val request = Request[TestEffect](Method.POST, uri = ApiPrefix / "devices").withEntity(dto)
        val result = routes.run(request).runA(TestState()).unsafeRunSync()
        result.status shouldBe Status.Unauthorized
      }
    }
  }

  "Unregister device" should {
    "unregister device" in {
      val session = ExampleUserSession.copy(apiKeyId = Some(ExampleApiKey.keyId))
      val initialState = TestState()
      val request = Request[TestEffect](Method.DELETE, uri = ApiPrefix / "devices" / "this").withHeaders(Header("X-Api-Key", "something"))
      val result = routes(session).run(request).runA(initialState).unsafeRunSync()
      result.status shouldBe Status.NoContent
    }
    "return 412" when {
      "API key not found in session" in {
        val session = ExampleUserSession.copy(apiKeyId = None)
        val initialState = TestState()
        val request = Request[TestEffect](Method.DELETE, uri = ApiPrefix / "devices" / "this").withHeaders(Header("X-Api-Key", "something"))
        val result = routes(session).run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.PreconditionFailed
        result.as[ErrorResponse.PreconditionFailed].runA(initialState).unsafeRunSync() shouldBe ErrorResponse.PreconditionFailed(
          "No API key found in session",
          None)
      }
      "API key is not device type" in {
        val session = ExampleUserSession.copy(apiKeyId = Some(ExampleApiKey.keyId))
        val initialState = TestState(
          devicesServiceState = DevicesServiceStub.DevicesServiceState(
            maybeError = Some(DeviceError.ApiKeyIsNotDeviceType(ExampleApiKey.keyId, KeyType.User))
          ))
        val request = Request[TestEffect](Method.DELETE, uri = ApiPrefix / "devices" / "this").withHeaders(Header("X-Api-Key", "something"))
        val result = routes(session).run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.PreconditionFailed
        result.as[ErrorResponse.PreconditionFailed].runA(initialState).unsafeRunSync() shouldBe ErrorResponse.PreconditionFailed(
          "Not device API key",
          Some(PreconditionFailedReason.InvalidApiKeyType))
      }
      "API key is not related to any device" in {
        val session = ExampleUserSession.copy(apiKeyId = Some(ExampleApiKey.keyId))
        val initialState = TestState(
          devicesServiceState = DevicesServiceStub.DevicesServiceState(
            maybeError = Some(DeviceError.ApiKeyNotRelatedToAnyDevice(ExampleApiKey.keyId))
          ))
        val request = Request[TestEffect](Method.DELETE, uri = ApiPrefix / "devices" / "this").withHeaders(Header("X-Api-Key", "something"))
        val result = routes(session).run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.PreconditionFailed
        result.as[ErrorResponse.PreconditionFailed].runA(initialState).unsafeRunSync() shouldBe ErrorResponse.PreconditionFailed(
          "API key is not assigned to any device",
          Some(PreconditionFailedReason.NotAssignedApiKey))
      }
    }
  }
}
