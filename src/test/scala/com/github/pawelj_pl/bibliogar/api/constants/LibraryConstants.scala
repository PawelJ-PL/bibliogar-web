package com.github.pawelj_pl.bibliogar.api.constants

import com.github.pawelj_pl.bibliogar.api.domain.library.{Library, LoanDurationUnit}

trait LibraryConstants extends CommonConstants with ResourcesIdentifiers {
  final val TestLibraryName = "My library"
  final val ExampleLibrary = Library(TestLibraryId, TestUserId, TestLibraryName, 2, LoanDurationUnit.Month, Now, Now)
}
