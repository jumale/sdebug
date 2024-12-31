package com.github.jumale.sdebug

/** @param depth
  *   Current level of depth (for recursive rendering).
  * @param indentSize
  *   Number of spaces for each level of indentation.
  * @param maxWidth
  *   Maximum width of a single line. Values that exceed this width will be split into multiple lines.
  * @param multiline
  *   Set it as FALSE to force-render all values as single-line, even if they exceed maxWidth.
  * @param showKeys
  *   Set it as FALSE to hide keys in maps and field-names in objects.
  * @param colorize
  *   Set it as FALSE to disable colors but keep formatting.
  * @param simplified
  *   Some values support an expanded and a simplified formatting mode. Set it as TRUE to force simplified mode.
  */
final case class RenderParams(
  depth: Int = 0,
  indentSize: Int = 2,
  maxWidth: Int = 120,
  multiline: Boolean = true,
  showKeys: Boolean = true,
  colorize: Boolean = true,
  simplified: Boolean = false
) {
  lazy val indent: String = " " * depth * indentSize
  lazy val fieldIndent: String = indent + (" " * indentSize)
  lazy val rightBorder: Int = maxWidth - (depth * indentSize)

  def nextDepth: RenderParams = copy(depth = depth + 1)
  def noColors: RenderParams = copy(colorize = false)
}
