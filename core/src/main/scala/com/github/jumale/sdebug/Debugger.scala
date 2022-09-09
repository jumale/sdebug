package com.github.jumale.sdebug

class Debugger(
  val settings: Debugger.Settings = Debugger.Settings(),
  val formatter: Formatter = Formatter(),
  printer: String => Unit = Console.println
) {
  protected val headerColor: String = formatter.settings.palette.black
  protected val resetColor: String = formatter.settings.palette.reset
  protected val underlineColor: String = formatter.settings.palette.underlined

  def log(msg: String): Unit = printer {
    s"-> $msg $breadcrumbSidebar"
  }

  def dump(value: Any): Unit = printer {
    val footer = value match {
      case e: Throwable if settings.showTraces => "\n  " + traceException(e).mkString("\n  ")
      case _                                   => ""
    }
    breadcrumbHeader + formatter(value) + footer
  }

  def diff(a: Any, b: Any): Unit = printer {
    breadcrumbHeader + formatter(a, b)
  }

  def trace(limit: Int = Int.MaxValue): Unit = printer {
    breadcrumbHeader + traceException(new Exception).tail.take(limit).mkString("\n") + "\n" + resetColor
  }

  def sleep(millis: Long): Unit = {
    printer(s"${resetColor}-> ⏱  ${millis}ms $breadcrumbSidebar")
    Thread.sleep(millis)
  }

  def table(header: Seq[String], rows: Seq[Any]*): Unit = printer {
    breadcrumbHeader + formatter(header, rows: _*)
  }

  def save(v: Any, fileName: String): Unit = saveBytes(v.toString.getBytes, fileName)

  def saveBytes(v: Array[Byte], fileName: String): Unit = {
    import java.io._
    val target = new BufferedOutputStream(new FileOutputStream(s"${settings.savingDir}/$fileName"))
    try v.foreach(target.write(_))
    finally target.close()
  }

  protected def header(title: String): String =
    if (settings.showBreadcrumbs) {
      s"$headerColor.....................($title).....................$resetColor\n"
    } else ""

  protected def breadcrumbHeader: String =
    if (settings.showBreadcrumbs) header(underlineColor + breadcrumb(3).toString + resetColor + headerColor)
    else ""

  protected def breadcrumbSidebar: String =
    if (settings.showBreadcrumbs) headerColor + lineLink(breadcrumb(3)) + " " + thread + resetColor
    else ""

  protected def breadcrumb(idx: Int = 2): Stack.Line = Stack().lift(idx).getOrElse(Stack.Line.empty)

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
    savingDir: String = "./target"
  )
}
