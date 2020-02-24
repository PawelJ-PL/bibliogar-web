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
  final val ExampleId3 = FUUID.fuuid("7a00e9a0-c766-4eec-b838-62805ff2c0e9")
  final val ExampleId4 = FUUID.fuuid("22c37633-ba26-40f7-b8e8-1a01beb5e63a")
  final val ExampleId5 = FUUID.fuuid("965874ed-e5bf-43ee-a9a8-b8ad6948964c")
  final val ExampleId6 = FUUID.fuuid("66bb80d1-aad7-4115-ab8d-e789dfbc2394")
  final val ExampleId7 = FUUID.fuuid("054d5e5f-c896-421b-91b6-ae6e375eb910")
  final val ExampleId8 = FUUID.fuuid("978a96d7-eea6-4417-b09f-dee8a0da0e53")
  final val ExampleId9 = FUUID.fuuid("baf37971-226e-4280-a9af-06b1844fb550")
  final val ExampleId10 = FUUID.fuuid("231652b2-d13e-48f7-bebb-fbc31b14b332")
  final val ExampleId11 = FUUID.fuuid("646207b1-fc24-452f-8f77-f19d4dea8c0a")
  final val ExampleId12 = FUUID.fuuid("2f9d50cb-e670-4364-a2f9-713c1f3c6b62")
  final val ExampleId13 = FUUID.fuuid("dc9cc20a-f3f6-49b6-87b0-4c75bc77dc94")
}
