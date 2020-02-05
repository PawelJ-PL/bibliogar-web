package com.github.pawelj_pl.bibliogar.api.infrastructure.http

import cats.Functor
import cats.data.EitherT
import cats.syntax.either._
import cats.syntax.functor._
import com.github.pawelj_pl.bibliogar.api.AppError

trait ResponseUtils extends ErrorHandler {
  def responseOrError[F[_]: Functor, E <: AppError, A, B](result: EitherT[F, E, A], successResponse: A => B): F[Either[ErrorResponse, B]] =
    result.bimap(errorToResponse, successResponse).value

  def emptyResponseOrError[F[_]: Functor, E <: AppError, A <: Any](result: EitherT[F, E, A]): F[Either[ErrorResponse, Unit]] =
    responseOrError(result, (_: Any) => (): Unit)

  def fromErrorlessResult[F[_]: Functor, A, B](result: F[A], response: A => B): F[Either[ErrorResponse, B]] =
    result.map(r => response(r).asRight[ErrorResponse])
}
