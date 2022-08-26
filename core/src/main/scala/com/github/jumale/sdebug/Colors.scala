package com.github.jumale.sdebug

final case class Colors(primaryColor: String, secondaryColor: String, resetColor: String) {
  def primary(implicit p: RenderParams): String = if (p.colorize) primaryColor else ""
  def secondary(implicit p: RenderParams): String = if (p.colorize) secondaryColor else ""
  def reset(implicit p: RenderParams): String = if (p.colorize) resetColor else ""
}

final case class Palette(
  black: String,
  red: String,
  green: String,
  yellow: String,
  blue: String,
  magenta: String,
  cyan: String,
  white: String,
  bold: String,
  underlined: String,
  reset: String
)

object Palette {
  lazy val default: Palette = Palette(
    black = Console.BLACK,
    red = Console.RED,
    green = Console.GREEN,
    yellow = Console.YELLOW,
    blue = Console.BLUE,
    magenta = Console.MAGENTA,
    cyan = Console.CYAN,
    white = Console.WHITE,
    bold = Console.BOLD,
    underlined = Console.UNDERLINED,
    reset = Console.RESET
  )

  lazy val debug: Palette = Palette(
    black = "[k]",
    red = "[r]",
    green = "[g]",
    yellow = "[y]",
    blue = "[e]",
    magenta = "[m]",
    cyan = "[c]",
    white = "[w]",
    bold = "[d]",
    underlined = "[u]",
    reset = "[t]"
  )
}
