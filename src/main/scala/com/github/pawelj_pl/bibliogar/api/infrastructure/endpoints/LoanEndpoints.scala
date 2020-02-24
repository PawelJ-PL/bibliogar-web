package com.github.pawelj_pl.bibliogar.api.infrastructure.endpoints

import java.time.Instant

import cats.data.NonEmptyList
import cats.syntax.show._
import com.github.pawelj_pl.bibliogar.api.infrastructure.authorization.{AuthInputs, SecuredEndpoint}
import com.github.pawelj_pl.bibliogar.api.infrastructure.dto.loan.{EditLoanReq, LoanDataResp, NewLoanReq}
import com.github.pawelj_pl.bibliogar.api.infrastructure.http.{ApiEndpoint, ErrorResponse, PreconditionFailedReason}
import com.github.pawelj_pl.bibliogar.api.infrastructure.http.Implicits.Fuuid._
import io.chrisdavenport.fuuid.FUUID
import sttp.tapir.Codec.PlainCodec
import sttp.tapir._
import sttp.tapir.json.circe._
import sttp.tapir.codec.cats._

object LoanEndpoints extends ApiEndpoint with SecuredEndpoint {
  private val loansPrefix = apiPrefix / "loans"

  private final val TestLoanId = FUUID.fuuid("d9d29a2b-2473-4213-9755-6554938f8746")
  private final val TestLibraryId = FUUID.fuuid("63702e27-34d4-4fe4-a594-d3ecc5122af5")
  private final val TestBookId = FUUID.fuuid("a07a8af9-ad88-49da-baf9-646994cc01db")

  private final val TestDataResp = LoanDataResp(TestLoanId,
                                                Some(TestLibraryId),
                                                Instant.EPOCH,
                                                Some(Instant.EPOCH.plusSeconds(360000L)),
                                                List(None, Some(TestBookId)),
                                                "someVersion")

  private final val TestEditReq = EditLoanReq(TestLibraryId,
                                              Instant.EPOCH,
                                              Some(Instant.EPOCH.plusSeconds(360000L)),
                                              NonEmptyList.of(None, Some(TestBookId)),
                                              Some("someVersion"))

  val createLoanEndpoint: Endpoint[(AuthInputs, Option[Boolean], NewLoanReq), ErrorResponse, LoanDataResp, Nothing] =
    endpoint
      .summary("Create new loan")
      .tag("loan")
      .post
      .prependIn(authenticationDetails)
      .in(loansPrefix)
      .in(query[Option[Boolean]]("allowLimitOverrun"))
      .in(jsonBody[NewLoanReq].example(NewLoanReq(TestLibraryId, NonEmptyList.of(None, Some(TestBookId)))))
      .out(jsonBody[LoanDataResp].example(TestDataResp))
      .errorOut(
        oneOf[ErrorResponse](
          StatusMappings.badRequest,
          StatusMappings.forbidden(),
          StatusMappings.preconditionFailed(),
          StatusMappings.unauthorized
        )
      )

  val editLoanEndpoint: Endpoint[(AuthInputs, FUUID, Option[Boolean], EditLoanReq), ErrorResponse, LoanDataResp, Nothing] =
    endpoint
      .summary("Edit loan")
      .tag("loan")
      .put
      .prependIn(authenticationDetails)
      .in(loansPrefix / path[FUUID]("loanId"))
      .in(query[Option[Boolean]]("allowLimitOverrun"))
      .in(jsonBody[EditLoanReq].example(TestEditReq))
      .out(jsonBody[LoanDataResp].example(TestDataResp))
      .errorOut(
        oneOf[ErrorResponse](
          StatusMappings.badRequest,
          StatusMappings.forbidden(),
          StatusMappings.preconditionFailed(),
          StatusMappings.unauthorized
        )
      )

  val finishLoanEndpoint: Endpoint[(AuthInputs, FUUID), ErrorResponse, LoanDataResp, Nothing] = endpoint
    .summary("Finish loan")
    .tag("loan")
    .delete
    .prependIn(authenticationDetails)
    .in(loansPrefix / path[FUUID]("loanId"))
    .out(jsonBody[LoanDataResp].example(TestDataResp))
    .errorOut(
      oneOf[ErrorResponse](
        StatusMappings.badRequest,
        StatusMappings.forbidden(),
        StatusMappings.preconditionFailed(
          "Loan already finished",
          ErrorResponse.PreconditionFailed(show"Trying to finish loan $TestLoanId which has been already finished",
                                           Some(PreconditionFailedReason.LoanAlreadyFinished))
        ),
        StatusMappings.unauthorized
      )
    )

  case class PositiveInt(value: Int) extends AnyVal
  object PositiveInt {
    implicit val codec: PlainCodec[PositiveInt] = Codec.intPlainCodec.validate(Validator.min(1)).map(PositiveInt(_))(_.value)
  }

  val listUsersLoansEndpoint
    : Endpoint[(AuthInputs, Option[PositiveInt], Option[PositiveInt]), ErrorResponse, (List[LoanDataResp], Int, Boolean), Nothing] =
    endpoint
      .summary("List all my loans")
      .tag("loan")
      .get
      .prependIn(authenticationDetails)
      .in(loansPrefix)
      .in(query[Option[PositiveInt]]("page"))
      .in(query[Option[PositiveInt]]("pageSize"))
      .out(jsonBody[List[LoanDataResp]].example(List(TestDataResp)))
      .out(header[Int]("X-Current-Page"))
      .out(header[Boolean]("X-Has-Next-Page"))
      .errorOut(oneOf[ErrorResponse](StatusMappings.unauthorized))

  val listUsersActiveLoansEndpoint: Endpoint[AuthInputs, ErrorResponse, List[LoanDataResp], Nothing] =
    endpoint
      .summary("List all my active loans")
      .tag("loan")
      .get
      .prependIn(authenticationDetails)
      .in(loansPrefix / "active")
      .out(jsonBody[List[LoanDataResp]].example(List(TestDataResp)))
      .errorOut(oneOf[ErrorResponse](StatusMappings.unauthorized))

  val getSingleLoanEndpoint: Endpoint[(AuthInputs, FUUID), ErrorResponse, LoanDataResp, Nothing] =
    endpoint
      .summary("Get loan data")
      .tag("loan")
      .get
      .prependIn(authenticationDetails)
      .in(loansPrefix / path[FUUID]("loanId"))
      .out(jsonBody[LoanDataResp].example(TestDataResp))
      .errorOut(
        oneOf[ErrorResponse](
          StatusMappings.badRequest,
          StatusMappings.forbidden(),
          StatusMappings.unauthorized
        ))

  override val endpoints: List[Endpoint[_, _, _, _]] = List(
    createLoanEndpoint,
    editLoanEndpoint,
    finishLoanEndpoint,
    listUsersLoansEndpoint,
    listUsersActiveLoansEndpoint,
    getSingleLoanEndpoint
  )
}
