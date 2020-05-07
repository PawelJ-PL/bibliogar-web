package com.github.pawelj_pl.bibliogar.api.domain.device

import java.time.Instant

import io.chrisdavenport.fuuid.FUUID

final case class NotificationToken(token: String, deviceId: FUUID, createdAt: Instant, updatedAt: Instant)
