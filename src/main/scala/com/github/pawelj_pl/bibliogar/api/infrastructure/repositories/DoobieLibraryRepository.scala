package com.github.pawelj_pl.bibliogar.api.infrastructure.repositories

import cats.data.OptionT
import com.github.pawelj_pl.bibliogar.api.DB
import com.github.pawelj_pl.bibliogar.api.domain.library.{Library, LibraryRepositoryAlgebra, LoanDurationUnit}
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.TimeProvider
import io.chrisdavenport.fuuid.FUUID

class DoobieLibraryRepository(implicit timeProvider: TimeProvider[DB]) extends LibraryRepositoryAlgebra[DB] with BasePostgresRepository {
  import doobieContext._
  import dbImplicits._

  implicit val encodeDurationUnit: MappedEncoding[LoanDurationUnit, String] = MappedEncoding[LoanDurationUnit, String](_.entryName)
  implicit val decodeDurationUnit: MappedEncoding[String, LoanDurationUnit] =
    MappedEncoding[String, LoanDurationUnit](LoanDurationUnit.withName)

  override def create(library: Library): DB[Library] =
    for {
      now <- timeProvider.now
      updatedLibrary = library.copy(createdAt = now, updatedAt = now)
      _ <- run(quote(libraries.insert(lift(updatedLibrary))))
    } yield updatedLibrary

  override def findById(libraryId: FUUID): OptionT[DB, Library] = OptionT(
    run(quote(libraries.filter(_.id == lift(libraryId)))).map(_.headOption)
  )

  override def findByOwner(ownerId: FUUID): DB[List[Library]] = run(quote(libraries.filter(_.ownerId == lift(ownerId))))

  override def delete(libraryIds: FUUID*): DB[Long] = run {
    quote {
      libraries.filter(l => liftQuery(libraryIds).contains(l.id)).delete
    }
  }

  override def update(library: Library): OptionT[DB, Library] =
    OptionT(for {
      now <- timeProvider.now
      updated = library.copy(updatedAt = now)
      count <- run(quote(libraries.filter(_.id == lift(library.id)).update(lift(updated))))
    } yield if (count > 0) Some(updated) else None)

  private val libraries = quote {
    querySchema[Library]("libraries")
  }
}
