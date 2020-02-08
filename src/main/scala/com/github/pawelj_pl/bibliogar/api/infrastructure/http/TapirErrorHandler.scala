package com.github.pawelj_pl.bibliogar.api.infrastructure.http

import org.log4s.getLogger
import sttp.model.StatusCode
import sttp.tapir.DecodeResult.InvalidValue
import sttp.tapir._
import sttp.tapir.json.circe._
import sttp.tapir.server.ServerDefaults.{FailureHandling, FailureMessages, ValidationMessages}
import sttp.tapir.server.{DecodeFailureContext, DecodeFailureHandler, DecodeFailureHandling, ServerDefaults}

object TapirErrorHandler {
  private[this] val logger = getLogger

  private val failResponse = (_: StatusCode, message: String) => {
    DecodeFailureHandling.response(jsonBody[ErrorResponse])(
      ErrorResponse.BadRequest(message)
    )
  }

  private def failureMessage: DecodeFailureContext => String = ctx => {
    val base = FailureMessages.failureSourceMessage(ctx.input)

    logError(ctx.failure)

    val detail = ctx.failure match {
      case InvalidValue(errors) if errors.nonEmpty => Some(ValidationMessages.validationErrorsMessage(errors))
      case _                                       => None
    }

    FailureMessages.combineSourceAndDetail(base, detail)
  }

  private def logError(error: DecodeFailure): Unit = {
    error match {
      case DecodeResult.Missing         =>
      case DecodeResult.Multiple(vs)    => logger.warn(s"Decoding error multiple: $vs")
      case DecodeResult.Error(_, error) => logger.warn(s"Error during decoding input: ${error.getMessage}")
      case DecodeResult.Mismatch(_, _)  =>
      case InvalidValue(errors)         => logger.warn(s"Validation failed: $errors")
    }
  }

  val handleDecodeFailure: DecodeFailureHandler = ServerDefaults.decodeFailureHandler.copy(
    respondWithStatusCode = FailureHandling.respondWithStatusCode(_,
                                                                  badRequestOnPathErrorIfPathShapeMatches = true,
                                                                  badRequestOnPathInvalidIfPathShapeMatches = true),
    response = failResponse,
    failureMessage = failureMessage
  )
}
