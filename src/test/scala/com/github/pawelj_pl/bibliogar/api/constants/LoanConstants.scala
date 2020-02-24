package com.github.pawelj_pl.bibliogar.api.constants

import java.time.Instant

import com.github.pawelj_pl.bibliogar.api.domain.loan.Loan

trait LoanConstants extends CommonConstants with ResourcesIdentifiers {
  final val TestReturnTo = Instant.ofEpochMilli(1583190000000L)

  final val ExampleLoan =
    Loan(TestLoanId, TestUserId, Some(TestLibraryId), TestReturnTo, None, List(None, Some(TestBookId), None), Now, Now)
}
