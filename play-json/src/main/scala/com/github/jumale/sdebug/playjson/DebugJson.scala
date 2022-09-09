package com.github.jumale.sdebug.playjson

import com.github.jumale.sdebug.Node.{BooleanNode, CollectionNode, MapNode, NullNode, NumberNode, StringNode}
import com.github.jumale.sdebug.{Debugger, Node, Settings}
import play.api.libs.json.{JsArray, JsBoolean, JsNull, JsNumber, JsObject, JsString}

class DebugJson(settings: Settings) extends Debugger(settings) {
  override def toNode(value: Any): Node[Any] =
    value match {
      case JsNull       => NullNode(nullColor)
      case s: JsString  => StringNode(s.value, strColor)
      case n: JsNumber  => NumberNode(n.value, numColor)
      case b: JsBoolean => BooleanNode(b.value, boolColor)
      case a: JsArray   => CollectionNode(a.getClass, a.value.map(toNode), arrColor, customName = Some("Json.arr"))
      case o: JsObject =>
        MapNode(
          o.getClass,
          o.fields.toVector.sortBy(_._1).map { case (k, v) => StringNode(k, strColor) -> toNode(v) },
          mapColor,
          customName = Some("Json.obj")
        )

      case _ => super.toNode(value)
    }
}

object DebugJson {
  def apply(settings: Settings): DebugJson = new DebugJson(settings)
}
