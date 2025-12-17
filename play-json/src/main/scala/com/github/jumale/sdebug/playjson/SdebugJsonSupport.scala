package com.github.jumale.sdebug.playjson

import play.api.libs.json.{Json, Reads}
import com.github.jumale.sdebug.Debugger

trait SdebugJsonSupport { self: Debugger =>
  def readJson[T: Reads](fileName: String): T = Json.parse(read(fileName)).as[T]
}
