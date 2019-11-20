package com.github.pawelj_pl.bibliogar.api.infrastructure.routes

import org.http4s.HttpRoutes

trait Router[F[_]] {
  val routes: HttpRoutes[F]
}
