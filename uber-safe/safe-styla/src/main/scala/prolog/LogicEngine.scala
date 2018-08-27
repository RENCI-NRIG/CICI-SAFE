package prolog

import prolog.interp.{Prog, Unfolder}
import prolog.io.IO
import prolog.io.TermParser
import prolog.terms._
import prolog.fluents.DataBase
import prolog.ProofService._

import scala.collection.mutable.ListBuffer
import com.typesafe.scalalogging.LazyLogging

class LogicEngine(var database: DataBase) extends Prog(database) with LazyLogging {
  def this() = this(new DataBase(List[List[Term]]()))
  def this(fname: String) = this(new DataBase(fname))
  def this(context: List[List[Term]]) = this(new DataBase(context))

  //val parser = new TermParser()
  //parser.vars.clear()

  //def setGoal(query: String) =
  //  set_query(parser.parse(query))
  
  /**
   * Simplify the logic engine by remove the parser
   */
  def setGoal(query: String) = { }

  def setGoal(answer: Term, goal: Term) = {
    Prog.init_with(database, answer, goal, this)
  }

  def askAnswer(): Term = this.getElement()
  
  /**
   * Entry point for invoking the Styla inference engine to solve a query
   * @param query       goals of the query
   * @param findall     boolean to indicate whether searching all solutions or searching one
   * @return            solutions as a list of terms 
   */
  def solveQuery(query: List[Term], findall: Boolean = true): List[Term] = {
    val solutions = ListBuffer[Term]()
    set_query(query)  // this will clean the trail and the orStack
    var more: Boolean = true
    while(more) {
      val answer = getElement()
      //println(s"[LogicEngine solveQuery] answer=${answer}")
      if(answer.eq(null)) more = false
      else {
        //println(s"[LogicEngine solveQuery] answer.getClass=${answer.getClass}")
        val cleancopier = new Copier() 
        //val s = answer.tcopy(cleancopier)
        val s = LogicEngine.copyTerm(answer, cleancopier)

        println(s"Answer to styla query: $s")
        println(s"Proof: ${orStack}    ${orStack.toList}") 
        println(s"Substitution trail: ${trail}    ${trail.toList}") 

        // print the logical proof
        //println( formatLogicalProof(orStack.toList, trail.toList) )

        println( formatLogicalInference(orStack.toList, trail.toList) )

        solutions += s
        if(findall != true) {
          more = false
        } 
      }
    } 
    //println(s"[LogicEngine solveQuery] db=${db}")
    println(s"[LogicEngine solveQuery] ${solutions.size} solutions are found: ${solutions}")
    //val solhead = solutions.head
    //println(s"[LogicEngine solveQuery] solutions.head=${solhead}   solutions.head.getClass=${solhead.getClass}")
    //trail.unwind(0)
    //orStack.clear()
    if(solutions.size == 0) { // for debugging
      logger.info("[LogicEngine solveQuery] No solutions are found")
      logger.info(s"[LogicEngine solveQuery] query: ${query}")
      logger.info(s"[LogicEngine solveQuery] trail: ${trail}")
      logger.info(s"[LogicEngine solveQuery] orStack: ${orStack}")
      //logger.info(s"[LogicEngine solveQuery] db.allByPrimary: ${db.allByPrimary}")
      //logger.info(s"[LogicEngine solveQuery] db.rulesByPrimary: ${db.rulesByPrimary}")
      //logger.info(s"[LogicEngine solveQuery] db.factsBySecondary: ${db.factsBySecondary}")
    }
    solutions.toList
  }
}

object LogicEngine {
  def copyTerm(t: Term, copier: Copier): Term = {
    t match {
      case f: Fun =>
        val arity = f.args.length

        val newfun = f.safeCopy()
        val newargs = new Array[Term](arity)
        newfun.args = newargs
        for(i <- 0 to arity-1) {
          newargs(i) = copyTerm(f.getArg(i), copier)
        }
        newfun
      case v: Var =>
        val root = v.ref 
        if(root == v) {
          copier.getOrElseUpdate(v, new Var(v.name))
        } else {
          copyTerm(root, copier)
        }
      case c: Const =>
        new Const(c.sym)
      case r: Real => Real(r.nval)
      case other =>  throw new Exception(s"Unrecognized term to copy: ${other}")
    }
  }

  def copyList(ts: List[Term], copier: Copier): List[Term] = {
    ts.map(t => copyTerm(t, copier))
  }

  def copyList(ts: List[Term]): List[Term] = {
    val copier = new Copier()
    copyList(ts, copier)
  }
}
