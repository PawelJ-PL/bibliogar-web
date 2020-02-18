package com.github.pawelj_pl.bibliogar.api

import com.softwaremill.diffx.{Derived, Diff}
import org.http4s.Uri

object Implicits {
  object Uri {
    implicit val uriDiff: Derived[Diff[Uri]] = Derived(Diff[String].contramap[Uri](_.renderString))
  }
}
