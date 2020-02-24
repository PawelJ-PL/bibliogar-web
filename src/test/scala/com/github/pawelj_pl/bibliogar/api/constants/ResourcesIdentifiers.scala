package com.github.pawelj_pl.bibliogar.api.constants

import io.chrisdavenport.fuuid.FUUID

trait ResourcesIdentifiers {
  final val TestUserId = FUUID.fuuid("1c3aea3c-956d-467a-aba3-51e9e0000001")
  final val TestApiKeyId = FUUID.fuuid("f579b5ae-6b87-47ec-aa2a-e75e753fab28")
  final val TestDeviceId = FUUID.fuuid("ba5a68e4-734d-42f7-ae87-6b7f32669b5c")
  final val TestLibraryId = FUUID.fuuid("a1a29b55-dc60-4fa6-b75e-47e4f7c27f25")
  final val TestBookId = FUUID.fuuid("0de7f7f4-4ef5-4828-a753-75fd42e8aef4")
  final val TestLoanId = FUUID.fuuid("fef3819b-a21d-4842-b4b3-65b80692f90f")
}
