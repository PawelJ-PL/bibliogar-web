package com.github.pawelj_pl.bibliogar.api.domain.book

import cats.{Applicative, Monad}
import cats.data.OptionT
import cats.effect.Sync
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.instances.string._
import cats.syntax.applicativeError._
import cats.syntax.functor._
import cats.syntax.show._
import com.github.pawelj_pl.bibliogar.api.infrastructure.http.Implicits.Http4sUri._
import com.github.pawelj_pl.bibliogar.api.infrastructure.utils.{RandomProvider, TimeProvider}
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import org.http4s.Uri
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.client.Client
import org.http4s.implicits._

trait IsbnService[F[_]] {
  def find(isbn: String): OptionT[F, Book]
}

object IsbnService {
  def apply[F[_]](implicit ev: IsbnService[F]): IsbnService[F] = ev

  def instance[F[_]: Sync: RandomProvider: TimeProvider](httpClient: Client[F]): IsbnService[F] = (isbn: String) => {
    OpenLibrary
      .ApiClient[F](httpClient)
      .get(isbn)
      .orElse(
        GoogleBooks
          .ApiClient(httpClient)
          .get(isbn)
          .orElse(
            BN.ApiClient(httpClient).get(isbn)
          )
      )
  }
}

object OpenLibrary {
  private final case class AuthorEntry(name: String)
  private object AuthorEntry {
    implicit val decoder: Decoder[AuthorEntry] = deriveDecoder[AuthorEntry]
  }
  private final case class CoverEntry(large: Uri)
  private object CoverEntry {
    implicit val decoder: Decoder[CoverEntry] = deriveDecoder[CoverEntry]
  }
  private final case class IsbnResponseEntry(title: String, authors: Option[List[AuthorEntry]], cover: Option[CoverEntry]) {
    def toDomain[F[_]: Monad: RandomProvider: TimeProvider](isbn: String): F[Book] =
      for {
        id  <- RandomProvider[F].randomFuuid
        now <- TimeProvider[F].now
      } yield
        Book(id, isbn, title, authors.map(_.map(_.name).mkString("; ")), cover.map(_.large), None, SourceType.OpenLibrary, None, now, now)
  }

  private implicit val responseDecoder: Decoder[IsbnResponseEntry] = deriveDecoder[IsbnResponseEntry]

  class ApiClient[F[_]: Sync: RandomProvider: TimeProvider](httpClient: Client[F]) {
    def get(isbn: String): OptionT[F, Book] = {
      val params = Map("jscmd" -> "data", "format" -> "json", "bibkeys" -> show"ISBN:$isbn")
      val uri = uri"https://openlibrary.org/api/books".withQueryParams(params)

      val response = httpClient.expect[Map[String, IsbnResponseEntry]](uri)
      ApiHelper.responseToBook[F, Map[String, IsbnResponseEntry], IsbnResponseEntry]("OpenLibrary",
                                                                                     isbn,
                                                                                     response,
                                                                                     _.get(show"ISBN:$isbn"),
                                                                                     _.toDomain(isbn))
    }
  }
  object ApiClient {
    def apply[F[_]: Sync: RandomProvider: TimeProvider](httpClient: Client[F]): ApiClient[F] = new ApiClient[F](httpClient)
  }
}

object GoogleBooks {
  private case class Images(smallThumbnail: Option[Uri], thumbnail: Option[Uri])
  private object Images {
    implicit val decoder: Decoder[Images] = deriveDecoder[Images]
  }
  private case class VolumeInfo(authors: Option[List[String]], title: String, imageLinks: Option[Images])
  private object VolumeInfo {
    implicit val decoder: Decoder[VolumeInfo] = deriveDecoder[VolumeInfo]
  }
  private case class Item(volumeInfo: VolumeInfo) {
    def toDomain[F[_]: Monad: RandomProvider: TimeProvider](isbn: String): F[Book] =
      for {
        id  <- RandomProvider[F].randomFuuid
        now <- TimeProvider[F].now
      } yield
        Book(
          id,
          isbn,
          volumeInfo.title,
          volumeInfo.authors.map(_.mkString("; ")),
          volumeInfo.imageLinks.flatMap(images => images.thumbnail.orElse(images.smallThumbnail)),
          None,
          SourceType.GoogleBooks,
          None,
          now,
          now
        )
  }
  private object Item {
    implicit val decoder: Decoder[Item] = deriveDecoder[Item]
  }
  private case class Response(items: Option[List[Item]])
  private object Response {
    implicit val decoder: Decoder[Response] = deriveDecoder[Response]
  }

  class ApiClient[F[_]: Sync: TimeProvider: RandomProvider](httpClient: Client[F]) {
    def get(isbn: String): OptionT[F, Book] = {
      val uri = uri"https://www.googleapis.com/books/v1/volumes".withQueryParam("q", show"isbn:$isbn")

      val response = httpClient.expect[Response](uri)
      ApiHelper.responseToBook[F, Response, Item]("Google Books", isbn, response, _.items.getOrElse(List.empty).headOption, _.toDomain(isbn))
    }
  }
  object ApiClient {
    def apply[F[_]: Sync: TimeProvider: RandomProvider](httpClient: Client[F]): ApiClient[F] = new ApiClient[F](httpClient)
  }
}

object BN {
  private case class Item(author: Option[String], title: String) {
    def toDomain[F[_]: Monad: TimeProvider: RandomProvider](isbn: String): F[Book] =
      for {
        now <- TimeProvider[F].now
        id  <- RandomProvider[F].randomFuuid
      } yield Book(id, isbn, title, author, None, None, SourceType.BibliotekaNarodowa, None, now, now)
  }
  private object Item {
    implicit val decoder: Decoder[Item] = deriveDecoder[Item]
  }
  private case class Response(bibs: List[Item])

  private implicit val responseDecoder: Decoder[Response] = deriveDecoder[Response]

  class ApiClient[F[_]: Sync: TimeProvider: RandomProvider](httpClient: Client[F]) {
    def get(isbn: String): OptionT[F, Book] = {
      val params = Map("isbnIssn" -> isbn, "limit" -> "1")
      val uri = uri"https://data.bn.org.pl/api/bibs.json".withQueryParams(params)

      val response = httpClient.expect[Response](uri)
      ApiHelper.responseToBook[F, Response, Item]("Biblioteka Narodowa", isbn, response, _.bibs.headOption, _.toDomain[F](isbn))
    }
  }

  object ApiClient {
    def apply[F[_]: Sync: TimeProvider: RandomProvider](httpClient: Client[F]) = new ApiClient[F](httpClient)
  }
}

private object ApiHelper {
  def responseToBook[F[_]: Sync, R, I](
    serviceName: String,
    isbn: String,
    response: F[R],
    respToItem: R => Option[I],
    itemToBook: I => F[Book]
  ): OptionT[F, Book] = {
    val log: Logger[F] = Slf4jLogger.getLogger[F]

    OptionT(
      log.info(show"Querying $serviceName for ISBN $isbn") *>
        response
          .map(respToItem)
          .flatTap(result =>
            if (result.isEmpty) {
              log.info(show"No entry for ISBN $isbn found in $serviceName")
            } else {
              Applicative[F].pure((): Unit)
          })
          .handleErrorWith(err => log.error(err)(show"Unable to fetch book data from $serviceName").as(None))
    ).semiflatMap(itemToBook)
  }
}
