package com.github.pawelj_pl.bibliogar.api.infrastructure.repositories

import java.time.Instant

import cats.data.OptionT
import com.github.pawelj_pl.bibliogar.api.DB
import com.github.pawelj_pl.bibliogar.api.domain.loan.{Loan, LoanRepositoryAlgebra}
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.TimeProvider
import io.chrisdavenport.fuuid.FUUID
import io.scalaland.chimney.dsl._

private final case class LoanEntity(
  id: FUUID,
  userId: FUUID,
  libraryId: Option[FUUID],
  returnTo: Instant,
  returnedAt: Option[Instant],
  createdAt: Instant,
  updatedAt: Instant) {
  def toDomain(books: List[Option[FUUID]]): Loan = this.into[Loan].withFieldConst(_.books, books).transform
}

private final case class LoanBookEntity(loanId: FUUID, bookId: Option[FUUID])

class DoobieLoanRepository(implicit timeProvider: TimeProvider[DB]) extends LoanRepositoryAlgebra[DB] with BasePostgresRepository {
  import doobieContext._
  import dbImplicits._
  import syntax._

  private implicit class LoanOps(loan: Loan) {
    def toEntity: LoanEntity = loan.transformInto[LoanEntity]
  }

  override def create(loan: Loan): DB[Loan] =
    (for {
      now <- timeProvider.now
      updatedLoan = loan.copy(createdAt = now, updatedAt = now)
      _ <- run(quote(loans.insert(lift(updatedLoan.toEntity))))
      books = loan.books.map(LoanBookEntity(loan.id, _))
      _ <- run(liftQuery(books).foreach(b => loanBooks.insert(b)))
    } yield updatedLoan).handleSqlError

  override def findById(id: FUUID): OptionT[DB, Loan] = OptionT(
    run {
      quote {
        loans
          .leftJoin(loanBooks)
          .on(_.id == _.loanId)
          .filter {
            case (loan, _) => loan.id == lift(id)
          }
      }
    }.map(
      _.groupMap(_._1.id)(identity)
        .get(id)
        .flatMap(entries =>
          entries.headOption
            .map(head => head._1.toDomain(entries.flatMap(_._2.map(_.bookId))))))
  )

  override def findByUser(userId: FUUID, offset: Int, limit: Int): DB[List[Loan]] =
    run {
      quote {
        loans
          .filter(_.userId == lift(userId))
          .sortBy(_.createdAt)(Ord.desc)
          .drop(lift(offset))
          .take(lift(limit))
          .leftJoin(loanBooks)
          .on(_.id == _.loanId)
      }
    }.map(_.groupMap(_._1)(_._2).map {
      case (loan, loanAndBooks) => loan.toDomain(loanAndBooks.flatMap(_.map(_.bookId)))
    }.toList.sortBy(_.createdAt).reverse)

  override def findByUserAndEmptyReturnedAt(userId: FUUID): DB[List[Loan]] =
    run {
      quote {
        loans
          .leftJoin(loanBooks)
          .on(_.id == _.loanId)
          .filter {
            case (loan, _) => loan.userId == lift(userId) && loan.returnedAt.isEmpty
          }
      }
    }.map(_.groupMap(_._1)(_._2).map {
      case (loan, loanAndBooks) => loan.toDomain(loanAndBooks.flatMap(_.map(_.bookId)))
    }.toList)

  override def update(loan: Loan): OptionT[DB, Loan] =
    OptionT((for {
      now <- timeProvider.now
      updated = loan.copy(updatedAt = now)
      count <- run(quote(loans.filter(_.id == lift(loan.id)).update(lift(updated.toEntity))))
      _     <- run(quote(loanBooks.filter(_.loanId == lift(loan.id)).delete))
      books = loan.books.map(LoanBookEntity(loan.id, _))
      _ <- run(liftQuery(books).foreach(b => loanBooks.insert(b)))
    } yield if (count > 0) Some(updated) else None).handleSqlError)

  private val loans = quote {
    querySchema[LoanEntity]("loans")
  }

  private val loanBooks = quote {
    querySchema[LoanBookEntity]("loan_books")
  }
}
