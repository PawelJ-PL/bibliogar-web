package com.github.pawelj_pl.bibliogar.api.infrastructure.repositories

import java.time.Instant
import java.util.{Date, UUID}

import doobie.quill.DoobieContext
import io.chrisdavenport.fuuid.FUUID
import io.getquill.SnakeCase
import org.http4s.Uri

trait BasePostgresRepository {
  val doobieContext = new DoobieContext.Postgres(SnakeCase)

  object dbImplicits {
    import doobieContext._

    implicit val encodeFuuid: MappedEncoding[FUUID, UUID] = MappedEncoding[FUUID, UUID](FUUID.Unsafe.toUUID)
    implicit val decodeFuuid: MappedEncoding[UUID, FUUID] = MappedEncoding[UUID, FUUID](FUUID.fromUUID)

    implicit val encodeInstant: MappedEncoding[Instant, Date] = MappedEncoding[Instant, Date](Date.from)
    implicit val decodeInstant: MappedEncoding[Date, Instant] = MappedEncoding[Date, Instant](d => d.toInstant)

    implicit class InstantSyntax(value: Instant) {
      def >(other: Instant) = {
        quote(infix"$value > $other".as[Boolean])
      }

      def <(other: Instant) = {
        quote(infix"$value < $other".as[Boolean])
      }
    }

    implicit val encodeUri: MappedEncoding[Uri, String] = MappedEncoding[Uri, String](_.renderString)
    implicit val decodeUri: MappedEncoding[String, Uri] = MappedEncoding[String, Uri](Uri.unsafeFromString)
  }
}