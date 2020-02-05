package com.github.pawelj_pl.bibliogar.api.itconstants

import com.github.pawelj_pl.bibliogar.api.domain.user.{ApiKey, AuthData, KeyType, TokenType, User, UserToken}

trait UserConstants extends CommonConstants with ResourcesIdentifiers {

  final val TestUserEmail = "some@example.org"
  final val TestNickname = "Dog"
  final val ExampleUser = User(TestUserId, TestUserEmail, TestNickname, Now, Now)

  final val ExamplePasswordHash = "$2a$15$gCikzez9ZW8nHPGCNpWEfuHH.i8AGiR.jo6RVnaky2EC4c62Wb7ti"
  final val ExampleAuthData = AuthData(TestUserId, ExamplePasswordHash, confirmed = true, enabled = true, Now)

  final val TestTokenValue = "someToken"
  final val ExampleUserToken = UserToken(TestTokenValue, TestUserId, TokenType.Registration, Now, Now)

  final val TestKeyValue = "secretKey"
  final val TestKeyDescription = "Some API key"
  final val ExampleApiKey = ApiKey(TestApiKeyId, TestKeyValue, TestUserId, None, KeyType.User, Some(TestKeyDescription), enabled = true, None, Now, Now)
}
