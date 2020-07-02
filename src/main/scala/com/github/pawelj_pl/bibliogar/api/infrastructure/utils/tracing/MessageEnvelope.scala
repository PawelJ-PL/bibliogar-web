package com.github.pawelj_pl.bibliogar.api.infrastructure.utils.tracing

import cats.effect.Sync
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.github.pawelj_pl.bibliogar.api.infrastructure.messagebus.Message
import fs2.concurrent.Topic
import kamon.Kamon
import kamon.context.Context

final case class MessageEnvelope(message: Message, context: Option[Context] = None) {
  def processWithContext[F[_]: Tracing, A](fa: Message => F[A]): F[A] =
    Tracing[F].withContext(fa(message), context.getOrElse(Context.Empty))
}

object MessageEnvelope {
  def forCurrentContext[F[_]: Sync](message: Message): F[MessageEnvelope] =
    Sync[F].delay(Kamon.currentContext()).map(ctx => MessageEnvelope(message, Some(ctx)))

  def broadcastWithCurrentContext[F[_]: Sync](message: Message, topic: Topic[F, MessageEnvelope]): F[Unit] =
    forCurrentContext[F](message).flatMap(envelope => topic.publish1(envelope))
}
