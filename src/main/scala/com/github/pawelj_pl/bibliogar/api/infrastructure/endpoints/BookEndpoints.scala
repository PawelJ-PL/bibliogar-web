package com.github.pawelj_pl.bibliogar.api.infrastructure.endpoints

import com.github.pawelj_pl.bibliogar.api.infrastructure.authorization.{AuthInputs, SecuredEndpoint}
import com.github.pawelj_pl.bibliogar.api.infrastructure.dto.book.{Authors, BookDataResp, BookReq, Cover, Implicits, Isbn, Title}
import com.github.pawelj_pl.bibliogar.api.infrastructure.http.{ApiEndpoint, ErrorResponse}
import com.github.pawelj_pl.bibliogar.api.infrastructure.http.Implicits.Fuuid._
import com.github.pawelj_pl.bibliogar.api.infrastructure.http.Implicits.Http4sUri._
import io.chrisdavenport.fuuid.FUUID
import org.http4s.implicits._
import sttp.tapir._
import sttp.tapir.json.circe._

object BookEndpoints extends ApiEndpoint with SecuredEndpoint with Implicits {
  private val booksPrefix = apiPrefix / "books"

  private final val exampleIsbn = "9788312345678"
  private final val exampleId = FUUID.fuuid("5658b53c-e616-4fae-b60c-71014ed3daaa")
  private val exampleReq =
    BookReq(Isbn(exampleIsbn), Title("My Book"), Some(Authors("John Smith")), Some(Cover(uri"http://localhost/cover.jpg")))
  private val exampleDataResp = BookDataResp(exampleId, exampleIsbn, "My Book", Some("John Smith"), Some(uri"http://localhost/cover.jpg"))

  val createBookEndpoint: Endpoint[(AuthInputs, BookReq), ErrorResponse, BookDataResp, Nothing] =
    endpoint
      .summary("Add new book entry")
      .tag("book")
      .post
      .prependIn(authenticationDetails)
      .in(booksPrefix)
      .in(jsonBody[BookReq].example(exampleReq))
      .out(jsonBody[BookDataResp].example(exampleDataResp))
      .errorOut(BadRequestOrUnauthorizedResp)

  val getBookEndpoint: Endpoint[(AuthInputs, FUUID), ErrorResponse, BookDataResp, Nothing] =
    endpoint
      .summary("Get book info")
      .tag("book")
      .get
      .prependIn(authenticationDetails)
      .in(booksPrefix / path[FUUID]("bookId"))
      .out(jsonBody[BookDataResp].example(exampleDataResp))
      .errorOut(
        oneOf[ErrorResponse](
          StatusMappings.badRequest,
          StatusMappings.notFound("Book not found"),
          StatusMappings.unauthorized
        )
      )

  val getIsbnSuggestionsEndpoint: Endpoint[(AuthInputs, Isbn), ErrorResponse, List[BookDataResp], Nothing] =
    endpoint
      .summary("Get suggestions for ISBN")
      .tag("book")
      .get
      .prependIn(authenticationDetails)
      .in(booksPrefix / "isbn" / path[Isbn]("isbn"))
      .out(jsonBody[List[BookDataResp]].example(List(exampleDataResp)))
      .errorOut(UnauthorizedResp)

  override val endpoints: List[Endpoint[_, _, _, _]] = List(
    createBookEndpoint,
    getBookEndpoint,
    getIsbnSuggestionsEndpoint
  )
}
