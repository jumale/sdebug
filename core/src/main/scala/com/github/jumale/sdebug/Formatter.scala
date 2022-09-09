package com.github.jumale.sdebug

import com.github.jumale.sdebug.Node._

import scala.collection.{immutable, mutable}
import scala.concurrent.Future
import scala.util.Try

final case class Formatter(
  settings: Formatter.Settings = Formatter.Settings(),
  extend: PartialFunction[(Any, Formatter), Node[Any]] = PartialFunction.empty
) {
  protected val renderParams: RenderParams = RenderParams( //
    indentSize = settings.indentSize,
    maxWidth = settings.maxWidth,
    multiline = settings.multiline,
    colorize = settings.colorize
  )

  def apply(value: Any): String =
    toNode(value).render(renderParams)

  def apply(a: Any, b: Any): String =
    Node.diff(toNode(a), toNode(b), settings.diffColors).render(renderParams)

  def apply(header: Seq[String], rows: Seq[Any]*): String =
    Table(header, rows.map(_.map(toNode)), settings.tableColor).render(renderParams)

  // noinspection DuplicatedCode
  def toNode(value: Any): Node[Any] =
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
      case o: Option[_]           => OptionNode(o.map(toNode), settings.enumColor)
      case e: Either[_, _]        => EitherNode(e.map(toNode).left.map(toNode), settings.enumColor)
      case e: Try[_]              => TryNode(e.map(toNode), settings.enumColor, settings.errorColor)
      case s: immutable.Seq[_]    => CollectionNode(s.getClass, s.map(toNode), settings.arrColor)
      case s: immutable.Set[_]    => CollectionNode(s.getClass, s.map(toNode), settings.arrColor)
      case m: immutable.Map[_, _] => MapNode(m.getClass, m.toVector.map(a => toNode(a._1) -> toNode(a._2)), settings.mapColor)
      case s: mutable.Seq[_]      => CollectionNode(s.getClass, s.map(toNode), settings.arrColor)
      case s: mutable.Set[_]      => CollectionNode(s.getClass, s.map(toNode), settings.arrColor)
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
        else if (fields.forall(_.matches("^_\\d(\\$.*)?$")))
          CollectionNode(p.getClass, values.map(toNode), settings.enumColor)

        // otherwise it's an object
        else
          ObjectNode( //
            p.getClass,
            fields.zip(values).map { case (k, v) => toNode(k) -> toNode(v) },
            settings.objColor
          )

      case e: Throwable => ErrorNode(e, settings.errorColor)
      case v            => RawNode(v, settings.defaultColor)
    }
}

object Formatter {
  final case class Settings(
    indentSize: Int,
    maxWidth: Int,
    multiline: Boolean,
    colorize: Boolean,
    palette: Palette,
    defaultColor: Colors,
    strColor: Colors,
    numColor: Colors,
    enumColor: Colors,
    futureColor: Colors,
    boolColor: Colors,
    nullColor: Colors,
    arrColor: Colors,
    mapColor: Colors,
    objColor: Colors,
    errorColor: Colors,
    diffColors: Colors,
    tableColor: Colors
  )

  object Settings {
    def apply(
      indentSize: Int = 2,
      maxWidth: Int = 120,
      multiline: Boolean = true,
      colorize: Boolean = true,
      palette: Palette = Palette.default
    ): Settings = Settings(
      indentSize = indentSize,
      maxWidth = maxWidth,
      multiline = multiline,
      colorize = colorize,
      palette = palette,
      defaultColor = Colors(palette.reset, palette.reset, palette.reset),
      strColor = Colors(palette.green, palette.black, palette.reset),
      numColor = Colors(palette.cyan, palette.reset, palette.reset),
      enumColor = Colors(palette.magenta, palette.reset, palette.reset),
      futureColor = Colors(palette.cyan, palette.red, palette.reset),
      boolColor = Colors(palette.red, palette.reset, palette.reset),
      nullColor = Colors(palette.red + palette.bold, palette.reset, palette.reset),
      arrColor = Colors(palette.blue, palette.blue + palette.underlined, palette.reset),
      mapColor = Colors(palette.cyan, palette.cyan + palette.underlined, palette.reset),
      objColor = Colors(palette.yellow, palette.reset, palette.reset),
      errorColor = Colors(palette.yellow, palette.red, palette.reset),
      diffColors = Colors(palette.green, palette.red, palette.reset),
      tableColor = Colors(palette.black, palette.yellow, palette.reset)
    )
  }
}
