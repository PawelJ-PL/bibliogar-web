package com.github.pawelj_pl.bibliogar.api.infrastructure.authorization

import com.github.pawelj_pl.bibliogar.api.infrastructure.http.Implicits.Fuuid._
import io.chrisdavenport.fuuid.FUUID
import org.http4s.headers.Cookie
import sttp.tapir._

trait SecuredEndpoint {
  val authenticationDetails: EndpointInput[AuthInputs] =
    extractFromRequest[Option[String]](r => r.header("Cookie").flatMap(extractCookieFromHeader))
      .and(header[Option[FUUID]]("X-Csrf-Token").description("CSRF token (not necessary for API key authentication)"))
      .and(extractFromRequest[String](r => r.method.method))
      .and(auth.apiKey(header[Option[String]]("X-Api-Key")))
      .and(auth.apiKey(query[Option[String]]("api-key")))
      .mapTo(AuthInputs)

  def extractCookieFromHeader(cookieHeaderValue: String): Option[String] =
    Cookie
      .parse(cookieHeaderValue)
      .toOption
      .flatMap(cookies => cookies.values.find(c => c.name == "session").map(c => c.content))
}