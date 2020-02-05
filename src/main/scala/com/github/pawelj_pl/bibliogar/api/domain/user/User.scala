package com.github.pawelj_pl.bibliogar.api.domain.user

import java.time.Instant

import io.chrisdavenport.fuuid.FUUID

final case class User(id: FUUID, email: String, nickName: String, createdAt: Instant, updatedAt: Instant) {
  val version: String = updatedAt.toEpochMilli.toString
}
