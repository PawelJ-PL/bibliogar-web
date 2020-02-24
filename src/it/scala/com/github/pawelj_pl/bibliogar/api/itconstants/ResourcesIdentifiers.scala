package com.github.pawelj_pl.bibliogar.api.itconstants

import io.chrisdavenport.fuuid.FUUID

trait ResourcesIdentifiers {
  final val TestUserId = FUUID.fuuid("f430bb72-74ae-431e-96e5-b47c527be9d5")
  final val TestApiKeyId = FUUID.fuuid("34c44100-48c8-4d53-a82f-698bfbce6741")

  final val TestDeviceId = FUUID.fuuid("ba5a68e4-734d-42f7-ae87-6b7f32669b5c")

  final val TestLibraryId = FUUID.fuuid("c6231ef3-7f7a-4bc9-b6f4-0776fc673235")

  final val TestBookId = FUUID.fuuid("c42b7079-65b6-4644-9ed5-0acbc3368c22")

  final val TestLoanId = FUUID.fuuid("a978651c-11bd-4ccd-a302-1eb9841ac3b6")
}
