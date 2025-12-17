package com.github.jumale.sdebug.slick

import com.github.jumale.sdebug.{Debugger, Palette}
import slick.dbio.{Effect, NoStream}
import slick.jdbc.PostgresProfile.api._
import slick.sql.SqlAction

import scala.concurrent.ExecutionContext
import scala.util.matching.Regex
import scala.util.{Failure, Success}

trait SdebugSlickSupport { self: Debugger =>
  trait SdebugSlickImplicits {
    implicit class SdebugDbioValueOps[T](self: DBIO[T]) {
      def sdebugDbio(implicit ec: ExecutionContext): DBIO[T] = {
        val header = breadcrumbHeader()
        self.asTry.flatMap {
          case Success(v) =>
            formatAndPrint(header = header)(self.getDumpInfo, v)
            DBIO.successful(v)

          case Failure(e) =>
            formatAndPrint(header = header)(self.getDumpInfo, e)
            DBIO.failed(e)
        }
      }

      def sdebugDbioMap(fn: T => Any)(implicit ec: ExecutionContext): DBIO[T] = {
        val header = breadcrumbHeader()
        self.asTry.flatMap {
          case Success(v) =>
            formatAndPrint(header = header)(self.getDumpInfo, fn(v))
            DBIO.successful(v)

          case Failure(e) =>
            formatAndPrint(header = header)(self.getDumpInfo, e)
            DBIO.failed(e)
        }
      }
    }

    implicit class SdebugSqlActionOps[+R, +S <: NoStream, -E <: Effect](self: SqlAction[R, S, E]) {
      def sdebugAnalyze(implicit ec: ExecutionContext): DBIOAction[R, S, Effect with E] = {
        val header = breadcrumbHeader()
        DBIO.sequence(self.statements.map { stmt =>
          //noinspection SqlDialectInspection
          sql"EXPLAIN ANALYZE #$stmt".as[String].map { result =>
            formatAndPrint(header = header)(stmt)
            doPrint("\n" + SdebugSlickSupport.formatAnalyzeOutput(result))
          }
        }).flatMap(_ => self)
      }
    }
  }
}

object SdebugSlickSupport {
  private val costRegex: Regex = """cost=([\d.]+)""".r
  private val timeRegex: Regex = """time=([\d.]+)""".r

  def formatAnalyzeOutput(output: Seq[String], palette: Palette = Palette.console): String = {
    def parseMaxRange(c: String): Double =
      c.split("\\.\\.").toSeq.map(_.toDouble).max

    val (allCosts, allTimes) = output.foldLeft((Seq.empty[Double], Seq.empty[Double])) {
      case ((accC, accT), line)=>
      (
        accC ++ costRegex.findAllIn(line).matchData.map(m => parseMaxRange(m.group(1))),
        accT ++ timeRegex.findAllIn(line).matchData.map(m => parseMaxRange(m.group(1))),
      )
    }
    val costEdge: Double = allCosts.distinct.sorted.reverse.lift(1).getOrElse(Double.MaxValue)
    val timeEdge: Double = allTimes.distinct.sorted.reverse.lift(1).getOrElse(Double.MaxValue)
    output
      .map(costRegex.replaceAllIn(_, { m =>
        val cost = parseMaxRange(m.group(1))
        val color = if (cost < costEdge) palette.blue else palette.red
        s"${palette.yellow}cost=$color${m.group(1)}${palette.reset}"
      }))
      .map(timeRegex.replaceAllIn(_, { m =>
        val time = parseMaxRange(m.group(1))
        val color = if (time < timeEdge) palette.cyan else palette.magenta
        s"${palette.green}time=$color${m.group(1)}${palette.reset}"
      }))
      .mkString("\n")
  }
}
