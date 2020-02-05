package com.github.pawelj_pl.bibliogar.api.itconstants

import com.github.pawelj_pl.bibliogar.api.domain.library.{Library, LoanDurationUnit}

trait LibraryConstants extends CommonConstants with ResourcesIdentifiers {
  final val TestLibraryName = "My library"
  final val ExampleLibrary = Library(TestLibraryId, TestUserId, TestLibraryName, 1, LoanDurationUnit.Month, Now, Now)
}
