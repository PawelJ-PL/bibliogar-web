package com.github.pawelj_pl.bibliogar.api.infrastructure.endpoints

import com.github.pawelj_pl.bibliogar.api.infrastructure.authorization.{AuthInputs, SecuredEndpoint}
import com.github.pawelj_pl.bibliogar.api.infrastructure.config.Config
import com.github.pawelj_pl.bibliogar.api.infrastructure.dto.user.Implicits._
import com.github.pawelj_pl.bibliogar.api.infrastructure.dto.user.{
  ChangePasswordReq,
  Email,
  NickName,
  Password,
  ResetPasswordReq,
  SessionCheckResp,
  SessionDetails,
  UserDataReq,
  UserDataResp,
  UserLoginReq,
  UserRegistrationReq
}
import com.github.pawelj_pl.bibliogar.api.infrastructure.http.{ApiEndpoint, ErrorResponse}
import com.github.pawelj_pl.bibliogar.api.infrastructure.http.Implicits.Fuuid._
import io.chrisdavenport.fuuid.FUUID
import sttp.model.{CookieValueWithMeta, StatusCode}
import sttp.tapir._
import sttp.tapir.Endpoint
import sttp.tapir.json.circe._

class UserEndpoints(val cookieConfig: Config.CookieConfig) extends ApiEndpoint with SecuredEndpoint {
  private val usersPrefix = apiPrefix / "users"
  private val mePrefix = usersPrefix / "me"

  private val exampleUserData = UserDataResp(
    FUUID.fuuid("b25e7694-297e-435d-bac3-99efcee4c49e"),
    "123",
    "some@example.org",
    "Lion"
  )

  val userRegistrationEndpoint: Endpoint[UserRegistrationReq, ErrorResponse, Unit, Nothing] =
    endpoint
      .summary("Register new user")
      .tags(List("user"))
      .post
      .in(usersPrefix)
      .in(jsonBody[UserRegistrationReq]
        .description("User registration request")
        .example(UserRegistrationReq(Email("some@example.org"), NickName("Adam"), Password("password"))))
      .out(statusCode(StatusCode.NoContent))
      .errorOut(
        oneOf[ErrorResponse](
          statusMapping(
            StatusCode.Conflict,
            jsonBody[ErrorResponse.Conflict]
              .example(ErrorResponse.Conflict("Email already registered"))
              .description("User already registered")
          ),
          statusMapping(StatusCode.BadRequest, jsonBody[ErrorResponse.BadRequest].description(Default400Description))
        )
      )

  val signUpConfirmEndpoint: Endpoint[String, ErrorResponse, UserDataResp, Nothing] =
    endpoint
      .summary("Confirm registration")
      .tags(List("user"))
      .get
      .in(apiPrefix / "registrations" / path[String].name("token").description("Registration token").example("abc123"))
      .out(jsonBody[UserDataResp].example(exampleUserData))
      .errorOut(
        oneOf[ErrorResponse](
          statusMapping(
            StatusCode.NotFound,
            jsonBody[ErrorResponse.NotFound]
              .example(ErrorResponse.NotFound("Provided token is not valid"))
              .description("Provided token not found")
          )
        )
      )

  val loginEndpoint: Endpoint[UserLoginReq, ErrorResponse, (UserDataResp, CookieValueWithMeta, String), Nothing] =
    endpoint
      .summary("Login")
      .tags(List("user"))
      .post
      .in(apiPrefix / "auth" / "login")
      .in(jsonBody[UserLoginReq]
        .description("User credentials")
        .example(UserLoginReq(Email("some@example.org"), Password("password"))))
      .out(jsonBody[UserDataResp].example(exampleUserData))
      .out(
        setCookie("session")
          .description("Cookie with session ID")
          .example(CookieValueWithMeta(
            "ABC",
            maxAge = Some(cookieConfig.maxAge.toSeconds),
            path = Some("/"),
            secure = cookieConfig.secure,
            httpOnly = cookieConfig.httpOnly,
            expires = None,
            domain = None,
            otherDirectives = Map.empty
          )))
      .out(header[String]("X-Csrf-Token").description("CSRF token"))
      .errorOut(
        oneOf[ErrorResponse](
          statusMapping(
            StatusCode.Unauthorized,
            jsonBody[ErrorResponse.Unauthorized]
              .example(ErrorResponse.Unauthorized("Invalid credentials"))
              .description("Invalid username or password")
          ),
          statusMapping(StatusCode.BadRequest, jsonBody[ErrorResponse.BadRequest].description(Default400Description))
        )
      )

  val logoutEndpoint: Endpoint[AuthInputs, ErrorResponse, CookieValueWithMeta, Nothing] =
    endpoint
      .summary("Logout")
      .tags(List("user"))
      .post
      .in(authenticationDetails)
      .in(apiPrefix / "auth" / "logout")
      .out(statusCode(StatusCode.NoContent))
      .out(
        setCookie("session")
          .description("Expired cookie with session ID")
          .example(
            CookieValueWithMeta("invalid",
                                maxAge = Some(0),
                                path = Some("/"),
                                expires = None,
                                domain = None,
                                secure = true,
                                httpOnly = true,
                                otherDirectives = Map.empty)))
      .errorOut(UnauthorizedResp)

  val sessionCheckEndpoint: Endpoint[AuthInputs, Unit, SessionCheckResp, Nothing] =
    endpoint
      .summary("Check current session status. Should always return status 200")
      .tags(List("user"))
      .get
      .in(authenticationDetails)
      .in(mePrefix / "session")
      .out(jsonBody[SessionCheckResp].example(
        SessionCheckResp(isValid = true, Some(SessionDetails(FUUID.fuuid("3996b7ba-b88a-417a-8edd-a9ca56480a84"))))))

  val userDataEndpoint: Endpoint[AuthInputs, ErrorResponse, UserDataResp, Nothing] =
    endpoint
      .summary("Read current user's profile data")
      .tags(List("user"))
      .get
      .in(authenticationDetails)
      .in(mePrefix / "data")
      .out(jsonBody[UserDataResp].example(exampleUserData))
      .errorOut(UnauthorizedResp)

  val setUserDataEndpoint: Endpoint[(AuthInputs, UserDataReq), ErrorResponse, UserDataResp, Nothing] =
    endpoint
      .summary("Update current user's profile data")
      .tags(List("user"))
      .put
      .in(authenticationDetails)
      .in(mePrefix / "data")
      .in(jsonBody[UserDataReq].example(UserDataReq(None, NickName("Lion"))))
      .out(jsonBody[UserDataResp].example(exampleUserData))
      .errorOut(
        oneOf[ErrorResponse](
          statusMapping(StatusCode.BadRequest, jsonBody[ErrorResponse.BadRequest].description(Default400Description)),
          statusMapping(StatusCode.Unauthorized,
                        jsonBody[ErrorResponse.Unauthorized].example(UnauthorizedExample).description(Default401Description)),
          statusMapping(StatusCode.PreconditionFailed, jsonBody[ErrorResponse.PreconditionFailed].description("Resource version mismatch"))
        )
      )

  val changePasswordEndpoint: Endpoint[(AuthInputs, ChangePasswordReq), ErrorResponse, Unit, Nothing] =
    endpoint
      .summary("Change password")
      .tags(List("user"))
      .post
      .in(authenticationDetails)
      .in(mePrefix / "password")
      .in(jsonBody[ChangePasswordReq].example(ChangePasswordReq(Password("password"), Password("NeWpa$$w0rd"))))
      .out(statusCode(StatusCode.NoContent))
      .errorOut(
        oneOf(
          statusMapping(StatusCode.Unauthorized, jsonBody[ErrorResponse.Unauthorized].description("The password is invalid")),
          statusMapping(StatusCode.Forbidden, jsonBody[ErrorResponse.Forbidden].description("Operation is forbidden")),
          statusMapping(StatusCode.BadRequest, jsonBody[ErrorResponse.BadRequest].description("Invalid payload")),
          statusMapping(StatusCode.UnprocessableEntity,
                        jsonBody[ErrorResponse.UnprocessableEntity].description("Old and new passwords are equal"))
        )
      )

  val requestPasswordResetEndpoint: Endpoint[String, Unit, Unit, Nothing] =
    endpoint
      .summary("Request token for password reset. Token will be sent to users e-mail")
      .tags(List("user"))
      .get
      .in(apiPrefix / "passwords" / path[String]("email").example(exampleUserData.email).description("Email"))
      .out(statusCode(StatusCode.NoContent))

  val resetPasswordEndpoint: Endpoint[(String, ResetPasswordReq), ErrorResponse, Unit, Nothing] =
    endpoint
      .summary("Reset password.")
      .tags(List("user"))
      .post
      .in(apiPrefix / "passwords" / path[String]("token").description("Reset password token"))
      .in(jsonBody[ResetPasswordReq])
      .out(statusCode(StatusCode.NoContent))
      .errorOut(
        oneOf(
          statusMapping(StatusCode.BadRequest, jsonBody[ErrorResponse.BadRequest].description(Default400Description)),
          statusMapping(StatusCode.NotFound, jsonBody[ErrorResponse.NotFound].description("Provided token not found"))
        )
      )

  override val endpoints: List[Endpoint[_, _, _, _]] = List(
    userRegistrationEndpoint,
    signUpConfirmEndpoint,
    loginEndpoint,
    logoutEndpoint,
    sessionCheckEndpoint,
    userDataEndpoint,
    setUserDataEndpoint,
    changePasswordEndpoint,
    requestPasswordResetEndpoint,
    resetPasswordEndpoint
  )
}
