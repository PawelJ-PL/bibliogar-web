package com.github.pawelj_pl.bibliogar.api.infrastructure.swagger

import java.util.Properties

import cats.data.NonEmptyList
import cats.effect.{Blocker, ContextShift, Resource, Sync}
import cats.instances.list._
import cats.syntax.flatMap._
import cats.syntax.reducible._
import cats.syntax.semigroupk._
import com.github.pawelj_pl.bibliogar.api.infrastructure.http.ApiEndpoint
import org.http4s.{HttpRoutes, StaticFile}
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Location
import org.http4s.implicits._
import sttp.tapir.docs.openapi._
import sttp.tapir.openapi.circe.yaml._
import sttp.tapir.openapi.OpenAPI

class SwaggerRoutes[F[_]: Sync: ContextShift](blocker: Blocker, apiEndpoints: NonEmptyList[ApiEndpoint]) extends Http4sDsl[F] {
  private val doc: OpenAPI = apiEndpoints.reduceMap(_.endpoints).toOpenAPI("Bibliogar", ApiEndpoint.latestApiVersion)

  private val swaggerYaml: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root / "swagger.yaml" => Ok(doc.toYaml)
  }

  private val swaggerUiVersion: F[String] = Resource
    .fromAutoCloseable(Sync[F].delay(getClass.getResourceAsStream("/META-INF/maven/org.webjars/swagger-ui/pom.properties")))
    .map(versionInputStream => {
      val props = new Properties()
      props.load(versionInputStream)
      props.getProperty("version")
    })
    .use(Sync[F].delay(_))

  private val swaggerUi: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root / "doc" / file =>
      swaggerUiVersion.flatMap(
        version =>
          StaticFile
            .fromResource(s"/META-INF/resources/webjars/swagger-ui/$version/$file", blocker)
            .getOrElseF(NotFound())
      )
  }

  private val indexRedirectAddress = uri"/doc/index.html?url=/swagger.yaml&defaultModelsExpandDepth=0&&docExpansion=none"

  private val swaggerUiIndex: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root / "doc" =>
      PermanentRedirect(Location(indexRedirectAddress))
    case GET -> Root / "doc" / "" =>
      PermanentRedirect(Location(indexRedirectAddress))
  }

  val routes: HttpRoutes[F] = swaggerYaml <+> swaggerUiIndex <+> swaggerUi
}
