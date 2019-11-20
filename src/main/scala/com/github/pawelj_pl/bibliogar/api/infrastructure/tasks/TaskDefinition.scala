package com.github.pawelj_pl.bibliogar.api.infrastructure.tasks

import cron4s.expr.CronExpr

trait TaskDefinition[F[_]] {
  def cron: CronExpr
  def task: F[Unit]
}
