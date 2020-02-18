package com.github.pawelj_pl.bibliogar.api.infrastructure.http

import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.json.circe._

trait ApiEndpoint extends ErrorHandler {
  import ApiEndpoint._

  val endpoints: List[Endpoint[_, _, _, _]]

  val apiPrefix: EndpointInput[Unit] = "api" / latestApiVersion

  final val Default400Description = "Invalid payload"
  final val Default401Description = "User not logged in"

  final val UnauthorizedExample = ErrorResponse.Unauthorized("Authentication failed")

  final val UnauthorizedResp: EndpointOutput.OneOf[ErrorResponse] = oneOf[ErrorResponse](StatusMappings.unauthorized)
  final val BadRequestOrUnauthorizedResp: EndpointOutput.OneOf[ErrorResponse] =
    oneOf(StatusMappings.badRequest, StatusMappings.unauthorized)

  object StatusMappings {
    val unauthorized: EndpointOutput.StatusMapping[ErrorResponse.Unauthorized] = statusMapping(
      StatusCode.Unauthorized,
      jsonBody[ErrorResponse.Unauthorized].example(UnauthorizedExample).description(Default401Description))
    val badRequest: EndpointOutput.StatusMapping[ErrorResponse.BadRequest] =
      statusMapping(StatusCode.BadRequest, jsonBody[ErrorResponse.BadRequest].description(Default400Description))
    def preconditionFailed(
      description: String = "Resource version mismatch"
    ): EndpointOutput.StatusMapping[ErrorResponse.PreconditionFailed] =
      statusMapping(StatusCode.PreconditionFailed, jsonBody[ErrorResponse.PreconditionFailed].description(description))
    def forbidden(description: String = "Operation is forbidden"): EndpointOutput.StatusMapping[ErrorResponse.Forbidden] =
      statusMapping(StatusCode.Forbidden, jsonBody[ErrorResponse.Forbidden].description(description))
    def notFound(description: String): EndpointOutput.StatusMapping[ErrorResponse.NotFound] =
      statusMapping(StatusCode.NotFound, jsonBody[ErrorResponse.NotFound].description(description))
  }
}

object ApiEndpoint {
  val latestApiVersion = "v1"
}
