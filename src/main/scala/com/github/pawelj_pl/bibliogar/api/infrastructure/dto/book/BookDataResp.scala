package com.github.pawelj_pl.bibliogar.api.infrastructure.dto.book

import com.github.pawelj_pl.bibliogar.api.domain.book.Book
import com.github.pawelj_pl.bibliogar.api.infrastructure.http.Implicits.Http4sUri._
import io.chrisdavenport.fuuid.circe._
import io.chrisdavenport.fuuid.FUUID
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.{Decoder, Encoder}
import io.scalaland.chimney.dsl._
import org.http4s.Uri

final case class BookDataResp(id: FUUID, isbn: String, title: String, authors: Option[String], cover: Option[Uri])

object BookDataResp {
  def fromDomain(book: Book): BookDataResp = book.transformInto[BookDataResp]

  implicit val encoder: Encoder[BookDataResp] = deriveEncoder[BookDataResp]
  implicit val decoder: Decoder[BookDataResp] = deriveDecoder[BookDataResp]
}
