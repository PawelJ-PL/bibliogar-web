package com.github.pawelj_pl.bibliogar.api.testdoubles.messagebus

import cats.Monad
import cats.mtl.MonadState
import cats.syntax.functor._
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.tracing.MessageEnvelope
import fs2.Stream
import fs2.Pipe
import fs2.concurrent.Topic

object MessageTopicFake {
  case class MessageTopicState(messages: List[MessageEnvelope] = List.empty)

  def instance[F[_]: Monad](implicit S: MonadState[F, MessageTopicState]): Topic[F, MessageEnvelope] = new Topic[F, MessageEnvelope] {
    override def publish: Pipe[F, MessageEnvelope, Unit] = ???

    override def publish1(a: MessageEnvelope): F[Unit] = S.modify(state => state.copy(messages = state.messages.prepended(a)))

    override def subscribe(maxQueued: Int): fs2.Stream[F, MessageEnvelope] = Stream.evalSeq(S.get.map(_.messages))

    override def subscribeSize(maxQueued: Int): fs2.Stream[F, (MessageEnvelope, Int)] = ???

    override def subscribers: fs2.Stream[F, Int] = ???
  }
}
