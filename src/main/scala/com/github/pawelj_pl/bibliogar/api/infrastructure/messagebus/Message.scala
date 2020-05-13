package com.github.pawelj_pl.bibliogar.api.infrastructure.messagebus

import cats.Show
import com.github.pawelj_pl.bibliogar.api.domain.library.Library
import com.github.pawelj_pl.bibliogar.api.domain.loan.Loan
import com.github.pawelj_pl.bibliogar.api.domain.user.{User, UserToken}

sealed trait Message extends Product with Serializable

object Message {
  case object TopicStarted extends Message
  final case class NewLoan(loan: Loan) extends Message
  final case class LoanUpdated(loan: Loan) extends Message
  final case class NewLibrary(library: Library) extends Message
  final case class LibraryUpdated(library: Library) extends Message
  final case class LibraryDeleted(library: Library) extends Message
  final case class UserCreated(user: User, registrationToken: UserToken) extends Message
  final case class PasswordResetRequested(user: User, resetToken: UserToken) extends Message

  implicit val show: Show[Message] = Show.fromToString
}
