package com.github.pawelj_pl.bibliogar.api.infrastructure.http

import cats.syntax.functor._
import enumeratum._
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax._
import sttp.tapir.{Schema, SchemaType, Validator}

sealed trait ErrorResponse extends Product with Serializable {
  def message: String
}

object ErrorResponse {
  final case class BadRequest(message: String) extends ErrorResponse
  case object BadRequest {
    implicit val encoder: Encoder[BadRequest] = deriveEncoder[BadRequest]
    implicit val decoder: Decoder[BadRequest] = deriveDecoder[BadRequest]
  }

  final case class Conflict(message: String) extends ErrorResponse
  case object Conflict {
    implicit val encoder: Encoder[Conflict] = deriveEncoder[Conflict]
    implicit val decoder: Decoder[Conflict] = deriveDecoder[Conflict]
  }

  final case class Forbidden(message: String) extends ErrorResponse
  object Forbidden {
    implicit val encoder: Encoder[Forbidden] = deriveEncoder[Forbidden]
    implicit val decoder: Decoder[Forbidden] = deriveDecoder[Forbidden]
  }

  final case class NotFound(message: String) extends ErrorResponse
  object NotFound {
    implicit val encoder: Encoder[NotFound] = deriveEncoder[NotFound]
    implicit val decoder: Decoder[NotFound] = deriveDecoder[NotFound]
  }

  final case class Unauthorized(message: String) extends ErrorResponse
  object Unauthorized {
    implicit val encoder: Encoder[Unauthorized] = deriveEncoder[Unauthorized]
    implicit val decoder: Decoder[Unauthorized] = deriveDecoder[Unauthorized]
  }

  final case class UnprocessableEntity(message: String) extends ErrorResponse
  object UnprocessableEntity {
    implicit val encoder: Encoder[UnprocessableEntity] = deriveEncoder[UnprocessableEntity]
    implicit val decoder: Decoder[UnprocessableEntity] = deriveDecoder[UnprocessableEntity]
  }

  final case class PreconditionFailed(message: String, reason: Option[PreconditionFailedReason]) extends ErrorResponse
  object PreconditionFailed {
    implicit val encoder: Encoder[PreconditionFailed] = deriveEncoder[PreconditionFailed]
    implicit val decoder: Decoder[PreconditionFailed] = deriveDecoder[PreconditionFailed]
  }

  implicit val encoder: Encoder[ErrorResponse] = Encoder.instance {
    case resp: BadRequest          => resp.asJson
    case resp: Conflict            => resp.asJson
    case resp: Forbidden           => resp.asJson
    case resp: NotFound            => resp.asJson
    case resp: Unauthorized        => resp.asJson
    case resp: UnprocessableEntity => resp.asJson
    case resp: PreconditionFailed  => resp.asJson
  }

  implicit val decoder: Decoder[ErrorResponse] =
    List[Decoder[ErrorResponse]](
      Decoder[BadRequest].widen,
      Decoder[Conflict].widen,
      Decoder[Forbidden].widen,
      Decoder[NotFound].widen,
      Decoder[Unauthorized].widen,
      Decoder[UnprocessableEntity].widen,
      Decoder[PreconditionFailed].widen
    ).reduceLeft(_ or _)
}

sealed trait PreconditionFailedReason extends EnumEntry

object PreconditionFailedReason extends Enum[PreconditionFailedReason] with CirceEnum[PreconditionFailedReason] {
  val values = findValues

  implicit val tapirSchema: Schema[PreconditionFailedReason] = Schema(SchemaType.SString)
  implicit val tapirValidator: Validator[PreconditionFailedReason] =
    Validator.enum(PreconditionFailedReason.values.toList, v => Some(v.entryName))

  case object ResourceErrorDoesNotMatch extends PreconditionFailedReason
  case object InvalidApiKeyType extends PreconditionFailedReason
  case object NotAssignedApiKey extends PreconditionFailedReason
  case object IncompatibleAppVersion extends PreconditionFailedReason
  case object BooksLimitExceeded extends PreconditionFailedReason
  case object LoanAlreadyFinished extends PreconditionFailedReason
}
