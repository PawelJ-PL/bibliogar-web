package com.github.pawelj_pl.bibliogar.api.infrastructure.routes

import cats.effect.{ContextShift, Sync}
import cats.syntax.applicative._
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.semigroupk._
import com.github.pawelj_pl.bibliogar.api.domain.device.DevicesService
import com.github.pawelj_pl.bibliogar.api.domain.user.{SessionRepositoryAlgebra, UserSession}
import com.github.pawelj_pl.bibliogar.api.infrastructure.authorization.AuthInputs
import com.github.pawelj_pl.bibliogar.api.infrastructure.dto.devices.{AppCompatibilityReq, DeviceRegistrationReq, DeviceRegistrationResp}
import com.github.pawelj_pl.bibliogar.api.infrastructure.http.{ErrorResponse, ResponseUtils}
import org.http4s.HttpRoutes
import org.log4s.getLogger
import tapir.model.SetCookieValue
import tapir.server.http4s.Http4sServerOptions
import tapir.server.http4s._

class DevicesRoutes[F[_]: Sync: ContextShift: Http4sServerOptions: DevicesService: SessionRepositoryAlgebra](
  authToSession: AuthInputs => F[Either[ErrorResponse, UserSession]])
    extends Router[F]
    with ResponseUtils {
  import com.github.pawelj_pl.bibliogar.api.infrastructure.endpoints.DevicesEndpoint._

  private[this] val logger = getLogger

  private def checkApiCompatibility(dto: AppCompatibilityReq): F[Either[ErrorResponse, Unit]] = {
    DevicesService[F]
      .isAppCompatibleWithApi(dto.appVersion)
      .map(isCompatible =>
        if (isCompatible) ((): Unit).asRight[ErrorResponse] else ErrorResponse.PreconditionFailed("Incompatible version").asLeft[Unit])
  }

  private def registerDevice(
    session: UserSession,
    dto: DeviceRegistrationReq
  ): F[Either[ErrorResponse, (SetCookieValue, DeviceRegistrationResp)]] =
    for {
      resp <- DevicesService[F]
        .registerDevice(session.userId, dto)
        .map {
          case (device, key) =>
            (SetCookieValue("invalid", maxAge = Some(0)), DeviceRegistrationResp(device.device_id, key.apiKey)).asRight[ErrorResponse]
        }
      _ <- SessionRepositoryAlgebra[F] deleteSession (session.sessionId)
    } yield resp

  private def unregisterDevice(session: UserSession): F[Either[ErrorResponse, Unit]] = session.apiKeyId match {
    case None =>
      logger.warn("Api key doesn't exist in session")
      ErrorResponse.PreconditionFailed("No API key found in session").asLeft[Unit].pure[F].widen
    case Some(keyId) =>
      emptyResponseOrError(DevicesService[F].unregisterDeviceAs(session.userId, keyId))
  }

  override val routes: HttpRoutes[F] =
    checkApiCompatibilityEndpoint.toRoutes(checkApiCompatibility) <+>
      registerDeviceEndpoint.toRoutes(authToSession.andThenFirstE((registerDevice _).tupled)) <+>
      unregisterDeviceEndpoint.toRoutes(authToSession.andThenFirstE(unregisterDevice))
}
