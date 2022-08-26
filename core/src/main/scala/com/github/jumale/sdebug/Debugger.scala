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

class Debugger {
  protected val indentSize: Int = 2
  protected val maxWidth: Int = 120
  protected val traceLimit: Int = 10
  protected val multiline: Boolean = true
  protected val showTraces: Boolean = true
  protected val showBreadcrumbs: Boolean = true
  protected val savingDir: String = "./target"

  protected val palette: Palette = Palette.default
  protected val headerColor: String = palette.black
  protected val defaultColor: Colors = Colors(palette.reset, palette.reset, palette.reset)
  protected val strColor: Colors = Colors(palette.green, palette.reset, palette.reset)
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

  def apply(value: Any): Unit = Console.println {
    value match {
      // if it's a short string - print it as a single-line log
      case str: String if str.length < maxWidth =>
        val line = lineLink(breadcrumb())
        s"-> $str $headerColor$line $thread${palette.reset}"

      // show exception stack-trace if enabled
      case e: Throwable if showTraces =>
        breadcrumbHeader + prettify(e) + "\n  " + traceException(e).mkString("\n  ")

      // otherwise fully dump the value
      case _ => breadcrumbHeader + prettify(value)
    }
  }

  def diff(a: Any, b: Any): Unit = Console.println {
    val d = Node.diff(toNode(a), toNode(b), diffColors)
    breadcrumbHeader + d.render(RenderParams(indentSize = indentSize, maxWidth = maxWidth, multiline = multiline))
  }

  def prettify(value: Any): String =
    toNode(value).render(RenderParams(indentSize = indentSize, maxWidth = maxWidth, multiline = multiline))

  def trace(limit: Int = Int.MaxValue): Unit = Console.println {
    breadcrumbHeader + traceException(new Exception).tail.take(limit).mkString("\n") + "\n" + palette.reset
  }

  def sleep(millis: Long): Unit = {
    val link = if (showBreadcrumbs) lineLink(breadcrumb()) else ""
    Console.println(s"-> ⏱  ${millis}ms $headerColor$link $thread${palette.reset}")
    Thread.sleep(millis)
  }

  def save(v: Any, fileName: String): Unit = saveBytes(v.toString.getBytes, fileName)

  def saveBytes(v: Array[Byte], fileName: String): Unit = {
    import java.io._
    val target = new BufferedOutputStream(new FileOutputStream(s"${savingDir}/$fileName"))
    try v.foreach(target.write(_))
    finally target.close()
  }

  protected def header(title: String): String =
    if (showBreadcrumbs) {
      s"$headerColor.....................($title).....................${palette.reset}\n"
    } else ""

  protected def breadcrumbHeader: String =
    header(palette.underlined + breadcrumb(3).toString + palette.reset + headerColor)

  protected def breadcrumb(idx: Int = 2): Stack.Line = Stack().lift(idx).getOrElse(Stack.Line.empty)

  protected def lineLink(line: Stack.Line): String = s"...(${line.toString})"

  protected def thread: String = s"[thread:${Thread.currentThread().getId}]"

  protected def traceException(value: Throwable): Seq[String] =
    value.getStackTrace.toIndexedSeq
      .take(traceLimit)
      .map { se =>
        val clazz = palette.reset + se.getClassName
        val method = se.getMethodName
        val file = se.getFileName + ":" + se.getLineNumber
        s"$clazz::$method($file)"
      }

  def toNode(value: Any): Node[Any] =
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
        val fields = value.getClass.getDeclaredFields.filterNot(_.isSynthetic).map(_.getName)
        val values = p.productIterator.toSeq
        // If we weren't able to match up fields/values, fall back to raw node.
        if (fields.length != values.length)
          RawNode(p, defaultColor)
        // if fields look like tuple
        else if (fields.toSeq == fields.toSeq.zipWithIndex.map { case (_, i) => s"_${i + 1}" })
          CollectionNode(p.getClass, values.map(toNode), enumColor)
        // otherwise it's an object
        else
          ObjectNode(p.getClass, fields.zip(values).map { case (k, v) => toNode(k) -> toNode(v) }.toVector, objColor)

      case e: Throwable => ErrorNode(e, errorColor)
      case v =>
        println(v.getClass.getName)
        RawNode(v, defaultColor)
    }
}

object Debugger {
  def apply(): Debugger = new Debugger
}
