package com.github.pawelj_pl.bibliogar.api.domain.library

import cats.{Monad, ~>}
import cats.data.StateT
import cats.effect.IO
import cats.mtl.instances.all._
import cats.mtl.MonadState
import com.github.pawelj_pl.bibliogar.api.{CommonError, LibraryError}
import com.github.pawelj_pl.bibliogar.api.constants.{LibraryConstants, UserConstants}
import com.github.pawelj_pl.bibliogar.api.infrastructure.dto.library.{BooksLimit, DurationValue, LibraryDataReq, LibraryName}
import com.github.pawelj_pl.bibliogar.api.infrastructure.messagebus.Message
import com.github.pawelj_pl.bibliogar.api.infrastructure.messagebus.Message.{LibraryDeleted, LibraryUpdated, NewLibrary}
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.{RandomProvider, TimeProvider}
import com.github.pawelj_pl.bibliogar.api.testdoubles.messagebus.MessageTopicFake
import com.github.pawelj_pl.bibliogar.api.testdoubles.repositories.LibraryRepositoryFake
import com.github.pawelj_pl.bibliogar.api.testdoubles.utils.{RandomProviderFake, TimeProviderFake}
import com.olegpy.meow.hierarchy.deriveMonadState
import com.softwaremill.diffx.scalatest.DiffMatcher
import fs2.concurrent.Topic
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class LibraryServiceSpec extends AnyWordSpec with Matchers with DiffMatcher with LibraryConstants with UserConstants {
  case class TestState(
    timeProviderState: TimeProviderFake.TimeProviderState = TimeProviderFake.TimeProviderState(),
    randomState: RandomProviderFake.RandomState = RandomProviderFake.RandomState(),
    libraryRepoState: LibraryRepositoryFake.LibraryRepositoryState = LibraryRepositoryFake.LibraryRepositoryState(),
    messageTopicState: MessageTopicFake.MessageTopicState = MessageTopicFake.MessageTopicState())
  type TestEffect[A] = StateT[IO, TestState, A]

  val instance: LibraryService[TestEffect] = {
    implicit def timeProvider[F[_]: Monad: MonadState[*[_], TestState]]: TimeProvider[F] = TimeProviderFake.instance[F]
    implicit def randomProvider[F[_]: Monad: MonadState[*[_], TestState]]: RandomProvider[F] = RandomProviderFake.instance[F]
    implicit def librariesRepo[F[_]: Monad: MonadState[*[_], TestState]]: LibraryRepositoryAlgebra[F] = LibraryRepositoryFake.instance[F]
    def messageTopic[F[_]: Monad: MonadState[*[_], TestState]]: Topic[F, Message] = MessageTopicFake.instance[F]

    implicit def dbToApp: TestEffect ~> TestEffect = new (TestEffect ~> TestEffect) {
      override def apply[A](fa: TestEffect[A]): TestEffect[A] = fa
    }

    LibraryService.withDb[TestEffect, TestEffect](messageTopic)
  }

  private val ExampleDataDto = LibraryDataReq(
    None,
    LibraryName(ExampleLibrary.name),
    DurationValue(ExampleLibrary.loanDurationValue),
    ExampleLibrary.loanDurationUnit,
    ExampleLibrary.booksLimit.map(BooksLimit)
  )

  "Create library" should {
    "be completed" in {
      val initialState = TestState()
      val expectedLibrary = ExampleLibrary.copy(id = FirstRandomUuid)
      val (state, result) = instance.createLibraryAs(ExampleDataDto, ExampleUser.id).run(initialState).unsafeRunSync()
      result should matchTo(expectedLibrary)
      state.libraryRepoState.libraries.toList should matchTo(List(expectedLibrary))
      state.messageTopicState.messages should matchTo(List[Message](NewLibrary(expectedLibrary)))
    }
  }

  "Get library as user" should {
    "return user" in {
      val initialState = TestState(
        libraryRepoState = LibraryRepositoryFake.LibraryRepositoryState(
          libraries = Set(ExampleLibrary)
        )
      )
      val result = instance.getLibraryAs(ExampleLibrary.id, ExampleUser.id).value.runA(initialState).unsafeRunSync()
      result should matchTo[Either[LibraryError, Library]](Right(ExampleLibrary))
    }
    "fail" when {
      "library not found" in {
        val initialState = TestState()
        val result = instance.getLibraryAs(ExampleLibrary.id, ExampleUser.id).value.runA(initialState).unsafeRunSync()
        result should matchTo[Either[LibraryError, Library]](Left(LibraryError.LibraryIdNotFound(ExampleLibrary.id)))
      }
      "library owned by other user" in {
        val initialState = TestState(
          libraryRepoState = LibraryRepositoryFake.LibraryRepositoryState(
            libraries = Set(ExampleLibrary)
          )
        )
        val result = instance.getLibraryAs(ExampleLibrary.id, ExampleId1).value.runA(initialState).unsafeRunSync()
        result should matchTo[Either[LibraryError, Library]](Left(LibraryError.LibraryNotOwnedByUser(ExampleLibrary.id, ExampleId1)))
      }
    }
  }

  "Get libraries of user" should {
    "return all users libraries" in {
      val initialState = TestState(
        libraryRepoState = LibraryRepositoryFake.LibraryRepositoryState(
          libraries = Set(ExampleLibrary, ExampleLibrary.copy(id = ExampleId1, ownerId = ExampleId2), ExampleLibrary.copy(ExampleId3))
        )
      )
      val result = instance.getAllLibrariesOfUser(ExampleUser.id).runA(initialState).unsafeRunSync()
      result.map(_.id) should contain theSameElementsAs (List(ExampleLibrary.id, ExampleId3))
    }
  }

  "Remove library" should {
    "be finished" in {
      val secondLib = ExampleLibrary.copy(id = ExampleId1)
      val initialState = TestState(
        libraryRepoState = LibraryRepositoryFake.LibraryRepositoryState(
          libraries = Set(ExampleLibrary, secondLib)
        )
      )
      val (state, result) = instance.deleteLibraryAs(ExampleLibrary.id, ExampleUser.id).value.run(initialState).unsafeRunSync()
      result shouldBe Right((): Unit)
      state.libraryRepoState.libraries should contain theSameElementsAs Set(secondLib)
      state.messageTopicState.messages should matchTo(List[Message](LibraryDeleted(ExampleLibrary)))
    }
    "fail" when {
      "library not found" in {
        val initialState = TestState(
          libraryRepoState = LibraryRepositoryFake.LibraryRepositoryState(
            libraries = Set(ExampleLibrary)
          )
        )
        val (state, result) = instance.deleteLibraryAs(ExampleId1, ExampleUser.id).value.run(initialState).unsafeRunSync()
        result shouldBe Left(LibraryError.LibraryIdNotFound(ExampleId1))
        state.libraryRepoState.libraries.toList should matchTo(initialState.libraryRepoState.libraries.toList)
      }
      "library owned by other user" in {
        val initialState = TestState(
          libraryRepoState = LibraryRepositoryFake.LibraryRepositoryState(
            libraries = Set(ExampleLibrary)
          )
        )
        val (state, result) = instance.deleteLibraryAs(ExampleLibrary.id, ExampleId1).value.run(initialState).unsafeRunSync()
        result shouldBe Left(LibraryError.LibraryNotOwnedByUser(ExampleLibrary.id, ExampleId1))
        state.libraryRepoState.libraries.toList should matchTo(initialState.libraryRepoState.libraries.toList)
      }
    }
  }

  "Update library" should {
    val updateDto = ExampleDataDto.copy(name = LibraryName("other name"))
    "succeed" when {
      "version check disabled" in {
        val initialState = TestState(
          libraryRepoState = LibraryRepositoryFake.LibraryRepositoryState(
            libraries = Set(ExampleLibrary)
          )
        )
        val (state, result) = instance.updateLibraryAs(ExampleLibrary.id, updateDto, ExampleUser.id).value.run(initialState).unsafeRunSync()
        result should matchTo[Either[LibraryError, Library]](Right(ExampleLibrary.copy(name = "other name")))
        state.libraryRepoState.libraries.toList should matchTo(Set(ExampleLibrary.copy(name = "other name")).toList)
        state.messageTopicState.messages should matchTo(List[Message](LibraryUpdated(ExampleLibrary.copy(name = "other name"))))
      }
      "version match" in {
        val dto = updateDto.copy(version = Some(ExampleLibrary.version))
        val initialState = TestState(
          libraryRepoState = LibraryRepositoryFake.LibraryRepositoryState(
            libraries = Set(ExampleLibrary)
          )
        )
        val (state, result) = instance.updateLibraryAs(ExampleLibrary.id, dto, ExampleUser.id).value.run(initialState).unsafeRunSync()
        result should matchTo[Either[LibraryError, Library]](Right(ExampleLibrary.copy(name = "other name")))
        state.libraryRepoState.libraries.toList should matchTo(Set(ExampleLibrary.copy(name = "other name")).toList)
      }
    }
    "fail" when {
      "library not found" in {
        val initialState = TestState()
        val (state, result) = instance.updateLibraryAs(ExampleLibrary.id, updateDto, ExampleUser.id).value.run(initialState).unsafeRunSync()
        result should matchTo[Either[LibraryError, Library]](Left(LibraryError.LibraryIdNotFound(ExampleLibrary.id)))
        state.libraryRepoState.libraries.toList should matchTo(initialState.libraryRepoState.libraries.toList)
      }
      "library owned by other user" in {
        val initialState = TestState(
          libraryRepoState = LibraryRepositoryFake.LibraryRepositoryState(
            libraries = Set(ExampleLibrary)
          )
        )
        val (state, result) = instance.updateLibraryAs(ExampleLibrary.id, updateDto, ExampleId1).value.run(initialState).unsafeRunSync()
        result should matchTo[Either[LibraryError, Library]](Left(LibraryError.LibraryNotOwnedByUser(ExampleLibrary.id, ExampleId1)))
        state.libraryRepoState.libraries.toList should matchTo(initialState.libraryRepoState.libraries.toList)
      }
      "version mismatch" in {
        val dto = updateDto.copy(version = Some("foo"))
        val initialState = TestState(
          libraryRepoState = LibraryRepositoryFake.LibraryRepositoryState(
            libraries = Set(ExampleLibrary)
          )
        )
        val (state, result) = instance.updateLibraryAs(ExampleLibrary.id, dto, ExampleUser.id).value.run(initialState).unsafeRunSync()
        result should matchTo[Either[LibraryError, Library]](Left(CommonError.ResourceVersionDoesNotMatch(ExampleLibrary.version, "foo")))
        state.libraryRepoState.libraries.toList should matchTo(initialState.libraryRepoState.libraries.toList)
      }
    }
  }
}
