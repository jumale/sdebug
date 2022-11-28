package com.github.jumale.sdebug

object Stack {
  def apply(target: Throwable): Seq[Line] =
    target.getStackTrace.toSeq.map(s => Line(s.getFileName, s.getLineNumber))

  def apply(): Seq[Line] = apply(new Exception).tail

  final case class Line(file: String, number: Int) {
    override def toString: String = s"$file:$number"
    def isEmpty: Boolean = this == Line.empty
  }

  object Line {
    val empty: Line = Line("unknown", 0)
  }
}
