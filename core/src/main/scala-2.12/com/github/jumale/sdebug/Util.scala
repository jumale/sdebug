package com.github.jumale.sdebug

object Util {
  def getProductFields(p: Product): Vector[String] =
    p.getClass.getDeclaredFields.filterNot(_.isSynthetic).map(_.getName).toVector
}
