import com.github.jumale.sdebug.playjson.JsonFormatter
import com.github.jumale.sdebug.{Debugger, Formatter}
import com.github.jumale.sdebug.scalactic.SdebugScalacticPrettifier
import org.scalactic.{Prettifier, PrettyPair}

package object sdebug extends Debugger(formatter = Formatter(extend = JsonFormatter)) {
  implicit val scalacticPrettifier: Prettifier = new SdebugScalacticPrettifier(formatter)
}
