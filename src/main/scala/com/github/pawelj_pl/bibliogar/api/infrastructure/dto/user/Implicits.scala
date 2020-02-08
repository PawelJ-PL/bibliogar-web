package com.github.pawelj_pl.bibliogar.api.infrastructure.dto.user

import io.circe.{Decoder, Encoder}
import sttp.tapir.{Schema, SchemaType, Validator}

object Implicits extends PasswordImplicits with NickNameImplicits with EmailImplicits

trait PasswordImplicits {
  implicit val passwordEncode: Encoder[Password] = Encoder.encodeString.contramap(_.value)
  implicit val passwordDecode: Decoder[Password] = Decoder.decodeString.map(Password.apply)
  implicit val schemaForPassword: Schema[Password] = Schema(SchemaType.SString)
  implicit val tapirPasswordValidator: Validator[Password] = Validator.minLength(12).contramap(_.value)
}

trait NickNameImplicits {
  implicit val nicknameEncode: Encoder[NickName] = Encoder.encodeString.contramap(_.value)
  implicit val nicknameDecode: Decoder[NickName] = Decoder.decodeString.map(NickName.apply)
  implicit val schemaForNickname: Schema[NickName] = Schema(SchemaType.SString)
  implicit val tapirNicknameValidator: Validator[NickName] = Validator
    .maxLength(30)
    .and(Validator.custom[String](data => data.trim.length >= 1, "Nickname can't be empty"))
    .contramap(_.value)
}

trait EmailImplicits {
  implicit val emailEncode: Encoder[Email] = Encoder.encodeString.contramap(_.value)
  implicit val emailDecode: Decoder[Email] = Decoder.decodeString.map(Email.apply)
  implicit val schemaForEmail: Schema[Email] = Schema(SchemaType.SString)
  implicit val tapirEmailValidator: Validator[Email] = Validator
    .maxLength(320)
    .and(Validator.pattern[String]("^\\S+@\\S+$"))
    .contramap(_.value)
}