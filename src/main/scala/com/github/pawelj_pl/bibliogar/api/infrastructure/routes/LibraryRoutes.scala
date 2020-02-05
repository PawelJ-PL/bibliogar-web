package com.github.pawelj_pl.bibliogar.api.infrastructure.routes

import cats.effect.{ContextShift, Sync}
import cats.syntax.semigroupk._
import com.github.pawelj_pl.bibliogar.api.domain.library.{Library, LibraryService}
import com.github.pawelj_pl.bibliogar.api.domain.user.UserSession
import com.github.pawelj_pl.bibliogar.api.infrastructure.authorization.AuthInputs
import com.github.pawelj_pl.bibliogar.api.infrastructure.dto.library.{LibraryDataReq, LibraryDataResp}
import com.github.pawelj_pl.bibliogar.api.infrastructure.http.{ErrorResponse, ResponseUtils}
import io.chrisdavenport.fuuid.FUUID
import org.http4s.HttpRoutes
import tapir.server.http4s.Http4sServerOptions
import tapir.server.http4s._

class LibraryRoutes[F[_]: Sync: ContextShift: Http4sServerOptions: LibraryService](
  authToSession: AuthInputs => F[Either[ErrorResponse, UserSession]])
    extends Router[F]
    with ResponseUtils {
  import com.github.pawelj_pl.bibliogar.api.infrastructure.endpoints.LibraryEndpoints._

  private def createLibrary(session: UserSession, dto: LibraryDataReq): F[Either[ErrorResponse, LibraryDataResp]] =
    fromErrorlessResult(
      LibraryService[F].createLibraryAs(dto, session.userId),
      LibraryDataResp.fromDomain
    )

  private def getUsersLibraries(session: UserSession): F[Either[ErrorResponse, List[LibraryDataResp]]] =
    fromErrorlessResult[F, List[Library], List[LibraryDataResp]](
      LibraryService[F].getAllLibrariesOfUser(session.userId),
      _.map(LibraryDataResp.fromDomain)
    )

  private def getSingleLibrary(session: UserSession, libraryId: FUUID): F[Either[ErrorResponse, LibraryDataResp]] = responseOrError(
    LibraryService[F].getLibraryAs(libraryId, session.userId),
    LibraryDataResp.fromDomain
  )

  private def removeLibrary(session: UserSession, libraryId: FUUID): F[Either[ErrorResponse, Unit]] =
    emptyResponseOrError(LibraryService[F].deleteLibraryAs(libraryId, session.userId))

  private def editLibrary(session: UserSession, libraryId: FUUID, dto: LibraryDataReq): F[Either[ErrorResponse, LibraryDataResp]] =
    responseOrError(
      LibraryService[F].updateLibraryAs(libraryId, dto, session.userId),
      LibraryDataResp.fromDomain
    )

  override val routes: HttpRoutes[F] =
    createLibraryEndpoint.toRoutes(authToSession.andThenFirstE((createLibrary _).tupled)) <+>
      getUsersLibrariesEndpoint.toRoutes(authToSession.andThenFirstE(getUsersLibraries)) <+>
      getSingleLibraryEndpoint.toRoutes(authToSession.andThenFirstE((getSingleLibrary _).tupled)) <+>
      removeLibraryEndpoint.toRoutes(authToSession.andThenFirstE((removeLibrary _).tupled)) <+>
      editLibraryEndpoint.toRoutes(authToSession.andThenFirstE((editLibrary _).tupled))
}
