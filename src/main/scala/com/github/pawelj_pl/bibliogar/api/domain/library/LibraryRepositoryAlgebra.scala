package com.github.pawelj_pl.bibliogar.api.domain.library

import cats.data.OptionT
import io.chrisdavenport.fuuid.FUUID

trait LibraryRepositoryAlgebra[F[_]] {
  def create(library: Library): F[Library]
  def findById(libraryId: FUUID): OptionT[F, Library]
  def findByOwner(ownerId: FUUID): F[List[Library]]
  def delete(libraryIds: FUUID*): F[Long]
  def update(library: Library): OptionT[F, Library]
}

object LibraryRepositoryAlgebra {
  def apply[F[_]](implicit ev: LibraryRepositoryAlgebra[F]): LibraryRepositoryAlgebra[F] = ev
}
