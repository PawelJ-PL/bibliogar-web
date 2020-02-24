package com.github.pawelj_pl.bibliogar.api

import java.time.Instant

import cats.instances.int._
import cats.instances.option._
import cats.instances.string._
import cats.syntax.show._
import com.github.pawelj_pl.bibliogar.api.domain.library.Library
import com.github.pawelj_pl.bibliogar.api.domain.user.{AuthData, KeyType, TokenType}
import com.github.pawelj_pl.bibliogar.api.infrastructure.repositories.DbError
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.timeInstances._
import io.chrisdavenport.fuuid.FUUID

sealed trait AppError extends Product with Serializable {
  def message: String
}

sealed trait UserError extends AppError

sealed trait CommonError extends UserError with LibraryError with LoanError

object CommonError {
  final case class ResourceVersionDoesNotMatch(current: String, provided: String) extends CommonError {
    override def message: String = show"Attempting to update resource from version $provided, but current version is $current"
  }

  final case class DbForeignKeyViolation(err: DbError.ForeignKeyViolation) extends CommonError {
    override def message: String = "Database foreign key violation"
  }
}

object UserError {
  final case class EmailAlreadyRegistered(email: String) extends UserError {
    override def message: String = show"Email $email already registered"
  }

  final case class UserIdNotFound(id: FUUID) extends UserError {
    override def message: String = show"User with id $id not found"
  }

  final case class UserEmailNotFound(email: String) extends UserError {
    override def message: String = show"User with email $email not found"
  }

  final case class TokenNotFound(token: String, tokenType: TokenType) extends UserError {
    override def message: String = show"Token $token with type $tokenType not found"
  }

  final case class OutdatedToken(token: String, tokenType: TokenType, createdAt: Instant) extends UserError {
    override def message: String = show"Token $token with type $tokenType created at $createdAt is outdated"
  }

  final case class UserNotActive(authData: AuthData) extends UserError {
    override def message: String = show"User ${authData.userId} is not active"
  }

  final case class InvalidCredentials(userId: FUUID) extends UserError {
    override def message: String = show"Invalid credentials for user $userId"
  }

  final case object NewAndOldPasswordAreEqual extends UserError {
    override def message: String = "New and old passwords are equal"
  }
}

sealed trait DeviceError extends AppError

object DeviceError {
  final case class ApiKeyIsNotDeviceType(keyId: FUUID, keyType: KeyType) extends DeviceError {
    override def message: String = show"API key $keyId is not device type but $keyType"
  }

  final case class DeviceNotOwnedByUser(deviceId: FUUID, userId: FUUID) extends DeviceError {
    override def message: String = show"Device $deviceId is not owned by user $userId"
  }

  final case class ApiKeyNotRelatedToAnyDevice(keyId: FUUID) extends DeviceError {
    override def message: String = show"Key $keyId is not related to any device"
  }

  final case class DeviceIdNotFound(deviceId: FUUID) extends DeviceError {
    override def message: String = show"Device with id $deviceId not found"
  }
}

sealed trait LibraryError extends AppError

object LibraryError {
  final case class LibraryNotOwnedByUser(libraryId: FUUID, userId: FUUID) extends LibraryError with LoanError {
    override def message: String = show"Library $libraryId is not owned by user $userId"
  }
  final case class LibraryIdNotFound(libraryId: FUUID) extends LibraryError with LoanError {
    override def message: String = show"Library with id $libraryId not found"
  }
}

sealed trait LoanError extends AppError

object LoanError {
  final case class BooksLimitExceeded(library: Library, actualSize: Int) extends LoanError {
    override def message: String = show"Books limit for library ${library.id} is ${library.booksLimit}, but requested $actualSize books"
  }
  final case class LoanNotFound(loanId: FUUID) extends LoanError {
    override def message: String = show"Loan with id $loanId not found"
  }
  final case class LoanNotOwnedByUser(loanId: FUUID, userId: FUUID) extends LoanError {
    override def message: String = show"Loan $loanId is not owned by user $userId"
  }
  final case class LoanAlreadyFinished(loanId: FUUID) extends LoanError {
    override def message: String = show"Trying to finish loan $loanId which has been already finished"
  }
}
