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
    if (lastColorIndex >= printColorsQueue.length) lastColorIndex = 0
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
    val line = currentLine
    val color = nextColor(line.toString)
    Console.println(s"$color-> $msg${Console.RESET} ${Console.BLACK}${line.asLink}$thread${Console.RESET}")
  }

  def sleep(millis: Long): Unit = {
    val line = currentLine
    Console.println(s"-> ⏱  ${millis}ms ${Console.BLACK}${line.asLink}$thread${Console.RESET}")
    Thread.sleep(millis)
  }

  def prettyPrint(name: String, a: Any): Unit = {
    val line = currentLine.copy(prefix = name)
    Console.println(s"---------- $line ----------")
    Console.println(prettyFormat(a))
  }

  private def thread: String = if (showThread) s" [thread:${Thread.currentThread().getId}]" else ""

  private final case class Line(file: String, number: Int, prefix: String = "") {
    override def toString: String = s"${if (prefix.isEmpty) "" else s"$prefix "}$file:$number"
    def asLink: String = s"...($toString)"
  }

  private def currentLine: Line =
    (new Exception).getStackTrace
      .lift(2)
      .map(s => Line(s.getFileName, s.getLineNumber))
      .getOrElse(Line("unknown", 0))

  def prettyPrint(a: Any): Unit = prettyPrint("", a)

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
  def prettyFormat(a: Any, indentSize: Int = 2, maxElementWidth: Int = 60, depth: Int = 0): String = {
    val indent = " " * depth * indentSize
    val fieldIndent = indent + (" " * indentSize)
    val thisDepth = prettyFormat(_: Any, indentSize, maxElementWidth, depth)
    val nextDepth = prettyFormat(_: Any, indentSize, maxElementWidth, depth + 1)
    def cBraces(s: String, color: String = Console.YELLOW): String =
      s"$color(${Console.RESET}$s$color)${Console.RESET}"
    def cField(s: String): String = s"${Console.GREEN}$s${Console.RESET}"
    a match {
      // Make Strings look similar to their literal form.
      case s: String =>
        val replaceMap = Seq("\n" -> "\\n", "\r" -> "\\r", "\t" -> "\\t", "\"" -> "\\\"")
        "\"" + replaceMap.foldLeft(s) { case (acc, (c, r)) => acc.replace(c, r) } + "\""
      case None => s"${Console.MAGENTA}None${Console.RESET}"
      case Some(v) =>
        s"${Console.MAGENTA}Some(${Console.RESET}${thisDepth(v)}${Console.MAGENTA})${Console.RESET}"
      // For an empty Seq just use its normal String representation.
      case xs: Seq[_] if xs.isEmpty =>
        Console.BLUE + xs.toString() + Console.RESET
      case xs: Seq[_] =>
        val prefix =
          s"${Console.BLUE}${xs.getClass.getSimpleName}${Console.RESET}"
        val resultOneLine =
          prefix + cBraces(xs.map(nextDepth).mkString(s"${Console.BLUE}, ${Console.RESET}"), Console.BLUE)
        if (resultOneLine.length <= maxElementWidth) {
          return resultOneLine
        }
        // Otherwise, build it with newlines and proper field indents.
        val result = xs
          .map(x => s"\n$fieldIndent${nextDepth(x)}")
          .mkString(s"${Console.BLUE}, ${Console.RESET}")
        prefix + cBraces(result.substring(0, result.length - 1) + "\n" + indent, Console.BLUE)
      // Product should cover case classes.
      case m: Map[_, _] =>
        val format = (v: String) => s"${Console.CYAN}Map(${Console.RESET}$v${Console.CYAN})${Console.RESET}"
        if (m.isEmpty) format("")
        else {
          val keyVal = m.map { case (k, v) =>
            s"$fieldIndent${thisDepth(k)} ${Console.CYAN}->${Console.RESET} ${nextDepth(v)}"
          }
          val resultOneLine = format(keyVal.mkString(s"${Console.CYAN},${Console.RESET} "))
          if (resultOneLine.length <= maxElementWidth) return resultOneLine
          format(s"\n${keyVal.mkString(s"${Console.CYAN},${Console.RESET}\n")}\n$indent")
        }
      case p: Product =>
        val prefix = s"${Console.YELLOW}${p.productPrefix}${Console.RESET}"
        // We'll use reflection to get the constructor arg names and values.
        val cls = p.getClass
        val fields = cls.getDeclaredFields.filterNot(_.isSynthetic).map(_.getName)
        val values = p.productIterator.toSeq
        // If we weren't able to match up fields/values, fall back to toString.
        if (fields.length != values.length) {
          return p.toString
        }
        fields.zip(values).toList match {
          // If there are no fields, just use the normal String representation.
          case Nil => p.toString
          // If there is just one field, let's just print it as a wrapper.
          case (_, value) :: Nil => s"$prefix${cBraces(thisDepth(value))}"
          // If there is more than one field, build up the field names and values.
          case kvps =>
            val maxLenField = kvps.maxBy(_._1.length)
            val aligned = kvps.map { case (k, v) =>
              k.padTo(maxLenField._1.length, ' ') -> v
            }
            val prettyFields = aligned.map { case (k, v) =>
              s"$fieldIndent${cField(k)} = ${nextDepth(v)}"
            }
            // If the result is not too long, pretty print on one line.
            val resultOneLine = s"$prefix${cBraces(prettyFields.mkString(s"${Console.YELLOW},${Console.RESET} "))}"
            if (resultOneLine.length <= maxElementWidth) return resultOneLine
            // Otherwise, build it with newlines and proper field indents.
            s"$prefix${cBraces(s"\n${prettyFields.mkString(s"${Console.YELLOW},${Console.RESET}\n")}\n$indent")}"
        }
      // If we haven't specialized this type, just use its toString.
      case _ => a.toString
    }
  }
}
