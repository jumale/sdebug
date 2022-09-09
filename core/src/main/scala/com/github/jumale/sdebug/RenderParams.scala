package com.github.jumale.sdebug

/** @param depth
  *   current level of depth (for recursive rendering)
  * @param indentSize
  *   amount of spaces to indent a single level
  * @param maxWidth
  *   objects and collections will be rendered as single-line if they fit into this width
  * @param multiline
  *   set it to false to force render everything as single line
  * @param colorize
  *   disables colors but keeps formatting
  * @param raw
  *   some values support raw mode (e.g. strings are rendered without quotes)
  */
final case class RenderParams( //
  depth: Int = 0,
  indentSize: Int = 2,
  maxWidth: Int = 120,
  multiline: Boolean = true,
  colorize: Boolean = true,
  raw: Boolean = false
) {
  lazy val indent: String = " " * depth * indentSize
  lazy val fieldIndent: String = indent + (" " * indentSize)
  lazy val rightBorder: Int = maxWidth - (depth * indentSize)

  def nextDepth: RenderParams = copy(depth = depth + 1)
  def noColors: RenderParams = copy(colorize = false)
}
