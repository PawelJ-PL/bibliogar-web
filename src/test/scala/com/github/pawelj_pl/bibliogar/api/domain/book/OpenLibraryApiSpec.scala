package com.github.pawelj_pl.bibliogar.api.domain.book

import cats.effect.IO
import io.circe.literal._
import com.github.pawelj_pl.bibliogar.api.constants.BookConstants
import com.github.pawelj_pl.bibliogar.api.Implicits.Uri._
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.{RandomProvider, TimeProvider}
import com.github.pawelj_pl.bibliogar.api.testdoubles.utils.{RandomProviderFake, TimeProviderFake}
import com.softwaremill.diffx.scalatest.DiffMatcher
import org.http4s.{HttpApp, HttpRoutes, Response}
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class OpenLibraryApiSpec extends AnyWordSpec with Matchers with DiffMatcher with BookConstants {
  implicit val timeProvider: TimeProvider[IO] = TimeProviderFake.withFixedValue[IO](Now)
  implicit val randomProvider: RandomProvider[IO] = RandomProviderFake.withFixedValues("abcd", ExampleId1)

  private def openLibraryApiResponses(resp: IO[Response[IO]]): HttpApp[IO] =
    HttpRoutes
      .of[IO] {
        case GET -> Root / "api" / "books" =>
          resp
      }
      .orNotFound

  "OpenLibrary API" should {
    "return book data" in {
      val body =
        json"""
            {
            "ISBN:9787924681111": {
              "authors": [
                {
                  "name": "J.Smith"
                },
                {
                  "name": "A. Kowalski"
                }
              ],
              "cover": {
                "large": "https://covers.openlibrary.org/cover.jpg"
              },
              "title": "My Book"
            }
          }
            """
      val client = Client.fromHttpApp(openLibraryApiResponses(Ok(body)))
      val service = OpenLibrary.ApiClient(client)
      val result = service.get(ExampleBook.isbn).value.unsafeRunSync()
      val expectedBook = ExampleBook.copy(id = ExampleId1, sourceType = SourceType.OpenLibrary, score = None, createdBy = None)
      result should matchTo(Option(expectedBook))
    }
    "return None" when {
      "book not found" in {
        val body =
          json"""
            {}
            """
        val client = Client.fromHttpApp(openLibraryApiResponses(Ok(body)))
        val service = OpenLibrary.ApiClient(client)
        val result = service.get(ExampleBook.isbn).value.unsafeRunSync()
        result shouldBe None
      }
      "response decoding failed" in {
        val body =
          json"""
            {
              "foo": "bar"
            }
            """
        val client = Client.fromHttpApp(openLibraryApiResponses(Ok(body)))
        val service = OpenLibrary.ApiClient(client)
        val result = service.get(ExampleBook.isbn).value.unsafeRunSync()
        result shouldBe None
      }
      "response failed" in {
        val client = Client.fromHttpApp(openLibraryApiResponses(InternalServerError()))
        val service = OpenLibrary.ApiClient(client)
        val result = service.get(ExampleBook.isbn).value.unsafeRunSync()
        result shouldBe None
      }
    }
  }
}
