package com.github.pawelj_pl.bibliogar.api.testdoubles.domain.library

import cats.Functor
import cats.data.EitherT
import cats.mtl.MonadState
import cats.syntax.either._
import cats.syntax.functor._
import com.github.pawelj_pl.bibliogar.api.LibraryError
import com.github.pawelj_pl.bibliogar.api.constants.LibraryConstants
import com.github.pawelj_pl.bibliogar.api.domain.library.{Library, LibraryService}
import com.github.pawelj_pl.bibliogar.api.infrastructure.dto.library.LibraryDataReq
import io.chrisdavenport.fuuid.FUUID

object LibraryServiceStub extends LibraryConstants {
  final case class LibraryServiceState(
    library: Library = ExampleLibrary,
    libraryOrError: Either[LibraryError, Library] = ExampleLibrary.asRight[LibraryError])

  def instance[F[_]: Functor](implicit S: MonadState[F, LibraryServiceState]): LibraryService[F] = new LibraryService[F] {
    override def createLibraryAs(dto: LibraryDataReq, userId: FUUID): F[Library] = S.get.map(_.library)

    override def getLibraryAs(libraryId: FUUID, userId: FUUID): EitherT[F, LibraryError, Library] = EitherT(S.get.map(_.libraryOrError))

    override def getAllLibrariesOfUser(userId: FUUID): F[List[Library]] = S.get.map(s => List(s.library))

    override def deleteLibraryAs(libraryId: FUUID, userId: FUUID): EitherT[F, LibraryError, Unit] =
      EitherT(S.get.map(state => state.libraryOrError.map(_ => (): Unit)))

    override def updateLibraryAs(libraryId: FUUID, dto: LibraryDataReq, userId: FUUID): EitherT[F, LibraryError, Library] = EitherT(S.get.map(_.libraryOrError))
  }
}
