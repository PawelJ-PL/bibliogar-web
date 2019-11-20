package com.github.pawelj_pl.bibliogar.api.infrastructure.authorization

import io.chrisdavenport.fuuid.FUUID

case class AuthInputs(
                       sessionCookieVal: Option[String],
                       csrfTokens: Option[FUUID],
                       method: String,
                       headerApiKey: Option[String],
                       paramApiKey: Option[String])
