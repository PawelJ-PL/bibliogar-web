package com.github.pawelj_pl.bibliogar.api.infrastructure.routes
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

import cats.data.EitherT
import cats.effect.{ContextShift, Sync}
import cats.syntax.either._
import cats.syntax.functor._
import cats.syntax.semigroupk._
import com.github.pawelj_pl.bibliogar.api.UserError
import com.github.pawelj_pl.bibliogar.api.domain.user.{AuthData, SessionRepositoryAlgebra, UserService, UserSession}
import com.github.pawelj_pl.bibliogar.api.infrastructure.authorization.AuthInputs
import com.github.pawelj_pl.bibliogar.api.infrastructure.dto.user.{
  ChangePasswordReq,
  ResetPasswordReq,
  SessionCheckResp,
  SessionDetails,
  UserDataReq,
  UserDataResp,
  UserLoginReq,
  UserRegistrationReq
}
import com.github.pawelj_pl.bibliogar.api.infrastructure.endpoints.UserEndpoints
import com.github.pawelj_pl.bibliogar.api.infrastructure.http.{ErrorResponse, ResponseUtils}
import org.http4s.HttpRoutes
import org.log4s.getLogger
import tapir.model.SetCookieValue
import tapir.server.http4s._

class UserRoutes[F[_]: Sync: ContextShift: Http4sServerOptions: UserService: SessionRepositoryAlgebra](
  userEndpoints: UserEndpoints,
  authToSession: AuthInputs => F[Either[ErrorResponse, UserSession]])
    extends Router[F]
    with ResponseUtils {
  import userEndpoints._

  private[this] val logger = getLogger

  private def registerUser(req: UserRegistrationReq): F[Either[ErrorResponse, Unit]] =
    emptyResponseOrError(UserService[F].registerUser(req))

  private def signUpConfirmation(token: String): F[Either[ErrorResponse, UserDataResp]] =
    responseOrError(UserService[F].confirmRegistration(token), UserDataResp.fromDomain)

  private def login(credentials: UserLoginReq): F[Either[ErrorResponse, (UserDataResp, SetCookieValue, String)]] =
    (for {
      user    <- UserService[F].verifyCredentials(credentials).leftMap(mapAuthErrorToErrorResponse)
      session <- EitherT.right[ErrorResponse](SessionRepositoryAlgebra[F].createFor(user.id, None))
      cookieValue = SetCookieValue(session.sessionId.toString(),
                                   maxAge = Some(cookieConfig.maxAge.toSeconds),
                                   path = Some("/"),
                                   secure = cookieConfig.secure,
                                   httpOnly = cookieConfig.httpOnly)
      csrfToken = session.csrfToken.toString()
    } yield (UserDataResp.fromDomain(user), cookieValue, csrfToken)).value

  private def mapAuthErrorToErrorResponse(err: UserError): ErrorResponse = {
    logger.warn(err.message)
    ErrorResponse.Unauthorized("Invalid credentials"): ErrorResponse
  }

  private def logout(userSession: UserSession): F[Either[ErrorResponse, SetCookieValue]] =
    SessionRepositoryAlgebra[F]
      .deleteSession(userSession.sessionId)
      .map(_ => SetCookieValue("invalid", maxAge = Some(0), path = Some("/")).asRight[ErrorResponse])

  private def checkCurrentSession(auth: AuthInputs): F[Either[Unit, SessionCheckResp]] =
    EitherT(authToSession(auth))
      .map(session => SessionCheckResp(isValid = true, Some(SessionDetails(session.csrfToken))))
      .getOrElse(SessionCheckResp(isValid = false, None))
      .map(_.asRight[Unit])

  private def getUserData(session: UserSession): F[Either[ErrorResponse, UserDataResp]] =
    UserService[F]
      .getUser(session.userId)
      .toRight(ErrorResponse.NotFound("User not found"): ErrorResponse)
      .map(UserDataResp.fromDomain)
      .value

  private def setUserData(session: UserSession, req: UserDataReq): F[Either[ErrorResponse, UserDataResp]] =
    UserService[F]
      .updateUser(session.userId, req)
      .leftMap(userErrorToResponse)
      .map(UserDataResp.fromDomain)
      .value

  private def changePassword(userSession: UserSession, dto: ChangePasswordReq): F[Either[ErrorResponse, Unit]] =
    UserService[F]
      .changePassword(dto, userSession.userId)
      .leftMap(userErrorToResponse)
      .flatMap(deleteAllUserSessions)
      .value

  private def deleteAllUserSessions(authData: AuthData): EitherT[F, ErrorResponse, Unit] =
    EitherT.right[ErrorResponse](
      SessionRepositoryAlgebra[F]
        .deleteAllUserSessions(authData.userId)
        .map(removed => logger.info(s"Removed following sessions: ${removed.mkString(",")}")))

  private def requestPasswordReset(email: String): F[Either[Unit, Unit]] =
    UserService[F]
      .requestPasswordReset(URLDecoder.decode(email, StandardCharsets.UTF_8.toString)) //toString for compatibility with Java 8
      .void
      .getOrElse(
        logger.warn(s"Reset password procedure failed. User ${URLDecoder.decode(email, StandardCharsets.UTF_8.toString)} not found"))
      .map(_ => ().asRight[Unit])

  private def resetPassword(token: String, dto: ResetPasswordReq): F[Either[ErrorResponse, Unit]] = {
    UserService[F]
      .resetPassword(token, dto.password.value)
      .leftMap {
        case UserError.UserNotActive(authData) =>
          logger.warn(s"User ${authData.userId} is not active. Unable to reset password")
          ErrorResponse.NotFound("Provided token is not valid")
        case e => userErrorToResponse(e)
      }
      .flatMap(deleteAllUserSessions)
      .value
  }

  override val routes: HttpRoutes[F] =
    userRegistrationEndpoint.toRoutes(registerUser) <+>
      signUpConfirmEndpoint.toRoutes(signUpConfirmation) <+>
      loginEndpoint.toRoutes(login) <+>
      logoutEndpoint.toRoutes(authToSession.andThenFirstE(logout)) <+>
      sessionCheckEndpoint.toRoutes(checkCurrentSession) <+>
      userDataEndpoint.toRoutes(authToSession.andThenFirstE(getUserData)) <+>
      setUserDataEndpoint.toRoutes(authToSession.andThenFirstE((setUserData _).tupled)) <+>
      changePasswordEndpoint.toRoutes(authToSession.andThenFirstE((changePassword _).tupled)) <+>
      requestPasswordResetEndpoint.toRoutes(requestPasswordReset) <+>
      resetPasswordEndpoint.toRoutes((resetPassword _).tupled)
}
