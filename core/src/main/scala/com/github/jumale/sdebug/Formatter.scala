package com.github.jumale.sdebug

import com.github.jumale.sdebug.FmtNode._

import scala.collection.{immutable, mutable}
import scala.concurrent.Future
import scala.util.Try
import scala.util.matching.Regex

final case class Formatter(
  settings: Formatter.Settings = Formatter.Settings.default(),
  extend: PartialFunction[(Any, Formatter), FmtNode[Any]] = PartialFunction.empty
) {
  private val renderParams: RenderParams = RenderParams( //
    indentSize = settings.indentSize,
    maxWidth = settings.maxWidth,
    multiline = settings.multiline,
    showKeys = settings.showKeys,
    colorize = settings.colorize
  )

  private var aliases: Map[Any, Any] = Map.empty

  private def resetColor = settings.defaultColor.reset(renderParams)

  def addAlias(from: Any, to: Any): Unit =
    aliases = aliases.updated(from, to)

  /** Format a single value to string.
    */
  def value(v: Any): String =
    toNode(v).render(renderParams) + resetColor

  /** Format a diff between two values.
    */
  def diff(prev: Any, next: Any): String =
    FmtNode.diff(toNode(prev), toNode(next), settings.diffColors).render(renderParams) + resetColor

  /** Format a table to string.
    */
  def table(header: String*)(rows: Seq[Any]*): String =
    Table(header, rows.map(_.map(toNode)), settings.tableColor).render(renderParams) + resetColor

  // noinspection DuplicatedCode
  def toNode(value: Any): FmtNode[Any] =
    aliases.get(value) match {
      case Some(alias) => toNode(alias)
      case None =>
        value match {
          case v if extend.isDefinedAt(v, this) => extend.apply(v, this)

          // scalafmt: { maxColumn = 170 }
          case v if v == null         => NullNode(settings.nullColor)
          case s: String              => StringNode(s, settings.strColor)
          case n: Int                 => NumberNode(n, settings.numColor)
          case n: Long                => NumberNode(n, settings.numColor)
          case n: BigInt              => NumberNode(n, settings.numColor)
          case n: Double              => NumberNode(n, settings.numColor)
          case n: Float               => NumberNode(n, settings.numColor)
          case b: Boolean             => BooleanNode(b, settings.boolColor)
          case o: Option[_]           => OptionNode(o.map(toNode), settings.coproductColor)
          case e: Either[_, _]        => EitherNode(e.map(toNode).left.map(toNode), settings.coproductColor)
          case e: Try[_]              => TryNode(e.map(toNode), settings.coproductColor, settings.errorColor)
          case s: immutable.Seq[_]    => CollectionNode(s.getClass, s.map(toNode), settings.arrColor, settings.classNames, None)
          case s: immutable.Set[_]    => CollectionNode(s.getClass, s.map(toNode), settings.arrColor, settings.classNames, None)
          case m: immutable.Map[_, _] => MapNode(m.getClass, m.toVector.map(a => toNode(a._1) -> toNode(a._2)), settings.mapColor)
          case s: mutable.Seq[_]      => CollectionNode(s.getClass, s.map(toNode), settings.arrColor, settings.classNames, None)
          case s: mutable.Set[_]      => CollectionNode(s.getClass, s.map(toNode), settings.arrColor, settings.classNames, None)
          case m: mutable.Map[_, _]   => MapNode(m.getClass, m.toVector.map(a => toNode(a._1) -> toNode(a._2)), settings.mapColor)
          case f: Future[_]           => FutureNode(f, toNode, settings.futureColor, settings.errorColor)
          // scalafmt: { maxColumn = 120 }

          case p: Product =>
            val fields = Util.getProductFields(p)
            val values = p.productIterator.toVector

            // If we weren't able to match up fields/values, fall back to raw node.
            if (fields.length != values.length)
              RawNode(p, settings.defaultColor)

            // if fields look like tuple
            else if (fields.nonEmpty && fields.forall(_.matches("^_\\d(\\$.*)?$")))
              CollectionNode(p.getClass, values.map(toNode), settings.coproductColor, settings.classNames, None)

            // otherwise it's an object
            else
              ObjectNode( //
                clazz = p.getClass,
                value = fields.zip(values).map { case (k, v) => toNode(k) -> toNode(v) },
                colors = settings.objColor,
                classNames = settings.classNames
              )

          case e: Throwable => ErrorNode(e, settings.errorColor)
          case v            => RawNode(v, settings.defaultColor)
        }
    }

}

object Formatter {

  /** @param indentSize
    *   Number of spaces for each level of indentation.
    * @param maxWidth
    *   Maximum width of a single line. Values that exceed this width will be split into multiple lines.
    * @param multiline
    *   Set it as FALSE to force-render all values as single-line, even if they exceed maxWidth.
    * @param showKeys
    *   Set it as FALSE to hide keys in maps and field-names in objects.
    * @param colorize
    *   Set it as FALSE to disable colors but keep formatting.
    * @param palette
    *   Colors palette for coloring output.
    * @param defaultColor
    *   Colors for default nodes (not mentioned in any further categories).
    * @param strColor
    *   Colors for string nodes.
    * @param numColor
    *   Colors for number nodes.
    * @param coproductColor
    *   Colors for standard coproduct-like nodes (Option, Either, Try, tuples).
    * @param futureColor
    *   Colors for Future nodes.
    * @param boolColor
    *   Colors for boolean nodes.
    * @param nullColor
    *   Colors for Java null nodes.
    * @param arrColor
    *   Colors for array-like nodes.
    * @param mapColor
    *   Colors for map-like nodes.
    * @param objColor
    *   Colors for object-like nodes.
    * @param errorColor
    *   Colors for error nodes.
    * @param diffColors
    *   Colors for diff nodes.
    * @param tableColor
    *   Colors for table nodes.
    */
  final case class Settings(
    indentSize: Int,
    maxWidth: Int,
    multiline: Boolean,
    showKeys: Boolean,
    colorize: Boolean,
    classNames: ClassNameSettings,
    palette: Palette,
    defaultColor: NodeColors,
    strColor: NodeColors,
    numColor: NodeColors,
    coproductColor: NodeColors,
    futureColor: NodeColors,
    boolColor: NodeColors,
    nullColor: NodeColors,
    arrColor: NodeColors,
    mapColor: NodeColors,
    objColor: NodeColors,
    errorColor: NodeColors,
    diffColors: NodeColors,
    tableColor: NodeColors
  )

  object Settings {
    def default(
      indentSize: Int = 2,
      maxWidth: Int = 120,
      multiline: Boolean = true,
      showNames: Boolean = true,
      colorize: Boolean = true,
      classNames: ClassNameSettings = ClassNameSettings(full = false, replace = Seq.empty),
      palette: Palette = Palette.console
    ): Settings = Settings(
      indentSize = indentSize,
      maxWidth = maxWidth,
      multiline = multiline,
      showKeys = showNames,
      colorize = colorize,
      palette = palette,
      classNames = classNames,
      defaultColor = NodeColors(palette.reset, palette.reset, palette.reset),
      strColor = NodeColors(palette.green, palette.black, palette.reset),
      numColor = NodeColors(palette.cyan, palette.reset, palette.reset),
      coproductColor = NodeColors(palette.magenta, palette.reset, palette.reset),
      futureColor = NodeColors(palette.cyan, palette.red, palette.reset),
      boolColor = NodeColors(palette.red, palette.reset, palette.reset),
      nullColor = NodeColors(palette.red + palette.bold, palette.reset, palette.reset),
      arrColor = NodeColors(palette.blue, palette.blue + palette.underlined, palette.reset),
      mapColor = NodeColors(palette.cyan, palette.yellow, palette.reset),
      objColor = NodeColors(palette.yellow, palette.blue, palette.reset),
      errorColor = NodeColors(palette.yellow, palette.red, palette.reset),
      diffColors = NodeColors(palette.greenBg + palette.white, palette.redBg + palette.white, palette.reset),
      tableColor = NodeColors(palette.black, palette.yellow, palette.reset)
    )
  }

  case class ClassNameSettings(full: Boolean, replace: Seq[(Regex, String)])
}
