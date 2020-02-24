package com.github.pawelj_pl.bibliogar.api.testdoubles.repositories

import cats.Monad
import cats.data.OptionT
import cats.mtl.MonadState
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.eq._
import com.github.pawelj_pl.bibliogar.api.domain.loan.{Loan, LoanRepositoryAlgebra}
import io.chrisdavenport.fuuid.FUUID

object LoanRepositoryFake {
  final case class LoanRepositoryState(loans: Set[Loan] = Set.empty)

  def instance[F[_]: Monad](implicit S: MonadState[F, LoanRepositoryState]): LoanRepositoryAlgebra[F] = new LoanRepositoryAlgebra[F] {

    override def create(loan: Loan): F[Loan] =
      S.modify(state => state.copy(loans = state.loans + loan)).as(loan)

    override def findById(id: FUUID): OptionT[F, Loan] = OptionT(S.get.map(_.loans.find(_.id === id)))

    override def findByUser(userId: FUUID, offset: Int, limit: Int): F[List[Loan]] =
      S.get.map(_.loans.filter(_.userId === userId).toList.sortBy(_.createdAt).reverse.slice(offset, offset + limit))

    override def findByUserAndEmptyReturnedAt(userId: FUUID): F[List[Loan]] =
      S.get.map(_.loans.filter(loan => loan.userId === userId && loan.returnedAt.isEmpty).toList)

    override def update(loan: Loan): OptionT[F, Loan] =
      OptionT({
        val transform = for {
          origState <- S.get
          _ <- S.modify(state => {
            state.loans.find(_.id === loan.id) match {
              case None => state
              case Some(l) =>
                val removed = state.loans - l
                state.copy(loans = removed + loan)
            }
          })
          newState <- S.get
        } yield origState != newState
        transform.map(changed => if (changed) Some(loan) else None)
      })
  }
}
