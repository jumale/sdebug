package com.github.jumale.sdebug

final case class Cell(x: Int, y: Int, value: Node[Any], width: Int = 0, height: Int = 0) {
  lazy val contentWidth: Int = rawLines.map(_.length).max
  lazy val contentHeight: Int = rawLines.length
  lazy val outerWidth: Int = width + 3

  private lazy val rawLines: Vector[String] = {
    value.render(RenderParams(colorize = false, simplified = true, maxWidth = 80)).split("\n").toVector
  }

  def render(colors: Colors)(implicit p: RenderParams): Vector[String] = {
    val header: String = colors.primary + ("-" * outerWidth)
    val content: Vector[String] = value
      .render(p.copy(simplified = true, maxWidth = 80))
      .split("\n")
      .toVector
      .padTo(height, "")
      .zipWithIndex
      .map { case (line, idx) =>
        colors.primary + "| " + line + (" " * (outerWidth - rawLines.lift(idx).getOrElse("").length - 2))
      }

    header +: content
  }
}

final case class Table(header: Seq[String], rows: Seq[Seq[Node[Any]]], colors: Colors) {
  private lazy val headerColors = Colors(colors.secondaryColor, colors.resetColor, colors.resetColor)

  def render(implicit p: RenderParams): String = {
    val allRows = header.map(v => Node.RawNode(v, headerColors)) +: rows

    val maxRowSize = allRows.map(_.size).max
    def pad(y: Int, row: Vector[Cell]): Vector[Cell] =
      row ++ (row.size until maxRowSize).map(x => Cell(x, y, Node.RawNode("", colors)))

    val cells = allRows.toVector.zipWithIndex.flatMap { case (row, y) =>
      pad(y, row.toVector.zipWithIndex.map { case (node, x) => Cell(x, y, node) })
    }

    val lines = cells
      .groupBy(_.x)
      .flatMap { case (_, col) =>
        val maxWidth = col.map(_.contentWidth).max
        col.map(_.copy(width = maxWidth))
      }
      .groupBy(_.y)
      .toVector
      .sortBy(_._1)
      .flatMap { case (_, row) =>
        val maxHeight = row.map(_.contentHeight).max
        row.toVector
          .sortBy(_.x)
          .map(_.copy(height = maxHeight).render(colors))
          .foldLeft(Vector.fill(maxHeight + 1)("")) { case (prev, lines) =>
            prev.zipWithIndex.map { case (prevLine, idx) =>
              prevLine + lines(idx)
            }
          }
          .map(line => line + (if (line.last == '-') "-" else colors.primary + "|"))
      }
    (lines :+ lines.head).mkString("\n")
  }
}
