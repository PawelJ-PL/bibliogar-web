package com.github.pawelj_pl.bibliogar.api.itconstants

import java.time.Instant

import io.chrisdavenport.fuuid.FUUID

trait CommonConstants {
  final val Now = Instant.ofEpochSecond(1572984160)

  final val ExampleId1 = FUUID.fuuid("2e7b2814-f6dd-426b-8e7c-892745235a98")
  final val ExampleId2 = FUUID.fuuid("d43b6427-1fb1-4c04-9777-1551d43ce578")
  final val ExampleId3 = FUUID.fuuid("b2a812f1-e61c-44b5-85bc-1b73e65b0dbc")
}
