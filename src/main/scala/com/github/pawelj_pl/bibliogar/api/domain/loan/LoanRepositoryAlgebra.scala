package com.github.pawelj_pl.bibliogar.api.domain.loan

import cats.data.OptionT
import io.chrisdavenport.fuuid.FUUID

trait LoanRepositoryAlgebra[F[_]] {
  def create(loan: Loan): F[Loan]
  def findById(id: FUUID): OptionT[F, Loan]
  def findByUser(userId: FUUID, offset: Int, limit: Int): F[List[Loan]]
  def findByUserAndEmptyReturnedAt(userId: FUUID): F[List[Loan]]
  def update(loan: Loan): OptionT[F, Loan]
}

object LoanRepositoryAlgebra {
  def apply[F[_]](implicit ev: LoanRepositoryAlgebra[F]): LoanRepositoryAlgebra[F] = ev
}
