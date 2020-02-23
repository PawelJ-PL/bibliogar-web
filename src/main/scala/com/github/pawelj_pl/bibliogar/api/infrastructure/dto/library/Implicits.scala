package com.github.pawelj_pl.bibliogar.api.infrastructure.dto.library

import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveUnwrappedDecoder, deriveUnwrappedEncoder}
import sttp.tapir.{Schema, SchemaType, Validator}

trait Implicits extends LibraryNameImplicits with DurationValueImplicits with BooksLimitImplicits

trait LibraryNameImplicits {
  implicit val libraryNameEncoder: Encoder[LibraryName] = deriveUnwrappedEncoder[LibraryName]
  implicit val libraryNameDecoder: Decoder[LibraryName] = deriveUnwrappedDecoder[LibraryName]
  implicit val schemaForLibraryName: Schema[LibraryName] = Schema(SchemaType.SString)
  implicit val tapirValidatorLibraryName: Validator[LibraryName] = Validator
    .maxLength(60)
    .and(Validator.custom[String](data => data.trim.length >= 1, "Library name can't be empty"))
    .contramap(_.value)
}

trait DurationValueImplicits {
  implicit val durationValueEncoder: Encoder[DurationValue] = deriveUnwrappedEncoder[DurationValue]
  implicit val durationValueDecoder: Decoder[DurationValue] = deriveUnwrappedDecoder[DurationValue]
  implicit val schemaForDurationValue: Schema[DurationValue] = Schema(SchemaType.SInteger)
  implicit val tapirValidatorDurationValue: Validator[DurationValue] = Validator
    .min(1)
    .and(Validator.max(7300))
    .contramap(_.value)
}

trait BooksLimitImplicits {
  implicit val booksLimitEncoder: Encoder[BooksLimit] = deriveUnwrappedEncoder[BooksLimit]
  implicit val booksLimitDecoder: Decoder[BooksLimit] = deriveUnwrappedDecoder[BooksLimit]
  implicit val schemaForBooksLimit: Schema[BooksLimit] = Schema(SchemaType.SInteger)
  implicit val tapirValidatorBooksLimit: Validator[BooksLimit] = Validator
    .min(1)
    .contramap(_.value)
}
