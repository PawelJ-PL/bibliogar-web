package com.github.pawelj_pl.bibliogar.api.infrastructure.http

import tapir._
import tapir.model.StatusCodes
import tapir.json.circe._

trait ApiEndpoint extends ErrorHandler {
  import ApiEndpoint._

  val endpoints: List[Endpoint[_, _, _, _]]

  val apiPrefix: EndpointInput[Unit] = "api" / latestApiVersion

  final val Default400Description = "Invalid payload"
  final val Default401Description = "User not logged in"

  final val UnauthorizedExample = ErrorResponse.Unauthorized("Authentication failed")

  final val UnauthorizedResp: EndpointOutput.OneOf[ErrorResponse] = oneOf[ErrorResponse](
    statusMapping(StatusCodes.Unauthorized,
                  jsonBody[ErrorResponse.Unauthorized].example(UnauthorizedExample).description(Default401Description)))
  final val BadRequestOrUnauthorizedResp: EndpointOutput.OneOf[ErrorResponse] = oneOf(
    statusMapping(StatusCodes.BadRequest, jsonBody[ErrorResponse.BadRequest].description(Default400Description)),
    statusMapping(StatusCodes.Unauthorized,
                  jsonBody[ErrorResponse.Unauthorized].example(UnauthorizedExample).description(Default401Description))
  )
}

object ApiEndpoint {
  val latestApiVersion = "v1"
}
