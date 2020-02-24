package com.github.pawelj_pl.bibliogar.api.infrastructure.routes

import cats.effect.{ContextShift, Sync}
import cats.syntax.functor._
import cats.syntax.semigroupk._
import com.github.pawelj_pl.bibliogar.api.domain.loan.{Loan, LoanService}
import com.github.pawelj_pl.bibliogar.api.domain.user.UserSession
import com.github.pawelj_pl.bibliogar.api.infrastructure.authorization.AuthInputs
import com.github.pawelj_pl.bibliogar.api.infrastructure.dto.loan.{EditLoanReq, LoanDataResp, NewLoanReq}
import com.github.pawelj_pl.bibliogar.api.infrastructure.http.{ErrorResponse, ResponseUtils}
import io.chrisdavenport.fuuid.FUUID
import org.http4s.HttpRoutes
import sttp.tapir.server.http4s._

class LoanRoutes[F[_]: Sync: ContextShift: Http4sServerOptions: LoanService](
  authToSession: AuthInputs => F[Either[ErrorResponse, UserSession]])
    extends Router[F]
    with ResponseUtils {
  import com.github.pawelj_pl.bibliogar.api.infrastructure.endpoints.LoanEndpoints._

  private def createLoan(
    session: UserSession,
    allowLimitOverrun: Option[Boolean],
    dto: NewLoanReq
  ): F[Either[ErrorResponse, LoanDataResp]] = responseOrError(
    LoanService[F].createLoanAs(dto, session.userId, allowLimitOverrun.getOrElse(false)),
    LoanDataResp.fromDomain
  )

  private def editLoan(
    session: UserSession,
    loanId: FUUID,
    allowLimitOverrun: Option[Boolean],
    dto: EditLoanReq
  ): F[Either[ErrorResponse, LoanDataResp]] = responseOrError(
    LoanService[F].updateLoanAs(loanId, dto, session.userId, allowLimitOverrun.getOrElse(false)),
    LoanDataResp.fromDomain
  )

  private def finishLoan(session: UserSession, loanId: FUUID): F[Either[ErrorResponse, LoanDataResp]] = responseOrError(
    LoanService[F].finishLoanAs(loanId, session.userId),
    LoanDataResp.fromDomain
  )

  private def listLoans(
    session: UserSession,
    page: Option[PositiveInt],
    pageSize: Option[PositiveInt]
  ): F[Either[ErrorResponse, (List[LoanDataResp], Int, Boolean)]] = {
    val pageInt = page.map(_.value).getOrElse(1)
    val pageSizeInt = pageSize.map(_.value).getOrElse(10)
    val offset = (pageInt - 1) * pageSizeInt
    fromErrorlessResult[F, (List[Loan], Boolean), (List[LoanDataResp], Int, Boolean)](
      LoanService[F]
        .getLoansOfUser(session.userId, offset, pageSizeInt + 1)
        .map(loans => if (loans.size > pageSizeInt) (loans.take(pageSizeInt), true) else (loans, false)),
      result => (result._1.map(LoanDataResp.fromDomain), pageInt, result._2)
    )
  }

  private def listActiveLoans(session: UserSession): F[Either[ErrorResponse, List[LoanDataResp]]] =
    fromErrorlessResult[F, List[Loan], List[LoanDataResp]](
      LoanService[F].getActiveLoansOfUser(session.userId),
      _.map(LoanDataResp.fromDomain)
    )

  private def getLoan(session: UserSession, loanId: FUUID): F[Either[ErrorResponse, LoanDataResp]] = responseOrError(
    LoanService[F].getLoanDataAs(loanId, session.userId),
    LoanDataResp.fromDomain
  )

  override val routes: HttpRoutes[F] =
    createLoanEndpoint.toRoutes(authToSession.andThenFirstE((createLoan _).tupled)) <+>
      editLoanEndpoint.toRoutes(authToSession.andThenFirstE((editLoan _).tupled)) <+>
      finishLoanEndpoint.toRoutes(authToSession.andThenFirstE((finishLoan _).tupled)) <+>
      listUsersLoansEndpoint.toRoutes(authToSession.andThenFirstE((listLoans _).tupled)) <+>
      listUsersActiveLoansEndpoint.toRoutes(authToSession.andThenFirstE(listActiveLoans)) <+>
      getSingleLoanEndpoint.toRoutes(authToSession.andThenFirstE((getLoan _).tupled))
}
