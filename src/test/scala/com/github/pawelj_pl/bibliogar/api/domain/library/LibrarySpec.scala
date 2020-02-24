package com.github.pawelj_pl.bibliogar.api.domain.library

import java.time.Period

import com.github.pawelj_pl.bibliogar.api.constants.LibraryConstants
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class LibrarySpec extends AnyWordSpec with Matchers with LibraryConstants {
  "Loan period" should {
    "return 1 day" in {
      val library = ExampleLibrary.copy(loanDurationValue = 1, loanDurationUnit = LoanDurationUnit.Day)
      val result = library.loanPeriod
      result shouldBe Period.of(0, 0, 1)
    }
    "return days" in {
      val library = ExampleLibrary.copy(loanDurationValue = 5, loanDurationUnit = LoanDurationUnit.Day)
      val result = library.loanPeriod
      result shouldBe Period.of(0, 0, 5)
    }
    "return weeks" in {
      val library = ExampleLibrary.copy(loanDurationValue = 3, loanDurationUnit = LoanDurationUnit.Week)
      val result = library.loanPeriod
      result shouldBe Period.of(0, 0, 21)
    }
    "return months" in {
      val library = ExampleLibrary.copy(loanDurationValue = 3, loanDurationUnit = LoanDurationUnit.Month)
      val result = library.loanPeriod
      result shouldBe Period.of(0, 3, 0)
    }
    "return years" in {
      val library = ExampleLibrary.copy(loanDurationValue = 3, loanDurationUnit = LoanDurationUnit.Year)
      val result = library.loanPeriod
      result shouldBe Period.of(3, 0, 0)
    }
  }
}
