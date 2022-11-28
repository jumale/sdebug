package com.github.jumale.sdebug.scalatest

import com.github.jumale.sdebug.{Formatter, Palette}
import org.scalactic.PrettyPair

object Prettifier {
  def sbt(fmt: Formatter = Formatter()): org.scalactic.Prettifier =
    new org.scalactic.Prettifier {
      override def apply(o: Any): String =
        fmt(o) + fmt.settings.palette.reset

      override def apply(left: Any, right: Any): PrettyPair = PrettyPair( //
        left = left.toString,
        right = right.toString,
        analysis = Some(fmt(left, right) + fmt.settings.palette.reset)
      )
    }

  def ide(fmt: Formatter = Formatter()): org.scalactic.Prettifier =
    new org.scalactic.Prettifier {
      private val formatter = fmt.copy(settings = fmt.settings.copy(colorize = false))
      override def apply(o: Any): String =
        formatter(o)

      override def apply(left: Any, right: Any): PrettyPair = PrettyPair( //
        left = formatter(left),
        right = formatter(right),
        analysis = None
      )
    }
}
