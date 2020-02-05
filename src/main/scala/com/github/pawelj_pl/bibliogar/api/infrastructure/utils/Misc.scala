package com.github.pawelj_pl.bibliogar.api.infrastructure.utils

import cats.Applicative
import cats.data.EitherT
import cats.instances.string._
import cats.syntax.eq._
import com.github.pawelj_pl.bibliogar.api.CommonError

object Misc {
  object resourceVersion {
    object syntax {
      implicit class VerifyVersionOps[F[_]: Applicative, A: VersionExtractor](resource: A) {
        def verifyOptVersion(otherVersion: String): EitherT[F, CommonError.ResourceVersionDoesNotMatch, Unit] =
          EitherT.cond[F](
            VersionExtractor[A].extract(resource).forall(v => v === otherVersion),
            (),
            CommonError.ResourceVersionDoesNotMatch(otherVersion, VersionExtractor[A].extract(resource).getOrElse(""))
          )
      }
    }
    trait VersionExtractor[A] {
      def extract(resource: A): Option[String]
    }
    object VersionExtractor {
      def apply[A](implicit ev: VersionExtractor[A]): VersionExtractor[A] = ev
      def of[A](extractor: A => Option[String]): VersionExtractor[A] = (resource: A) => extractor(resource)
    }
  }
}
