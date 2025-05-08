import com.github.jumale.sdebug.playjson.JsonFormatter
import com.github.jumale.sdebug.{Debugger, Formatter, NodeColors, Palette}
import com.github.jumale.sdebug.scalactic.SdebugScalacticPrettifier
import org.scalactic.{Prettifier, PrettyPair}

package object sdebug extends Debugger(formatter = Formatter(extend = JsonFormatter)) {
  // Reset-color may look red in scalatest outputs, so we better replace it with explicit white
  private val prettifierFormatter = formatter.copy(settings =
    formatter.settings.copy(defaultColor =
      NodeColors(
        primaryColor = Palette.console.white,
        secondaryColor = Palette.console.white,
        resetColor = Palette.console.reset
      )
    )
  )

  implicit val scalacticPrettifier: Prettifier =
    new SdebugScalacticPrettifier(prettifierFormatter)

  implicit val scalacticPrettifierAnalyse: Prettifier =
    new SdebugScalacticPrettifier(prettifierFormatter, printAnalysis = true)
}
