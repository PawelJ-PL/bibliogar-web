package com.github.pawelj_pl.bibliogar.api.infrastructure.dto.library

import com.github.pawelj_pl.bibliogar.api.domain.library.LoanDurationUnit
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveUnwrappedDecoder, deriveUnwrappedEncoder}
import tapir.{Schema, SchemaFor, Validator}

trait Implicits extends LibraryNameImplicits with DurationValueImplicits with DurationUnitImplicits

trait LibraryNameImplicits {
  implicit val libraryNameEncoder: Encoder[LibraryName] = deriveUnwrappedEncoder[LibraryName]
  implicit val libraryNameDecoder: Decoder[LibraryName] = deriveUnwrappedDecoder[LibraryName]
  implicit val schemaForLibraryName: SchemaFor[LibraryName] = SchemaFor(Schema.SString)
  implicit val tapirValidatorLibraryName: Validator[LibraryName] = Validator
    .maxLength(60)
    .and(Validator.custom[String](data => data.trim.length >= 1, "Library name can't be empty"))
    .contramap(_.value)
}

trait DurationValueImplicits {
  implicit val durationValueEncoder: Encoder[DurationValue] = deriveUnwrappedEncoder[DurationValue]
  implicit val durationValueDecoder: Decoder[DurationValue] = deriveUnwrappedDecoder[DurationValue]
  implicit val schemaForDurationValue: SchemaFor[DurationValue] = SchemaFor(Schema.SInteger)
  implicit val tapirValidatorDurationValue: Validator[DurationValue] = Validator
    .min(1)
    .and(Validator.max(7300))
    .contramap(_.value)
}

trait DurationUnitImplicits {
  implicit val schema: SchemaFor[LoanDurationUnit] = SchemaFor[LoanDurationUnit](Schema.SString)
  implicit val validator: Validator[LoanDurationUnit] = Validator.enum(LoanDurationUnit.values.toList, v => Some(v.entryName))
}
