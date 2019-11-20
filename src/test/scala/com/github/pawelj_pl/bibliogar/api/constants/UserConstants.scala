package com.github.pawelj_pl.bibliogar.api.constants

import com.github.pawelj_pl.bibliogar.api.domain.user.{ApiKey, KeyType, User, UserSession}
import com.github.pawelj_pl.bibliogar.api.testdoubles.domain.device.DevicesServiceStub.ExampleDevice
import io.chrisdavenport.fuuid.FUUID

trait UserConstants extends CommonConstants {
  final val TestUserId = FUUID.fuuid("1c3aea3c-956d-467a-aba3-51e9e0000001")
  final val TestEmail = "some@example.org"
  final val TestNickname = "Fox"
  final val ExampleUser = User(TestUserId, TestEmail, TestNickname, Now, Now)

  final val ExamplePassword = "MySecretPassword"

  final val TestSessionId = FUUID.fuuid("0c25733e-23fe-45ca-b4d8-0c47e7275a65")
  final val TestCsrfToken = FUUID.fuuid("b0962f78-b317-4244-abd0-74306196599d")
  final val ExampleUserSession = UserSession(TestSessionId, None, TestUserId, TestCsrfToken, Now.minusSeconds(120))

  final val TestKeyId = FUUID.fuuid("f579b5ae-6b87-47ec-aa2a-e75e753fab28")
  final val TestKeySecret = "secretApiKey"
  final val ExampleApiKey = ApiKey(TestKeyId, TestKeySecret, ExampleUser.id, Some(ExampleDevice.device_id), KeyType.User, None, enabled = true, None, Now, Now)
}
