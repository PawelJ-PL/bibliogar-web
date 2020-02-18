package com.github.pawelj_pl.bibliogar.api.itconstants

import com.github.pawelj_pl.bibliogar.api.domain.book.{Book, SourceType}
import org.http4s.implicits._

trait BookConstants extends CommonConstants with ResourcesIdentifiers {
  final val TestAuthors = "J.Smith; A. Kowalski"
  final val TestTitle = "My Book"
  final val TestIsbn = "1357924681111"
  final val TestCoverUrl = uri"http://localhost:9999/cover.jpg"

  final val ExampleBook =
    Book(TestBookId, TestIsbn, TestTitle, Some(TestAuthors), Some(TestCoverUrl), Some(4), SourceType.User, Some(TestUserId), Now, Now)
}
