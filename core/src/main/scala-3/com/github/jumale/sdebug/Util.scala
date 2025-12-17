package com.github.jumale.sdebug

object Util {
  def getProductFields(p: Product): Vector[String] =
    p.productElementNames.toVector
}
