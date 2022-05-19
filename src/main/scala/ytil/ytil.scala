import play.api.libs.json._
import ytil.Pretty.Params

import scala.annotation.tailrec

package object ytil {
  val COLOR: Color = Color.console
  val showThread: Boolean = true

  def log(msg: String): Unit = {
    val line = Stack.line(2)
    Console.println(s"-> $msg${COLOR.RESET} ${COLOR.BLACK}${line.asLink}$thread${COLOR.RESET}")
  }

  def prettyPrint(v: Any, params: Params = Params()): Unit = {
    val line = Stack.line(2)
    Console.println(Console.BLACK + line.asHeadLink + COLOR.RESET)
    Console.println(Pretty(v).render(params) + COLOR.RESET)
  }

  def prettyDiff(a: Any, b: Any, params: Params = Params()): Unit = {
    val prettyA = Pretty(a).render(params).split('\n')
    val prettyB = Pretty(b).render(params).split('\n')
    val diff = prettyA.zipWithIndex
      .flatMap { case (line, idx) =>
        val fA = Option(line.replaceAll('\u001b'.toString + """\[\d+m""", ""))
        val fB = prettyB.lift(idx).map(_.replaceAll('\u001b'.toString + """\[\d+m""", ""))
        if (fA == fB)
          Seq(line)
        else
          Seq(
            fA.map(s => COLOR.RED + COLOR.BOLD + "-" + s + COLOR.RESET),
            fB.map(s => COLOR.GREEN + COLOR.BOLD + "+" + s + COLOR.RESET)
          ).flatten
      }
    val line = Stack.line(2)
    Console.println(COLOR.BLACK + line.asHeadLink + COLOR.RESET)
    Console.println(diff.mkString("\n") + COLOR.RESET)
  }

  def trace(limit: Int = Int.MaxValue): Unit = {
    Console.println(COLOR.BLACK + Stack.line(2).asHeadLink + COLOR.RESET)
    Console.println(Stack.full.tail.tail.map(_.asLink).take(limit).mkString("\n") + COLOR.RESET)
  }

  def sleep(millis: Long): Unit = {
    val line = Stack.line(2)
    Console.println(s"-> ⏱  ${millis}ms ${COLOR.BLACK}${line.asLink}$thread${COLOR.RESET}")
    Thread.sleep(millis)
  }

  def save(v: Any, fileName: String): Unit = saveBytes(v.toString.getBytes, fileName)

  def saveBytes(v: Array[Byte], fileName: String): Unit = {
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

  trait Color {
    def BLACK: String
    def RED: String
    def GREEN: String
    def YELLOW: String
    def BLUE: String
    def MAGENTA: String
    def CYAN: String
    def WHITE: String
    def BOLD: String
    def RESET: String
  }

  object Color {
    val console: Color = new Color {
      val BLACK: String = Console.BLACK
      val RED: String = Console.RED
      val GREEN: String = Console.GREEN
      val YELLOW: String = Console.YELLOW
      val BLUE: String = Console.BLUE
      val MAGENTA: String = Console.MAGENTA
      val CYAN: String = Console.CYAN
      val WHITE: String = Console.WHITE
      val BOLD: String = Console.BOLD
      val RESET: String = Console.RESET
    }

    val debug: Color = new Color {
      val BLACK: String = "[black]"
      val RED: String = "[red]"
      val GREEN: String = "[green]"
      val YELLOW: String = "[yellow]"
      val BLUE: String = "[blue]"
      val MAGENTA: String = "[magenta]"
      val CYAN: String = "[cyan]"
      val WHITE: String = "[white]"
      val BOLD: String = "[bold]"
      val RESET: String = "[:]"
    }
  }

  object Pretty {
    def apply(a: Any): Val = {
      a match {
        case s: String       => StringVal(s)
        case n: Double       => NumVal(n)
        case n: Float        => NumVal(n)
        case n: Int          => NumVal(n)
        case n: BigInt       => NumVal(n)
        case b: Boolean      => BoolVal(b)
        case o: Option[_]    => OptionVal(o.map(apply))
        case e: Either[_, _] => EitherVal(e.map(apply).left.map(apply))
        case s: Seq[_]       => CollectionVal(s.getClass.getName, s.map(apply))
        case s: Set[_]       => CollectionVal(s.getClass.getName, s.map(apply))
        case m: Map[_, _]    => MapVal("Map", m.map { case (k, v) => apply(k) -> apply(v) })
        case e: Throwable    => ErrorVal(e)
        case JsNull          => NullVal
        case s: JsString     => StringVal(s.value)
        case n: JsNumber     => NumVal(n.value)
        case b: JsBoolean    => BoolVal(b.value)
        case a: JsArray      => CollectionVal("Json.arr", a.value.map(apply))
        case o: JsObject     => MapVal("Json.obj", o.fields.map { case (k, v) => StringVal(k) -> apply(v) })

        case p: Product =>
          val fields = a.getClass.getDeclaredFields.filterNot(_.isSynthetic).map(_.getName)
          val values = p.productIterator.toSeq
          if (fields.length != values.length)
            RawVal(p) // If we weren't able to match up fields/values, fall back to toString.
          else if (fields.toSeq == fields.toSeq.zipWithIndex.map { case (_, i) => s"_${i + 1}" })
            CollectionVal("", values.map(apply), COLOR.MAGENTA)
          else
            ObjectVal(p.getClass.getSimpleName, fields.zip(values).map { case (k, v) => apply(k) -> apply(v) })

        case v => RawVal(v)
      }
    }

    final case class Params(depth: Int = 0, indentSize: Int = 2, maxWidth: Int = 120, stackLimit: Int = 10) {
      lazy val indent: String = " " * depth * indentSize
      lazy val fieldIndent: String = indent + (" " * indentSize)
      lazy val rightBorder: Int = maxWidth - (depth * indentSize)
      def nextDepth: Params = copy(depth = depth + 1)
    }

    trait Val {
      def value: Any
      def render(p: Params = Params()): String

      def flex(p: Params)(singleLine: => String, multiLine: => String): String = {
        if (singleLine.length <= p.rightBorder)
          singleLine
        else
          multiLine
      }
    }

    private val strEscape = Seq("\n" -> "\\n", "\r" -> "\\r", "\t" -> "\\t", "\"" -> "\\\"")

    final case class RawVal(value: Any) extends Val {
      def render(p: Params): String = COLOR.RESET + value.toString
    }

    final case class StringVal(value: String) extends Val {
      def render(p: Params): String = wrap(escaped)
      protected def wrap(s: String): String = COLOR.GREEN + "\"" + s + "\""
      private lazy val escaped: String = strEscape.foldLeft(value) { case (p, (c, r)) => p.replace(c, r) }
    }

    final case class NumVal(value: Any) extends Val {
      def render(p: Params): String = COLOR.CYAN + value.toString
    }

    final case class BoolVal(value: Boolean) extends Val {
      def render(p: Params): String = COLOR.RED + value.toString
    }

    object NullVal extends Val {
      override def value: Any = null
      def render(p: Params): String = COLOR.RED + COLOR.BOLD + "null" + COLOR.RESET // to get rid of BOLD
    }

    final case class OptionVal(value: Option[Val]) extends Val {
      def render(p: Params): String = COLOR.MAGENTA + (value match {
        case Some(v) => "Some(" + v.render(p) + COLOR.MAGENTA + ")"
        case None    => "None"
      })
    }

    final case class EitherVal(value: Either[Val, Val]) extends Val {
      def render(p: Params): String = COLOR.MAGENTA + (value match {
        case Left(v)  => "Left(" + v.render(p)
        case Right(v) => "Right(" + v.render(p)
      }) + COLOR.MAGENTA + ")"
    }

    final case class CollectionVal(name: String, value: Iterable[Val], color: String = COLOR.BLUE) extends Val {
      def render(p: Params): String = {
        val oneLine = open + value.map(_.render(p.nextDepth)).mkString(sep) + close
        if (oneLine.length <= p.rightBorder) {
          oneLine
        } else if (value.size > 1) {
          open + "\n" + value
            .map(x => p.fieldIndent + x.render(p.nextDepth))
            .mkString(sep + "\n") + "\n" + p.indent + close
        } else {
          open + value.map(x => x.render(p)).mkString(sep) + close
        }
      }

      protected lazy val open: String = color + prettyName + "("
      protected lazy val close: String = color + ")"
      protected lazy val sep: String = color + ", "
      private lazy val prettyName: String = name match {
        case "scala.collection.immutable.$colon$colon" => "Seq"
        case "scala.collection.immutable.Nil$"         => "Seq"
        case "scala.collection.immutable.Vector1"      => "Vector"
        case _                                         => name
      }
    }

    trait KeyVal extends Val {
      def name: String
      def value: Iterable[(Val, Val)]

      def render(p: Params): String = {
        val oneLine = open + fieldsOneLine(p) + close
        if (oneLine.length <= p.rightBorder) {
          oneLine
        } else {
          open + "\n" + fieldsMultiLine(p) + "\n" + p.indent + close
        }
      }

      protected def color: String = COLOR.YELLOW
      protected lazy val open: String = s"$color$name("
      protected lazy val close: String = color + ")"
      protected lazy val sep: String = color + ", "
      protected lazy val eq: String = color + "="

      protected def renderFields[T](kp: Params, vp: Params)(fn: ((String, String)) => T): Iterable[T] =
        value.map(f => fn(f._1.render(kp) -> f._2.render(vp)))

      protected def fieldsOneLine(p: Params): String =
        renderFields(p, p) { case (k, v) => k + s" $eq " + v }.mkString(sep)

      protected def fieldsMultiLine(p: Params): String = {
        val pairs = renderFields(p, p.nextDepth)(v => v)
        val max = pairs.maxBy(_._1.length)._1.length
        pairs
          .map { case (k, v) => p.fieldIndent + k.padTo(max, ' ') + s" $eq " + v }
          .mkString(sep + "\n")
      }
    }

    final case class MapVal(name: String, value: Iterable[(Val, Val)], override val color: String = COLOR.CYAN)
        extends KeyVal {
      override lazy val eq: String = COLOR.RESET + "->"
    }

    final case class ObjectVal(name: String, value: Iterable[(Val, Val)]) extends KeyVal {
      override protected def renderFields[T](kp: Params, vp: Params)(fn: ((String, String)) => T): Iterable[T] =
        value.map(f => fn(COLOR.RESET + f._1.value.toString -> f._2.render(vp)))

      override def render(p: Params): String =
        if (value.size == 1)
          open + renderFields(p, p)(_._2).head + close
        else
          super.render(p)
    }

    final case class ErrorVal(value: Throwable) extends Val {
      override def render(p: Params): String =
        open + "\n    " + COLOR.YELLOW + value.getMessage + "\n" +
          stack.take(p.stackLimit).mkString("\n") + "\n" + p.indent + close

      protected lazy val name: String = value.getClass.getSimpleName
      protected lazy val open: String = COLOR.RED + COLOR.BOLD + name + "(" + COLOR.RESET
      protected lazy val close: String = COLOR.RED + COLOR.BOLD + ")" + COLOR.RESET
      protected lazy val stack: Seq[String] =
        value.getStackTrace.toIndexedSeq
          .map { se =>
            val clazz = COLOR.CYAN + se.getClassName
            val method = se.getMethodName
            val file = se.getFileName + ":" + se.getLineNumber
            s"    $clazz::$method${COLOR.BLACK}($file)${COLOR.RESET}"
          }
    }
  }

  object Stack {
    def full: Seq[Line] = (new Exception).getStackTrace.toSeq.map(s => Line(s.getFileName, s.getLineNumber))

    def line(n: Int): Line = (new Exception).getStackTrace
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
      val c = line(n)
      if (c.isEmpty || c.file.matches(pattern))
        c
      else
        catchLineRec(pattern, n + 1)
    }

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
  }
}
