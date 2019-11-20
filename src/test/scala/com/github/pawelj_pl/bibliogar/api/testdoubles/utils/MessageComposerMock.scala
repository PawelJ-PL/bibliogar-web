package com.github.pawelj_pl.bibliogar.api.testdoubles.utils

import cats.Applicative
import cats.syntax.applicative._
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.MessageComposer

object MessageComposerMock {
  def instance[F[_]: Applicative]: MessageComposer[F] = new MessageComposer[F] {
    override def generateMessage(messageName: String, variables: Map[String, AnyRef]): F[String] =
      (messageName + " - " + variables.keys.toSeq.sorted
        .map(variable => s"$variable: ${variables.getOrElse(variable, "empty")}")
        .mkString(";")).pure[F]
  }
}
