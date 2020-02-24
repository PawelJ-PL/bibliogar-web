package com.github.pawelj_pl.bibliogar.api.infrastructure.repositories

import java.sql.SQLException

sealed trait DbError extends Throwable {
  def sqlError: SQLException
}

object DbError {
  final case class ForeignKeyViolation(sqlError: SQLException) extends DbError
}
