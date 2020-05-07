package com.github.pawelj_pl.bibliogar.api.domain.loan

import java.time.{ZoneOffset, ZonedDateTime}

import cats.data.EitherT
import cats.effect.Sync
import cats.{Applicative, Monad, ~>}
import cats.syntax.applicativeError._
import cats.syntax.apply._
import cats.syntax.bifunctor._
import cats.syntax.either._
import cats.syntax.eq._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.show._
import com.github.pawelj_pl.bibliogar.api.domain.book.BookRepositoryAlgebra
import com.github.pawelj_pl.bibliogar.api.{CommonError, LibraryError, LoanError}
import com.github.pawelj_pl.bibliogar.api.domain.library.LibraryRepositoryAlgebra
import com.github.pawelj_pl.bibliogar.api.infrastructure.dto.loan.{EditLoanReq, NewLoanReq}
import com.github.pawelj_pl.bibliogar.api.infrastructure.messagebus.Message
import com.github.pawelj_pl.bibliogar.api.infrastructure.repositories.DbError
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.Misc.resourceVersion.syntax._
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.{RandomProvider, TimeProvider}
import fs2.concurrent.Topic
import io.chrisdavenport.fuuid.FUUID
import io.chrisdavenport.log4cats.SelfAwareStructuredLogger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

trait LoanService[F[_]] {
  def createLoanAs(dto: NewLoanReq, userId: FUUID, allowLimitOverrun: Boolean = false): EitherT[F, LoanError, Loan]
  def updateLoanAs(loanId: FUUID, dto: EditLoanReq, userId: FUUID, allowLimitOverrun: Boolean = false): EitherT[F, LoanError, Loan]
  def finishLoanAs(loanId: FUUID, userId: FUUID): EitherT[F, LoanError, Loan]
  def getLoanDataAs(loanId: FUUID, userId: FUUID): EitherT[F, LoanError, Loan]
  def getLoansOfUser(userId: FUUID, offset: Int, limit: Int): F[List[Loan]]
  def getActiveLoansOfUser(userId: FUUID): F[List[Loan]]
}

object LoanService {
  def apply[F[_]](implicit ev: LoanService[F]): LoanService[F] = ev

  def withDb[F[_]: Monad, D[_]: Sync: TimeProvider: RandomProvider: LibraryRepositoryAlgebra: LoanRepositoryAlgebra: BookRepositoryAlgebra](
    messageTopic: Topic[F, Message]
  )(implicit dbToF: D ~> F
  ): LoanService[F] =
    new LoanService[F] {
      private val log: SelfAwareStructuredLogger[D] = Slf4jLogger.getLogger[D]

      override def createLoanAs(dto: NewLoanReq, userId: FUUID, allowLimitOverrun: Boolean = false): EitherT[F, LoanError, Loan] =
        (for {
          library <- LibraryRepositoryAlgebra[D]
            .findById(dto.libraryId)
            .toRight(LibraryError.LibraryIdNotFound(dto.libraryId))
            .leftWiden[LoanError]
          _ <- EitherT.cond[D](library.ownerId === userId, (), LibraryError.LibraryNotOwnedByUser(library.id, userId)).leftWiden[LoanError]
          _ <- EitherT
            .cond[D](allowLimitOverrun || library.booksLimit.forall(_ >= dto.books.size),
                     (),
                     LoanError.BooksLimitExceeded(library, dto.books.size))
            .leftWiden[LoanError]
          now    <- EitherT.right[LoanError](TimeProvider[D].now)
          loanId <- EitherT.right[LoanError](RandomProvider[D].randomFuuid)
          returnTo = ZonedDateTime.ofInstant(now, ZoneOffset.UTC).plus(library.loanPeriod).toInstant
          loan = Loan(loanId, userId, Some(library.id), returnTo, None, dto.books.toList, now, now)
          _ <- EitherT.right[LoanError](scoreBooks(List.empty, loan.books))
          _ <- EitherT.right[LoanError](log.info(show"Creating new loan ${loan.id} for user $userId"))
          saved <- EitherT(LoanRepositoryAlgebra[D].create(loan).map(_.asRight[LoanError]).recoverWith {
            case e: DbError.ForeignKeyViolation =>
              log.warn(e.sqlError)("Unable to create loan").as(CommonError.DbForeignKeyViolation(e).asLeft[Loan])
          })
        } yield saved)
          .mapK(dbToF)
          .semiflatTap(l => messageTopic.publish1(Message.NewLoan(l)))

      private def scoreBooks(actualBooks: List[Option[FUUID]], futureBooks: List[Option[FUUID]]): D[Unit] = {
        import cats.instances.set._
        def sanitizeBooks(books: List[Option[FUUID]]): Set[FUUID] = books.flatten.toSet

        val actualSet = sanitizeBooks(actualBooks)
        val futureSet = sanitizeBooks(futureBooks)

        if (actualSet === futureSet) {
          Applicative[D].pure(())
        } else {
          val toIncrease = futureSet.diff(actualSet)
          val toDecrease = actualSet.diff(futureSet)
          for {
            _ <- log.info(s"increasing score for books ${toIncrease.mkString(",")}") *>
              BookRepositoryAlgebra[D].increaseScore(toIncrease.toList)
            _ <- log.info(s"decreasing score for books ${toDecrease.mkString(",")}") *>
              BookRepositoryAlgebra[D].increaseScore(toDecrease.toList, -1)
          } yield ()
        }
      }

      override def updateLoanAs(
        loanId: FUUID,
        dto: EditLoanReq,
        userId: FUUID,
        allowLimitOverrun: Boolean = false
      ): EitherT[F, LoanError, Loan] =
        (for {
          current <- LoanRepositoryAlgebra[D].findById(loanId).toRight(LoanError.LoanNotFound(loanId)).leftWiden[LoanError]
          _       <- EitherT.cond[D](current.userId === userId, (), LoanError.LoanNotOwnedByUser(loanId, userId)).leftWiden[LoanError]
          _       <- dto.verifyOptVersion[D](current.version).leftWiden[LoanError]
          _ <- if (current.libraryId.exists(_ =!= dto.libraryId) || current.books != dto.books.toList)
            verifyLibrary(dto, userId, allowLimitOverrun)
          else EitherT.fromEither[D](().asRight[LoanError])
          toSave = current.copy(libraryId = Some(dto.libraryId), returnTo = dto.returnTo, books = dto.books.toList)
          _     <- EitherT.right[LoanError](scoreBooks(current.books, toSave.books))
          _     <- EitherT.right[LoanError](log.info(show"Updating loan $loanId as user $userId"))
          saved <- updateLoanWithRecover(toSave)
        } yield saved)
          .mapK(dbToF)
          .semiflatTap(l => messageTopic.publish1(Message.LoanUpdated(l)))

      private def verifyLibrary(dto: EditLoanReq, userId: FUUID, allowLimitOverrun: Boolean): EitherT[D, LoanError, Unit] =
        for {
          library <- LibraryRepositoryAlgebra[D]
            .findById(dto.libraryId)
            .toRight(LibraryError.LibraryIdNotFound(dto.libraryId))
            .leftWiden[LoanError]
          _ <- EitherT.cond[D](library.ownerId === userId, (), LibraryError.LibraryNotOwnedByUser(library.id, userId)).leftWiden[LoanError]
          _ <- EitherT
            .cond[D](allowLimitOverrun || library.booksLimit.forall(_ >= dto.books.size),
                     (),
                     LoanError.BooksLimitExceeded(library, dto.books.size))
            .leftWiden[LoanError]
        } yield ()

      private def updateLoanWithRecover(loan: Loan) =
        EitherT(
          LoanRepositoryAlgebra[D]
            .update(loan)
            .map(_.asRight[LoanError])
            .getOrElse(LoanError.LoanNotFound(loan.id).asLeft[Loan])
            .recoverWith {
              case err: DbError.ForeignKeyViolation =>
                log.warn(err.sqlError)(show"Unable to create loan ${loan.id}").as(CommonError.DbForeignKeyViolation(err).asLeft[Loan])
            })

      override def finishLoanAs(loanId: FUUID, userId: FUUID): EitherT[F, LoanError, Loan] =
        (for {
          current <- LoanRepositoryAlgebra[D].findById(loanId).toRight(LoanError.LoanNotFound(loanId)).leftWiden[LoanError]
          _       <- EitherT.cond[D](current.userId === userId, (), LoanError.LoanNotOwnedByUser(loanId, userId)).leftWiden[LoanError]
          _       <- EitherT.cond[D](current.returnedAt.isEmpty, (), LoanError.LoanAlreadyFinished(loanId)).leftWiden[LoanError]
          now     <- EitherT.right[LoanError](TimeProvider[D].now)
          toSave = current.copy(returnedAt = Some(now))
          _     <- EitherT.right[LoanError](log.info(show"Finishing loan $loanId by $userId"))
          saved <- updateLoanWithRecover(toSave)
        } yield saved)
          .mapK(dbToF)
          .semiflatTap(l => messageTopic.publish1(Message.LoanUpdated(l)))

      override def getLoanDataAs(loanId: FUUID, userId: FUUID): EitherT[F, LoanError, Loan] =
        (for {
          loan <- LoanRepositoryAlgebra[D].findById(loanId).toRight(LoanError.LoanNotFound(loanId)).leftWiden[LoanError]
          _    <- EitherT.cond[D](loan.userId === userId, (), LoanError.LoanNotOwnedByUser(loanId, userId)).leftWiden[LoanError]
        } yield loan).mapK(dbToF)

      override def getLoansOfUser(userId: FUUID, offset: Int, limit: Int): F[List[Loan]] =
        dbToF(LoanRepositoryAlgebra[D].findByUser(userId, offset, limit))

      override def getActiveLoansOfUser(userId: FUUID): F[List[Loan]] = dbToF(LoanRepositoryAlgebra[D].findByUserAndEmptyReturnedAt(userId))
    }
}
