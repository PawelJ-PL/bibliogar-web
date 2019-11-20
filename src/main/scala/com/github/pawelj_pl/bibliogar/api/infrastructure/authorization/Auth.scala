package com.github.pawelj_pl.bibliogar.api.infrastructure.authorization

import cats.{Monad, ~>}
import cats.data.EitherT
import cats.syntax.bifunctor._
import cats.syntax.either._
import com.github.pawelj_pl.bibliogar.api.domain.user.{
  ApiKeyRepositoryAlgebra,
  AuthData,
  SessionRepositoryAlgebra,
  UserRepositoryAlgebra,
  UserSession
}
import com.github.pawelj_pl.bibliogar.api.infrastructure.http.ErrorResponse
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.{RandomProvider, TimeProvider}
import io.chrisdavenport.fuuid.FUUID
import org.log4s.getLogger

trait Auth[F[_]] {
  def authToSession(authInputs: AuthInputs): F[Either[ErrorResponse, UserSession]]
}

object Auth {
  def apply[F[_]](implicit ev: Auth[F]): Auth[F] = ev

  def create[
    F[_]: Monad: SessionRepositoryAlgebra,
    D[_]: Monad: TimeProvider: RandomProvider: ApiKeyRepositoryAlgebra: UserRepositoryAlgebra
  ](implicit dbToF: D ~> F
  ): Auth[F] = new Auth[F] {
    private[this] val logger = getLogger
    private[this] val safeMethods = Set("GET", "HEAD", "OPTIONS")

    override def authToSession(authInputs: AuthInputs): F[Either[ErrorResponse, UserSession]] =
      authInputs.headerApiKey
        .orElse(authInputs.paramApiKey)
        .map(sessionFromApiKey)
        .orElse(authInputs.sessionCookieVal
          .map(session => sessionFromCookie(session, authInputs)))
        .getOrElse(EitherT.leftT[F, UserSession](AuthError.AuthenticationInputNotProvided).leftWiden[AuthError])
        .leftMap(err => {
          logger.warn(s"Unable to validate user credentials: $err")
          ErrorResponse.Unauthorized("Authentication failed"): ErrorResponse
        })
        .value

    private def sessionFromApiKey(apiKey: String): EitherT[F, AuthError, UserSession] =
      (for {
        key          <- ApiKeyRepositoryAlgebra[D].find(apiKey).toRight(AuthError.ApiKeyNotFound(apiKey)).leftWiden[AuthError]
        isActive     <- EitherT.right[AuthError](key.isActive[D])
        _            <- EitherT.cond[D](isActive, (), AuthError.ApiKeyIsNotActive(key.keyId)).leftWiden[AuthError]
        sessionId    <- EitherT.right[AuthError](RandomProvider[D].randomFuuid)
        now          <- EitherT.right[AuthError](TimeProvider[D].now)
        authData     <- UserRepositoryAlgebra[D].findAuthDataFor(key.userId).toRight(AuthError.AuthDataNotFound(key.userId)).leftWiden[AuthError]
        isUserActive <- EitherT.right[AuthError](authData.isActive[D])
        _            <- EitherT.cond[D](isUserActive, (), AuthError.UserIsNotActive(authData)).leftWiden[AuthError]
      } yield UserSession(sessionId, Some(key.keyId), key.userId, sessionId, now)).mapK(dbToF)

    private def sessionFromCookie(cookie: String, authInputs: AuthInputs): EitherT[F, AuthError, UserSession] =
      for {
        fuuid   <- EitherT.fromOption[F](FUUID.fromStringOpt(cookie), AuthError.UnableToCreateFuuid(cookie)).leftWiden[AuthError]
        session <- EitherT.fromOptionF(SessionRepositoryAlgebra[F].getSession(fuuid), AuthError.SessionNotFound(fuuid)).leftWiden[AuthError]
        _       <- EitherT.fromEither[F](verifyCsrfToken(session, authInputs))
      } yield session

    private def verifyCsrfToken(session: UserSession, authInputs: AuthInputs): Either[AuthError, Unit] = {
      if (safeMethods.contains(authInputs.method.toUpperCase)) ().asRight[AuthError]
      else
        for {
          token <- authInputs.csrfTokens.toRight(AuthError.CsrfTokenNotProvided(session.sessionId))
          _     <- Either.cond(token.eqv(session.csrfToken), (), AuthError.CsrfTokenNotMatch(token, session.csrfToken))
        } yield ()
    }
  }
}

sealed trait AuthError extends Product with Serializable

object AuthError {
  final case class UnableToCreateFuuid(input: String) extends AuthError
  final case class SessionNotFound(sessionId: FUUID) extends AuthError
  final case object AuthenticationInputNotProvided extends AuthError
  final case class ApiKeyNotFound(apiKey: String) extends AuthError
  final case class ApiKeyIsNotActive(keysId: FUUID) extends AuthError
  final case class CsrfTokenNotProvided(sessionId: FUUID) extends AuthError
  final case class CsrfTokenNotMatch(provided: FUUID, expected: FUUID) extends AuthError
  final case class AuthDataNotFound(userId: FUUID) extends AuthError
  final case class UserIsNotActive(authData: AuthData) extends AuthError
}
