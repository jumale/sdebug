package com.github.jumale.sdebug.playjson

import com.github.jumale.sdebug.FmtNode._
import com.github.jumale.sdebug.{Formatter, FmtNode}
import play.api.libs.json._

/** A formatter-extension, which transforms each type of PlayJson values into corresponding FmtNodes.
  */
object JsonFormatter extends PartialFunction[(Any, Formatter), FmtNode[Any]] {
  override def isDefinedAt(x: (Any, Formatter)): Boolean = impl(x._2).isDefinedAt(x._1)
  override def apply(x: (Any, Formatter)): FmtNode[Any] = impl(x._2).apply(x._1)

  private def impl(fmt: Formatter): PartialFunction[Any, FmtNode[Any]] = {
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
