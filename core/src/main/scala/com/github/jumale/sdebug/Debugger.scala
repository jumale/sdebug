package com.github.jumale.sdebug

import com.github.jumale.sdebug.Node.{
  BooleanNode,
  CollectionNode,
  EitherNode,
  ErrorNode,
  FutureNode,
  MapNode,
  NullNode,
  NumberNode,
  ObjectNode,
  OptionNode,
  RawNode,
  StringNode,
  TryNode
}

import scala.collection.{immutable, mutable}
import scala.concurrent.Future
import scala.util.Try

final case class Settings(
  indentSize: Int = 2,
  maxWidth: Int = 120,
  traceLimit: Int = 10,
  multiline: Boolean = true,
  showTraces: Boolean = true,
  showBreadcrumbs: Boolean = true,
  savingDir: String = "./target",
  palette: Palette = Palette.default
)

class Debugger(settings: Settings) {
  protected val palette: Palette = settings.palette
  protected val headerColor: String = palette.black
  protected val defaultColor: Colors = Colors(palette.reset, palette.reset, palette.reset)
  protected val strColor: Colors = Colors(palette.green, palette.black, palette.reset)
  protected val numColor: Colors = Colors(palette.cyan, palette.reset, palette.reset)
  protected val enumColor: Colors = Colors(palette.magenta, palette.reset, palette.reset)
  protected val futureColor: Colors = Colors(palette.cyan, palette.red, palette.reset)
  protected val boolColor: Colors = Colors(palette.red, palette.reset, palette.reset)
  protected val nullColor: Colors = Colors(palette.red + palette.bold, palette.reset, palette.reset)
  protected val arrColor: Colors = Colors(palette.blue, palette.blue + palette.underlined, palette.reset)
  protected val mapColor: Colors = Colors(palette.cyan, palette.cyan + palette.underlined, palette.reset)
  protected val objColor: Colors = Colors(palette.yellow, palette.reset, palette.reset)
  protected val errorColor: Colors = Colors(palette.yellow, palette.red, palette.reset)
  protected val diffColors: Colors = Colors(palette.green, palette.red, palette.reset)
  protected val tableColor: Colors = Colors(palette.black, palette.yellow, palette.reset)

  protected val renderParams: RenderParams = RenderParams( //
    indentSize = settings.indentSize,
    maxWidth = settings.maxWidth,
    multiline = settings.multiline
  )

  protected def write(s: String): Unit = Console.println(s)

  def apply(value: Any): Unit = write {
    value match {
      // if it's a short string - print it as a single-line log
      case str: String if str.length < settings.maxWidth =>
        s"-> $str $breadcrumbSidebar"

      // show exception stack-trace if enabled
      case e: Throwable if settings.showTraces =>
        breadcrumbHeader + prettify(e) + "\n  " + traceException(e).mkString("\n  ")

      // otherwise fully dump the value
      case _ => breadcrumbHeader + prettify(value)
    }
  }

  def diff(a: Any, b: Any): Unit = write {
    val d = Node.diff(toNode(a), toNode(b), diffColors)
    breadcrumbHeader + d.render(renderParams)
  }

  def trace(limit: Int = Int.MaxValue): Unit = write {
    breadcrumbHeader + traceException(new Exception).tail.take(limit).mkString("\n") + "\n" + palette.reset
  }

  def sleep(millis: Long): Unit = {
    write(s"-> ⏱  ${millis}ms $breadcrumbSidebar")
    Thread.sleep(millis)
  }

  def table(header: Seq[String], rows: Seq[Any]*): Unit = write {
    breadcrumbHeader + Table(header, rows.map(_.map(toNode)), tableColor).render(renderParams)
  }

  def save(v: Any, fileName: String): Unit = saveBytes(v.toString.getBytes, fileName)

  def saveBytes(v: Array[Byte], fileName: String): Unit = {
    import java.io._
    val target = new BufferedOutputStream(new FileOutputStream(s"${settings.savingDir}/$fileName"))
    try v.foreach(target.write(_))
    finally target.close()
  }

  def prettify(value: Any): String =
    toNode(value).render(renderParams)

  protected def header(title: String): String =
    if (settings.showBreadcrumbs) {
      s"$headerColor.....................($title).....................${palette.reset}\n"
    } else ""

  protected def breadcrumbHeader: String =
    if (settings.showBreadcrumbs) header(palette.underlined + breadcrumb(3).toString + palette.reset + headerColor)
    else ""

  protected def breadcrumbSidebar: String =
    if (settings.showBreadcrumbs) headerColor + lineLink(breadcrumb()) + " " + thread + palette.reset
    else ""

  protected def breadcrumb(idx: Int = 2): Stack.Line = Stack().lift(idx).getOrElse(Stack.Line.empty)

  protected def lineLink(line: Stack.Line): String = s"...(${line.toString})"

  protected def thread: String = s"[thread:${Thread.currentThread().getId}]"

  protected def traceException(value: Throwable): Seq[String] =
    value.getStackTrace.toIndexedSeq
      .take(settings.traceLimit)
      .map { se =>
        val clazz = palette.reset + se.getClassName
        val method = se.getMethodName
        val file = se.getFileName + ":" + se.getLineNumber
        s"$clazz::$method($file)"
      }

  // noinspection DuplicatedCode
  protected def toNode(value: Any): Node[Any] =
    value match { // scalafmt: { maxColumn = 170 }
      case v if v == null         => NullNode(nullColor)
      case s: String              => StringNode(s, strColor)
      case n: Int                 => NumberNode(n, numColor)
      case n: Long                => NumberNode(n, numColor)
      case n: BigInt              => NumberNode(n, numColor)
      case n: Double              => NumberNode(n, numColor)
      case n: Float               => NumberNode(n, numColor)
      case b: Boolean             => BooleanNode(b, boolColor)
      case o: Option[_]           => OptionNode(o.map(toNode), enumColor)
      case e: Either[_, _]        => EitherNode(e.map(toNode).left.map(toNode), enumColor)
      case e: Try[_]              => TryNode(e.map(toNode), enumColor, errorColor)
      case s: immutable.Seq[_]    => CollectionNode(s.getClass, s.map(toNode), arrColor)
      case s: immutable.Set[_]    => CollectionNode(s.getClass, s.map(toNode), arrColor)
      case m: immutable.Map[_, _] => MapNode(m.getClass, m.toVector.map(a => toNode(a._1) -> toNode(a._2)), mapColor)
      case s: mutable.Seq[_]      => CollectionNode(s.getClass, s.map(toNode), arrColor)
      case s: mutable.Set[_]      => CollectionNode(s.getClass, s.map(toNode), arrColor)
      case m: mutable.Map[_, _]   => MapNode(m.getClass, m.toVector.map(a => toNode(a._1) -> toNode(a._2)), mapColor)
      case f: Future[_]           => FutureNode(f, toNode, futureColor, errorColor)
      // scalafmt: { maxColumn = 120 }

      case p: Product =>
        val fields = Util.getProductFields(p)
        val values = p.productIterator.toVector
        // If we weren't able to match up fields/values, fall back to raw node.
        if (fields.length != values.length)
          RawNode(p, defaultColor)
        // if fields look like tuple
        else if (fields.forall(_.matches("^_\\d(\\$.*)?$")))
          CollectionNode(p.getClass, values.map(toNode), enumColor)
        // otherwise it's an object
        else
          ObjectNode(p.getClass, fields.zip(values).map { case (k, v) => toNode(k) -> toNode(v) }.toVector, objColor)

      case e: Throwable                                                   => ErrorNode(e, errorColor)
      case v if v.getClass.getInterfaces.contains(classOf[scala.Product]) => RawNode("!!!!!!", defaultColor)
      case v                                                              => RawNode(v, defaultColor)
    }
}

object Debugger {
  def apply(settings: Settings): Debugger = new Debugger(settings)
}
