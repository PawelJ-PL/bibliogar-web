package com.github.pawelj_pl.bibliogar.api.infrastructure.dto.user

import cats.Monad
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.github.pawelj_pl.bibliogar.api.domain.user.{AuthData, User}
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.{CryptProvider, RandomProvider, TimeProvider}
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.deriveEncoder
import io.circe.generic.semiauto.deriveDecoder

final case class Password(value: String) extends AnyVal
final case class NickName(value: String) extends AnyVal
final case class Email(value: String) extends AnyVal

final case class UserRegistrationReq(email: Email, nickName: NickName, password: Password) {
  def toDomain[F[_]: Monad: RandomProvider: TimeProvider: CryptProvider]: F[(User, AuthData)] =
    for {
      userId <- RandomProvider[F].randomFuuid
      now    <- TimeProvider[F].now
      hashPw <- CryptProvider[F].bcryptHash(password.value)
    } yield
      (
        User(userId, email.value, nickName.value, now, now),
        AuthData(userId, hashPw, confirmed = false, enabled = true, now)
      )
}

object UserRegistrationReq extends PasswordImplicits with EmailImplicits with NickNameImplicits {
  implicit val encode: Encoder[UserRegistrationReq] = deriveEncoder[UserRegistrationReq]
  implicit val decoder: Decoder[UserRegistrationReq] = deriveDecoder[UserRegistrationReq]
}
