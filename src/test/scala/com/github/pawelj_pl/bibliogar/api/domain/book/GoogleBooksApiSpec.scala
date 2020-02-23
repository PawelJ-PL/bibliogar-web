package com.github.pawelj_pl.bibliogar.api.domain.book

import cats.effect.IO
import com.github.pawelj_pl.bibliogar.api.constants.BookConstants
import com.github.pawelj_pl.bibliogar.api.Implicits.Uri._
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.{RandomProvider, TimeProvider}
import com.github.pawelj_pl.bibliogar.api.testdoubles.utils.{RandomProviderFake, TimeProviderFake}
import com.softwaremill.diffx.scalatest.DiffMatcher
import io.circe.literal._
import org.http4s.client.Client
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.{HttpApp, HttpRoutes, Response}
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class GoogleBooksApiSpec extends AnyWordSpec with Matchers with DiffMatcher with BookConstants {
  implicit val timeProvider: TimeProvider[IO] = TimeProviderFake.withFixedValue[IO](Now)
  implicit val randomProvider: RandomProvider[IO] = RandomProviderFake.withFixedValues("abcd", ExampleId1)

  private def googleBooksApiResponses(resp: IO[Response[IO]]): HttpApp[IO] =
    HttpRoutes
      .of[IO] {
        case GET -> Root / "books" / "v1" / "volumes" =>
          resp
      }
      .orNotFound

  "Google Books API" should {
    "return book data" in {
      val body =
        json"""
            {
              "items": [
                {
                  "volumeInfo": {
                    "authors": ["J.Smith", "A. Kowalski"],
                    "title": "My Book",
                    "imageLinks": {
                      "smallThumbnail": "http://localhost:9999/cover-small.jpg",
                      "thumbnail": "https://covers.openlibrary.org/cover.jpg"
                    }
                  }
                }
              ]
            }
            """
      val client = Client.fromHttpApp(googleBooksApiResponses(Ok(body)))
      val service = GoogleBooks.ApiClient(client)
      val result = service.get(ExampleBook.isbn).unsafeRunSync()
      result should matchTo(Option(ExampleBook.copy(id = ExampleId1, score = None, sourceType = SourceType.GoogleBooks, createdBy = None)))
    }
    "use small thumbnail" when {
      "standard not found" in {
        val body =
          json"""
            {
              "items": [
                {
                  "volumeInfo": {
                    "authors": ["J.Smith", "A. Kowalski"],
                    "title": "My Book",
                    "imageLinks": {
                      "smallThumbnail": "http://localhost:9999/cover-small.jpg"
                    }
                  }
                }
              ]
            }
            """
        val client = Client.fromHttpApp(googleBooksApiResponses(Ok(body)))
        val service = GoogleBooks.ApiClient(client)
        val result = service.get(ExampleBook.isbn).unsafeRunSync()
        result should matchTo(
          Option(
            ExampleBook.copy(id = ExampleId1,
                             score = None,
                             cover = Some(uri"http://localhost:9999/cover-small.jpg"),
                             sourceType = SourceType.GoogleBooks,
                             createdBy = None)))
      }
    }
    "return None" when {
      "book not found" in {
        val body =
          json"{}"
        val client = Client.fromHttpApp(googleBooksApiResponses(Ok(body)))
        val service = GoogleBooks.ApiClient(client)
        val result = service.get(ExampleBook.isbn).unsafeRunSync()
        result shouldBe None
      }
      "response decode failed" in {
        val body =
          json"""
            {
              "items": [
                {}
              ]
            }
            """
        val client = Client.fromHttpApp(googleBooksApiResponses(Ok(body)))
        val service = GoogleBooks.ApiClient(client)
        val result = service.get(ExampleBook.isbn).unsafeRunSync()
        result shouldBe None
      }
      "http call failed" in {
        val client = Client.fromHttpApp(googleBooksApiResponses(InternalServerError()))
        val service = GoogleBooks.ApiClient(client)
        val result = service.get(ExampleBook.isbn).unsafeRunSync()
        result shouldBe None
      }
    }
  }
}
