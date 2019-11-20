package com.github.pawelj_pl.bibliogar.api.testdoubles.utils

import cats.data.Chain
import cats.mtl.MonadState
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.Correspondence

object CorrespondenceMock {
  case class CorrespondenceState(sentMessages: Chain[Message] = Chain.empty)
  case class Message(to: String, subject: String, message: String)

  def instance[F[_]](implicit S: MonadState[F, CorrespondenceState]): Correspondence[F] = new Correspondence[F] {
    override def sendMessage(to: String, subject: String, message: String): F[Unit] =
      S.modify(state => state.copy(state.sentMessages.append(Message(to, subject, message))))
  }
}
