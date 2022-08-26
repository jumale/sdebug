package com.github.jumale.sdebug

import org.scalatest.wordspec.AnyWordSpec
import com.github.jumale.sdebug.playjson.DebugJson
import play.api.libs.json.{JsNull, JsValue, Json}

final case class Foo(bar: Bar)
final case class Bar(json: JsValue)

class ExampleTest extends AnyWordSpec {
  val debug: Debugger = DebugJson()

  val example: Foo = Foo(
    Bar(
      Json.obj(
        "type" -> "object",
        "properties" -> Json.arr(
          Json.obj(
            "code" -> Json.obj("type" -> "integer", "example" -> 42),
            "required" -> true,
            "tags" -> Json.arr("foo", "bar"),
            "message" -> Json.obj("type" -> "string"),
            "order" -> 12.0042,
            "xml" -> JsNull
          )
        )
      )
    )
  )

  "dump json" in {
    debug(example)
  }
}
