package com.github.pawelj_pl.bibliogar.api.infrastructure.routes

import cats.{Applicative, Functor}
import cats.data.StateT
import cats.effect.{ContextShift, IO}
import cats.mtl.instances.all._
import cats.mtl.MonadState
import cats.syntax.either._
import com.github.pawelj_pl.bibliogar.api.{CommonError, LibraryError}
import com.github.pawelj_pl.bibliogar.api.constants.{LibraryConstants, UserConstants}
import com.github.pawelj_pl.bibliogar.api.domain.library.{Library, LibraryService}
import com.github.pawelj_pl.bibliogar.api.infrastructure.authorization.AuthInputs
import com.github.pawelj_pl.bibliogar.api.infrastructure.dto.library.{DurationValue, LibraryDataReq, LibraryDataResp, LibraryName}
import com.github.pawelj_pl.bibliogar.api.infrastructure.http.ApiEndpoint.latestApiVersion
import com.github.pawelj_pl.bibliogar.api.infrastructure.http.ErrorResponse
import com.github.pawelj_pl.bibliogar.api.testdoubles.domain.library.LibraryServiceStub
import com.olegpy.meow.hierarchy.deriveMonadState
import com.softwaremill.diffx.scalatest.DiffMatcher
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.{Header, HttpApp, Method, Request, Status, Uri}
import org.http4s.implicits._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.ExecutionContext

class LibraryRoutesSpec extends AnyWordSpec with Matchers with DiffMatcher with UserConstants with LibraryConstants {
  case class TestState(libraryServiceState: LibraryServiceStub.LibraryServiceState = LibraryServiceStub.LibraryServiceState())
  type TestEffect[A] = StateT[IO, TestState, A]

  final val ApiPrefix: Uri = Uri.unsafeFromString("api") / latestApiVersion

  final val AuthToSession = (authInputs: AuthInputs) =>
    Applicative[TestEffect].pure(
      Either.cond(authInputs.headerApiKey.isDefined, ExampleUserSession, ErrorResponse.Unauthorized("unauthorized"): ErrorResponse))

  private val routes: HttpApp[TestEffect] = {
    implicit val IoCs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
    implicit val TestEffectCs: ContextShift[TestEffect] = ContextShift.deriveStateT[IO, TestState]
    implicit def libraryService[F[_]: Functor: MonadState[*[_], TestState]]: LibraryService[F] = LibraryServiceStub.instance[F]

    new LibraryRoutes[TestEffect](AuthToSession).routes.orNotFound
  }

  final val ExampleLibraryDataReq =
    LibraryDataReq(None, LibraryName(ExampleLibrary.name), DurationValue(ExampleLibrary.loanDurationValue), ExampleLibrary.loanDurationUnit)
  final val ExampleLibraryDataResp = LibraryDataResp(ExampleLibrary.version,
                                                     ExampleLibrary.id,
                                                     ExampleLibrary.name,
                                                     ExampleLibrary.loanDurationValue,
                                                     ExampleLibrary.loanDurationUnit)

  "Create library" should {
    "return 200" in {
      val initialState = TestState()
      val request = Request[TestEffect](method = Method.POST, uri = ApiPrefix / "libraries")
        .withEntity(ExampleLibraryDataReq)
        .withHeaders(Header("X-Api-Key", "something"))
      val result = routes.run(request).runA(initialState).unsafeRunSync()
      result.status shouldBe Status.Ok
      result.as[LibraryDataResp].runA(initialState).unsafeRunSync() should matchTo(ExampleLibraryDataResp)
    }
    "return 401" in {
      val initialState = TestState()
      val request = Request[TestEffect](method = Method.POST, uri = ApiPrefix / "libraries")
        .withEntity(ExampleLibraryDataReq)
      val result = routes.run(request).runA(initialState).unsafeRunSync()
      result.status shouldBe Status.Unauthorized
    }
  }

  "Get users libraries" should {
    "return libraries" in {
      val initialState = TestState()
      val request = Request[TestEffect](method = Method.GET, uri = ApiPrefix / "libraries").withHeaders(Header("X-Api-Key", "something"))
      val result = routes.run(request).runA(initialState).unsafeRunSync()
      result.status shouldBe Status.Ok
      result.as[List[LibraryDataResp]].runA(initialState).unsafeRunSync() should matchTo(List(ExampleLibraryDataResp))
    }
    "return 401" in {
      val initialState = TestState()
      val request = Request[TestEffect](method = Method.GET, uri = ApiPrefix / "libraries")
      val result = routes.run(request).runA(initialState).unsafeRunSync()
      result.status shouldBe Status.Unauthorized
    }
  }

  "Get single library" should {
    "return library" in {
      val initialState = TestState()
      val request = Request[TestEffect](method = Method.GET, uri = ApiPrefix / "libraries" / ExampleLibrary.id.toString).withHeaders(Header("X-Api-Key", "something"))
      val result = routes.run(request).runA(initialState).unsafeRunSync()
      result.status shouldBe Status.Ok
      result.as[LibraryDataResp].runA(initialState).unsafeRunSync() should matchTo(ExampleLibraryDataResp)
    }
    "return 401" in {
      val initialState = TestState()
      val request = Request[TestEffect](method = Method.GET, uri = ApiPrefix / "libraries" / ExampleLibrary.id.toString)
      val result = routes.run(request).runA(initialState).unsafeRunSync()
      result.status shouldBe Status.Unauthorized
    }
    "return 403" when {
      "library not found" in {
        val initialState = TestState(libraryServiceState = LibraryServiceStub.LibraryServiceState(
          libraryOrError = LibraryError.LibraryIdNotFound(ExampleLibrary.id).asLeft[Library]
        ))
        val request = Request[TestEffect](method = Method.GET, uri = ApiPrefix / "libraries" / ExampleLibrary.id.toString).withHeaders(Header("X-Api-Key", "something"))
        val result = routes.run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.Forbidden
      }
      "library owned by other user" in {
        val initialState = TestState(libraryServiceState = LibraryServiceStub.LibraryServiceState(
          libraryOrError = LibraryError.LibraryNotOwnedByUser(ExampleLibrary.id, ExampleId1).asLeft[Library]
        ))
        val request = Request[TestEffect](method = Method.GET, uri = ApiPrefix / "libraries" / ExampleLibrary.id.toString).withHeaders(Header("X-Api-Key", "something"))
        val result = routes.run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.Forbidden
      }
    }
  }

  "Remove library" should {
    "return 201" in {
      val initialState = TestState()
      val request = Request[TestEffect](method = Method.DELETE, uri = ApiPrefix / "libraries" / ExampleLibrary.id.toString).withHeaders(Header("X-Api-Key", "something"))
      val result = routes.run(request).runA(initialState).unsafeRunSync()
      result.status shouldBe Status.NoContent
    }
  }
  "return 401" in {
    val initialState = TestState()
    val request = Request[TestEffect](method = Method.DELETE, uri = ApiPrefix / "libraries" / ExampleLibrary.id.toString)
    val result = routes.run(request).runA(initialState).unsafeRunSync()
    result.status shouldBe Status.Unauthorized
  }
  "return 403" when {
    "library not found" in {
      val initialState = TestState(libraryServiceState = LibraryServiceStub.LibraryServiceState(
        libraryOrError = LibraryError.LibraryIdNotFound(ExampleLibrary.id).asLeft[Library]
      ))
      val request = Request[TestEffect](method = Method.DELETE, uri = ApiPrefix / "libraries" / ExampleLibrary.id.toString).withHeaders(Header("X-Api-Key", "something"))
      val result = routes.run(request).runA(initialState).unsafeRunSync()
      result.status shouldBe Status.Forbidden
    }
    "library not owned by user" in {
      val initialState = TestState(libraryServiceState = LibraryServiceStub.LibraryServiceState(
        libraryOrError = LibraryError.LibraryNotOwnedByUser(ExampleLibrary.id, ExampleUser.id).asLeft[Library]
      ))
      val request = Request[TestEffect](method = Method.DELETE, uri = ApiPrefix / "libraries" / ExampleLibrary.id.toString).withHeaders(Header("X-Api-Key", "something"))
      val result = routes.run(request).runA(initialState).unsafeRunSync()
      result.status shouldBe Status.Forbidden
    }
  }

  "Edit library" should {
    "return updated library" in {
      val initialState = TestState(libraryServiceState = LibraryServiceStub.LibraryServiceState(
        libraryOrError = ExampleLibrary.copy(name = "newName").asRight[LibraryError]
      ))
      val request = Request[TestEffect](method = Method.PUT, uri = ApiPrefix / "libraries" / ExampleLibrary.id.toString)
        .withEntity(ExampleLibraryDataReq.copy(name = LibraryName("newName")))
        .withHeaders(Header("X-Api-Key", "something"))
      val result = routes.run(request).runA(initialState).unsafeRunSync()
      result.status shouldBe Status.Ok
      result.as[LibraryDataResp].runA(initialState).unsafeRunSync() should matchTo(ExampleLibraryDataResp.copy(name = "newName"))
    }
    "return 401" in {
      val initialState = TestState()
      val request = Request[TestEffect](method = Method.PUT, uri = ApiPrefix / "libraries" / ExampleLibrary.id.toString)
        .withEntity(ExampleLibraryDataReq.copy(name = LibraryName("newName")))
      val result = routes.run(request).runA(initialState).unsafeRunSync()
      result.status shouldBe Status.Unauthorized
    }
    "return 412" when {
      "version does not match" in {
        val initialState = TestState(libraryServiceState = LibraryServiceStub.LibraryServiceState(
          libraryOrError = CommonError.ResourceVersionDoesNotMatch("foo", ExampleLibrary.version).asLeft[Library]
        ))
        val request = Request[TestEffect](method = Method.PUT, uri = ApiPrefix / "libraries" / ExampleLibrary.id.toString)
          .withEntity(ExampleLibraryDataReq.copy(name = LibraryName("newName")))
          .withHeaders(Header("X-Api-Key", "something"))
        val result = routes.run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.PreconditionFailed
      }
    }
    "return 403" when {
      "library not found" in {
        val initialState = TestState(libraryServiceState = LibraryServiceStub.LibraryServiceState(
          libraryOrError = LibraryError.LibraryIdNotFound(ExampleLibrary.id).asLeft[Library]
        ))
        val request = Request[TestEffect](method = Method.PUT, uri = ApiPrefix / "libraries" / ExampleLibrary.id.toString)
          .withEntity(ExampleLibraryDataReq.copy(name = LibraryName("newName")))
          .withHeaders(Header("X-Api-Key", "something"))
        val result = routes.run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.Forbidden
      }
      "library owned by other user" in {
        val initialState = TestState(libraryServiceState = LibraryServiceStub.LibraryServiceState(
          libraryOrError = LibraryError.LibraryNotOwnedByUser(ExampleLibrary.id, ExampleUser.id).asLeft[Library]
        ))
        val request = Request[TestEffect](method = Method.PUT, uri = ApiPrefix / "libraries" / ExampleLibrary.id.toString)
          .withEntity(ExampleLibraryDataReq.copy(name = LibraryName("newName")))
          .withHeaders(Header("X-Api-Key", "something"))
        val result = routes.run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.Forbidden
      }
    }
  }
}
