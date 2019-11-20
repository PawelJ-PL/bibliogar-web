package com.github.pawelj_pl.bibliogar.api.domain.user

import java.time.Instant

import io.chrisdavenport.fuuid.FUUID

final case class UserSession(sessionId: FUUID, apiKeyId: Option[FUUID], userId: FUUID, csrfToken: FUUID, createdAt: Instant)
