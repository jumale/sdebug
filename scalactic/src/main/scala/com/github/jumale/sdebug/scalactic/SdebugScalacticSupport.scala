package com.github.jumale.sdebug.scalactic

import com.github.jumale.sdebug.{Debugger, NodeColors, Palette}
import org.scalactic.Prettifier

trait SdebugScalacticSupport  { self: Debugger =>
  private var _printAnalysis: Boolean = false

  def printAnalysis(enabled: Boolean = true): Unit =
    _printAnalysis = enabled


  trait SdebugScalacticImplicits {
    implicit def sdebugScalacticPrettifier: Prettifier =
      new SdebugScalacticPrettifier(
        printAnalysis = _printAnalysis,
        fmt = formatter.copy(settings =
        // Default color can look red in scalatest outputs, so we better replace it with explicit white
          formatter.settings.copy(defaultColor =
            NodeColors(
              primaryColor = Palette.console.white,
              secondaryColor = Palette.console.white,
              resetColor = Palette.console.reset
            )
          )
        )
      )
  }
}
