import com.github.jumale.sdebug.playjson.DebugJson
import com.github.jumale.sdebug.Settings

package object sdebug {
  var maxWidth: Int = 120

  val debug: DebugJson = DebugJson(Settings(maxWidth = maxWidth))

  def prettyPrint(v: Any): Unit = debug.apply(v)
  def diff(a: Any, b: Any): Unit = debug.diff(a, b)
}
