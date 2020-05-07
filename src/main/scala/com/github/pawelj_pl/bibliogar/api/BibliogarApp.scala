package com.github.pawelj_pl.bibliogar.api

import cats.data.NonEmptyList
import cats.effect.{Blocker, Clock, ConcurrentEffect, ContextShift, Sync, Timer}
import cats.syntax.reducible._
import cats.syntax.semigroupk._
import cats.{Parallel, ~>}
import com.github.pawelj_pl.bibliogar.api.domain.book.{BookService, IsbnService}
import com.github.pawelj_pl.bibliogar.api.domain.device.DevicesService
import com.github.pawelj_pl.bibliogar.api.domain.library.LibraryService
import com.github.pawelj_pl.bibliogar.api.domain.loan.LoanService
import com.github.pawelj_pl.bibliogar.api.domain.user.{UserService, UserSession}
import com.github.pawelj_pl.bibliogar.api.infrastructure.authorization.{Auth, AuthInputs}
import com.github.pawelj_pl.bibliogar.api.infrastructure.config.Config
import com.github.pawelj_pl.bibliogar.api.infrastructure.endpoints.{BookEndpoints, DevicesEndpoint, LibraryEndpoints, LoanEndpoints, UserEndpoints}
import com.github.pawelj_pl.bibliogar.api.infrastructure.http.{ApiEndpoint, ErrorResponse, TapirErrorHandler}
import com.github.pawelj_pl.bibliogar.api.infrastructure.messagebus.Message
import com.github.pawelj_pl.bibliogar.api.infrastructure.repositories.{CachedSessionRepository, DoobieApiKeyRepository, DoobieBookRepository, DoobieDevicesRepository, DoobieLibraryRepository, DoobieLoanRepository, DoobieUserRepository, DoobieUserTokenRepository}
import com.github.pawelj_pl.bibliogar.api.infrastructure.routes.{BookRoutes, DevicesRoutes, LibraryRoutes, LoanRoutes, Router, UserRoutes}
import com.github.pawelj_pl.bibliogar.api.infrastructure.swagger.SwaggerRoutes
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.{Correspondence, CryptProvider, MessageComposer, RandomProvider, TimeProvider}
import fs2.concurrent.Topic
import io.chrisdavenport.fuuid.FUUID
import org.http4s.HttpApp
import org.http4s.client.Client
import org.http4s.client.middleware.{Logger => ClientLogger}
import org.http4s.server.middleware.Logger
import org.http4s.syntax.all._
import scalacache.Mode
import scalacache.caffeine.CaffeineCache
import sttp.tapir.server.http4s.Http4sServerOptions

class BibliogarApp[F[_]: Sync: Parallel: ContextShift: ConcurrentEffect: Timer: Mode](
  blocker: Blocker,
  appConfig: Config,
  httpClient: Client[F],
  messageTopic: Topic[F, Message]
)(implicit dbToF: DB ~> F,
  liftF: F ~> DB) {
  private implicit val serverOptions: Http4sServerOptions[F] =
    Http4sServerOptions.default
      .copy(blockingExecutionContext = blocker.blockingContext, decodeFailureHandler = TapirErrorHandler.handleDecodeFailure)

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

  private val loggedClient: Client[F] = ClientLogger(logHeaders = false, logBody = false)(httpClient)

  private implicit val userRepo: DoobieUserRepository = new DoobieUserRepository
  private implicit val userTokenRepo: DoobieUserTokenRepository = new DoobieUserTokenRepository
  private implicit val sessionRepo: CachedSessionRepository[F] = new CachedSessionRepository[F](appConfig.auth.cookie)
  private implicit val apiKeyRepo: DoobieApiKeyRepository = new DoobieApiKeyRepository
  private implicit val devicesRepo: DoobieDevicesRepository = new DoobieDevicesRepository
  private implicit val libraryRepo: DoobieLibraryRepository = new DoobieLibraryRepository
  private implicit val bookRepo: DoobieBookRepository = new DoobieBookRepository
  private implicit val loanRepo: DoobieLoanRepository = new DoobieLoanRepository

  private implicit val userService: UserService[F] = UserService.withDb[F, DB](appConfig.auth)
  implicit val devicesService: DevicesService[F] = DevicesService.withDb[F, DB](appConfig.mobileApp)
  private implicit val libraryService: LibraryService[F] = LibraryService.withDb[F, DB](messageTopic)
  private implicit val isbnService: IsbnService[F] = IsbnService.instance(loggedClient)
  private implicit val bookService: BookService[F] = BookService.withDb[F, DB]()
  private implicit val loanService: LoanService[F] = LoanService.withDb[F, DB](messageTopic)

  private val authToSession: AuthInputs => F[Either[ErrorResponse, UserSession]] =
    Auth.create[F, DB].authToSession

  private val userEndpoints: UserEndpoints = new UserEndpoints(appConfig.auth.cookie)

  private val endpoints: NonEmptyList[ApiEndpoint] =
    NonEmptyList.of(userEndpoints, DevicesEndpoint, LibraryEndpoints, BookEndpoints, LoanEndpoints)

  private val userRoutes: UserRoutes[F] = new UserRoutes[F](userEndpoints, authToSession)
  private val devicesRoutes: DevicesRoutes[F] = new DevicesRoutes[F](authToSession)
  private val libraryRoutes: LibraryRoutes[F] = new LibraryRoutes[F](authToSession)
  private val bookRoutes: BookRoutes[F] = new BookRoutes[F](authToSession)
  private val loanRoutes: LoanRoutes[F] = new LoanRoutes[F](authToSession)

  private val routes: NonEmptyList[Router[F]] = NonEmptyList.of(userRoutes, devicesRoutes, libraryRoutes, bookRoutes, loanRoutes)
  private val swaggerRoutes: SwaggerRoutes[F] = new SwaggerRoutes[F](blocker, endpoints)
  private val apiRoutes = routes.reduceMapK(_.routes)

  val httpApp: HttpApp[F] = Logger.httpApp(logHeaders = false, logBody = false)(
    (
      swaggerRoutes.routes <+>
        apiRoutes
    ).orNotFound
  )
}
