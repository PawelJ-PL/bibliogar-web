package com.github.pawelj_pl.bibliogar.api.testdoubles.thirdparty

import cats.Functor
import cats.syntax.either._
import cats.syntax.functor._
import cats.mtl.MonadState
import com.github.pawelj_pl.fcm4s.Common
import com.github.pawelj_pl.fcm4s.Common.ErrorResponse
import com.github.pawelj_pl.fcm4s.messaging.{Message, MessageDataEncoder, Messaging}

object MessagingFake {
  case class MessagingState(messages: List[Any] = List.empty)

  def instance[F[_]: Functor](implicit S: MonadState[F, MessagingState]): Messaging[F] = new Messaging[F] {
    override def send[A](message: Message[A])(implicit evidence$1: MessageDataEncoder[A]): F[Either[Common.ErrorResponse, String]] = ???

    override def sendMany[A](messages: List[Message[A]])(implicit evidence$2: MessageDataEncoder[A]): F[List[Either[Common.ErrorResponse, String]]] = S.modify(state => state.copy(messages = state.messages ++ messages)).as(List("some message id".asRight[ErrorResponse]))

    override def sendMany[A](messages: Message[A]*)(implicit evidence$3: MessageDataEncoder[A]): F[List[Either[Common.ErrorResponse, String]]] = ???
  }
}
