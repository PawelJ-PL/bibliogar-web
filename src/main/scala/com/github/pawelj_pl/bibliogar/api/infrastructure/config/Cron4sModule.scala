package com.github.pawelj_pl.bibliogar.api.infrastructure.config

import cron4s.{Cron, CronExpr}
import pureconfig.ConfigConvert
import pureconfig.error.CannotConvert

//Source: https://github.com/pureconfig/pureconfig/blob/master/modules/cron4s/src/main/scala/pureconfig/module/cron4s/package.scala
object Cron4sModule {
  implicit val cron4sPureconfigModule: ConfigConvert[CronExpr] = ConfigConvert.viaNonEmptyString(
    str => Cron.parse(str).fold(
      err  => Left(CannotConvert(str, "CronExpr", err.getMessage)),
      expr => Right(expr)), _.toString)
}
