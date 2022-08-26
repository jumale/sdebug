package com.github.jumale.sdebug

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

trait Node[+T] {
  def value: T
  def render(implicit p: RenderParams): String
}

object Node {
  def diff(a: Node[Any], b: Node[Any], colors: Colors): Node[Any] =
    (a, b) match {
      case (x, y) if x.value == y.value => x

      case (x: CollectionNode[_], y: CollectionNode[_]) =>
        if (x.name != y.name)
          DiffNode(Some(x), Some(y), colors)
        else {
          val (source, target, direction) =
            if (x.value.size >= y.value.size)
              (x.value.toVector, y.value.toVector, true)
            else
              (y.value.toVector, x.value.toVector, false)

          x.copy(value = source.zipWithIndex.map { case (s, i) =>
            (target.lift(i), direction) match {
              case (Some(t), _)  => diff(s, t, colors)
              case (None, true)  => DiffNode(Some(s), None, colors)
              case (None, false) => DiffNode(None, Some(s), colors)
            }
          })
        }

      case (x: MapNode[Any, Any], y: MapNode[Any, Any]) =>
        if (x.name != y.name)
          DiffNode(Some(x), Some(y), colors)
        else {
          val xm = x.value.toMap
          val ym = y.value.toMap
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
        if (x.name != y.name)
          DiffNode(Some(x), Some(y), colors)
        else {
          val xm = x.value.toMap
          val ym = y.value.toMap
          x.copy(value = (xm.keySet ++ ym.keySet).map { key =>
            (xm.get(key), ym.get(key)) match {
              case (Some(l), None)    => DiffNode(Some(key), None, colors) -> l
              case (None, Some(r))    => DiffNode(None, Some(key), colors) -> r
              case (Some(l), Some(r)) => key -> diff(l, r, colors)
              case _                  => throw new RuntimeException("impossible")
            }
          }.toVector)
        }

      case _ => DiffNode(Some(a), Some(b), colors)
    }

  final case class DiffNode(a: Option[Node[Any]], b: Option[Node[Any]], colors: Colors) extends Node[Any] {
    def value: Any = a.map(_.value).orElse(b.map(_.value)).orNull

    private def added(v: Node[Any])(implicit p: RenderParams): String =
      colors.primary + "+++" + v.render(p.copy(colorize = false))

    private def removed(v: Node[Any])(implicit p: RenderParams): String =
      colors.secondary + "---" + v.render(p.copy(colorize = false))

    override def render(implicit p: RenderParams): String =
      ((a, b) match {
        case (Some(l), None)    => removed(l)
        case (None, Some(r))    => added(r)
        case (Some(l), Some(r)) => removed(l) + colors.reset + " >>> " + added(r)
        case _                  => throw new RuntimeException("DiffNode requires at least one non-empty value")
      })
  }

  trait SimpleNode[+T] extends Node[T] {
    def value: T
    def colors: Colors
    def render(implicit p: RenderParams): String = colors.primary + value.toString
  }

  final case class RawNode(value: Any, colors: Colors) extends SimpleNode[Any]
  final case class NumberNode(value: Any, colors: Colors) extends SimpleNode[Any]
  final case class BooleanNode(value: Boolean, colors: Colors) extends SimpleNode[Boolean]
  final case class NullNode(colors: Colors) extends SimpleNode[Any] { val value: Any = "null" }

  final case class StringNode(value: String, colors: Colors) extends Node[String] {
    def render(implicit p: RenderParams): String =
      colors.primary + wrapChar + value + wrapChar

    private lazy val wrapChar: String =
      if (value.matches("[\"\r\n]+")) "\"\"\""
      else "\""
  }

  final case class OptionNode[T](value: Option[Node[T]], colors: Colors) extends Node[Option[Node[T]]] {
    def render(implicit p: RenderParams): String = colors.primary + (value match {
      case Some(v) => "Some(" + v.render + colors.primary + ")"
      case None    => "None"
    })
  }

  final case class TryNode[T](value: Try[Node[T]], colors: Colors, errorColors: Colors) extends Node[Try[Node[T]]] {
    def render(implicit p: RenderParams): String = colors.primary + (value match {
      case Success(v) => "Success(" + v.render + colors.primary + ")"
      case Failure(e) => "Failure(" + ErrorNode(e, errorColors).render + ")"
    })
  }

  final case class EitherNode[L, R](value: Either[Node[L], Node[R]], colors: Colors)
      extends Node[Either[Node[L], Node[R]]] {
    def render(implicit p: RenderParams): String = colors.primary + (value match {
      case Left(v)  => "Left(" + v.render
      case Right(v) => "Right(" + v.render
    }) + colors.primary + ")"
  }

  final case class FutureNode[T](
    value: Future[T],
    resultToNode: Try[T] => Node[Any],
    colors: Colors,
    errorColors: Colors
  ) extends Node[Future[T]] {
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
    value: Iterable[Node[T]],
    colors: Colors,
    customName: Option[String] = None
  ) extends Node[Iterable[Node[T]]] {
    def render(implicit p: RenderParams): String =
      if (value.isEmpty) {
        classColor + name + colors.reset + colors.primary + ".empty"
      } else {
        val oneLine = open + value.map(_.render(p.nextDepth)).mkString(sep) + close
        if (oneLine.length <= p.rightBorder || !p.multiline) {
          oneLine
        } else if (value.size > 1) {
          open + "\n" + value
            .map(x => p.fieldIndent + x.render(p.nextDepth))
            .mkString(sep + "\n") + "\n" + p.indent + close
        } else {
          open + value.map(_.render).mkString(sep) + close
        }
      }

    lazy val name: String = customName.getOrElse {
      clazz.getName match {
        case n if n.matches(collectionPkg + "\\$colon\\$colon")    => "List"
        case n if n.matches(collectionPkg + "Nil.*")               => "List"
        case n if n.matches(collectionPkg + "Vector.*")            => "Vector"
        case n if n.matches(collectionPkg + "Set\\$(Empty)?Set.*") => "Set"
        case n if n.matches("scala\\.Tuple.*")                     => ""
        case n                                                     => clazz.getSimpleName
      }
    }

    protected def classColor(implicit p: RenderParams): String =
      if (clazz.getName.contains(".mutable.")) colors.secondary
      else colors.primary

    protected def open(implicit p: RenderParams): String =
      if (name.isEmpty) colors.primary + "("
      else classColor + name + colors.reset + colors.primary + "("

    protected def close(implicit p: RenderParams): String = colors.primary + ")"
    protected def sep(implicit p: RenderParams): String = colors.primary + ", "
  }

  trait KeyValNode[K, V] extends Node[Vector[(Node[K], Node[V])]] {
    def name: String

    def colors: Colors

    def value: Vector[(Node[K], Node[V])]

    def render(implicit p: RenderParams): String = {
      val oneLine = open + fieldsOneLine(p) + close
      if (value.isEmpty) {
        classColor + name + colors.reset + colors.primary + ".empty"
      } else if (oneLine.length <= p.rightBorder || !p.multiline) {
        oneLine
      } else {
        open + "\n" + fieldsMultiLine(p) + "\n" + p.indent + close
      }
    }

    protected def classColor(implicit p: RenderParams): String = colors.primary
    protected def open(implicit p: RenderParams): String = s"$classColor$name${colors.reset}${colors.primary}("
    protected def close(implicit p: RenderParams): String = colors.primary + ")"
    protected def sep(implicit p: RenderParams): String = colors.primary + ", "
    protected def eq(implicit p: RenderParams): String = colors.primary + "="
    protected def renderKey(k: Node[K])(implicit p: RenderParams): String = k.render
    protected def renderVal(v: Node[V])(implicit p: RenderParams): String = v.render

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
    value: Vector[(Node[K], Node[V])],
    colors: Colors,
    customName: Option[String] = None
  ) extends KeyValNode[K, V] {
    override def eq(implicit p: RenderParams): String = colors.primary + "->"

    override protected def classColor(implicit p: RenderParams): String =
      if (clazz.getName.contains(".mutable.")) colors.secondary else colors.primary

    lazy val name: String = customName.getOrElse {
      clazz.getName match {
        case n if n.matches(collectionPkg + "Map\\$(Empty)?Map.*") => "Map"
        case n                                                     => clazz.getSimpleName
      }
    }
  }

  final case class ObjectNode(
    clazz: Class[_],
    value: Vector[(Node[Any], Node[Any])],
    colors: Colors,
    customName: Option[String] = None
  ) extends KeyValNode[Any, Any] {
    override def name: String = customName.getOrElse(clazz.getSimpleName).replace("$1", "")

    override protected def renderKey(k: Node[Any])(implicit p: RenderParams): String = colors.reset + k.value.toString

    override def render(implicit p: RenderParams): String =
      if (value.size == 1) // render without field-names if it's a single-field-class
        open + renderFields(p, p)(_._2).head + close
      else
        super.render
  }

  final case class ErrorNode(value: Throwable, colors: Colors) extends Node[Throwable] {
    override def render(implicit p: RenderParams): String = {
      val msgColors =
        Colors(primaryColor = colors.secondaryColor, secondaryColor = colors.resetColor, resetColor = colors.resetColor)
      val msg = StringNode(value.getMessage, msgColors).render(p.nextDepth)

      val oneLine = open + msg + close
      if (oneLine.length <= p.rightBorder || !p.multiline) {
        oneLine
      } else {
        open + "\n" + p.indent + msg + "\n" + p.indent + close
      }
    }

    protected def name: String = value.getClass.getSimpleName.stripSuffix("$1")
    protected def open(implicit p: RenderParams): String = colors.primary + name + "("
    protected def close(implicit p: RenderParams): String = colors.primary + ")"
  }
}
