package com.github.jumale.sdebug.playjson

import com.github.jumale.sdebug.{Debugger, Formatter}
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsNull, JsValue, Json}

final case class Foo(bar: Bar)
final case class Bar(json: JsValue)

class ExampleTest extends AnyWordSpec {
  val debug: Debugger = new Debugger(formatter = Formatter(extend = JsonFormatter))

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
    debug.dump(example)
  }
}
