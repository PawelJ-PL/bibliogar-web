package com.github.pawelj_pl.bibliogar.api.infrastructure.utils

import com.vdurmont.semver4j.Semver

import scala.util.Try

object SemverWrapper {
  def of(version: String): Either[Throwable, Semver] = Try(new Semver(version)).toEither

  implicit class SemverOps(thisVersion: Semver) {
    def ===(otherVersion: Semver): Boolean = thisVersion.isEqualTo(otherVersion)
    def >=(otherVersion: Semver): Boolean = thisVersion.isGreaterThanOrEqualTo(otherVersion)
    def >(otherVersion: Semver): Boolean = thisVersion.isGreaterThan(otherVersion)
    def <=(otherVersion: Semver): Boolean = thisVersion.isLowerThanOrEqualTo(otherVersion)
    def <(otherVersion: Semver): Boolean = thisVersion.isLowerThan(otherVersion)
  }
}
