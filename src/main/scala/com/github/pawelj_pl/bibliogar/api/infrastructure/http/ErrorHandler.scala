package com.github.pawelj_pl.bibliogar.api.infrastructure.http

import com.github.pawelj_pl.bibliogar.api.{AppError, CommonError, DeviceError, LibraryError, LoanError, UserError}
import org.log4s.getLogger

trait ErrorHandler {
  private[this] val logger = getLogger

  def errorToResponse(err: AppError): ErrorResponse = err match {
    case e: UserError    => userErrorToResponse(e)
    case e: DeviceError  => deviceErrorToResponse(e)
    case e: LibraryError => libraryErrorToResponse(e)
    case e: LoanError    => loanErrorToResponse(e)
  }
  def userErrorToResponse(err: UserError): ErrorResponse = err match {
    case UserError.EmailAlreadyRegistered(_) =>
      logger.warn(err.message)
      ErrorResponse.Conflict("Email already registered")
    case UserError.UserIdNotFound(_) =>
      logger.warn(err.message)
      ErrorResponse.Forbidden("Not allowed")
    case UserError.UserEmailNotFound(_) =>
      logger.warn(err.message)
      ErrorResponse.Forbidden("Not allowed")
    case UserError.TokenNotFound(_, _) =>
      logger.warn(err.message)
      ErrorResponse.NotFound("Provided token is not valid")
    case UserError.OutdatedToken(_, _, _) =>
      logger.warn(err.message)
      ErrorResponse.NotFound("Provided token is not valid")
    case UserError.UserNotActive(_) =>
      logger.warn(err.message)
      ErrorResponse.Forbidden("Not allowed")
    case UserError.InvalidCredentials(_) =>
      logger.warn(err.message)
      ErrorResponse.Unauthorized("Invalid credentials")
    case _: UserError.NewAndOldPasswordAreEqual.type =>
      ErrorResponse.UnprocessableEntity("The new password is the same than old one")
    case CommonError.ResourceVersionDoesNotMatch(_, _) =>
      logger.warn(err.message)
      ErrorResponse.PreconditionFailed(err.message, Some(PreconditionFailedReason.ResourceErrorDoesNotMatch))
    case CommonError.DbForeignKeyViolation(_) =>
      ErrorResponse.BadRequest("Invalid data")
  }

  def deviceErrorToResponse(err: DeviceError): ErrorResponse = err match {
    case DeviceError.ApiKeyIsNotDeviceType(_, _) =>
      logger.warn(err.message)
      ErrorResponse.PreconditionFailed("Not device API key", Some(PreconditionFailedReason.InvalidApiKeyType))
    case DeviceError.DeviceNotOwnedByUser(_, _) =>
      logger.warn(err.message)
      ErrorResponse.Forbidden("Not allowed")
    case DeviceError.ApiKeyNotRelatedToAnyDevice(_) =>
      logger.warn(err.message)
      ErrorResponse.PreconditionFailed("API key is not assigned to any device", Some(PreconditionFailedReason.NotAssignedApiKey))
    case DeviceError.DeviceIdNotFound(_) =>
      logger.warn(err.message)
      ErrorResponse.Forbidden("Not allowed")
  }

  def libraryErrorToResponse(err: LibraryError): ErrorResponse = err match {
    case LibraryError.LibraryNotOwnedByUser(_, _) =>
      logger.warn(err.message)
      ErrorResponse.Forbidden("Not allowed")
    case LibraryError.LibraryIdNotFound(_) =>
      logger.warn(err.message)
      ErrorResponse.Forbidden("Not allowed")
    case CommonError.ResourceVersionDoesNotMatch(_, _) =>
      logger.warn(err.message)
      ErrorResponse.PreconditionFailed(err.message, Some(PreconditionFailedReason.ResourceErrorDoesNotMatch))
    case CommonError.DbForeignKeyViolation(_) =>
      ErrorResponse.BadRequest("Invalid data")
  }

  def loanErrorToResponse(err: LoanError): ErrorResponse = err match {
    case LibraryError.LibraryNotOwnedByUser(_, _) =>
      logger.warn(err.message)
      ErrorResponse.Forbidden("Not allowed")
    case LibraryError.LibraryIdNotFound(_) =>
      logger.warn(err.message)
      ErrorResponse.Forbidden("Not allowed")
    case LoanError.BooksLimitExceeded(_, _) =>
      logger.warn(err.message)
      ErrorResponse.PreconditionFailed("Books limit exceeded", Some(PreconditionFailedReason.BooksLimitExceeded))
    case LoanError.LoanNotFound(_) =>
      logger.warn(err.message)
      ErrorResponse.Forbidden("Not allowed")
    case LoanError.LoanNotOwnedByUser(_, _) =>
      logger.warn(err.message)
      ErrorResponse.Forbidden("Not allowed")
    case CommonError.ResourceVersionDoesNotMatch(_, _) =>
      logger.warn(err.message)
      ErrorResponse.PreconditionFailed(err.message, Some(PreconditionFailedReason.ResourceErrorDoesNotMatch))
    case LoanError.LoanAlreadyFinished(_) =>
      logger.warn(err.message)
      ErrorResponse.PreconditionFailed(err.message, Some(PreconditionFailedReason.LoanAlreadyFinished))
    case CommonError.DbForeignKeyViolation(_) =>
      ErrorResponse.BadRequest("Invalid data")
  }
}
