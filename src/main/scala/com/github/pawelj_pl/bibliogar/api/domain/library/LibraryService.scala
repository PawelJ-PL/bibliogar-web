package com.github.pawelj_pl.bibliogar.api.domain.library

import cats.data.EitherT
import cats.effect.Sync
import cats.syntax.apply._
import cats.syntax.bifunctor._
import cats.syntax.eq._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.show._
import cats.~>
import com.github.pawelj_pl.bibliogar.api.LibraryError
import com.github.pawelj_pl.bibliogar.api.infrastructure.dto.library.LibraryDataReq
import com.github.pawelj_pl.bibliogar.api.infrastructure.messagebus.Message
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.Misc.resourceVersion.syntax._
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.tracing.MessageEnvelope
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.{RandomProvider, TimeProvider}
import fs2.concurrent.Topic
import io.chrisdavenport.fuuid.FUUID
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

trait LibraryService[F[_]] {
  def createLibraryAs(dto: LibraryDataReq, userId: FUUID): F[Library]
  def getLibraryAs(libraryId: FUUID, userId: FUUID): EitherT[F, LibraryError, Library]
  def getAllLibrariesOfUser(userId: FUUID): F[List[Library]]
  def deleteLibraryAs(libraryId: FUUID, userId: FUUID): EitherT[F, LibraryError, Unit]
  def updateLibraryAs(libraryId: FUUID, dto: LibraryDataReq, userId: FUUID): EitherT[F, LibraryError, Library]
}

object LibraryService {
  def apply[F[_]](implicit ev: LibraryService[F]): LibraryService[F] = ev

  def withDb[F[_]: Sync, D[_]: TimeProvider: RandomProvider: LibraryRepositoryAlgebra: Sync](
    messageTopic: Topic[F, MessageEnvelope]
  )(implicit dbToF: D ~> F
  ): LibraryService[F] =
    new LibraryService[F] {
      private val logD: Logger[D] = Slf4jLogger.getLogger[D]

      override def createLibraryAs(dto: LibraryDataReq, userId: FUUID): F[Library] =
        dbToF(for {
          library <- dto.toDomain[D](userId)
          _       <- logD.info(show"Creating new library: $library")
          saved   <- LibraryRepositoryAlgebra[D].create(library)
        } yield saved).flatTap(l => MessageEnvelope.broadcastWithCurrentContext(Message.NewLibrary(l), messageTopic))

      override def getLibraryAs(libraryId: FUUID, userId: FUUID): EitherT[F, LibraryError, Library] =
        (for {
          library <- LibraryRepositoryAlgebra[D]
            .findById(libraryId)
            .toRight(LibraryError.LibraryIdNotFound(libraryId))
            .leftWiden[LibraryError]
          _ <- EitherT
            .cond[D](library.ownerId === userId, (), LibraryError.LibraryNotOwnedByUser(libraryId, userId))
            .leftWiden[LibraryError]
        } yield library).mapK(dbToF)

      override def getAllLibrariesOfUser(userId: FUUID): F[List[Library]] = dbToF(LibraryRepositoryAlgebra[D].findByOwner(userId))

      override def deleteLibraryAs(libraryId: FUUID, userId: FUUID): EitherT[F, LibraryError, Unit] =
        (for {
          library <- LibraryRepositoryAlgebra[D]
            .findById(libraryId)
            .toRight(LibraryError.LibraryIdNotFound(libraryId))
            .leftWiden[LibraryError]
          _ <- EitherT
            .cond[D](library.ownerId === userId, (), LibraryError.LibraryNotOwnedByUser(libraryId, userId))
            .leftWiden[LibraryError]
          _ <- EitherT.liftF[D, LibraryError, Unit](
            logD.info(show"Attempt to remove library $libraryId as user $userId") *>
              LibraryRepositoryAlgebra[D].delete(libraryId).void
          )
        } yield library)
          .mapK(dbToF)
          .semiflatMap(l => MessageEnvelope.broadcastWithCurrentContext(Message.LibraryDeleted(l), messageTopic))

      override def updateLibraryAs(libraryId: FUUID, dto: LibraryDataReq, userId: FUUID): EitherT[F, LibraryError, Library] =
        (for {
          current <- LibraryRepositoryAlgebra[D].findById(libraryId).toRight(LibraryError.LibraryIdNotFound(libraryId))
          _       <- EitherT.cond[D](current.ownerId === userId, (), LibraryError.LibraryNotOwnedByUser(libraryId, userId)).leftWiden
          _       <- dto.verifyOptVersion[D](current.version).leftWiden[LibraryError]
          updated = current.copy(name = dto.name.value,
                                 loanDurationValue = dto.loanDurationValue.value,
                                 loanDurationUnit = dto.loanDurationUnit,
                                 booksLimit = dto.booksLimit.map(_.value))
          _     <- EitherT.liftF(logD.info(show"User $userId will update library $libraryId with data: $dto"))
          saved <- LibraryRepositoryAlgebra[D].update(updated).toRight(LibraryError.LibraryIdNotFound(updated.id)).leftWiden[LibraryError]
        } yield saved)
          .mapK(dbToF)
          .semiflatTap(l => MessageEnvelope.broadcastWithCurrentContext(Message.LibraryUpdated(l), messageTopic))
    }
}
