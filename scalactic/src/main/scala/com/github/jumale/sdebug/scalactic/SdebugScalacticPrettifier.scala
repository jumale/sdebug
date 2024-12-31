package com.github.jumale.sdebug.scalactic

import com.github.jumale.sdebug.Formatter
import org.scalactic.{Prettifier, PrettyPair}

/** And implementation of scalactic.Prettifier to format values for console output, using the provided sdebug-formatter.
  */
class SdebugScalacticPrettifier(
  fmt: Formatter = Formatter(),
  enableAnalysis: Boolean = true,
  resetColors: Boolean = true,
  prefix: String = "",
  analysisPrefix: String = ""
) extends Prettifier {
  private def reset = if (resetColors) fmt.settings.palette.reset else ""

  override def apply(o: Any): String =
    prefix + fmt.value(o) + reset

  override def apply(left: Any, right: Any): PrettyPair = PrettyPair(
    left = left.toString,
    right = right.toString,
    analysis = if (enableAnalysis) Some(analysisPrefix + fmt.diff(left, right) + reset) else None
  )
}
