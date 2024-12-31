package com.github.jumale.sdebug

/** A set of colors for coloring output of a single type of node (e.g. a string, or a number, etc.).
  *
  * @param primaryColor
  *   The main foreground color of the node.
  * @param secondaryColor
  *   A secondary color of the node. The meaning can vary depending on the node type.
  * @param resetColor
  *   The color to reset the output to default after the node.
  */
final case class NodeColors(primaryColor: String, secondaryColor: String, resetColor: String) {
  def primary(implicit p: RenderParams): String = if (p.colorize) primaryColor else ""
  def secondary(implicit p: RenderParams): String = if (p.colorize) secondaryColor else ""
  def reset(implicit p: RenderParams): String = if (p.colorize) resetColor else ""
}
