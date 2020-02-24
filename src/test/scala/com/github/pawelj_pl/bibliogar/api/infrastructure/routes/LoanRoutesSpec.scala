package com.github.pawelj_pl.bibliogar.api.infrastructure.routes

import cats.{Applicative, Functor}
import cats.data.{NonEmptyList, StateT}
import cats.effect.{ContextShift, IO}
import cats.mtl.instances.all._
import cats.mtl.MonadState
import cats.syntax.show._
import com.github.pawelj_pl.bibliogar.api.{CommonError, LibraryError, LoanError}
import com.github.pawelj_pl.bibliogar.api.constants.{LibraryConstants, LoanConstants, UserConstants}
import com.github.pawelj_pl.bibliogar.api.domain.loan.LoanService
import com.github.pawelj_pl.bibliogar.api.infrastructure.authorization.AuthInputs
import com.github.pawelj_pl.bibliogar.api.infrastructure.dto.loan.{EditLoanReq, LoanDataResp, NewLoanReq}
import com.github.pawelj_pl.bibliogar.api.infrastructure.http.ApiEndpoint.latestApiVersion
import com.github.pawelj_pl.bibliogar.api.infrastructure.http.{ErrorResponse, PreconditionFailedReason}
import com.github.pawelj_pl.bibliogar.api.testdoubles.domain.loan.LoanServiceStub
import com.olegpy.meow.hierarchy.deriveMonadState
import com.softwaremill.diffx.scalatest.DiffMatcher
import org.http4s.{Header, HttpApp, Method, Request, Status, Uri}
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.implicits._
import org.http4s.util.CaseInsensitiveString
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.ExecutionContext

class LoanRoutesSpec extends AnyWordSpec with Matchers with DiffMatcher with LoanConstants with UserConstants with LibraryConstants {
  case class TestState(loanServiceState: LoanServiceStub.LoanServiceState = LoanServiceStub.LoanServiceState())
  type TestEffect[A] = StateT[IO, TestState, A]

  final val ApiPrefix: Uri = Uri.unsafeFromString("api") / latestApiVersion

  final val AuthToSession = (authInputs: AuthInputs) =>
    Applicative[TestEffect].pure(
      Either.cond(authInputs.headerApiKey.isDefined, ExampleUserSession, ErrorResponse.Unauthorized("unauthorized"): ErrorResponse))

  private val routes: HttpApp[TestEffect] = {
    implicit val IoCs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
    implicit val TestEffectCs: ContextShift[TestEffect] = ContextShift.deriveStateT[IO, TestState]
    implicit def loanService[F[_]: Functor: MonadState[*[_], TestState]]: LoanService[F] = LoanServiceStub.instance[F]

    new LoanRoutes[TestEffect](AuthToSession).routes.orNotFound
  }

  private final val ExpectedLoanDataResp = LoanDataResp(ExampleLoan.id,
                                                        ExampleLoan.libraryId,
                                                        ExampleLoan.returnTo,
                                                        ExampleLoan.returnedAt,
                                                        ExampleLoan.books,
                                                        ExampleLoan.createdAt.toEpochMilli.toString)

  "Create loan" should {
    val dto = NewLoanReq(TestLibraryId, NonEmptyList.fromListUnsafe(ExampleLoan.books))

    "return 200 and new loan" in {
      val initialState = TestState()
      val request =
        Request[TestEffect](method = Method.POST, uri = ApiPrefix / "loans").withEntity(dto).withHeaders(Header("X-Api-Key", "something"))
      val result = routes.run(request).runA(initialState).unsafeRunSync()
      result.status shouldBe Status.Ok
      result.as[LoanDataResp].runA(initialState).unsafeRunSync() should matchTo(ExpectedLoanDataResp)
    }

    "return 401" when {
      "user not logged in" in {
        val initialState = TestState()
        val request =
          Request[TestEffect](method = Method.POST, uri = ApiPrefix / "loans").withEntity(dto)
        val result = routes.run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.Unauthorized
      }
    }

    "return 403" when {
      "library not found" in {
        val initialState = TestState(
          loanServiceState = LoanServiceStub.LoanServiceState(
            loanOrError = Left(LibraryError.LibraryIdNotFound(TestLibraryId))
          ))
        val request =
          Request[TestEffect](method = Method.POST, uri = ApiPrefix / "loans").withEntity(dto).withHeaders(Header("X-Api-Key", "something"))
        val result = routes.run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.Forbidden
      }

      "library owned by other user" in {
        val initialState = TestState(
          loanServiceState = LoanServiceStub.LoanServiceState(
            loanOrError = Left(LibraryError.LibraryNotOwnedByUser(TestLibraryId, ExampleUser.id))
          ))
        val request =
          Request[TestEffect](method = Method.POST, uri = ApiPrefix / "loans").withEntity(dto).withHeaders(Header("X-Api-Key", "something"))
        val result = routes.run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.Forbidden
      }
    }

    "return 412" when {
      "books limit exceeded" in {
        val initialState = TestState(
          loanServiceState = LoanServiceStub.LoanServiceState(
            loanOrError = Left(LoanError.BooksLimitExceeded(ExampleLibrary, 10))
          ))
        val request =
          Request[TestEffect](method = Method.POST, uri = ApiPrefix / "loans").withEntity(dto).withHeaders(Header("X-Api-Key", "something"))
        val result = routes.run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.PreconditionFailed
        result.as[ErrorResponse.PreconditionFailed].runA(initialState).unsafeRunSync() should matchTo(
          ErrorResponse.PreconditionFailed("Books limit exceeded", Some(PreconditionFailedReason.BooksLimitExceeded)))
      }
    }
  }

  "Edit loan" should {
    val dto = EditLoanReq(
      TestLibraryId,
      ExampleLoan.returnTo,
      ExampleLoan.returnedAt,
      NonEmptyList.fromListUnsafe(ExampleLoan.books),
      Some(ExampleLoan.createdAt.toEpochMilli.toString)
    )

    "return 200 and updated loan" in {
      val initialState = TestState()
      val request = Request[TestEffect](method = Method.PUT, uri = ApiPrefix / "loans" / ExampleLoan.id.toString())
        .withEntity(dto)
        .withHeaders(Header("X-Api-Key", "something"))
      val result = routes.run(request).runA(initialState).unsafeRunSync()
      result.status shouldBe Status.Ok
      result.as[LoanDataResp].runA(initialState).unsafeRunSync() should matchTo(ExpectedLoanDataResp)
    }

    "return 401" when {
      "user not logged in" in {
        val initialState = TestState()
        val request = Request[TestEffect](method = Method.PUT, uri = ApiPrefix / "loans" / ExampleLoan.id.toString())
          .withEntity(dto)
        val result = routes.run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.Unauthorized
      }
    }

    "return 403" when {
      "loan not found" in {
        val initialState = TestState(
          loanServiceState = LoanServiceStub.LoanServiceState(
            loanOrError = Left(LoanError.LoanNotFound(ExampleLoan.id))
          ))
        val request = Request[TestEffect](method = Method.PUT, uri = ApiPrefix / "loans" / ExampleLoan.id.toString())
          .withEntity(dto)
          .withHeaders(Header("X-Api-Key", "something"))
        val result = routes.run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.Forbidden
      }

      "loan owned by other user" in {
        val initialState = TestState(
          loanServiceState = LoanServiceStub.LoanServiceState(
            loanOrError = Left(LoanError.LoanNotOwnedByUser(ExampleLoan.id, ExampleId1))
          ))
        val request = Request[TestEffect](method = Method.PUT, uri = ApiPrefix / "loans" / ExampleLoan.id.toString())
          .withEntity(dto)
          .withHeaders(Header("X-Api-Key", "something"))
        val result = routes.run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.Forbidden
      }

      "library not found" in {
        val initialState = TestState(
          loanServiceState = LoanServiceStub.LoanServiceState(
            loanOrError = Left(LibraryError.LibraryIdNotFound(TestLibraryId))
          ))
        val request = Request[TestEffect](method = Method.PUT, uri = ApiPrefix / "loans" / ExampleLoan.id.toString())
          .withEntity(dto)
          .withHeaders(Header("X-Api-Key", "something"))
        val result = routes.run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.Forbidden
      }

      "library owned by other user" in {
        val initialState = TestState(
          loanServiceState = LoanServiceStub.LoanServiceState(
            loanOrError = Left(LibraryError.LibraryNotOwnedByUser(TestLibraryId, ExampleId1))
          ))
        val request = Request[TestEffect](method = Method.PUT, uri = ApiPrefix / "loans" / ExampleLoan.id.toString())
          .withEntity(dto)
          .withHeaders(Header("X-Api-Key", "something"))
        val result = routes.run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.Forbidden
      }
    }

    "return 412" when {
      "version mismatch" in {
        val initialState = TestState(
          loanServiceState = LoanServiceStub.LoanServiceState(
            loanOrError = Left(CommonError.ResourceVersionDoesNotMatch("some", "other"))
          ))
        val request = Request[TestEffect](method = Method.PUT, uri = ApiPrefix / "loans" / ExampleLoan.id.toString())
          .withEntity(dto)
          .withHeaders(Header("X-Api-Key", "something"))
        val result = routes.run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.PreconditionFailed
        result.as[ErrorResponse.PreconditionFailed].runA(initialState).unsafeRunSync() should matchTo(
          ErrorResponse.PreconditionFailed("Attempting to update resource from version other, but current version is some",
                                           Some(PreconditionFailedReason.ResourceErrorDoesNotMatch)))
      }

      "books limit exceeded" in {
        val initialState = TestState(
          loanServiceState = LoanServiceStub.LoanServiceState(
            loanOrError = Left(LoanError.BooksLimitExceeded(ExampleLibrary, 10))
          ))
        val request = Request[TestEffect](method = Method.PUT, uri = ApiPrefix / "loans" / ExampleLoan.id.toString())
          .withEntity(dto)
          .withHeaders(Header("X-Api-Key", "something"))
        val result = routes.run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.PreconditionFailed
        result.as[ErrorResponse.PreconditionFailed].runA(initialState).unsafeRunSync() should matchTo(
          ErrorResponse.PreconditionFailed("Books limit exceeded", Some(PreconditionFailedReason.BooksLimitExceeded)))
      }
    }
  }

  "Finish loan" should {
    "return 200 and loan data" in {
      val initialState = TestState()
      val request = Request[TestEffect](method = Method.DELETE, uri = ApiPrefix / "loans" / ExampleLoan.id.toString())
        .withHeaders(Header("X-Api-Key", "something"))
      val result = routes.run(request).runA(initialState).unsafeRunSync()
      result.status shouldBe Status.Ok
      result.as[LoanDataResp].runA(initialState).unsafeRunSync() should matchTo(ExpectedLoanDataResp)
    }

    "return 401" when {
      "user not logged in" in {
        val initialState = TestState()
        val request = Request[TestEffect](method = Method.DELETE, uri = ApiPrefix / "loans" / ExampleLoan.id.toString())
        val result = routes.run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.Unauthorized
      }
    }

    "return 403" when {
      "loan not found" in {
        val initialState = TestState(
          loanServiceState = LoanServiceStub.LoanServiceState(
            loanOrError = Left(LoanError.LoanNotFound(ExampleLoan.id))
          ))
        val request = Request[TestEffect](method = Method.DELETE, uri = ApiPrefix / "loans" / ExampleLoan.id.toString())
          .withHeaders(Header("X-Api-Key", "something"))
        val result = routes.run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.Forbidden
      }

      "loan not owned by user" in {
        val initialState = TestState(
          loanServiceState = LoanServiceStub.LoanServiceState(
            loanOrError = Left(LoanError.LoanNotOwnedByUser(ExampleLoan.id, ExampleId1))
          ))
        val request = Request[TestEffect](method = Method.DELETE, uri = ApiPrefix / "loans" / ExampleLoan.id.toString())
          .withHeaders(Header("X-Api-Key", "something"))
        val result = routes.run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.Forbidden
      }
    }

    "return 412" when {
      "loan already finished" in {
        val initialState = TestState(
          loanServiceState = LoanServiceStub.LoanServiceState(
            loanOrError = Left(LoanError.LoanAlreadyFinished(ExampleLoan.id))
          ))
        val request = Request[TestEffect](method = Method.DELETE, uri = ApiPrefix / "loans" / ExampleLoan.id.toString())
          .withHeaders(Header("X-Api-Key", "something"))
        val result = routes.run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.PreconditionFailed
        result.as[ErrorResponse.PreconditionFailed].runA(initialState).unsafeRunSync() should matchTo(
          ErrorResponse.PreconditionFailed(show"Trying to finish loan ${ExampleLoan.id} which has been already finished",
                                           Some(PreconditionFailedReason.LoanAlreadyFinished)))
      }
    }
  }

  "List loans" should {
    "return 200 and Page number in header" in {
      val initialState = TestState()
      val queryParams = Map("page" -> 1, "pageSize" -> 10)
      val request = Request[TestEffect](method = Method.GET, uri = (ApiPrefix / "loans").withQueryParams(queryParams))
        .withHeaders(Header("X-Api-Key", "something"))
      val result = routes.run(request).runA(initialState).unsafeRunSync()
      result.status shouldBe Status.Ok
      result.as[List[LoanDataResp]].runA(initialState).unsafeRunSync() should matchTo(List(ExpectedLoanDataResp))
      result.headers.get(CaseInsensitiveString("X-Current-Page")).map(_.value) should matchTo(Option("1"))
    }

    "return default page number" in {
      val initialState = TestState()
      val request = Request[TestEffect](method = Method.GET, uri = ApiPrefix / "loans").withHeaders(Header("X-Api-Key", "something"))
      val result = routes.run(request).runA(initialState).unsafeRunSync()
      result.status shouldBe Status.Ok
      result.as[List[LoanDataResp]].runA(initialState).unsafeRunSync() should matchTo(List(ExpectedLoanDataResp))
      result.headers.get(CaseInsensitiveString("X-Current-Page")).map(_.value) should matchTo(Option("1"))
    }

    "return paged result" in {
      val loans = List(
        ExampleLoan,
        ExampleLoan.copy(ExampleId1),
        ExampleLoan.copy(ExampleId2),
        ExampleLoan.copy(ExampleId3),
        ExampleLoan.copy(ExampleId4),
        ExampleLoan.copy(ExampleId5),
        ExampleLoan.copy(ExampleId6)
      )
      val initialState = TestState(loanServiceState = LoanServiceStub.LoanServiceState(
        loans = loans
      ))
      val queryParams = Map("page" -> 3, "pageSize" -> 2)
      val request = Request[TestEffect](method = Method.GET, uri = (ApiPrefix / "loans").withQueryParams(queryParams))
        .withHeaders(Header("X-Api-Key", "something"))
      val result = routes.run(request).runA(initialState).unsafeRunSync()
      result.status shouldBe Status.Ok
      result.as[List[LoanDataResp]].runA(initialState).unsafeRunSync().map(_.id) should matchTo(List(ExampleId4, ExampleId5))
      result.headers.get(CaseInsensitiveString("X-Current-Page")).map(_.value) should matchTo(Option("3"))
      result.headers.get(CaseInsensitiveString("X-Has-Next-Page")).map(_.value) should matchTo(Option("true"))
    }

    "return has next page header false" when {
      "result size lower than page size" in {
        val loans = List(
          ExampleLoan,
          ExampleLoan.copy(ExampleId1),
          ExampleLoan.copy(ExampleId2),
          ExampleLoan.copy(ExampleId3),
          ExampleLoan.copy(ExampleId4),
          ExampleLoan.copy(ExampleId5),
          ExampleLoan.copy(ExampleId6)
        )
        val initialState = TestState(loanServiceState = LoanServiceStub.LoanServiceState(
          loans = loans
        ))
        val queryParams = Map("page" -> 4, "pageSize" -> 2)
        val request = Request[TestEffect](method = Method.GET, uri = (ApiPrefix / "loans").withQueryParams(queryParams))
          .withHeaders(Header("X-Api-Key", "something"))
        val result = routes.run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.Ok
        result.as[List[LoanDataResp]].runA(initialState).unsafeRunSync().map(_.id) should matchTo(List(ExampleId6))
        result.headers.get(CaseInsensitiveString("X-Has-Next-Page")).map(_.value) should matchTo(Option("false"))
      }

      "result size equal page size" in {
        val loans = List(
          ExampleLoan,
          ExampleLoan.copy(ExampleId1),
          ExampleLoan.copy(ExampleId2),
          ExampleLoan.copy(ExampleId3),
          ExampleLoan.copy(ExampleId4),
          ExampleLoan.copy(ExampleId5)
        )
        val initialState = TestState(loanServiceState = LoanServiceStub.LoanServiceState(
          loans = loans
        ))
        val queryParams = Map("page" -> 3, "pageSize" -> 2)
        val request = Request[TestEffect](method = Method.GET, uri = (ApiPrefix / "loans").withQueryParams(queryParams))
          .withHeaders(Header("X-Api-Key", "something"))
        val result = routes.run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.Ok
        result.as[List[LoanDataResp]].runA(initialState).unsafeRunSync().map(_.id) should matchTo(List(ExampleId4, ExampleId5))
        result.headers.get(CaseInsensitiveString("X-Has-Next-Page")).map(_.value) should matchTo(Option("false"))
      }
    }

    "return 401" when {
      "user not logged in" in {
        val initialState = TestState()
        val request = Request[TestEffect](method = Method.GET, uri = ApiPrefix / "loans")
        val result = routes.run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.Unauthorized
        result.headers.get(CaseInsensitiveString("X-Current-Page")).map(_.value) should matchTo(Option.empty[String])
      }
    }

    "return 400" when {
      "page is lower than 1" in {
        val initialState = TestState()
        val queryParams = Map("page" -> 0, "pageSize" -> 10)
        val request = Request[TestEffect](method = Method.GET, uri = (ApiPrefix / "loans").withQueryParams(queryParams))
          .withHeaders(Header("X-Api-Key", "something"))
        val result = routes.run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.BadRequest
      }
      "pageSize is lower than 1" in {
        val initialState = TestState()
        val queryParams = Map("page" -> 1, "pageSize" -> 0)
        val request = Request[TestEffect](method = Method.GET, uri = (ApiPrefix / "loans").withQueryParams(queryParams))
          .withHeaders(Header("X-Api-Key", "something"))
        val result = routes.run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.BadRequest
      }
    }
  }

  "List active loans" should {
    "return active loans" in {
      val initialState = TestState()
      val request =
        Request[TestEffect](method = Method.GET, uri = ApiPrefix / "loans" / "active").withHeaders(Header("X-Api-Key", "something"))
      val result = routes.run(request).runA(initialState).unsafeRunSync()
      result.status shouldBe Status.Ok
      result.as[List[LoanDataResp]].runA(initialState).unsafeRunSync() should matchTo(List(ExpectedLoanDataResp))
    }

    "return 401" when {
      "user not logged in" in {
        val initialState = TestState()
        val request = Request[TestEffect](method = Method.GET, uri = ApiPrefix / "loans" / "active")
        val result = routes.run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.Unauthorized
      }
    }
  }

  "Get single loan" should {
    "return loan" in {
      val initialState = TestState()
      val request = Request[TestEffect](method = Method.GET, uri = ApiPrefix / "loans" / ExampleLoan.id.toString)
        .withHeaders(Header("X-Api-Key", "something"))
      val result = routes.run(request).runA(initialState).unsafeRunSync()
      result.status shouldBe Status.Ok
      result.as[LoanDataResp].runA(initialState).unsafeRunSync() should matchTo(ExpectedLoanDataResp)
    }

    "return 401" when {
      "user not logged in" in {
        val initialState = TestState()
        val request = Request[TestEffect](method = Method.GET, uri = ApiPrefix / "loans" / ExampleLoan.id.toString)
        val result = routes.run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.Unauthorized
      }

      "return 403" when {
        "loan not found" in {
          val initialState = TestState(
            loanServiceState = LoanServiceStub.LoanServiceState(
              loanOrError = Left(LoanError.LoanNotFound(ExampleLoan.id))
            ))
          val request = Request[TestEffect](method = Method.GET, uri = ApiPrefix / "loans" / ExampleLoan.id.toString)
            .withHeaders(Header("X-Api-Key", "something"))
          val result = routes.run(request).runA(initialState).unsafeRunSync()
          result.status shouldBe Status.Forbidden
        }

        "loan not owned by user" in {
          val initialState = TestState(
            loanServiceState = LoanServiceStub.LoanServiceState(
              loanOrError = Left(LoanError.LoanNotOwnedByUser(ExampleLoan.id, ExampleUser.id))
            ))
          val request = Request[TestEffect](method = Method.GET, uri = ApiPrefix / "loans" / ExampleLoan.id.toString)
            .withHeaders(Header("X-Api-Key", "something"))
          val result = routes.run(request).runA(initialState).unsafeRunSync()
          result.status shouldBe Status.Forbidden
        }
      }
    }
  }
}
