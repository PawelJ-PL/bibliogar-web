package com.github.pawelj_pl.bibliogar.api.testdoubles.repositories

import cats.Monad
import cats.data.OptionT
import cats.mtl.MonadState
import cats.syntax.eq._
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.github.pawelj_pl.bibliogar.api.domain.library.{Library, LibraryRepositoryAlgebra}
import io.chrisdavenport.fuuid.FUUID

object LibraryRepositoryFake {
  final case class LibraryRepositoryState(libraries: Set[Library] = Set.empty)

  def instance[F[_]: Monad](implicit S: MonadState[F, LibraryRepositoryState]): LibraryRepositoryAlgebra[F] =
    new LibraryRepositoryAlgebra[F] {
      implicit val stateEq: cats.kernel.Eq[LibraryRepositoryState] = cats.kernel.Eq.fromUniversalEquals

      override def create(library: Library): F[Library] =
        S.modify(state => state.copy(libraries = state.libraries + library)).map(_ => library)

      override def findById(libraryId: FUUID): OptionT[F, Library] = OptionT(S.get.map(_.libraries.find(_.id === libraryId)))

      override def findByOwner(ownerId: FUUID): F[List[Library]] = S.get.map(_.libraries.filter(_.ownerId === ownerId).toList)

      override def delete(libraryIds: FUUID*): F[Long] =
        for {
          origState <- S.get
          toDelete = libraryIds.flatMap(l => origState.libraries.find(_.id == l))
          _ <- S.modify(state => state.copy(libraries = state.libraries -- toDelete))
        } yield toDelete.length.toLong

      override def update(library: Library): OptionT[F, Library] = OptionT({
        val transform = for {
          origState <- S.get
          _ <- S.modify(state => {
            state.libraries.find(_.id === library.id) match {
              case None => state
              case Some(l) =>
                val removed = state.libraries - l
                state.copy(libraries = removed + library)
            }
          })
          newState <- S.get
        } yield origState =!= newState
        transform.map(changed => if (changed) Some(library) else None)
      })
    }
}
