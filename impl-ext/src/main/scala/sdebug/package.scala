import com.github.jumale.sdebug.playjson.JsonFormatter
import com.github.jumale.sdebug.{Debugger, Formatter, NodeColors, Palette}
import com.github.jumale.sdebug.scalactic.SdebugScalacticPrettifier
import org.scalactic.{Prettifier, PrettyPair}
import play.api.libs.json.{JsValue, Json, Reads}

package object sdebug extends Debugger(formatter = Formatter(extend = JsonFormatter)) {
  // Reset-color may look red in scalatest outputs, so we better replace it with explicit white
  private def prettifierFormatter = formatter.copy(settings =
    formatter.settings.copy(defaultColor =
      NodeColors(
        primaryColor = Palette.console.white,
        secondaryColor = Palette.console.white,
        resetColor = Palette.console.reset
      )
    )
  )

  implicit def scalacticPrettifier: Prettifier =
    new SdebugScalacticPrettifier(prettifierFormatter)

  implicit def scalacticPrettifierAnalyse: Prettifier =
    new SdebugScalacticPrettifier(prettifierFormatter, printAnalysis = true)

  def readJson[T: Reads](fileName: String): T =
    Json.parse(read(fileName)).as[T]
}
