package com.github.pawelj_pl.bibliogar.api.infrastructure.routes

import cats.effect.{ContextShift, Sync}
import cats.syntax.semigroupk._
import cats.syntax.show._
import com.github.pawelj_pl.bibliogar.api.domain.book.{Book, BookService}
import com.github.pawelj_pl.bibliogar.api.domain.user.UserSession
import com.github.pawelj_pl.bibliogar.api.infrastructure.authorization.AuthInputs
import com.github.pawelj_pl.bibliogar.api.infrastructure.dto.book.{BookDataResp, BookReq, Isbn}
import com.github.pawelj_pl.bibliogar.api.infrastructure.http.{ErrorResponse, ResponseUtils}
import io.chrisdavenport.fuuid.FUUID
import org.http4s.HttpRoutes
import sttp.tapir.server.http4s._

class BookRoutes[F[_]: Sync: ContextShift: Http4sServerOptions: BookService](
  authToSession: AuthInputs => F[Either[ErrorResponse, UserSession]])
    extends Router[F]
    with ResponseUtils {
  import com.github.pawelj_pl.bibliogar.api.infrastructure.endpoints.BookEndpoints._

  private def createBook(session: UserSession, dto: BookReq): F[Either[ErrorResponse, BookDataResp]] = fromErrorlessResult(
    BookService[F].createBookAs(dto, session.userId),
    BookDataResp.fromDomain
  )

  private def getSingleBook(bookId: FUUID): F[Either[ErrorResponse, BookDataResp]] =
    BookService[F]
      .getBook(bookId)
      .map(BookDataResp.fromDomain)
      .toRight(ErrorResponse.NotFound(show"Book $bookId not found"): ErrorResponse)
      .value

  private def getIsbnSuggestions(isbn: String): F[Either[ErrorResponse, List[BookDataResp]]] =
    fromErrorlessResult[F, List[Book], List[BookDataResp]](
      BookService[F].getSuggestionForIsbn(isbn),
      _.map(BookDataResp.fromDomain)
    )

  override val routes: HttpRoutes[F] =
    createBookEndpoint.toRoutes(authToSession.andThenFirstE((createBook _).tupled)) <+>
      getBookEndpoint.toRoutes(authToSession.andThenFirstE(((_: UserSession, id: FUUID) => getSingleBook(id)).tupled)) <+>
      getIsbnSuggestionsEndpoint.toRoutes(
        authToSession.andThenFirstE(((_: UserSession, isbn: Isbn) => getIsbnSuggestions(isbn.value)).tupled))
}
