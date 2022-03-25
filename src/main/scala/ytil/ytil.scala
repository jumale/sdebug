import play.api.libs.json.{JsValue, Json}

import scala.annotation.tailrec

package object ytil {
  private val printColorsQueue = Seq(
    Console.CYAN,
    Console.GREEN,
    Console.YELLOW,
    Console.BLUE,
    Console.MAGENTA,
    Console.WHITE,
    Console.RED,
    Console.BLACK + Console.CYAN_B,
    Console.WHITE + Console.GREEN_B,
    Console.BLACK + Console.YELLOW_B,
    Console.WHITE + Console.BLUE_B,
    Console.WHITE + Console.MAGENTA_B,
    Console.WHITE + Console.RED_B
  )

  private var usedColors: Map[String, String] = Map.empty
  private var lastColorIndex: Int = -1
  private def nextColorIndex: Int = {
    lastColorIndex += 1
    if (lastColorIndex >= printColorsQueue.length)
      lastColorIndex = 0
    lastColorIndex
  }
  private def nextColor(key: String): String = this.synchronized {
    usedColors.getOrElse(
      key, {
        val color = printColorsQueue(nextColorIndex)
        usedColors = usedColors.updated(key, color)
        color
      }
    )
  }

  val showThread: Boolean = true

  def log(msg: String): Unit = {
    val line = lineOfStack(2)
    val color = nextColor(line.toString)
    Console.println(s"$color-> $msg${Console.RESET} ${Console.BLACK}${line.asLink}$thread${Console.RESET}")
  }

  def prettyPrint(v: Any): Unit = prettyPrint(lineOfStack(2), v)
  def prettyPrintMap(v: (String, Any)*): Unit = prettyPrint(lineOfStack(2), MapValues(v))
  def prettyPrint(name: String, v: Any): Unit = prettyPrint(lineOfStack(2).copy(prefix = name), v)
  def prettyPrint(line: Line, v: Any): Unit = {
    Console.println(Console.BLACK + line.asHeadLink + Console.RESET)
    Console.println(prettyFormat(v))
    v match {
      case e: Throwable =>
        Console.println(s"   ${Console.RED}error: ${Console.YELLOW}${e.getMessage}${Console.RESET}")
        Console.println(s"   ${Console.RED}trace:")
        e.getStackTrace
          .foreach { se =>
            val clazz = s"${Console.YELLOW}${se.getClassName}"
            val method = se.getMethodName
            val file = s"${Console.BLACK}${se.getFileName}:${se.getLineNumber}"
            Console.println(s"    $clazz::$method($file)${Console.RESET}")
          }
      case _ =>
        ()
    }
  }

  def prettyDiff(a: Any, b: Any): Unit = {
    val prettyA = prettyFormat(a).split('\n')
    val prettyB = prettyFormat(b).split('\n')
    val diff = prettyA.zipWithIndex
      .flatMap { case (line, idx) =>
        val fA = Option(line.replaceAll('\u001b'.toString + """\[\d+m""", ""))
        val fB = prettyB.lift(idx).map(_.replaceAll('\u001b'.toString + """\[\d+m""", ""))
        if (fA == fB)
          Seq(line)
        else
          Seq(
            fA.map(s => Console.RED + "-" + s + Console.RESET),
            fB.map(s => Console.GREEN + "-" + s + Console.RESET)
          ).flatten
      }
    val line = lineOfStack(2)
    Console.println(Console.BLACK + line.asHeadLink + Console.RESET)
    Console.println(diff.mkString("\n"))
  }

  def trace(): Unit = {
    Console.println(Console.BLACK + lineOfStack(2).asHeadLink + Console.RESET)
    Console.println(fullStack.tail.tail.map(_.asLink).mkString("\n"))
  }

  def sleep(millis: Long): Unit = {
    val line = lineOfStack(2)
    Console.println(s"-> ⏱  ${millis}ms ${Console.BLACK}${line.asLink}$thread${Console.RESET}")
    Thread.sleep(millis)
  }

  def dump(v: Any, fileName: String): Unit = dumpBytes(v.toString.getBytes, fileName)

  def dumpBytes(v: Array[Byte], fileName: String): Unit = {
    import java.io._
    val target = new BufferedOutputStream(new FileOutputStream(s"target/$fileName"))
    try v.foreach(target.write(_))
    finally target.close()
  }

  private def thread: String =
    if (showThread)
      s" [thread:${Thread.currentThread().getId}]"
    else
      ""

  final case class Line(file: String, number: Int, prefix: String = "") {
    override def toString: String =
      s"${if (prefix.isEmpty)
        ""
      else
        s"$prefix "}$file:$number"
    def asLink: String = s"...($toString)"
    def asHeadLink: String = s".....................($toString)....................."
    def isEmpty: Boolean = this == Line.empty
  }
  object Line {
    val empty: Line = Line("unknown", 0)
  }

  def fullStack: Seq[Line] = (new Exception).getStackTrace.toSeq.map(s => Line(s.getFileName, s.getLineNumber))

  def lineOfStack(n: Int): Line = (new Exception).getStackTrace
    .lift(n)
    .map(s => Line(s.getFileName, s.getLineNumber))
    .getOrElse(Line.empty)

  def catchLine(patterns: String*): Line = patterns
    .collectFirst {
      case pattern if !catchLineRec(pattern, 0).isEmpty =>
        catchLineRec(pattern, 0)
    }
    .getOrElse(Line.empty)

  @tailrec
  private def catchLineRec(pattern: String, n: Int): Line = {
    val c = lineOfStack(n)
    if (c.isEmpty || c.file.matches(pattern))
      c
    else
      catchLineRec(pattern, n + 1)
  }

  /** Pretty formats a Scala value similar to its source representation. Particularly useful for case classes.
    * @param a
    *   - The value to pretty format.
    * @param indentSize
    *   - Number of spaces for each indent.
    * @param maxElementWidth
    *   - Largest element size before wrapping.
    * @param depth
    *   - Initial depth to pretty format indents.
    * @return
    */
  def prettyFormat(a: Any, indentSize: Int = 2, maxElementWidth: Int = 120, depth: Int = 0): String = {
    val indent = " " * depth * indentSize
    val fieldIndent = indent + (" " * indentSize)
    val thisDepth = prettyFormat(_: Any, indentSize, maxElementWidth, depth)
    val nextDepth = prettyFormat(_: Any, indentSize, maxElementWidth, depth + 1)

    def cString(content: String): String = Console.GREEN + "\"" + content + "\""
    def cNum(content: String): String = Console.CYAN + content
    def cClass(name: String, content: String): String = s"${Console.YELLOW}$name($content${Console.YELLOW})"
    def cClassSep: String = Console.YELLOW + ", "
    def cClassEq(trim: Boolean = false): String =
      Console.YELLOW +
        (if (trim)
           "="
         else
           " = ")
    def cClassField(name: String): String = Console.RESET + name
    def cColl(name: String, content: String = ""): String = s"${Console.BLUE}$name($content${Console.BLUE})"
    def cCollSep: String = Console.BLUE + ", "
    def cJson(content: String): String = s"${Console.CYAN}JsValue($content${Console.CYAN})"
    def cMap(content: String): String = s"${Console.CYAN}Map($content${Console.CYAN})"
    def cMapSep: String = Console.CYAN + ", "
    def cMapEq(trim: Boolean = false): String =
      Console.CYAN +
        (if (trim)
           "->"
         else
           " -> ")
    def cMapField(name: String): String = Console.RESET + name
    def cNone: String = Console.MAGENTA + "None"
    def cSome(content: String): String = s"${Console.MAGENTA}Some($content${Console.MAGENTA})"
    def cBool(content: String = ""): String = Console.RED + Console.BOLD + content
    lazy val strEscape = Seq("\n" -> "\\n", "\r" -> "\\r", "\t" -> "\\t", "\"" -> "\\\"")
    lazy val clazz =
      a.getClass.getName match {
        case "scala.collection.immutable.$colon$colon" =>
          "::"
        case "scala.collection.immutable.Nil$" =>
          "Seq"
        case _ =>
          a.getClass.getSimpleName
      }

    val fmt =
      a match {
        // Make Strings look similar to their literal form.
        case s: String =>
          cString(strEscape.foldLeft(s) { case (p, (c, r)) =>
            p.replace(c, r)
          })
        case n: Double =>
          cNum(n.toString)
        case n: Float =>
          cNum(n.toString)
        case n: Int =>
          cNum(n.toString)
        case n: BigInt =>
          cNum(n.toString)
        case b: Boolean =>
          cBool(b.toString)
        case None =>
          cNone
        case Some(v) =>
          cSome(thisDepth(v))

        case j: JsValue =>
          cJson(
            Console.RESET +
              Json
                .prettyPrint(j)
                .replaceAll("""("[^"]+")\s*:\s*""", s"${Console.GREEN}$$1${Console.RESET} -> ")
                .replace("[ ]", s"${Console.BLUE}Vector()${Console.RESET}")
                .replace("{ }", "Json.obj()")
                .replaceAll("true", s"${Console.MAGENTA + Console.BOLD}true${Console.RESET}")
                .replaceAll("false", s"${Console.MAGENTA + Console.BOLD}false${Console.RESET}")
                .replaceAll(": (\\d+)", s": ${Console.CYAN}$$1${Console.RESET}")
                .replaceAll("\\n(\\s*)", "\n" + indent + "$1")
          )

        case _: Seq[_] | _: Set[_] =>
          val s = a.asInstanceOf[Iterable[_]]
          val oneLine = cColl(clazz, s.map(nextDepth).mkString(cCollSep))
          if (oneLine.length <= maxElementWidth) {
            oneLine
          } else if (s.size > 1) {
            cColl(clazz, "\n" + s.map(x => fieldIndent + nextDepth(x)).mkString(cCollSep + "\n") + "\n" + indent)
          } else {
            cColl(clazz, s.map(x => thisDepth(x)).mkString(cCollSep))
          }

        case m: Map[_, _] =>
          val oneLine = cMap(
            m.map { case (k, v) =>
              cMapField(thisDepth(k)) + cMapEq() + nextDepth(v)
            }.mkString(cMapSep)
          )
          if (oneLine.length <= maxElementWidth) {
            oneLine
          } else {
            cMap(
              "\n" +
                m.map { case (k, v) =>
                  fieldIndent + cMapField(nextDepth(k)) + cMapEq() + nextDepth(v)
                }.mkString(cMapSep + "\n") + "\n" + indent
            )
          }
        case mv: MapValues =>
          mv.items
            .map { case (k, v) =>
              cMapField(nextDepth(k)) + cMapEq() + nextDepth(v)
            }
            .mkString(cMapSep + "\n")

        case p: Product =>
          val fields = a.getClass.getDeclaredFields.filterNot(_.isSynthetic).map(_.getName)
          val values = p.productIterator.toSeq
          // If we weren't able to match up fields/values, fall back to toString.
          if (fields.length != values.length) {
            return p.toString
          }
          fields.zip(values).toList match {
            // If there are no fields, just use the normal String representation.
            case Nil =>
              p.toString
            // If there is just one field, let's just print it as a wrapper.
            case (_, value) :: Nil =>
              cClass(clazz, thisDepth(value))
            // If there is more than one field, build up the field names and values.
            case pairs =>
              def formatPairsOneLine(pairs: Seq[(String, Any)]): String = pairs
                .map { case (k, v) =>
                  cClassField(k) + cClassEq(trim = true) + thisDepth(v)
                }
                .mkString(cClassSep)

              def formatPairs(pairs: Seq[(String, Any)]): String = {
                val max = pairs.maxBy(_._1.length)._1.length
                pairs
                  .map { case (k, v) =>
                    fieldIndent + cClassField(k.padTo(max, ' ')) + cClassEq() + nextDepth(v)
                  }
                  .mkString(cClassSep + "\n")
              }

              val oneLine = cClass(clazz, formatPairsOneLine(pairs))
              if (oneLine.length <= maxElementWidth) {
                oneLine
              } else {
                cClass(clazz, "\n" + formatPairs(pairs) + "\n" + indent)
              }
          }

        // If we haven't specialized this type, just use its toString.
        case _ =>
          Console.RESET + a.toString
      }

    if (depth == 0)
      fmt + Console.RESET
    else
      fmt
  }

  final private case class MapValues(items: Seq[(String, Any)])
}
