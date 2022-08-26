package com.github.jumale.sdebug

final case class RenderParams( //
  depth: Int = 0,
  indentSize: Int = 2,
  maxWidth: Int = 120,
  multiline: Boolean = true,
  colorize: Boolean = true
) {
  lazy val indent: String = " " * depth * indentSize
  lazy val fieldIndent: String = indent + (" " * indentSize)
  lazy val rightBorder: Int = maxWidth - (depth * indentSize)

  def nextDepth: RenderParams = copy(depth = depth + 1)
}
