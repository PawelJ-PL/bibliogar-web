package com.github.pawelj_pl.bibliogar.api.infrastructure.endpoints

import com.github.pawelj_pl.bibliogar.api.domain.device.DeviceDescription
import com.github.pawelj_pl.bibliogar.api.infrastructure.authorization.{AuthInputs, SecuredEndpoint}
import com.github.pawelj_pl.bibliogar.api.infrastructure.dto.devices.{AppCompatibilityReq, DeviceRegistrationReq, DeviceRegistrationResp}
import com.github.pawelj_pl.bibliogar.api.infrastructure.http.{ApiEndpoint, ErrorResponse}
import com.github.pawelj_pl.bibliogar.api.infrastructure.http.Implicits.Fuuid._
import com.github.pawelj_pl.bibliogar.api.infrastructure.http.Implicits.Semver._
import com.vdurmont.semver4j.Semver
import io.chrisdavenport.fuuid.FUUID
import sttp.model.{CookieValueWithMeta, StatusCode}
import sttp.tapir._
import sttp.tapir.Endpoint
import sttp.tapir.json.circe._

object DevicesEndpoint extends ApiEndpoint with SecuredEndpoint {
  private val devicesPrefix = apiPrefix / "devices"

  private final val ExampleDeviceRegistrationReq =
    DeviceRegistrationReq("dd96dec43fb81c97", Some("abcXYZ"), DeviceDescription("xiaomi", "goldfish", "Unknown"))

  val checkApiCompatibilityEndpoint: Endpoint[AppCompatibilityReq, ErrorResponse, Unit, Nothing] =
    endpoint
      .summary("Check whether application version is compatible with API")
      .tags(List("device"))
      .post
      .in(devicesPrefix / "compatibility")
      .in(jsonBody[AppCompatibilityReq].example(AppCompatibilityReq(new Semver("2.0.8"))))
      .out(statusCode(StatusCode.Ok))
      .errorOut(
        oneOf[ErrorResponse](
          statusMapping(StatusCode.BadRequest, jsonBody[ErrorResponse.BadRequest].description(Default400Description)),
          statusMapping(StatusCode.PreconditionFailed, jsonBody[ErrorResponse.PreconditionFailed].description("Incompatible version"))
        )
      )

  val registerDeviceEndpoint
    : Endpoint[(AuthInputs, DeviceRegistrationReq), ErrorResponse, (CookieValueWithMeta, DeviceRegistrationResp), Nothing] =
    endpoint
      .summary("Pair device with user")
      .tags(List("device"))
      .post
      .in(authenticationDetails)
      .in(devicesPrefix)
      .in(jsonBody[DeviceRegistrationReq].example(ExampleDeviceRegistrationReq))
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
      .out(jsonBody[DeviceRegistrationResp].example(DeviceRegistrationResp(FUUID.fuuid("b24766de-26b2-4a45-bdff-3458d2ac53af"), "abc123")))
      .errorOut(BadRequestOrUnauthorizedResp)

  val unregisterDeviceEndpoint: Endpoint[AuthInputs, ErrorResponse, Unit, Nothing] =
    endpoint
      .summary("Unpair device and user")
      .tags(List("device"))
      .delete
      .in(authenticationDetails)
      .in(devicesPrefix / "this")
      .out(statusCode(StatusCode.NoContent))
      .errorOut(
        oneOf[ErrorResponse](
          statusMapping(StatusCode.Unauthorized, jsonBody[ErrorResponse.Unauthorized].description(Default401Description)),
          statusMapping(StatusCode.PreconditionFailed, jsonBody[ErrorResponse.PreconditionFailed].description("No related device found"))
        )
      )

  override val endpoints: List[Endpoint[_, _, _, _]] = List(checkApiCompatibilityEndpoint, registerDeviceEndpoint, unregisterDeviceEndpoint)
}
