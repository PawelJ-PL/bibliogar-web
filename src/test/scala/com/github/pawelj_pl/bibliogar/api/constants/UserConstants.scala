package com.github.pawelj_pl.bibliogar.api.constants

import com.github.pawelj_pl.bibliogar.api.domain.user.{ApiKey, KeyType, User, UserSession}
import io.chrisdavenport.fuuid.FUUID

trait UserConstants extends CommonConstants with ResourcesIdentifiers {
  final val TestEmail = "some@example.org"
  final val TestNickname = "Fox"
  final val ExampleUser = User(TestUserId, TestEmail, TestNickname, Now, Now)

  final val ExamplePassword = "MySecretPassword"

  final val TestSessionId = FUUID.fuuid("0c25733e-23fe-45ca-b4d8-0c47e7275a65")
  final val TestCsrfToken = FUUID.fuuid("b0962f78-b317-4244-abd0-74306196599d")
  final val ExampleUserSession = UserSession(TestSessionId, None, TestUserId, TestCsrfToken, Now.minusSeconds(120))

  final val TestKeySecret = "secretApiKey"
  final val ExampleApiKey = ApiKey(TestApiKeyId, TestKeySecret, ExampleUser.id, Some(TestDeviceId), KeyType.User, None, enabled = true, None, Now, Now)
}
