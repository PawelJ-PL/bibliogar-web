package com.github.pawelj_pl.bibliogar.api

import cats.data.NonEmptyList
import cats.effect.{Blocker, Clock, ConcurrentEffect, ContextShift, Sync, Timer}
import cats.syntax.reducible._
import cats.syntax.semigroupk._
import cats.~>
import com.github.pawelj_pl.bibliogar.api.domain.device.DevicesService
import com.github.pawelj_pl.bibliogar.api.domain.user.{UserService, UserSession}
import com.github.pawelj_pl.bibliogar.api.infrastructure.authorization.{Auth, AuthInputs}
import com.github.pawelj_pl.bibliogar.api.infrastructure.config.Config
import com.github.pawelj_pl.bibliogar.api.infrastructure.endpoints.{DevicesEndpoint, UserEndpoints}
import com.github.pawelj_pl.bibliogar.api.infrastructure.http.{ApiEndpoint, ErrorResponse}
import com.github.pawelj_pl.bibliogar.api.infrastructure.repositories.{CachedSessionRepository, DoobieApiKeyRepository, DoobieDevicesRepository, DoobieUserRepository, DoobieUserTokenRepository}
import com.github.pawelj_pl.bibliogar.api.infrastructure.routes.{DevicesRoutes, Router, UserRoutes}
import com.github.pawelj_pl.bibliogar.api.infrastructure.swagger.SwaggerRoutes
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.{Correspondence, CryptProvider, MessageComposer, RandomProvider, TimeProvider}
import io.chrisdavenport.fuuid.FUUID
import org.http4s.{HttpApp, Request}
import org.http4s.server.middleware.Logger
import org.http4s.syntax.all._
import org.log4s.getLogger
import scalacache.Mode
import scalacache.caffeine.CaffeineCache
import tapir.json.circe._
import tapir.{DecodeResult, Validator, jsonBody}
import tapir.server.{DecodeFailureHandler, DecodeFailureHandling, ServerDefaults}
import tapir.server.http4s.Http4sServerOptions

class BibliogarApp[F[_]: Sync: ContextShift: ConcurrentEffect: Timer: Mode](blocker: Blocker, appConfig: Config)(implicit dbToF: DB ~> F) {
  private[this] val logger = getLogger

  private def failResponse(msg: String): DecodeFailureHandling =
    DecodeFailureHandling.response(jsonBody[ErrorResponse])(ErrorResponse.BadRequest(msg))

  private val handleDecodeFailure: DecodeFailureHandler[Request[F]] = (req, input, failure) => {
    failure match {
      case DecodeResult.Missing                        =>
      case DecodeResult.Multiple(vs)                   => logger.warn(s"Decoding error multiple: $vs")
      case DecodeResult.Error(_, error)                => logger.warn(s"Error during decoding input: ${error.getMessage}")
      case DecodeResult.Mismatch(_, _)                 =>
      case DecodeResult.InvalidValue(validationErrors) => logger.warn(s"Validation failed: $validationErrors")
    }
    ServerDefaults.decodeFailureHandlerUsingResponse(
      (_, msg) => failResponse(msg),
      badRequestOnPathFailureIfPathShapeMatches = true,
      validationError =>
        validationError.validator match {
          case Validator.Min(value, _)           => s"Value ${validationError.invalidValue} is too small (min value should be $value)"
          case Validator.Max(value, _)           => s"Value ${validationError.invalidValue} is too big (max value should be $value)"
          case Validator.Pattern(value)          => s"Value ${validationError.invalidValue} doesn't match to pattern: $value"
          case Validator.MinLength(value)        => s"Value ${validationError.invalidValue} is too short(min length $value)"
          case Validator.MaxLength(value)        => s"Value ${validationError.invalidValue} is too long(max length $value)"
          case Validator.MinSize(value)          => s"Value ${validationError.invalidValue} has not enough elements (at least $value required)"
          case Validator.MaxSize(value)          => s"Value ${validationError.invalidValue} has to many elements (at most $value required)"
          case Validator.Custom(_, message)      => message
          case Validator.Enum(possibleValues, _) => s"Value ${validationError.invalidValue} is not one of: ${possibleValues.mkString(", ")}"
      }
    )(req, input, failure)
  }

  private implicit val serverOptions: Http4sServerOptions[F] =
    Http4sServerOptions.default.copy(blockingExecutionContext = blocker.blockingContext, decodeFailureHandler = handleDecodeFailure)

  private implicit val clockD: Clock[DB] = Clock.create[DB]
  private implicit val clockF: Clock[F] = Clock.create[F]
  private implicit val timeProviderD: TimeProvider[DB] = TimeProvider.create[DB]
  private implicit val timeProviderF: TimeProvider[F] = TimeProvider.create[F]
  private implicit val cryptProviderD: CryptProvider[DB] = CryptProvider.create[DB](appConfig.auth.cryptRounds)
  private implicit val randomProviderD: RandomProvider[DB] = RandomProvider.create[DB]
  private implicit val randomProviderF: RandomProvider[F] = RandomProvider.create[F]
  private implicit val messageComposerD: MessageComposer[DB] = MessageComposer.create[DB]
  private implicit val correspondenceD: Correspondence[DB] = Correspondence.create[DB](appConfig.correspondence)

  implicit val caffeineSessionCache: CaffeineCache[UserSession] = CaffeineCache[UserSession]
  implicit val caffeineSessionToUserCache: CaffeineCache[Set[FUUID]] = CaffeineCache[Set[FUUID]]

  private implicit val userRepo: DoobieUserRepository = new DoobieUserRepository
  private implicit val userTokenRepo: DoobieUserTokenRepository = new DoobieUserTokenRepository
  private implicit val sessionRepo: CachedSessionRepository[F] = new CachedSessionRepository[F](appConfig.auth.cookie)
  private implicit val apiKeyRepo: DoobieApiKeyRepository = new DoobieApiKeyRepository
  private implicit val devicesRepo: DoobieDevicesRepository = new DoobieDevicesRepository

  private implicit val userService: UserService[F] = UserService.withDb[F, DB](appConfig.auth)
  private implicit val devicesService: DevicesService[F] = DevicesService.withDb[F, DB](appConfig.mobileApp)

  private val authToSession: AuthInputs => F[Either[ErrorResponse, UserSession]] =
    Auth.create[F, DB].authToSession

  private val userEndpoints: UserEndpoints = new UserEndpoints(appConfig.auth.cookie)

  private val endpoints: NonEmptyList[ApiEndpoint] = NonEmptyList.of(userEndpoints, DevicesEndpoint)

  private val userRoutes: UserRoutes[F] = new UserRoutes[F](userEndpoints, authToSession)
  private val devicesRoutes: DevicesRoutes[F] = new DevicesRoutes[F](authToSession)

  private val routes: NonEmptyList[Router[F]] = NonEmptyList.of(userRoutes, devicesRoutes)
  private val swaggerRoutes: SwaggerRoutes[F] = new SwaggerRoutes[F](blocker, endpoints)
  private val apiRoutes = routes.reduceMapK(_.routes)

  val httpApp: HttpApp[F] = Logger.httpApp(logHeaders = false, logBody = false)(
    (
      swaggerRoutes.routes <+>
        apiRoutes
    ).orNotFound
  )
}
