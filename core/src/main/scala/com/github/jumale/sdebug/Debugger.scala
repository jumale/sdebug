package com.github.jumale.sdebug

import java.time.LocalTime

//noinspection ScalaWeakerAccess,ScalaUnusedSymbol
class Debugger(
  protected var settings: Debugger.Settings = Debugger.Settings.default(),
  protected var formatter: Formatter = Formatter(),
  protected val printer: String => Unit = Console.println
) {
  protected def resetColor: String = getColor(_.reset)
  protected def headerColor: String = getColor(_.black)
  protected def underlineColor: String = getColor(_.underlined)
  protected var doPrint: String => Unit = printer

  def getFormatter: Formatter = formatter

  /** Enable printing. */
  def on(): Unit = doPrint = printer

  /** Suppress printing until it's enabled again. */
  def off(): Unit = doPrint = _ => ()

  /** Override the 'showTime' setting. */
  def setShowTime(showTime: Boolean): Unit =
    settings = settings.copy(showTime = showTime)

  /** Override the 'colorize' setting. */
  def setColorize(colorize: Boolean): Unit =
    formatter = formatter.copy(settings = formatter.settings.copy(colorize = colorize))

  /** Override the 'errorTraceLimit' setting. */
  def setErrorTraceLimit(size: Int): Unit =
    settings = settings.copy(errorTraceLimit = size)

  /** Print a single-line message log. */
  def log(msg: String): Unit = doPrint {
    s"$timePrefix-> $msg ${breadcrumbSidebar()}"
  }

  /** Format and print provided values */
  def print(values: Any*): Unit = doPrint {
    breadcrumbHeader + values.map(value => formatter.value(value) + footer(value)).mkString("\n")
  }

  /** Print a diff between the two provided values. */
  def diff(prev: Any, next: Any): Unit = doPrint {
    breadcrumbHeader + formatter.diff(prev, next)
  }

  /** Print a table with provided header and rows. */
  def table(header: String*)(rows: Seq[Any]*): Unit = doPrint {
    breadcrumbHeader + formatter.table(header: _*)(rows: _*)
  }

  /** Print a stack trace for the current context. */
  def trace(limit: Int = Int.MaxValue): Unit = doPrint {
    breadcrumbHeader + traceError(new Exception).tail.take(limit).mkString("\n") + "\n" + resetColor
  }

  /** Call thread-sleep with a breadcrumb. */
  def sleep(millis: Long): Unit = doSleep(millis)

  /** Call thread-sleep with a named breadcrumb. */
  def sleep(name: String, millis: Long): Unit = doSleep(millis, name)

  private def doSleep(millis: Long, name: String = ""): Unit = {
    doPrint(s"$resetColor$timePrefix-> ⏱  ${millis}ms" + fmtLogName(name) + breadcrumbSidebar(idx = 4))
    Thread.sleep(millis)
  }

  /** Measure and print execution time of the provided code-block. */
  def measure[T](f: => T): T = doMeasure(f)

  /** Measure and print (with a custom name) execution time of the provided code-block. */
  def measure[T](name: String)(f: => T): T = doMeasure(f, name)

  private def doMeasure[T](f: => T, name: String = ""): T = {
    val start = System.currentTimeMillis()
    val result = f
    val millis = System.currentTimeMillis() - start
    doPrint(s"$resetColor$timePrefix-> ⏱  ${millis}ms" + fmtLogName(name) + breadcrumbSidebar(idx = 4))
    result
  }

  /** Save formatted variables into a file. */
  def formatAndSave(fileName: String)(v: Any*): Unit = save(fileName) {
    val fmt = formatter.copy(settings = formatter.settings.copy(colorize = false))
    val result = breadcrumbHeader + v.map(value => fmt.value(value) + footer(value)).mkString("\n")
    result
  }

  /** Save contents into a file. */
  def save(fileName: String)(contents: String): Unit = {
    import java.io._
    val target = new BufferedOutputStream(new FileOutputStream(s"${settings.savingDir}/$fileName"))
    try target.write(contents.getBytes)
    finally target.close()
  }

  protected def getColor(targetColor: Palette => String): String =
    if (formatter.settings.colorize) targetColor(formatter.settings.palette)
    else ""

  private def fmtLogName(n: String): String = {
    val trimmed = n.trim
    if (trimmed.isEmpty) "" else s" ${trimmed.stripSuffix(" ")} "
  }

  protected def header(title: String): String = {
    lazy val prefix =
      if (settings.showTime)
        s"$fmtTime..........."
      else
        "....................."

    if (settings.showBreadcrumbs)
      s"$headerColor$prefix($title).....................$resetColor\n"
    else
      ""
  }

  protected def footer(value: Any): String = value match {
    case e: Throwable if settings.showErrorTraces => "\n  " + traceError(e).mkString("\n  ")
    case _                                        => ""
  }

  protected def breadcrumbHeader: String =
    if (settings.showBreadcrumbs) header(underlineColor + breadcrumb().toString + resetColor + headerColor)
    else ""

  protected def breadcrumbSidebar(idx: Int = 3): String =
    if (settings.showBreadcrumbs) headerColor + lineLink(breadcrumb(idx)) + " " + thread + resetColor
    else ""

  protected def timePrefix: String =
    if (settings.showTime) headerColor + fmtTime + " " + resetColor
    else ""

  protected def fmtTime: String = LocalTime.now().toString

  protected def breadcrumb(idx: Int = 3): Stack.Line = Stack().lift(idx).getOrElse(Stack.Line.empty)

  protected def lineLink(line: Stack.Line): String = s"...(${line.toString})"

  protected def thread: String = s"[thread:${Thread.currentThread().getId}]"

  protected def traceError(value: Throwable): Seq[String] =
    value.getStackTrace.toIndexedSeq
      .take(settings.errorTraceLimit)
      .map { se =>
        val clazz = resetColor + se.getClassName
        val method = se.getMethodName
        val file = se.getFileName + ":" + se.getLineNumber
        s"$clazz::$method($file)"
      }
}

object Debugger {

  /** @param errorTraceLimit
    *   Maximum number of stack-trace lines to show when tracing errors (if enabled).
    * @param showErrorTraces
    *   Show stack-trace when printing errors.
    * @param showBreadcrumbs
    *   Show breadcrumbs in the header, which lead to the current print.
    * @param showTime
    *   Show current time in each print.
    * @param savingDir
    *   Path to directory where to save files with functions like 'Debugger.save' (e.g. './target').
    */
  final case class Settings(
    errorTraceLimit: Int,
    showErrorTraces: Boolean,
    showBreadcrumbs: Boolean,
    showTime: Boolean,
    savingDir: String
  )

  object Settings {
    def default(): Settings =
      Settings(
        errorTraceLimit = 10,
        showErrorTraces = true,
        showBreadcrumbs = true,
        showTime = false,
        savingDir = "./target"
      )
  }
}
