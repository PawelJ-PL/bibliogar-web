package com.github.pawelj_pl.bibliogar.api.domain.book

import cats.effect.IO
import com.github.pawelj_pl.bibliogar.api.constants.BookConstants
import com.github.pawelj_pl.bibliogar.api.Implicits.Uri._
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.tracing.Tracing
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.{RandomProvider, TimeProvider}
import com.github.pawelj_pl.bibliogar.api.testdoubles.utils.tracing.DummyTracer
import com.github.pawelj_pl.bibliogar.api.testdoubles.utils.{RandomProviderFake, TimeProviderFake}
import com.softwaremill.diffx.scalatest.DiffMatcher
import io.circe.literal._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.client.Client
import org.http4s.{HttpApp, HttpRoutes, Response}
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class BibliotekaNarodowaApiSpec extends AnyWordSpec with Matchers with DiffMatcher with BookConstants {
  implicit val timeProvider: TimeProvider[IO] = TimeProviderFake.withFixedValue[IO](Now)
  implicit val randomProvider: RandomProvider[IO] = RandomProviderFake.withFixedValues("abcd", ExampleId1)
  implicit val tracing: Tracing[IO] = DummyTracer.instance[IO]

  private def bibliotekaNarodowaApiResponses(resp: IO[Response[IO]]): HttpApp[IO] =
    HttpRoutes
      .of[IO] {
        case GET -> Root / "api" / "bibs.json" =>
          resp
      }
      .orNotFound

  "Biblioteka Narodowa API" should {
    "return book data" in {
      val body =
        json"""
          {
            "bibs": [
              {
                "author": "J.Smith; A. Kowalski",
                "title": "My Book"
              }
            ]
          }
            """
      val client = Client.fromHttpApp(bibliotekaNarodowaApiResponses(Ok(body)))
      val service = BN.ApiClient(client)
      val result = service.get(ExampleBook.isbn).unsafeRunSync()
      result should matchTo(
        Option(ExampleBook.copy(id = ExampleId1, cover = None, score = None, sourceType = SourceType.BibliotekaNarodowa, createdBy = None)))
    }
    "return None" when {
      "book not found" in {
        val body =
          json"""
          {
            "bibs": []
          }
            """
        val client = Client.fromHttpApp(bibliotekaNarodowaApiResponses(Ok(body)))
        val service = BN.ApiClient(client)
        val result = service.get(ExampleBook.isbn).unsafeRunSync()
        result shouldBe None
      }
      "response decode failed" in {
        val body =
          json"{}"
        val client = Client.fromHttpApp(bibliotekaNarodowaApiResponses(Ok(body)))
        val service = BN.ApiClient(client)
        val result = service.get(ExampleBook.isbn).unsafeRunSync()
        result shouldBe None
      }
      "http call failed" in {
        val client = Client.fromHttpApp(bibliotekaNarodowaApiResponses(InternalServerError()))
        val service = BN.ApiClient(client)
        val result = service.get(ExampleBook.isbn).unsafeRunSync()
        result shouldBe None
      }
    }
  }
}
