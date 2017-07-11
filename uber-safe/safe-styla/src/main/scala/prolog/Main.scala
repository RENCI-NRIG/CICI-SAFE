package prolog

import prolog.interp.Prog
import prolog.io.{IO, TermParser}
import prolog.terms.{Term, Var}
import scala.collection.mutable.{LinkedHashMap}
import com.typesafe.scalalogging.LazyLogging

object Styla extends LazyLogging{
  def main(args: Array[String]): Unit = {
    go(args)
  }

  def go(args: Array[String]) = {
    IO.start
    toplevel(args.toList)
    IO.stop // println("\nProlog execution halted\n")
  }

  def printvar(x: (String, Term)) = {
    val a = x._1.toString
    //val b: String = TermParser.term2string(prog.db.revVars(), List(x._2), "")
    val b: String = TermParser.term2string(x._2)
    if (a != b && !a.startsWith("_"))
      IO.println(a + " = " + b)
  }

  def toplevel(topgoals: List[String]) {

    val prog: Prog = new Prog()
    val parser = new TermParser() //prog.db.vars)

    topgoals.foreach { x =>
      parser.vars.clear()
      val gv = parser.parse(x)
      if (null != gv) {
        prog.set_query(gv)
        var more = true
        while (more) {
          val answer = prog.getElement()
          if (answer.eq(null)) more = false
          else
            parser.vars.foreach(printvar)
        }
      }
    }

    var goalWithVars = (parser.parse("true. "), parser.vars)
    while (!goalWithVars.eq(null)) {
      goalWithVars = parser.readGoal()
      solveStyQuery(prog, goalWithVars)
    }
  }

  /**
   * A clean interface for invoking Styla inference
   * @param prog          styla inference engine
   * @param goalWithVars  goal (a list of terms) with embedded variables 
   * @param findall       a boolean indicating whether to find all solution
   * @return              solution clauses
   */

  def solveStyQuery(prog: Prog, goalWithVars: Tuple2[List[Term], LinkedHashMap[String, Var]], 
      findall: Boolean = true): List[List[Term]] = {
    val solutions = List[List[Term]]()
    if (!goalWithVars.eq(null)) {
      val goal = goalWithVars._1
      logger.info(s"goal=${TermParser.clause2string(goal)}")
      val vars = goalWithVars._2
      prog.set_query(goal)
      var more = true
      while (more) {
        val answer = prog.getElement()
        if (answer.eq(null)) more = false
        else {
          if (vars.isEmpty) {
            IO.println("yes.")
            //logger.info(s"[Styla solveStyQuery] prog.db.allByPrimary: ${prog.db.allByPrimary}")
            //logger.info(s"[Styla solveStyQuery] prog.db.rulesByPrimary: ${prog.db.rulesByPrimary}")
            //logger.info(s"[Styla solveStyQuery] prog.db.factsBySecondary: ${prog.db.factsBySecondary}")
          } else {
            vars.foreach(printvar)
            IO.println(";")
          }
          if(findall != true) {
            more = false  // early terminate; do findOne
          }
        }
      }
      IO.println("no (more) answers\n")
      prog.trail.unwind(0)
    }     
    solutions
  } 
}

