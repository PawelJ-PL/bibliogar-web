package com.github.pawelj_pl.bibliogar.api.domain.user

import java.time.Instant
import java.time.temporal.TemporalAmount

import cats.~>
import cats.data.{EitherT, OptionT}
import cats.effect.Sync
import cats.instances.string._
import cats.syntax.apply._
import cats.syntax.bifunctor._
import cats.syntax.eq._
import cats.syntax.functor._
import com.github.pawelj_pl.bibliogar.api.{CommonError, UserError}
import com.github.pawelj_pl.bibliogar.api.UserError.UserIdNotFound
import com.github.pawelj_pl.bibliogar.api.infrastructure.config.Config
import com.github.pawelj_pl.bibliogar.api.infrastructure.dto.user.{ChangePasswordReq, UserDataReq, UserLoginReq, UserRegistrationReq}
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.{
  Correspondence,
  CryptProvider,
  MessageComposer,
  RandomProvider,
  TimeProvider
}
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.timeSyntax._
import io.chrisdavenport.fuuid.FUUID
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

trait UserService[F[_]] {
  def registerUser(dto: UserRegistrationReq): EitherT[F, UserError, User]
  def confirmRegistration(token: String): EitherT[F, UserError, User]
  def verifyCredentials(credentials: UserLoginReq): EitherT[F, UserError, User]
  def getUser(userId: FUUID): OptionT[F, User]
  def updateUser(userId: FUUID, dto: UserDataReq): EitherT[F, UserError, User]
  def changePassword(dto: ChangePasswordReq, userId: FUUID): EitherT[F, UserError, AuthData]
  def requestPasswordReset(email: String): OptionT[F, UserToken]
  def resetPassword(token: String, newPassword: String): EitherT[F, UserError, AuthData]
}

object UserService {
  def apply[F[_]](implicit ev: UserService[F]): UserService[F] = ev

  def withDb[
    F[_],
    D[_]: Sync: TimeProvider: CryptProvider: RandomProvider: UserRepositoryAlgebra: UserTokenRepositoryAlgebra: MessageComposer: Correspondence
  ](authConfig: Config.AuthConfig
  )(implicit dbToF: D ~> F
  ): UserService[F] =
    new UserService[F] {
      private val logD: Logger[D] = Slf4jLogger.getLogger[D]

      private val activationTtl: TemporalAmount = authConfig.registration.ttl.toTemporal
      private val resetPasswordTtl: TemporalAmount = authConfig.resetPassword.ttl.toTemporal

      override def registerUser(dto: UserRegistrationReq): EitherT[F, UserError, User] =
        (for {
          (user, authData) <- EitherT.right[UserError](dto.toDomain[D])
          _ <- UserRepositoryAlgebra[D]
            .findUserByEmail(user.email)
            .map(_ => UserError.EmailAlreadyRegistered(user.email): UserError)
            .toLeft((): Unit)
          savedUser   <- EitherT.right[UserError](logD.info(s"Creating new user with id ${user.id}") *> UserRepositoryAlgebra[D].create(user))
          _           <- EitherT.right[UserError](UserRepositoryAlgebra[D].create(authData))
          randomToken <- EitherT.right[UserError](RandomProvider[D].secureRandomString(6))
          userToken = UserToken(randomToken, savedUser.id, TokenType.Registration, Instant.ofEpochSecond(0), Instant.ofEpochSecond(0))
          _ <- EitherT.right[UserError](UserTokenRepositoryAlgebra[D].create(userToken))
          messageValues = Map("token" -> userToken.token)
          message <- EitherT.right[UserError](MessageComposer[D].generateMessage("newRegistration", messageValues))
          _       <- EitherT.right[UserError](Correspondence[D].sendMessage(savedUser.email, "Rejestracja w systemie Bibliogar", message))
          _       <- EitherT.right[UserError](logD.info(s"Registration message has been sent to user related to registration ${savedUser.id}"))
        } yield savedUser).mapK(dbToF)

      override def confirmRegistration(token: String): EitherT[F, UserError, User] =
        (for {
          savedToken <- validateAndDeleteToken(token, TokenType.Registration, activationTtl)
          (user, authData) <- UserRepositoryAlgebra[D]
            .findUserWithAuthById(savedToken.account)
            .toRight[UserError](UserError.UserIdNotFound(savedToken.account))
          _ <- EitherT.right[UserError](logD.info(s"User registration ${user.id} confirmed"))
          _ <- UserRepositoryAlgebra[D]
            .update(authData.copy(confirmed = true))
            .toRight[UserError](UserError.UserIdNotFound(authData.userId))
        } yield user).mapK(dbToF)

      private def validateAndDeleteToken(token: String, tokenType: TokenType, ttl: TemporalAmount): EitherT[D, UserError, UserToken] =
        for {
          savedToken <- UserTokenRepositoryAlgebra[D].get(token, tokenType).toRight[UserError](UserError.TokenNotFound(token, tokenType))
          _          <- EitherT.right(logD.info(s"Removing token $token created at ${savedToken.createdAt} for user ${savedToken.account}"))
          _          <- EitherT.right(UserTokenRepositoryAlgebra[D].delete(token))
          now        <- EitherT.right(TimeProvider[D].now)
          _ <- EitherT
            .cond[D](savedToken.createdAt.plus(ttl).isAfter(now), (), UserError.OutdatedToken(token, tokenType, savedToken.createdAt))
            .leftWiden[UserError]
        } yield savedToken

      override def verifyCredentials(credentials: UserLoginReq): EitherT[F, UserError, User] =
        (for {
          (user, auth) <- UserRepositoryAlgebra[D]
            .findUserWithAuthByEmail(credentials.email.value)
            .orElse(performDummyComputation)
            .toRight[UserError](UserError.UserEmailNotFound(credentials.email.value))
          authResult <- EitherT.liftF(auth.checkPassword(credentials.password.value))
          _          <- EitherT.cond(authResult, (), UserError.InvalidCredentials(auth.userId)).leftWiden[UserError]
          active     <- EitherT.liftF(auth.isActive)
          _          <- EitherT.cond(active, (), UserError.UserNotActive(auth)).leftWiden[UserError]
        } yield user).mapK(dbToF)

      private def performDummyComputation: OptionT[D, (User, AuthData)] = {
        OptionT(CryptProvider[D].bcryptCheckPw("secret", authConfig.dummyPasswordHash).map(_ => None))
      }

      override def getUser(userId: FUUID): OptionT[F, User] = UserRepositoryAlgebra[D].findUserById(userId).mapK(dbToF)

      override def updateUser(userId: FUUID, dto: UserDataReq): EitherT[F, UserError, User] =
        (for {
          savedUser <- UserRepositoryAlgebra[D].findUserById(userId).toRight(UserError.UserIdNotFound(userId)).leftWiden[UserError]
          _ <- EitherT.cond(
            dto.version.forall(v => v === savedUser.updatedAt.asVersion),
            (),
            CommonError.ResourceVersionDoesNotMatch(savedUser.updatedAt.asVersion, dto.version.getOrElse(""))
          )
          updatedUser <- UserRepositoryAlgebra[D]
            .update(savedUser.copy(nickName = dto.nickName.value))
            .toRight(UserIdNotFound(userId))
            .leftWiden[UserError]
        } yield updatedUser).mapK(dbToF)

      override def changePassword(dto: ChangePasswordReq, userId: FUUID): EitherT[F, UserError, AuthData] =
        (for {
          _                     <- EitherT.cond[D](dto.newPassword != dto.oldPassword, (), UserError.NewAndOldPasswordAreEqual).leftWiden[UserError]
          auth                  <- UserRepositoryAlgebra[D].findAuthDataFor(userId).toRight(UserError.UserIdNotFound(userId)).leftWiden[UserError]
          isActive              <- EitherT.right[UserError](auth.isActive)
          _                     <- EitherT.cond[D](isActive, (), UserError.UserNotActive(auth)).leftWiden[UserError]
          pwdVerificationResult <- EitherT.right(CryptProvider[D].bcryptCheckPw(dto.oldPassword.value, auth.passwordHash))
          _                     <- EitherT.cond[D](pwdVerificationResult, (), UserError.InvalidCredentials(userId)).leftWiden[UserError]
          newHash               <- EitherT.right[UserError](CryptProvider[D].bcryptHash(dto.newPassword.value))
          _                     <- EitherT.right[UserError](logD.info(s"Changing password for user $userId"))
          updatedAuth <- UserRepositoryAlgebra[D]
            .update(auth.copy(passwordHash = newHash))
            .toRight(UserError.UserIdNotFound(userId))
            .leftWiden[UserError]
        } yield updatedAuth).mapK(dbToF)

      override def requestPasswordReset(email: String): OptionT[F, UserToken] =
        (for {
          user        <- UserRepositoryAlgebra[D].findUserByEmail(email)
          randomToken <- OptionT.liftF(RandomProvider[D].secureRandomString(6))
          userToken = UserToken(randomToken, user.id, TokenType.PasswordReset, Instant.ofEpochMilli(0), Instant.ofEpochMilli(0))
          _          <- OptionT.liftF(logD.info(s"Generated new password reset token for user ${user.id}"))
          savedToken <- OptionT.liftF(UserTokenRepositoryAlgebra[D].create(userToken))
          messageValues = Map("token" -> userToken.token)
          message <- OptionT.liftF(MessageComposer[D].generateMessage("resetPassword", messageValues))
          _       <- OptionT.liftF(Correspondence[D].sendMessage(user.email, "Bibliogar - reset hasła", message))
          _       <- OptionT.liftF(logD.info(s"Password reset message has been sent to user ${user.id}"))
        } yield savedToken).mapK(dbToF)

      override def resetPassword(token: String, newPassword: String): EitherT[F, UserError, AuthData] =
        (for {
          savedToken <- validateAndDeleteToken(token, TokenType.PasswordReset, resetPasswordTtl)
          authData <- UserRepositoryAlgebra[D]
            .findAuthDataFor(savedToken.account)
            .toRight(UserError.UserIdNotFound(savedToken.account))
            .leftWiden[UserError]
          isActive <- EitherT.right[UserError](authData.isActive)
          _        <- EitherT.cond[D](isActive, (), UserError.UserNotActive(authData)).leftWiden[UserError]
          _        <- EitherT.right[UserError](logD.info(s"Resetting password for user ${savedToken.account}"))
          hash     <- EitherT.right[UserError](CryptProvider[D].bcryptHash(newPassword))
          _        <- UserRepositoryAlgebra[D].update(authData.copy(passwordHash = hash)).toRight(UserError.UserIdNotFound(authData.userId))
          _        <- EitherT.right[UserError](logD.info(s"Removing all reset password tokens for user ${savedToken.account}"))
          _        <- EitherT.right[UserError](UserTokenRepositoryAlgebra[D].deleteByAccountAndType(savedToken.account, TokenType.PasswordReset))
        } yield authData).mapK(dbToF)
    }
}
