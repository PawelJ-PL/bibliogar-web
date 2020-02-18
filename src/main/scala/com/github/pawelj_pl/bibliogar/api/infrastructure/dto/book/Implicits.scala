package com.github.pawelj_pl.bibliogar.api.infrastructure.dto.book

import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.deriveUnwrappedDecoder
import io.circe.generic.extras.semiauto.deriveUnwrappedEncoder
import org.http4s.Uri
import sttp.tapir.Codec.PlainCodec
import sttp.tapir.{Codec, DecodeResult, Schema, SchemaType, Validator}

trait Implicits extends IsbnImplicits with TitleImplicits with AuthorsImplicits with CoverImplicits

trait IsbnImplicits {
  private val isbnPattern = "^(97(8|9))?\\d{9}(\\d|X)$"

  implicit val isbnEncoder: Encoder[Isbn] = deriveUnwrappedEncoder[Isbn]
  implicit val isbnDecoder: Decoder[Isbn] = deriveUnwrappedDecoder[Isbn]
  implicit val schemaForIsbn: Schema[Isbn] = Schema(SchemaType.SString)
  implicit val tapirValidatorIsbn: Validator[Isbn] = Validator
    .pattern[String](isbnPattern)
    .contramap(_.value)

  private val decodeIsbn: String => DecodeResult[Isbn] = string =>
    if (string.matches(isbnPattern)) DecodeResult.Value(Isbn(string))
    else DecodeResult.Error(string, new RuntimeException("Not valid ISBN"))

  implicit val isbnCodec: PlainCodec[Isbn] = Codec.stringPlainCodecUtf8.mapDecode(decodeIsbn)(_.value)
}

trait TitleImplicits {
  implicit val titleEncoder: Encoder[Title] = deriveUnwrappedEncoder[Title]
  implicit val titleDecoder: Decoder[Title] = deriveUnwrappedDecoder[Title]
  implicit val schemaForTitle: Schema[Title] = Schema(SchemaType.SString)
  implicit val tapirValidatorTitle: Validator[Title] = Validator
    .maxLength(200)
    .and(Validator.custom[String](data => data.trim.length >= 1, "Title can't be empty"))
    .contramap(_.value)
}

trait AuthorsImplicits {
  implicit val authorsEncoder: Encoder[Authors] = deriveUnwrappedEncoder[Authors]
  implicit val authorsDecoder: Decoder[Authors] = deriveUnwrappedDecoder[Authors]
  implicit val schemaForAuthors: Schema[Authors] = Schema(SchemaType.SString)
  implicit val tapirValidatorAuthors: Validator[Authors] = Validator
    .maxLength(200)
    .and(Validator.custom[String](data => data.trim.length >= 1, "Authors can't be empty"))
    .contramap(_.value)
}

trait CoverImplicits {
  import com.github.pawelj_pl.bibliogar.api.infrastructure.http.Implicits.Http4sUri._

  implicit val coverEncoder: Encoder[Cover] = deriveUnwrappedEncoder[Cover]
  implicit val coverDecoder: Decoder[Cover] = deriveUnwrappedDecoder[Cover]
  implicit val schemaForCover: Schema[Cover] = Schema(SchemaType.SString)
  implicit val tapirValidatorCover: Validator[Cover] = Validator
    .custom[String](uri => Uri.fromString(uri).isRight, "Not valid Uri")
    .and(Validator.custom(uri => uri.startsWith("http://books.google.com/books") || uri.startsWith("https://covers.openlibrary.org/"),
                          "Improper address"))
    .contramap(_.value.renderString)
}
