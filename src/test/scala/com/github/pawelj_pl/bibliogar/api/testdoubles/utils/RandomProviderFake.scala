package com.github.pawelj_pl.bibliogar.api.testdoubles.utils

import java.util.UUID

import cats.Monad
import cats.mtl.MonadState
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.RandomProvider
import io.chrisdavenport.fuuid.FUUID
import tsec.common.SecureRandomId

object RandomProviderFake {
  case class RandomState(actualRandomUuid: UUID = UUID.fromString("1c3aea3c-956d-467a-aba3-51e9e0000000"), actualRandomId: Int = 123)

  def instance[F[_]: Monad](implicit S: MonadState[F, RandomState]): RandomProvider[F] = new RandomProvider[F] {

    override def secureRandomString(size: Int): F[SecureRandomId] =
      S.modify(state => state.copy(actualRandomId = state.actualRandomId + 1))
        .flatMap(_ => S.get.map(state => state.actualRandomId.toString.asInstanceOf[SecureRandomId]))

    override def randomFuuid: F[FUUID] =
      S.modify(state => state.copy(actualRandomUuid = incrementUUID(state.actualRandomUuid)))
        .flatMap(_ => S.get.map(state => FUUID.fromUUID(state.actualRandomUuid)))

    private def incrementUUID(uuid: UUID): UUID = {
      val parts = uuid.toString.split("-")
      val asLong = parts.last.toLowerCase.toList.map(c => "0123456789abcdef".indexOf(c.toString)).map(_.toLong).reduceLeft(_ * 16 + _)
      val next = if (asLong < 262709978263278L) asLong + 1L else 0L
      val incrementedLast = "%012X".format(next)
      val incrementedString = parts.toSeq.dropRight(1).mkString("-") + "-" + incrementedLast
      UUID.fromString(incrementedString)
    }
  }
}
