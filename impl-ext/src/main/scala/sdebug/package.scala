import com.github.jumale.sdebug.playjson.{JsonFormatter, SdebugJsonSupport}
import com.github.jumale.sdebug.scalactic.SdebugScalacticSupport
import com.github.jumale.sdebug.slick.SdebugSlickSupport
import com.github.jumale.sdebug.{Debugger, Formatter}

import scala.language.implicitConversions

package object sdebug extends Debugger(formatter = Formatter(extend = JsonFormatter)) with SdebugJsonSupport with SdebugSlickSupport with SdebugScalacticSupport {
  object implicits  extends DebuggerImplicits with SdebugSlickImplicits with SdebugScalacticImplicits
}
