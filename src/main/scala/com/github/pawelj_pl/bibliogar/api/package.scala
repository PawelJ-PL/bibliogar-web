package com.github.pawelj_pl.bibliogar

import doobie.free.connection.ConnectionIO

package object api {
  type DB[A] = ConnectionIO[A]
}
