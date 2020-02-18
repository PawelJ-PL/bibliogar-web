package com.github.pawelj_pl.bibliogar.api.infrastructure.routes

import cats.{Applicative, Functor}
import cats.data.StateT
import cats.effect.{ContextShift, IO}
import cats.mtl.instances.all._
import cats.mtl.MonadState
import com.github.pawelj_pl.bibliogar.api.constants.{BookConstants, UserConstants}
import com.github.pawelj_pl.bibliogar.api.domain.book.BookService
import com.github.pawelj_pl.bibliogar.api.infrastructure.authorization.AuthInputs
import com.github.pawelj_pl.bibliogar.api.infrastructure.dto.book.{Authors, BookDataResp, BookReq, Cover, Isbn, Title}
import com.github.pawelj_pl.bibliogar.api.infrastructure.http.ApiEndpoint.latestApiVersion
import com.github.pawelj_pl.bibliogar.api.infrastructure.http.ErrorResponse
import com.github.pawelj_pl.bibliogar.api.testdoubles.domain.book.BookServiceFake
import com.softwaremill.diffx.scalatest.DiffMatcher
import com.olegpy.meow.hierarchy.deriveMonadState
import com.softwaremill.diffx.{Derived, Diff}
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.{Header, HttpApp, Method, Request, Status, Uri}
import org.http4s.implicits._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.ExecutionContext

class BookRoutesSpec extends AnyWordSpec with Matchers with DiffMatcher with UserConstants with BookConstants {
  case class TestState(bookServiceState: BookServiceFake.BookServiceState = BookServiceFake.BookServiceState())
  type TestEffect[A] = StateT[IO, TestState, A]

  implicit val uriDiff: Derived[Diff[Uri]] = Derived(Diff[String].contramap[Uri](_.renderString))

  final val ApiPrefix: Uri = Uri.unsafeFromString("api") / latestApiVersion

  final val AuthToSession = (authInputs: AuthInputs) =>
    Applicative[TestEffect].pure(
      Either.cond(authInputs.headerApiKey.isDefined, ExampleUserSession, ErrorResponse.Unauthorized("unauthorized"): ErrorResponse))

  private val routes: HttpApp[TestEffect] = {
    implicit val IoCs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
    implicit val TestEffectCs: ContextShift[TestEffect] = ContextShift.deriveStateT[IO, TestState]
    implicit def bookService[F[_]: Functor: MonadState[*[_], TestState]]: BookService[F] = BookServiceFake.instance[F]

    new BookRoutes[TestEffect](AuthToSession).routes.orNotFound
  }

  final val ExampleBookDataReq =
    BookReq(Isbn(ExampleBook.isbn), Title(ExampleBook.title), ExampleBook.authors.map(Authors), ExampleBook.cover.map(Cover))
  final val ExampleBookDataResp =
    BookDataResp(ExampleBook.id, ExampleBook.isbn, ExampleBook.title, ExampleBook.authors, ExampleBook.cover)

  "Create book" should {
    "return new book" in {
      val initialState = TestState()
      val request = Request[TestEffect](method = Method.POST, uri = ApiPrefix / "books")
        .withEntity(ExampleBookDataReq)
        .withHeaders(Header("X-Api-Key", "something"))
      val result = routes.run(request).runA(initialState).unsafeRunSync()
      result.status shouldBe Status.Ok
      result.as[BookDataResp].runA(initialState).unsafeRunSync() should matchTo(ExampleBookDataResp)
    }
    "return 401" in {
      val initialState = TestState()
      val request = Request[TestEffect](method = Method.POST, uri = ApiPrefix / "books")
        .withEntity(ExampleBookDataReq)
      val result = routes.run(request).runA(initialState).unsafeRunSync()
      result.status shouldBe Status.Unauthorized
    }
  }

  "Get book" should {
    "return book data" in {
      val initialState = TestState()
      val request = Request[TestEffect](method = Method.GET, uri = ApiPrefix / "books" / ExampleBook.id.toString)
        .withHeaders(Header("X-Api-Key", "something"))
      val result = routes.run(request).runA(initialState).unsafeRunSync()
      result.status shouldBe Status.Ok
      result.as[BookDataResp].runA(initialState).unsafeRunSync() should matchTo(ExampleBookDataResp)
    }
    "return 404" when {
      "book not found" in {
        val initialState = TestState(
          BookServiceFake.BookServiceState(
            maybeBook = None
          ))
        val request = Request[TestEffect](method = Method.GET, uri = ApiPrefix / "books" / ExampleBook.id.toString)
          .withHeaders(Header("X-Api-Key", "something"))
        val result = routes.run(request).runA(initialState).unsafeRunSync()
        result.status shouldBe Status.NotFound
      }
    }
    "return 401" in {
      val initialState = TestState()
      val request = Request[TestEffect](method = Method.GET, uri = ApiPrefix / "books" / ExampleBook.id.toString)
      val result = routes.run(request).runA(initialState).unsafeRunSync()
      result.status shouldBe Status.Unauthorized
    }
  }

  "Get ISBN suggestion" should {
    "return suggested books" in {
      val initialState = TestState()
      val request = Request[TestEffect](method = Method.GET, uri = ApiPrefix / "books" / "isbn" / ExampleBook.isbn)
        .withHeaders(Header("X-Api-Key", "something"))
      val result = routes.run(request).runA(initialState).unsafeRunSync()
      result.status shouldBe Status.Ok
      result.as[List[BookDataResp]].runA(initialState).unsafeRunSync() should matchTo(List(ExampleBookDataResp))
    }
    "return 401" in {
      val initialState = TestState()
      val request = Request[TestEffect](method = Method.GET, uri = ApiPrefix / "books" / "isbn" / ExampleBook.isbn)
      val result = routes.run(request).runA(initialState).unsafeRunSync()
      result.status shouldBe Status.Unauthorized
    }
  }
}
