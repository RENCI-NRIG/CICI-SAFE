package prolog.interp

import prolog.terms.SystemObject
import prolog.terms.Term
import prolog.terms.Trail
import com.typesafe.scalalogging.LazyLogging

class Unfolder(prog: Prog, val goal: List[Term], matchingClauses: List[List[Term]])
  extends SystemObject with LazyLogging {
  def this(prog: Prog) = this(prog, null, List[List[Term]]())
  type CLAUSE = List[Term]
  type GOAL = List[Term]

  val atClause: Iterator[List[Term]] = matchingClauses.toIterator

  // For debugging
  var previousClause: List[Term] = null
  var numTakenBranches: Int = 0

  // old top of the inference trail
  private val oldtop = prog.trail.size
  def isLastClause = !atClause.hasNext
  def getOldtop = this.oldtop

  private final def unfoldWith(cs: CLAUSE, trail: Trail): GOAL = {
    trail.unwind(oldtop)

    goal match {
      case Nil => Nil
      case g0 :: xs =>
        //logger.info(s"\n[Unfolder unfoldWith] cs= ${Term.printClause(cs)}")
        //println(s"\n[Unfolder unfoldWith] cs= ${Term.printClause(cs)}")

        //prog.copier.clear
        //val ds = Term.copyList(cs, prog.copier)
        val ds = Term.copyList(cs)

        // Check copy list
        logger.info(s"[Unfolder unfoldWith]  copylist= ${Term.printClause(ds)}")

        //if (cs.head.matches(g0, trail)) {
        val oldtop = trail.size
        if (ds.head.unify(g0, trail)) { // copy first to avoid a concurrency bug
          //ds.head.unify(g0, trail)
          logger.info(s"[Unfolder unfoldWith]  unification succeeds  new goals= ${ds.tail ++ xs}")
          ds.tail ++ xs
        } else { // unification fails
          logger.info(s"[Unfolder unfoldwith] ${ds.head} cannot be unified with ${g0}")
          trail.unwind(oldtop)
          null
        }
    }
    // Nil: no more work
    // null: we have failed
  }

  def nextGoal(): GOAL = {
    var newgoal: GOAL = null
    while (newgoal.eq(null) && atClause.hasNext) {

      logger.info(s"\n[Unfolder nextGoal] goal= ${Term.printClause(goal)}") 

      val clause: CLAUSE = atClause.next
      previousClause = clause
      numTakenBranches = numTakenBranches + 1 

      //println(s"\n[Unfolder nextGoal] goal= ${Term.printClause(goal)}")
      logger.info(s"\n[Unfolder nextGoal] clause= ${Term.printClause(clause)}")
      newgoal = unfoldWith(clause, prog.trail)
      println(s"\n[Unfolder nextGoal] newgoal= ${Term.printClause(newgoal)}")
    }
    newgoal
  }

  //def topGoal()
 
  /**
   * @DeveloperAPI
   */
  def unfolderStateAsString: String = {
    s"Previous clause: ${previousClause}      number of taken branches: ${numTakenBranches}"
  }

  override def toString(): String = {
    //println(s"atClause.size: ${atClause.size} (at the beginning)  atClause.length: ${atClause.length}")
    //atClause.foreach {c => println(s"${c};")}
    //println(s"atClause.size: ${atClause.size}  atClause.length: ${atClause.length}  MatchingClauses.length: ${matchingClauses.length}")
    //println(s"atClause.length: ${atClause.length}    MatchingClauses.length: ${matchingClauses.length}")
    //val array = new Array[List[Term]](atClause.size)
    //atClause.copyToArray(array)
    val res = s"""Step: ${goal}   MatchingClauses: ${matchingClauses.mkString(" | ")}   last: ${isLastClause}\n""" +
              unfolderStateAsString
    //println(s"atClause.size (after display): ${atClause.size}   atClause.length: ${atClause.length}")
    res
  }
}

