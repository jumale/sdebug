package com.github.jumale.sdebug

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/** An AST-node for representing formatted values. */
trait FmtNode[+T] {
  def value: T
  def render(implicit p: RenderParams): String
}

object FmtNode {
  def diff(actual: FmtNode[Any], expected: FmtNode[Any], colors: NodeColors): FmtNode[Any] =
    (expected, actual) match {
      case (x, y) if x.value == y.value => x

      case (x: OptionNode[_], y: OptionNode[_]) =>
        (x.value, y.value) match {
          case (Some(xv), Some(yv)) => x.copy(value = x.value.map(_ => diff(xv, yv, colors)))
          case (_, _)               => DiffNode(Some(expected), Some(actual), colors)
        }

      case (x: CollectionNode[_], y: CollectionNode[_]) =>
        def handleSeq(xv: Iterable[FmtNode[Any]], yv: Iterable[FmtNode[Any]]) = {
          val (source, target, direction) =
            if (xv.size >= yv.size) (xv.toVector, yv.toVector, true)
            else (yv.toVector, xv.toVector, false)

          x.copy(value = source.zipWithIndex.map { case (s, i) =>
            (target.lift(i), direction) match {
              case (Some(t), _)  => diff(s, t, colors)
              case (None, true)  => DiffNode(Some(s), None, colors)
              case (None, false) => DiffNode(None, Some(s), colors)
            }
          })
        }

        def handleSet(xv: Iterable[FmtNode[Any]], yv: Iterable[FmtNode[Any]]) = {
          val xvs = xv.toSet
          val yvs = yv.toSet
          val left = xvs.diff(yvs)
          val right = yvs.diff(xvs)
          x.copy(value =
            xvs.diff(left).diff(right) ++
              right.map(v => DiffNode(Some(v), None, colors)) ++
              left.map(v => DiffNode(None, Some(v), colors))
          )
        }

        (x.value, y.value) match {
          case (xv: scala.collection.immutable.Seq[_], yv: scala.collection.immutable.Seq[_]) => handleSeq(xv, yv)
          case (xv: scala.collection.mutable.Seq[_], yv: scala.collection.mutable.Seq[_])     => handleSeq(xv, yv)
          case (xv: scala.collection.immutable.Set[_], yv: scala.collection.immutable.Set[_]) => handleSet(xv, yv)
          case (xv: scala.collection.immutable.Set[_], yv: scala.collection.immutable.Set[_]) => handleSet(xv, yv)
          case _                                                                              => x
        }

      case (x: MapNode[_, _], y: MapNode[_, _]) =>
        // noinspection DuplicatedCode
        if (x.name != y.name)
          DiffNode(Some(x), Some(y), colors)
        else {
          val xm: Map[FmtNode[Any], FmtNode[Any]] = x.value.toMap
          val ym: Map[FmtNode[Any], FmtNode[Any]] = y.value.toMap
          x.copy(value = (xm.keySet ++ ym.keySet).map { key =>
            (xm.get(key), ym.get(key)) match {
              case (Some(l), None)    => DiffNode(Some(key), None, colors) -> l
              case (None, Some(r))    => DiffNode(None, Some(key), colors) -> r
              case (Some(l), Some(r)) => key -> diff(l, r, colors)
              case _                  => throw new RuntimeException("impossible")
            }
          }.toVector)
        }

      case (x: ObjectNode, y: ObjectNode) =>
        // noinspection DuplicatedCode
        if (x.name != y.name)
          DiffNode(Some(x), Some(y), colors)
        else {
          val xm: Map[FmtNode[Any], FmtNode[Any]] = x.value.toMap
          val ym: Map[FmtNode[Any], FmtNode[Any]] = y.value.toMap
          x.copy(value = (xm.keySet ++ ym.keySet).map { key =>
            (xm.get(key), ym.get(key)) match {
              case (Some(l), None)    => DiffNode(Some(key), None, colors) -> l
              case (None, Some(r))    => DiffNode(None, Some(key), colors) -> r
              case (Some(l), Some(r)) => key -> diff(l, r, colors)
              case _                  => throw new RuntimeException("impossible")
            }
          }.toVector)
        }

      case _ => DiffNode(Some(expected), Some(actual), colors)
    }

  final case class DiffNode(a: Option[FmtNode[Any]], b: Option[FmtNode[Any]], colors: NodeColors) extends FmtNode[Any] {
    def value: Any = a.map(_.value).orElse(b.map(_.value)).orNull

    private def added(v: FmtNode[Any])(implicit p: RenderParams): String =
      colors.primary + v.render(p.copy(colorize = false)) + colors.reset

    private def removed(v: FmtNode[Any])(implicit p: RenderParams): String =
      colors.secondary + v.render(p.copy(colorize = false)) + colors.reset

    override def render(implicit p: RenderParams): String =
      (a, b) match {
        case (Some(l), None)    => removed(l)
        case (None, Some(r))    => added(r)
        case (Some(l), Some(r)) => removed(l) + colors.reset + " >>> " + added(r)
        case _                  => throw new RuntimeException("DiffNode requires at least one non-empty value")
      }
  }

  trait SimpleNode[+T] extends FmtNode[T] {
    def value: T
    def colors: NodeColors
    def render(implicit p: RenderParams): String = colors.primary + value.toString
  }

  final case class RawNode(value: Any, colors: NodeColors) extends SimpleNode[Any]
  final case class NumberNode(value: Any, colors: NodeColors) extends SimpleNode[Any]
  final case class BooleanNode(value: Boolean, colors: NodeColors) extends SimpleNode[Boolean]
  final case class NullNode(colors: NodeColors) extends SimpleNode[Any] { val value: Any = "null" }

  final case class StringNode(value: String, colors: NodeColors) extends FmtNode[String] {
    def render(implicit p: RenderParams): String =
      open + value.replaceAll("([\r\n]+)", "$1" + colors.primary) + close

    private lazy val needsTripleQuote: Boolean = value.contains("\n") || value.contains("\"") || value.contains("\r")

    private def open(implicit p: RenderParams): String = {
      if (p.simplified) colors.primary
      else if (needsTripleQuote) colors.primary + "\"" + colors.secondary + "\"\"" + colors.primary
      else colors.primary + "\""
    }

    private def close(implicit p: RenderParams): String =
      if (p.simplified) colors.primary
      else if (needsTripleQuote) colors.secondary + "\"\"" + colors.primary + "\""
      else "\""
  }

  final case class OptionNode[T](value: Option[FmtNode[T]], colors: NodeColors) extends FmtNode[Option[FmtNode[T]]] {
    def render(implicit p: RenderParams): String = colors.primary + (value match {
      case Some(v) => "Some(" + v.render + colors.primary + ")"
      case None    => "None"
    })
  }

  final case class TryNode[T](value: Try[FmtNode[T]], colors: NodeColors, errorColors: NodeColors)
      extends FmtNode[Try[FmtNode[T]]] {
    def render(implicit p: RenderParams): String = colors.primary + (value match {
      case Success(v) => "Success(" + v.render + colors.primary + ")"
      case Failure(e) => "Failure(" + ErrorNode(e, errorColors).render + ")"
    })
  }

  final case class EitherNode[L, R](value: Either[FmtNode[L], FmtNode[R]], colors: NodeColors)
      extends FmtNode[Either[FmtNode[L], FmtNode[R]]] {
    def render(implicit p: RenderParams): String = colors.primary + (value match {
      case Left(v)  => "Left(" + v.render
      case Right(v) => "Right(" + v.render
    }) + colors.primary + ")"
  }

  final case class FutureNode[T](
    value: Future[T],
    resultToNode: Try[T] => FmtNode[Any],
    colors: NodeColors,
    errorColors: NodeColors
  ) extends FmtNode[Future[T]] {
    def render(implicit p: RenderParams): String = {
      val inner = value.value match {
        case None         => colors.secondary + "<not completed>"
        case Some(result) => resultToNode(result).render
      }
      colors.primary + "Future(" + inner + colors.primary + ")"
    }
  }

  private val collectionPkg: String = "scala\\.collection\\.(im)?mutable\\."

  final case class CollectionNode[T](
    clazz: Class[_],
    value: Iterable[FmtNode[T]],
    colors: NodeColors,
    classNames: Formatter.ClassNameSettings = Formatter.ClassNameSettings(full = false, replace = Seq.empty),
    customName: Option[String] = None
  ) extends FmtNode[Iterable[FmtNode[T]]] {
    def render(implicit p: RenderParams): String =
      if (value.isEmpty)
        classColor + name + colors.reset + colors.primary + ".empty"
      else if (singleLine(p.noColors).length <= p.rightBorder || !p.multiline)
        singleLine
      else if (value.size > 1)
        multiLine
      else
        singleElement

    lazy val name: String = customName.getOrElse {
      clazz.getName match {
        case n if n.matches(collectionPkg + "\\$colon\\$colon")    => "List"
        case n if n.matches(collectionPkg + "Nil.*")               => "List"
        case n if n.matches(collectionPkg + "Vector.*")            => "Vector"
        case n if n.matches(collectionPkg + "Set\\$(Empty)?Set.*") => "Set"
        case n if n.matches(collectionPkg + "ArraySeq.*")          => "ArraySeq"
        case n if n.matches("scala\\.Tuple.*")                     => ""
        case _                                                     => fmtClassName(clazz, classNames)
      }
    }

    private def singleElement(implicit p: RenderParams): String =
      open + value.map(_.render).mkString(sep) + close

    private def singleLine(implicit p: RenderParams): String =
      open + value.map(_.render(nextDepth)).mkString(sep) + close

    private def multiLine(implicit p: RenderParams): String =
      open + break() + value
        .map(x => indent + x.render(nextDepth))
        .mkString(multiSep) + break(p.indent) + close

    private def classColor(implicit p: RenderParams): String =
      if (clazz.getName.contains(".mutable.")) colors.secondary
      else colors.primary

    private def open(implicit p: RenderParams): String =
      if (p.simplified) ""
      else if (name.isEmpty) colors.primary + "("
      else classColor + name + colors.reset + colors.primary + "("

    private def close(implicit p: RenderParams): String =
      if (p.simplified) ""
      else colors.primary + ")"

    private def break(after: String = "")(implicit p: RenderParams): String =
      if (p.simplified) ""
      else "\n" + after

    private def nextDepth(implicit p: RenderParams): RenderParams =
      if (p.simplified) p
      else p.nextDepth

    private def indent(implicit p: RenderParams): String =
      if (p.simplified) ""
      else p.fieldIndent

    private def sep(implicit p: RenderParams): String =
      colors.primary + ", "

    private def multiSep(implicit p: RenderParams): String =
      if (p.simplified) "\n"
      else colors.primary + ", \n"
  }

  trait KeyValNode[K, V] extends FmtNode[Vector[(FmtNode[K], FmtNode[V])]] {
    def name: String

    def colors: NodeColors

    def value: Vector[(FmtNode[K], FmtNode[V])]

    def render(implicit p: RenderParams): String =
      if (value.isEmpty)
        emptyState
      else if (singleLine(p.noColors).length <= p.rightBorder || !p.multiline)
        singleLine
      else
        multiLine

    protected def emptyState(implicit p: RenderParams): String =
      classColor + name + colors.reset + colors.primary + ".empty"

    protected def singleLine(implicit p: RenderParams): String =
      open + fieldsOneLine(p) + close

    protected def multiLine(implicit p: RenderParams): String =
      open + "\n" + fieldsMultiLine(p) + "\n" + p.indent + close

    protected def classColor(implicit p: RenderParams): String = colors.primary
    protected def open(implicit p: RenderParams): String = s"$classColor$name${colors.reset}${colors.primary}("
    protected def close(implicit p: RenderParams): String = colors.primary + ")"
    protected def sep(implicit p: RenderParams): String = colors.primary + ", "
    protected def eq(implicit p: RenderParams): String = colors.secondary + "="
    protected def renderKey(k: FmtNode[K])(implicit p: RenderParams): String = k.render
    protected def renderVal(v: FmtNode[V])(implicit p: RenderParams): String = v.render

    protected def renderFields[T](kp: RenderParams, vp: RenderParams)(fn: ((String, String)) => T): Iterable[T] =
      value.map(f => fn(renderKey(f._1)(kp) -> renderVal(f._2)(vp)))

    protected def fieldsOneLine(implicit p: RenderParams): String =
      renderFields(p, p) { case (k, v) => k + s" $eq " + v }.mkString(sep)

    protected def fieldsMultiLine(implicit p: RenderParams): String = {
      val keys = value.map(f => renderKey(f._1)(p.copy(colorize = false)).length)
      val max = keys.max
      val keyPads = keys.map(max - _)
      val pairs = renderFields(p, p.nextDepth)(identity)
      pairs.zipWithIndex
        .map { case ((k, v), i) => p.fieldIndent + k + (" " * keyPads(i)) + s" $eq " + v }
        .mkString(sep + "\n")
    }
  }

  final case class MapNode[K, V](
    clazz: Class[_],
    value: Vector[(FmtNode[K], FmtNode[V])],
    colors: NodeColors,
    customName: Option[String] = None
  ) extends KeyValNode[K, V] {
    override def eq(implicit p: RenderParams): String = colors.secondary + "->"

    override protected def classColor(implicit p: RenderParams): String =
      if (clazz.getName.contains(".mutable.")) colors.secondary else colors.primary

    lazy val name: String = customName.getOrElse {
      clazz.getName match {
        case n if n.matches(collectionPkg + "Map\\$(Empty)?Map.*") => "Map"
        case _                                                     => clazz.getSimpleName
      }
    }
  }

  final case class ObjectNode(
    clazz: Class[_],
    value: Vector[(FmtNode[Any], FmtNode[Any])],
    colors: NodeColors,
    classNames: Formatter.ClassNameSettings,
    customName: Option[String] = None
  ) extends KeyValNode[Any, Any] {
    override def name: String = customName.getOrElse(fmtClassName(clazz, classNames))

    override protected def renderKey(k: FmtNode[Any])(implicit p: RenderParams): String =
      colors.secondary + k.value.toString

    override def render(implicit p: RenderParams): String =
      if (value.size == 1) // render without field-names if it's a single-field-class
        open + renderFields(p, p)(_._2).head + close
      else
        super.render

    override protected def fieldsOneLine(implicit p: RenderParams): String =
      if (p.showKeys) super.fieldsOneLine(p) else renderFields(p, p)(_._2).mkString(sep)

    override protected def emptyState(implicit p: RenderParams): String =
      classColor + name.stripSuffix("$")
  }

  final case class ErrorNode(value: Throwable, colors: NodeColors) extends FmtNode[Throwable] {
    override def render(implicit p: RenderParams): String =
      if (singleLine(p.noColors).length <= p.rightBorder || !p.multiline)
        singleLine
      else
        multiLine

    private val msgColors: NodeColors = NodeColors( //
      primaryColor = colors.secondaryColor,
      secondaryColor = colors.resetColor,
      resetColor = colors.resetColor
    )

    private def msg(implicit p: RenderParams): String =
      StringNode(value.getMessage, msgColors).render(p.nextDepth)

    private def singleLine(implicit p: RenderParams): String =
      open + msg + close

    private def multiLine(implicit p: RenderParams): String =
      open + "\n" + p.indent + msg + "\n" + p.indent + close

    private def name: String = value.getClass.getSimpleName.stripSuffix("$1")
    private def open(implicit p: RenderParams): String = colors.primary + name + "("
    private def close(implicit p: RenderParams): String = colors.primary + ")"
  }

  private def fmtClassName(clazz: Class[_], settings: Formatter.ClassNameSettings): String = {
    val baseName =
      if (settings.full)
        clazz.getName.replace("$1", "").split('.').last.split('$').mkString(".")
      else
        clazz.getSimpleName.replace("$1", "")

    settings.replace.foldLeft(baseName) { case (acc, (regex, replacement)) =>
      regex.replaceAllIn(acc, replacement)
    }
  }
}
