package com.github.pawelj_pl.bibliogar.api.domain.user

import cats.effect.IO
import com.github.pawelj_pl.bibliogar.api.constants.UserConstants
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class AuthDataSpec extends AnyWordSpec with Matchers with UserConstants {
  "isActive" should {
    "return true" when {
      "all properties are valid" in {
        val input = AuthData(TestUserId, "passwordHash", confirmed = true, enabled = true, Now)
        val result = input.isActive[IO].unsafeRunSync()
        result shouldBe true
      }
    }
    "return false" when {
      "is not confirmed" in {
        val input = AuthData(TestUserId, "passwordHash", confirmed = false, enabled = true, Now)
        val result = input.isActive[IO].unsafeRunSync()
        result shouldBe false
      }
      "is not enabled" in {
        val input = AuthData(TestUserId, "passwordHash", confirmed = true, enabled = false, Now)
        val result = input.isActive[IO].unsafeRunSync()
        result shouldBe false
      }
    }
  }
}
