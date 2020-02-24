package com.github.pawelj_pl.bibliogar.api.testdoubles.domain.loan

import cats.Functor
import cats.data.EitherT
import cats.mtl.MonadState
import cats.syntax.functor._
import com.github.pawelj_pl.bibliogar.api.LoanError
import com.github.pawelj_pl.bibliogar.api.constants.LoanConstants
import com.github.pawelj_pl.bibliogar.api.domain.loan.{Loan, LoanService}
import com.github.pawelj_pl.bibliogar.api.infrastructure.dto.loan.{EditLoanReq, NewLoanReq}
import io.chrisdavenport.fuuid.FUUID

object LoanServiceStub extends LoanConstants {
  case class LoanServiceState(
                               loanOrError: Either[LoanError, Loan] = Right(ExampleLoan),
                               loans: List[Loan] = List(ExampleLoan)
                             )

  def instance[F[_]: Functor](implicit S: MonadState[F, LoanServiceState]): LoanService[F] = new LoanService[F] {
    override def createLoanAs(dto: NewLoanReq, userId: FUUID, allowLimitOverrun: Boolean): EitherT[F, LoanError, Loan] =
      EitherT(S.get.map(_.loanOrError))

    override def updateLoanAs(loanId: FUUID, dto: EditLoanReq, userId: FUUID, allowLimitOverrun: Boolean): EitherT[F, LoanError, Loan] =
      EitherT(S.get.map(_.loanOrError))

    override def finishLoanAs(loanId: FUUID, userId: FUUID): EitherT[F, LoanError, Loan] = EitherT(S.get.map(_.loanOrError))

    override def getLoanDataAs(loanId: FUUID, userId: FUUID): EitherT[F, LoanError, Loan] = EitherT(S.get.map(_.loanOrError))

    override def getLoansOfUser(userId: FUUID, offset: Int, limit: Int): F[List[Loan]] =
      S.get.map(_.loans.slice(offset, offset + limit))

    override def getActiveLoansOfUser(userId: FUUID): F[List[Loan]] = S.get.map(_.loanOrError.map(List(_)).getOrElse(List.empty))
  }
}
