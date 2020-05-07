package com.github.pawelj_pl.bibliogar.api.testdoubles.messagebus

import cats.Monad
import cats.mtl.MonadState
import cats.syntax.functor._
import com.github.pawelj_pl.bibliogar.api.infrastructure.messagebus.Message
import fs2.Stream
import fs2.Pipe
import fs2.concurrent.Topic

object MessageTopicFake {
  case class MessageTopicState(messages: List[Message] = List.empty)

  def instance[F[_]: Monad](implicit S: MonadState[F, MessageTopicState]): Topic[F, Message] = new Topic[F, Message] {
    override def publish: Pipe[F, Message, Unit] = ???

    override def publish1(a: Message): F[Unit] = S.modify(state => state.copy(messages = state.messages.prepended(a)))

    override def subscribe(maxQueued: Int): fs2.Stream[F, Message] = Stream.evalSeq(S.get.map(_.messages))

    override def subscribeSize(maxQueued: Int): fs2.Stream[F, (Message, Int)] = ???

    override def subscribers: fs2.Stream[F, Int] = ???
  }
}
