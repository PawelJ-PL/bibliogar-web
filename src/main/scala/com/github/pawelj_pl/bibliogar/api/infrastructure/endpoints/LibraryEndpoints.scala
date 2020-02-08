package com.github.pawelj_pl.bibliogar.api.infrastructure.endpoints

import com.github.pawelj_pl.bibliogar.api.domain.library.LoanDurationUnit
import com.github.pawelj_pl.bibliogar.api.infrastructure.authorization.{AuthInputs, SecuredEndpoint}
import com.github.pawelj_pl.bibliogar.api.infrastructure.dto.library.{DurationValue, Implicits, LibraryDataReq, LibraryDataResp, LibraryName}
import com.github.pawelj_pl.bibliogar.api.infrastructure.http.{ApiEndpoint, ErrorResponse}
import com.github.pawelj_pl.bibliogar.api.infrastructure.http.Implicits.Fuuid._
import io.chrisdavenport.fuuid.FUUID
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.json.circe._
import sttp.tapir.codec.enumeratum._

object LibraryEndpoints extends ApiEndpoint with SecuredEndpoint with Implicits {
  private val librariesPrefix = apiPrefix / "libraries"

  private final val ExampleLibraryDataReq =
    LibraryDataReq(None, LibraryName("My awesome library"), DurationValue(2), LoanDurationUnit.Month)
  private final val ExampleLibraryDataResp =
    LibraryDataResp("foo", FUUID.fuuid("9e34652f-6921-4e2b-82af-de959fb9871a"), "My awesome library", 2, LoanDurationUnit.Month)

  val createLibraryEndpoint: Endpoint[(AuthInputs, LibraryDataReq), ErrorResponse, LibraryDataResp, Nothing] =
    endpoint
      .summary("Create library")
      .tags(List("library"))
      .post
      .in(authenticationDetails)
      .in(librariesPrefix)
      .in(jsonBody[LibraryDataReq].example(ExampleLibraryDataReq))
      .out(jsonBody[LibraryDataResp].example(ExampleLibraryDataResp))
      .errorOut(BadRequestOrUnauthorizedResp)

  val getUsersLibrariesEndpoint: Endpoint[AuthInputs, ErrorResponse, List[LibraryDataResp], Nothing] =
    endpoint
      .summary("Get users libraries")
      .tags(List("library"))
      .get
      .in(authenticationDetails)
      .in(librariesPrefix)
      .out(jsonBody[List[LibraryDataResp]].example(List(ExampleLibraryDataResp)))
      .errorOut(UnauthorizedResp)

  val getSingleLibraryEndpoint: Endpoint[(AuthInputs, FUUID), ErrorResponse, LibraryDataResp, Nothing] =
    endpoint
      .summary("Get single library")
      .tags(List("library"))
      .get
      .in(authenticationDetails)
      .in(librariesPrefix / path[FUUID]("libraryId"))
      .out(jsonBody[LibraryDataResp].example(ExampleLibraryDataResp))
      .errorOut(
        oneOf[ErrorResponse](
          StatusMappings.badRequest,
          StatusMappings.unauthorized,
          StatusMappings.forbidden(),
          StatusMappings.preconditionFailed()
        )
      )

  val removeLibraryEndpoint: Endpoint[(AuthInputs, FUUID), ErrorResponse, Unit, Nothing] =
    endpoint
      .summary("Remove library")
      .tags(List("library"))
      .delete
      .in(authenticationDetails)
      .in(librariesPrefix / path[FUUID]("libraryId"))
      .out(statusCode(StatusCode.NoContent).description("Library removed"))
      .errorOut(
        oneOf(
          StatusMappings.unauthorized,
          StatusMappings.forbidden()
        )
      )

  val editLibraryEndpoint: Endpoint[(AuthInputs, FUUID, LibraryDataReq), ErrorResponse, LibraryDataResp, Nothing] =
    endpoint
      .summary("Edit library")
      .tags(List("library"))
      .put
      .in(authenticationDetails)
      .in(librariesPrefix / path[FUUID]("libraryId"))
      .in(jsonBody[LibraryDataReq].example(ExampleLibraryDataReq))
      .out(jsonBody[LibraryDataResp].example(ExampleLibraryDataResp))
      .errorOut(
        oneOf(
          StatusMappings.badRequest,
          StatusMappings.unauthorized,
          StatusMappings.forbidden(),
          StatusMappings.preconditionFailed()
        )
      )

  override val endpoints: List[Endpoint[_, _, _, _]] = List(
    createLibraryEndpoint,
    getUsersLibrariesEndpoint,
    getSingleLibraryEndpoint,
    removeLibraryEndpoint,
    editLibraryEndpoint
  )
}
