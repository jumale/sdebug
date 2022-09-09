package com.github.jumale.sdebug.playjson

import com.github.jumale.sdebug.Node._
import com.github.jumale.sdebug.{Formatter, Node}
import play.api.libs.json._

object JsonFormatter extends PartialFunction[(Any, Formatter), Node[Any]] {
  override def isDefinedAt(x: (Any, Formatter)): Boolean = impl(x._2).isDefinedAt(x._1)
  override def apply(x: (Any, Formatter)): Node[Any] = impl(x._2).apply(x._1)

  private def impl(fmt: Formatter): PartialFunction[Any, Node[Any]] = {
    case JsNull       => NullNode(fmt.settings.nullColor)
    case s: JsString  => StringNode(s.value, fmt.settings.strColor)
    case n: JsNumber  => NumberNode(n.value, fmt.settings.numColor)
    case b: JsBoolean => BooleanNode(b.value, fmt.settings.boolColor)

    case a: JsArray =>
      CollectionNode(a.getClass, a.value.map(fmt.toNode), fmt.settings.arrColor, customName = Some("Json.arr"))

    case o: JsObject =>
      MapNode(
        o.getClass,
        o.fields.toVector.sortBy(_._1).map { case (k, v) => StringNode(k, fmt.settings.strColor) -> fmt.toNode(v) },
        fmt.settings.mapColor,
        customName = Some("Json.obj")
      )
  }

}
