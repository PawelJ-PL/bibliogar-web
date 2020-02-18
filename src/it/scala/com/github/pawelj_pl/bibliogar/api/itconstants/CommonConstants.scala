package com.github.pawelj_pl.bibliogar.api.itconstants

import java.time.Instant

import io.chrisdavenport.fuuid.FUUID

trait CommonConstants {
  final val Now = Instant.ofEpochSecond(1572984160)

  final val ExampleId1 = FUUID.fuuid("2e7b2814-f6dd-426b-8e7c-892745235a98")
  final val ExampleId2 = FUUID.fuuid("d43b6427-1fb1-4c04-9777-1551d43ce578")
  final val ExampleId3 = FUUID.fuuid("b2a812f1-e61c-44b5-85bc-1b73e65b0dbc")
  final val ExampleId4 = FUUID.fuuid("4fc18ef0-f963-49b4-a37f-a913500d611b")
  final val ExampleId5 = FUUID.fuuid("520df5d6-3c1c-4328-bd43-5e5d28b1c014")
  final val ExampleId6 = FUUID.fuuid("68740d74-e630-471c-959f-885ad8eee32e")
  final val ExampleId7 = FUUID.fuuid("798eecad-fa33-4f0c-bba9-44b3b9e78f8d")
  final val ExampleId8 = FUUID.fuuid("0ccabab2-510e-43aa-9147-04e885e2ba35")
  final val ExampleId9 = FUUID.fuuid("a73abdc6-7942-457c-9932-002b91317e4c")
  final val ExampleId10 = FUUID.fuuid("579b722e-9662-4ee3-8b39-601005f358f0")
  final val ExampleId11 = FUUID.fuuid("7a60008c-2f6c-422c-9327-e9dd0b3d4565")
}
