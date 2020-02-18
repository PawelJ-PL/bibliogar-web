package com.github.pawelj_pl.bibliogar.api.infrastructure.dto.book

import cats.Monad
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.github.pawelj_pl.bibliogar.api.domain.book.{Book, SourceType}
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.{RandomProvider, TimeProvider}
import io.chrisdavenport.fuuid.FUUID
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.{Decoder, Encoder}
import org.http4s.Uri

final case class Isbn(value: String) extends AnyVal
final case class Title(value: String) extends AnyVal
final case class Authors(value: String) extends AnyVal
final case class Cover(value: Uri) extends AnyVal

final case class BookReq(isbn: Isbn, title: Title, authors: Option[Authors], cover: Option[Cover]) {
  def toDomain[F[_]: Monad: TimeProvider: RandomProvider](userId: FUUID): F[Book] =
    for {
      now <- TimeProvider[F].now
      id  <- RandomProvider[F].randomFuuid
    } yield Book(id, isbn.value, title.value, authors.map(_.value), cover.map(_.value), Some(0), SourceType.User, Some(userId), now, now)
}

object BookReq extends IsbnImplicits with TitleImplicits with AuthorsImplicits with CoverImplicits {
  implicit val encoder: Encoder[BookReq] = deriveEncoder[BookReq]
  implicit val decoder: Decoder[BookReq] = deriveDecoder[BookReq]
}