package com.github.pawelj_pl.bibliogar.api.infrastructure.http

import cats.syntax.either._
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.SemverWrapper
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.SemverWrapper.of
import com.vdurmont.semver4j.Semver
import io.chrisdavenport.fuuid.FUUID
import io.circe.syntax._
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor}
import org.http4s.Uri
import sttp.tapir.Codec.PlainCodec
import sttp.tapir.{Codec, DecodeResult, Schema, SchemaType, Validator}

object Implicits {
  object Fuuid {
    private def decodeFuuid(string: String): DecodeResult[FUUID] = FUUID.fromString(string) match {
      case Right(v) => DecodeResult.Value(v)
      case Left(f)  => DecodeResult.Error(string, f)
    }
    private def encodeFuuid(fuuid: FUUID): String = fuuid.toString

    implicit val fuuidCodec: PlainCodec[FUUID] = Codec.stringPlainCodecUtf8.mapDecode(decodeFuuid)(encodeFuuid)

    implicit val schemaForFuuid: Schema[FUUID] = Schema(SchemaType.SString)
  }

  object Semver {
    implicit val SemverCirceEncoder: Encoder[Semver] = Encoder.encodeString.contramap(_.getValue)
    implicit val SemverCirceDecoder: Decoder[Semver] = (c: HCursor) =>
      for {
        str <- c.as[String]
        ver <- of(str).leftMap(err => DecodingFailure.fromThrowable(err, List()))
      } yield ver

    private val decodeSemver: String => DecodeResult[Semver] = string =>
      SemverWrapper.of(string) match {
        case Right(value) => DecodeResult.Value(value)
        case Left(error)  => DecodeResult.Error(string, error)
    }
    implicit val semverCodec: PlainCodec[Semver] = Codec.stringPlainCodecUtf8.mapDecode(decodeSemver)(_.getValue)

    implicit val schemaForSemver: Schema[Semver] = Schema(SchemaType.SString)
  }

  object Http4sUri {
    implicit val uriDecoder: Decoder[Uri] = Decoder[String].emap(Uri.fromString(_).leftMap(_.toString))
    implicit val uriEncoder: Encoder[Uri] = Encoder.instance(_.renderString.asJson)

    implicit val schemaForUri: Schema[Uri] = Schema(SchemaType.SString)
    implicit val tapirValidatorForUri: Validator[Uri] =
      Validator.custom[String](uri => Uri.fromString(uri).isRight, "Not valid Uri").contramap(_.renderString)
  }
}
