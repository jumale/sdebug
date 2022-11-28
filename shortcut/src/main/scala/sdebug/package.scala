import com.github.jumale.sdebug.playjson.JsonFormatter
import com.github.jumale.sdebug.scalatest.Prettifier
import com.github.jumale.sdebug.{Debugger, Formatter}

package object sdebug extends Debugger(formatter = Formatter(extend = JsonFormatter)) {
  def showTime(): Unit = settings = settings.copy(showTime = true)

  implicit val scalatestPrettifierSBT: org.scalactic.Prettifier = Prettifier.sbt(formatter)
  implicit val scalatestPrettifierIDE: org.scalactic.Prettifier = Prettifier.ide(formatter)
}
