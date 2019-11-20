package com.github.pawelj_pl.bibliogar.api.constants

import java.time.Instant

import io.chrisdavenport.fuuid.FUUID

trait CommonConstants {
  final val NowMilis = 1552854779888L
  final val Now = Instant.ofEpochMilli(NowMilis)

  final val FirstRandomUuid = FUUID.fuuid("1c3aea3c-956d-467a-aba3-51e9e0000001")
  final val SecondRandomUuid = FUUID.fuuid("1c3aea3c-956d-467a-aba3-51e9e0000002")

  final val ExampleId1 = FUUID.fuuid("4ee2ee3d-fe4a-439f-89cb-269e2245a989")
  final val ExampleId2 = FUUID.fuuid("39069af8-36c3-4f50-b7ab-6fe9951acd62")
}