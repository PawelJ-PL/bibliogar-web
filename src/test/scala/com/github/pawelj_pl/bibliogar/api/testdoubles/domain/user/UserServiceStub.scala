package com.github.pawelj_pl.bibliogar.api.testdoubles.domain.user

import cats.data.{EitherT, OptionT}
import cats.mtl.MonadState
import cats.{Applicative, Functor}
import cats.syntax.either._
import cats.syntax.functor._
import com.github.pawelj_pl.bibliogar.api.UserError
import com.github.pawelj_pl.bibliogar.api.constants.UserConstants
import com.github.pawelj_pl.bibliogar.api.domain.user.{AuthData, TokenType, User, UserService, UserToken}
import com.github.pawelj_pl.bibliogar.api.infrastructure.dto.user.{ChangePasswordReq, UserDataReq, UserLoginReq, UserRegistrationReq}
import io.chrisdavenport.fuuid.FUUID

object UserServiceStub extends UserConstants {
  private val authData = AuthData(ExampleUser.id, "someHash", confirmed = true, enabled = true, Now)
  private val userToken = UserToken("xyz", ExampleUser.id, TokenType.PasswordReset, Now, Now)

  final case class UserServiceState(
    userOrError: Either[UserError, User] = ExampleUser.asRight[UserError],
    maybeUser: Option[User] = Some(ExampleUser),
    authDataOrError: Either[UserError, AuthData] = authData.asRight[UserError],
    maybeUserToken: Option[UserToken] = Some(userToken))

  def instance[F[_]: Functor: Applicative](implicit S: MonadState[F, UserServiceState]): UserService[F] = new UserService[F] {
    override def registerUser(dto: UserRegistrationReq): EitherT[F, UserError, User] = EitherT(S.get.map(_.userOrError))

    override def confirmRegistration(token: String): EitherT[F, UserError, User] = EitherT(S.get.map(_.userOrError))

    override def verifyCredentials(credentials: UserLoginReq): EitherT[F, UserError, User] = EitherT(S.get.map(_.userOrError))

    override def getUser(userId: FUUID): OptionT[F, User] = OptionT(S.get.map(_.maybeUser))

    override def updateUser(userId: FUUID, dto: UserDataReq): EitherT[F, UserError, User] = EitherT(S.get.map(_.userOrError))

    override def changePassword(dto: ChangePasswordReq, userId: FUUID): EitherT[F, UserError, AuthData] =
      EitherT(S.get.map(_.authDataOrError))

    override def requestPasswordReset(email: String): OptionT[F, UserToken] = OptionT(S.get.map(_.maybeUserToken))

    override def resetPassword(token: String, newPassword: String): EitherT[F, UserError, AuthData] = EitherT(S.get.map(_.authDataOrError))
  }
}
