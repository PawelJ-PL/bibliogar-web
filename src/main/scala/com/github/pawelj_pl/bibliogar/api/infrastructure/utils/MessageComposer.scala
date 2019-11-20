package com.github.pawelj_pl.bibliogar.api.infrastructure.utils

import cats.effect.{Resource, Sync}
import cats.syntax.either._
import cats.syntax.functor._
import cats.syntax.monadError._
import yamusca.context.{Context, Value}
import yamusca.imports.mustache

import scala.io.Source

trait MessageComposer[F[_]] {
  def generateMessage(messageName: String, variables: Map[String, AnyRef]): F[String]
}
object MessageComposer {
  def apply[F[_]](implicit ev: MessageComposer[F]): MessageComposer[F] = ev
  def create[F[_]: Sync]: MessageComposer[F] = new MessageComposer[F] {
    override def generateMessage(messageName: String, variables: Map[String, AnyRef]): F[String] = {
      val source = Sync[F].delay(Source.fromResource(s"templates/$messageName.mustache"))
      val templateResource = Resource.fromAutoCloseable(source)

      val template: F[String] = templateResource.use(getTemplateContent)

      template
        .map(t => processTemplate(t, variables))
        .rethrow
    }

    private def getTemplateContent(source: Source): F[String] = Sync[F].delay(source.mkString).adaptError {
      case _: NullPointerException => new Exception("Unable to open template file")
    }

    private def processTemplate(template: String, variables: Map[String, AnyRef]): Either[Throwable, String] = {
      val ctx = Context.fromMap(variables.view.mapValues(v => Value.of(v.toString)).toMap)
      mustache
        .parse(template)
        .map(mustache.render(_)(ctx))
        .leftMap(err => new Exception(s"error: ${err._2}; input: ${err._1}"): Throwable)
    }
  }
}
