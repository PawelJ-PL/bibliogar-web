package com.github.pawelj_pl.bibliogar.api.infrastructure.dto.library

import cats.{Monad, Show}
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.github.pawelj_pl.bibliogar.api.domain.library.{Library, LoanDurationUnit}
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.Misc.resourceVersion.VersionExtractor
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.{RandomProvider, TimeProvider}
import io.chrisdavenport.fuuid.FUUID
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import sttp.tapir.codec.enumeratum.TapirCodecEnumeratum

final case class LibraryName(value: String) extends AnyVal
final case class DurationValue(value: Int) extends AnyVal
final case class BooksLimit(value: Int) extends AnyVal

final case class LibraryDataReq(
  version: Option[String],
  name: LibraryName,
  loanDurationValue: DurationValue,
  loanDurationUnit: LoanDurationUnit,
  booksLimit: Option[BooksLimit]) {
  def toDomain[F[_]: Monad: TimeProvider: RandomProvider](userId: FUUID): F[Library] =
    for {
      now <- TimeProvider[F].now
      id  <- RandomProvider[F].randomFuuid
    } yield Library(id, userId, name.value, loanDurationValue.value, loanDurationUnit, booksLimit.map(_.value), now, now)
}

object LibraryDataReq extends LibraryNameImplicits with DurationValueImplicits with TapirCodecEnumeratum with BooksLimitImplicits {
  implicit val versionExtractor: VersionExtractor[LibraryDataReq] = VersionExtractor.of[LibraryDataReq](_.version)
  implicit val show: Show[LibraryDataReq] = Show.fromToString

  implicit val encoder: Encoder[LibraryDataReq] = deriveEncoder[LibraryDataReq]
  implicit val decoder: Decoder[LibraryDataReq] = deriveDecoder[LibraryDataReq]
}
