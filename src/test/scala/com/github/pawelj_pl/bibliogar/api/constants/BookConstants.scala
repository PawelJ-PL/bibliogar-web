package com.github.pawelj_pl.bibliogar.api.constants

import org.http4s.implicits._
import com.github.pawelj_pl.bibliogar.api.domain.book.{Book, SourceType}

trait BookConstants extends CommonConstants with ResourcesIdentifiers {
  final val TestAuthors = "J.Smith; A. Kowalski"
  final val TestTitle = "My Book"
  final val TestIsbn = "9787924681111"
  final val TestCoverUrl = uri"https://covers.openlibrary.org/cover.jpg"

  final val ExampleBook =
    Book(TestBookId, TestIsbn, TestTitle, Some(TestAuthors), Some(TestCoverUrl), Some(4), SourceType.User, Some(TestUserId), Now, Now)
}
