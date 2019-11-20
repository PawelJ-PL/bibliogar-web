package com.github.pawelj_pl.bibliogar.api.infrastructure.http

import cats.syntax.either._
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.SemverWrapper
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.SemverWrapper.of
import com.vdurmont.semver4j.Semver
import io.chrisdavenport.fuuid.FUUID
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor}
import tapir.Codec.PlainCodec
import tapir.{Codec, DecodeResult, Schema, SchemaFor}

object Implicits {
  object Fuuid {
    private def decodeFuuid(string: String): DecodeResult[FUUID] = FUUID.fromString(string) match {
      case Right(v) => DecodeResult.Value(v)
      case Left(f)  => DecodeResult.Error(string, f)
    }
    private def encodeFuuid(fuuid: FUUID): String = fuuid.toString

    implicit val fuuidCodec: PlainCodec[FUUID] = Codec.stringPlainCodecUtf8.mapDecode(decodeFuuid)(encodeFuuid)

    implicit val schemaForFuuid: SchemaFor[FUUID] = SchemaFor(Schema.SString)
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

    implicit val schemaForSemver: SchemaFor[Semver] = SchemaFor(Schema.SString)
  }
}
