package com.github.jumale.sdebug

import java.time.LocalTime

class Debugger(
  var settings: Debugger.Settings = Debugger.Settings(),
  var formatter: Formatter = Formatter(),
  printer: String => Unit = Console.println
) {
  protected val headerColor: String = formatter.settings.palette.black
  protected val resetColor: String = formatter.settings.palette.reset
  protected val underlineColor: String = formatter.settings.palette.underlined
  private var print: String => Unit = printer

  def on(): Unit = print = printer
  def off(): Unit = print = _ => ()

  def log(msg: String): Unit = print {
    s"$timePrefix-> $msg $breadcrumbSidebar"
  }

  def dump(values: Any*): Unit = print {
    breadcrumbHeader + values.map(value => formatter(value) + footer(value)).mkString("\n")
  }

  def diff(actual: Any, expected: Any): Unit = print {
    breadcrumbHeader + formatter(actual, expected)
  }

  def trace(limit: Int = Int.MaxValue): Unit = print {
    breadcrumbHeader + traceException(new Exception).tail.take(limit).mkString("\n") + "\n" + resetColor
  }

  def sleep(millis: Long): Unit = sleep("", millis)

  def sleep(name: String, millis: Long): Unit = {
    print(s"$resetColor$timePrefix-> ⏱  ${millis}ms" + s" $name".stripSuffix(" ") + " " + breadcrumbSidebar)
    Thread.sleep(millis)
  }

  def measure[T](f: => T): T = measure("")(f)

  def measure[T](name: String)(f: => T): T = {
    val start = System.currentTimeMillis()
    val result = f
    val millis = System.currentTimeMillis() - start
    print(s"$resetColor$timePrefix-> ⏱  ${millis}ms" + s" $name".stripSuffix(" ") + " " + breadcrumbSidebar)
    result
  }

  def table(header: Seq[String], rows: Seq[Any]*): Unit = print {
    breadcrumbHeader + formatter(header, rows: _*)
  }

  def save(fileName: String)(v: Any*): Unit = saveBytes(fileName) {
    val fmt = formatter.copy(settings = formatter.settings.copy(colorize = false))
    val result = breadcrumbHeader + v.map(value => fmt(value) + footer(value)).mkString("\n")
    result.getBytes
  }

  def saveBytes(fileName: String)(v: Array[Byte]): Unit = {
    import java.io._
    val target = new BufferedOutputStream(new FileOutputStream(s"${settings.savingDir}/$fileName"))
    try v.foreach(target.write(_))
    finally target.close()
  }

  protected def header(title: String): String =
    (settings.showBreadcrumbs, settings.showTime) match {
      case (true, true)  => s"$headerColor$fmtTime...........($title).....................$resetColor\n"
      case (true, false) => s"$headerColor.....................($title).....................$resetColor\n"
      case _             => ""
    }

  protected def footer(value: Any): String = value match {
    case e: Throwable if settings.showTraces => "\n  " + traceException(e).mkString("\n  ")
    case _                                   => ""
  }

  protected def breadcrumbHeader: String =
    if (settings.showBreadcrumbs) header(underlineColor + breadcrumb().toString + resetColor + headerColor)
    else ""

  protected def breadcrumbSidebar: String =
    if (settings.showBreadcrumbs) headerColor + lineLink(breadcrumb()) + " " + thread + resetColor
    else ""

  protected def timePrefix: String =
    if (settings.showTime) headerColor + fmtTime + " " + resetColor
    else ""

  protected def fmtTime: String = LocalTime.now().toString

  protected def breadcrumb(idx: Int = 3): Stack.Line = Stack().lift(idx).getOrElse(Stack.Line.empty)

  protected def lineLink(line: Stack.Line): String = s"...(${line.toString})"

  protected def thread: String = s"[thread:${Thread.currentThread().getId}]"

  protected def traceException(value: Throwable): Seq[String] =
    value.getStackTrace.toIndexedSeq
      .take(settings.traceLimit)
      .map { se =>
        val clazz = resetColor + se.getClassName
        val method = se.getMethodName
        val file = se.getFileName + ":" + se.getLineNumber
        s"$clazz::$method($file)"
      }
}

object Debugger {
  final case class Settings(
    traceLimit: Int = 10,
    showTraces: Boolean = true,
    showBreadcrumbs: Boolean = true,
    showTime: Boolean = false,
    savingDir: String = "./target"
  )
}
