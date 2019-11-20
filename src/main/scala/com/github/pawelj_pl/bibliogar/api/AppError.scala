package com.github.pawelj_pl.bibliogar.api

import java.time.Instant

import com.github.pawelj_pl.bibliogar.api.domain.user.{AuthData, KeyType, TokenType}
import io.chrisdavenport.fuuid.FUUID

sealed trait AppError extends Product with Serializable {
  def message: String
}

sealed trait UserError extends AppError

object UserError {
  final case class EmailAlreadyRegistered(email: String) extends UserError {
    override def message: String = s"Email $email already registered"
  }

  final case class UserIdNotFound(id: FUUID) extends UserError {
    override def message: String = s"User with id $id not found"
  }

  final case class UserEmailNotFound(email: String) extends UserError {
    override def message: String = s"User with email $email not found"
  }

  final case class TokenNotFound(token: String, tokenType: TokenType) extends UserError {
    override def message: String = s"Token $token with type $tokenType not found"
  }

  final case class OutdatedToken(token: String, tokenType: TokenType, createdAt: Instant) extends UserError {
    override def message: String = s"Token $token with type $tokenType created at $createdAt is outdated"
  }

  final case class UserNotActive(authData: AuthData) extends UserError {
    override def message: String = s"User ${authData.userId} is not active"
  }

  final case class InvalidCredentials(userId: FUUID) extends UserError {
    override def message: String = s"Invalid credentials for user $userId"
  }

  final case object NewAndOldPasswordAreEqual extends UserError {
    override def message: String = "New and old passwords are equal"
  }
}

sealed trait DeviceError extends AppError

object DeviceError {
  final case class ApiKeyIsNotDeviceType(keyId: FUUID, keyType: KeyType)  extends DeviceError {
    override def message: String = s"API key $keyId is not device type but $keyType"
  }

  final case class DeviceNotOwnedByUser(deviceId: FUUID, userId: FUUID) extends DeviceError {
    override def message: String = s"Device $deviceId is not owned by user $userId"
  }

  final case class ApiKeyNotRelatedToAnyDevice(keyId: FUUID) extends DeviceError {
    override def message: String = s"Key $keyId is not related to any device"
  }

  final case class DeviceIdNotFound(deviceId: FUUID) extends DeviceError {
    override def message: String = s"Device with id $deviceId not found"
  }
}