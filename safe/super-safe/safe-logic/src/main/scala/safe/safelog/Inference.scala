package safe.safelog

import scala.collection.{GenIterable, GenSeq, GenSet}
//import scala.collection.parallel.immutable.ParSet
import scala.concurrent.ExecutionContext.Implicits.global //TODO

import Term.{dropStatement, isGrounded, mostGenericUnifier, subst, mguSeq}

import com.typesafe.config.ConfigFactory

//import InferenceService.{envContext, localSlangContext, proofContext}
//import InferenceService.{proofContext}

import safe.cache.SafeTable

import SafelogException.printLabel

import safe.safelog.AnnotationTags._

import scala.util.control.Breaks._
import scala.collection.mutable.{LinkedHashSet => OrderedSet, Map => MutableMap}

trait InferenceImpl extends com.typesafe.scalalogging.LazyLogging {
  slogInference: InferenceService =>

  protected def substQuery(
      terms: Seq[Term]
    , substSeq: Iterable[Term => Term]
    , accumulator: scala.collection.mutable.Map[String, Seq[String]]
  ): scala.collection.mutable.Map[String, Seq[String]] = {

    @annotation.tailrec
    def substQueryHelper(terms: Seq[Term], subst: Term => Term): Unit = terms match {
      case Variable(name, _, _, _) +: tail => 
        val head = terms.head
	val value = subst(head)
        if(value != head) {
	  if(!accumulator.contains(head.toString())) accumulator(head.toString()) = value.toString() +: Nil
	  else accumulator.update(head.toString(), value.toString() +: accumulator(head.toString()))
	}
	substQueryHelper(tail, subst)
      case Structure(name, xterms, _, _, _) +: tail => 
        val head = terms.head
	val value = subst(head)
        if(value != head) substQueryHelper(xterms ++: tail, subst)
	else substQueryHelper(tail, subst)
      case head +: tail =>  //TODO: we may simple ignore this case; Constant, FunTerm, and SetTerm substitutions may not make sense
	val value = subst(head)
        if(value != head) {
	  if(!accumulator.contains(head.toString())) accumulator(head.toString()) = value.toString() +: Nil
	  else accumulator.update(head.toString(), value.toString()+: accumulator(head.toString()))
	}
	substQueryHelper(tail, subst)
      //case Nil => accumulator.toMap
      case Nil => 
    }
    for (subst <- substSeq) { 
      substQueryHelper(terms, subst) 
    }
    accumulator
  }

  /**
   * solve: given a parsed data set, solve a sequence of queries
   */
  def solveWithValue(
      program: Map[Index, OrderedSet[Statement]]
    , queries: Seq[Statement]
    , isInteractive: Boolean = false
  ): Seq[Seq[Map[String, Seq[String]]]] = {
    val subcontext = Subcontext("_object", program)
    val res = for {
      query <- queries
      resultTerms = solveAQuery(query, isInteractive) (envContext, Seq(subcontext))
      accumulator = substQuery(query.terms, resultTerms, scala.collection.mutable.Map.empty[String, Seq[String]])
    } yield(accumulator.toMap)
    Seq(res.toSeq)
  }

  def solveInParallel(
      program: Map[Index, OrderedSet[Statement]]
    , queries: Seq[Statement]
    , isInteractive: Boolean = false
  ): Seq[Seq[Statement]] = {

    /**=== DEBUG Start=====
    if(true) {
      return Seq(Seq(Result(Constant("slogPong") +: Nil)))
    } 
    ====== DEBUG End==== **/

    //logger.info(s"Starting inference")
    val t0 = System.nanoTime()
    val subcontext = Subcontext("_object", program)

    val tsc = System.nanoTime()
    println(s"Elapsed time for creating proof subcontext is: ${(tsc - t0)/Math.pow(10, 6)} ms")

    val res = for {
      query <- queries
      t00 = System.nanoTime() 
      _ = println(s"query: ${query}")
      resultTerms = solveAQuery(query, isInteractive, findAllSolutions = false) (envContext, Seq(subcontext))
      t01 = System.nanoTime() 
      //_ = logger.info(s"Elapsed time for SOLVE is: ${(t01 - t00)/Math.pow(10, 6)} ms\n\n")
      _ = println(s"Elapsed time for SOLVE is: ${(t01 - t00)/Math.pow(10, 6)} ms") // DEBUG
      _ = if(isInteractive) iterativePrint(resultTerms, query, isInteractive)
      subst <- resultTerms
      //if(!(subst(Constant('defenvMayBe)) == Constant('nil))) // check to verify if the output is nil
      //_ = println(s"query.terms: ${query.terms} -> ${query.terms.map(subst)}")
    } yield query.terms.length match {
      case 1 => Result(query.terms.map(subst))
      case _ => Result(Constant("query(_*)") +: query.terms.map(subst)) // > 1
    }

    val t1 = System.nanoTime()
    //val elapsedTime = (t1 - t0)/Math.pow(10, 6)
    logger.info(s"Elapsed time for solve is: ${(t1 - t0)/Math.pow(10, 6)} ms")
    println(s"Elapsed time for solve is: ${(t1 - t0)/Math.pow(10, 6)} ms")
    if(res.isEmpty && isInteractive == true) 
      printLabel('failure)

    Seq(res.toSeq)
  }
 

  def solve(
      program: Map[Index, OrderedSet[Statement]]
    , queries: Seq[Statement]
    , isInteractive: Boolean = false
  ): Seq[Seq[Statement]] = {

    /**=== DEBUG Start=====
    if(true) {
      return Seq(Seq(Result(Constant("slogPong") +: Nil)))
    } 
    ====== DEBUG End==== **/

    logger.info(s"Starting inference")
    val t0 = System.nanoTime()
    val subcontext = Subcontext("_object", program)

    //println("[slogInference solve] proofContext.keySet: " + proofContext.keySet)
    //println("[slogInference solve] proofContext.values: " + proofContext.values)

    val res = for {
      query <- queries
      t00 = System.nanoTime() 
      //_ = println(s"envContext = ${envContext}")
      resultTerms = solveAQuery(query, isInteractive) (envContext, Seq(subcontext))
      t01 = System.nanoTime() 
      //_ = logger.info(s"Elapsed time for SOLVE is: ${(t01 - t00)/Math.pow(10, 6)} ms\n\n")
      //_ = println(s"Elapsed time for SOLVE is: ${(t01 - t00)/Math.pow(10, 6)} ms\n") // DEBUG
      _ = if(isInteractive) iterativePrint(resultTerms, query, isInteractive)
      subst <- resultTerms
      //if(!(subst(Constant('defenvMayBe)) == Constant('nil))) // check to verify if the output is nil
      //_ = println(s"query.terms: ${query.terms} -> ${query.terms.map(subst)}")
    } yield query.terms.length match {
      case 1 => Result(query.terms.map(subst))
      case _ => Result(Constant("query(_*)") +: query.terms.map(subst)) // > 1
    }

    val t1 = System.nanoTime()
    //val elapsedTime = (t1 - t0)/Math.pow(10, 6)
    //logger.info(s"Elapsed time for solve is: ${(t1 - t0)/Math.pow(10, 6)} ms")
    println(s"Solved in ${(t1 - t0)/Math.pow(10, 6)} ms")
    //if(res.isEmpty && isInteractive == true) 
    //  printLabel('failure)

    Seq(res.toSeq)
  }
  
  /**
   * Build resulting statements according to the 
   * solution of a query 
   */ 
  def buildResultStatements(query: Statement, solutions: Iterable[Term => Term]): Iterable[Statement] = {
    val res = for(subst <- solutions) yield query.terms.length match {
      case 1 => Result(query.terms.map(subst))
      case _ => Result(Constant("query(_*)") +: query.terms.map(subst)) // > 1
    }
    res
  } 
    
  def solveWithContext(
      queries: Seq[Statement]
    , isInteractive: Boolean
    , findAllSolutions: Boolean = true // by defaut to find all solutions; for legacy code. 
  )(  envContext: MutableMap[StrLit, EnvValue]
    , subcontexts: Seq[Subcontext]
  ): Seq[Seq[Statement]] = {

    /**=== DEBUG Start=====
    if(true) {
      return Seq(Seq(Result(Constant("slogPong") +: Nil)))
    } 
    === DEBUG Start=====**/

    logger.info(s"Starting inference")
    val t0 = System.nanoTime()

    val denyQueries = queries.collect { case aq: AnnotatedQuery if (aq.queryTag == DENY) => aq }
    val requireQueries = queries.collect { 
      case aq: AnnotatedQuery if (aq.queryTag == REQUIRE) => aq
      case q: Query => q  // by default require queries
    }
    val allowQueries = queries.collect { case aq: AnnotatedQuery if (aq.queryTag == ALLOW) => aq }

    //println("[slogInference] solveWithContext  queries " + queries)
    //println("[slogInference] solveWithContext  denyQueries " + denyQueries)
    //println("[slogInference] solveWithContext  requireQueries " + requireQueries)
    //println("[slogInference] solveWithContext  allowQueries " + allowQueries)

    var denySatisfied: Boolean = false
    var requireSatisfied: Boolean = false
    var allowSatisfied: Boolean = false
    //val wAllowOrRequire: Boolean = (!requireQuery.isEmpty || !allowQueries.isEmpty)

    var denyRes = Seq[Statement]() 
    var allowRes = Seq[Statement]()
    var requireRes = Seq[Statement]()
 
    breakable {
      for(query <- denyQueries) { // Process deny queries
        val t00 = System.nanoTime() 
        val resultTerms = solveAQuery(query, isInteractive, findAllSolutions)(
                                      envContext, subcontexts)
        val t01 = System.nanoTime() 
        if(resultTerms.isEmpty == false) {
          val resTmp = buildResultStatements(query, resultTerms) 
          denyRes = denyRes ++ resTmp
          denySatisfied = true
          break
        }
      }
    }

    if(!denySatisfied) {
      breakable {
        for(query <- allowQueries) { // Process allow queries
          val t00 = System.nanoTime()
          val resultTerms = solveAQuery(query, isInteractive, findAllSolutions)(
                                        envContext, subcontexts)
          val t01 = System.nanoTime()
          if(resultTerms.isEmpty == false) {
            val resTmp = buildResultStatements(query, resultTerms)
            allowRes = allowRes ++ resTmp
            allowSatisfied = true
            break
          }
          else {
            allowSatisfied = false
          }
        }
      }

      if(!allowSatisfied) {
        breakable {
          for(query <- requireQueries) { // Process require queries
            val t00 = System.nanoTime()
            val resultTerms = solveAQuery(query, isInteractive, findAllSolutions)(
                                          envContext, subcontexts)
            val t01 = System.nanoTime()
            if(resultTerms.isEmpty == true) {
              requireSatisfied = false
              break
            }
            else {
              val resTmp = buildResultStatements(query, resultTerms)
              requireRes = requireRes ++ resTmp
              requireSatisfied = true
            }
          }
        }
      }
    }

    val res = denyRes ++ allowRes ++ requireRes
    val t1 = System.nanoTime()
    val elapsedTime = (t1 - t0)/Math.pow(10, 6)
    logger.info(s"Elapsed time for solveWithContext is: ${(t1 - t0)/Math.pow(10, 6)} ms")
    //println(s"Elapsed time for solveWithContext is: ${(t1 - t0)/Math.pow(10, 6)} ms")
    //if(res.isEmpty && isInteractive == true) 
    //  printLabel('failure)
    val isInteractiveTmp = true
    //println("denySatisfied=" + denySatisfied + "  allowSatisfied=" + allowSatisfied 
    //          + "   requireSatisfied=" + requireSatisfied) 
    if( (denySatisfied || (!allowSatisfied && !requireSatisfied))  && isInteractiveTmp == true) { 
      //println("FAILURE")
      printLabel('failure) 
    }

    def passed: Boolean = !( (denySatisfied == true) || (allowSatisfied == false && requireSatisfied == false) )
    
    if(!passed) { // didn't pass
      Seq[Seq[Statement]]()
    } else {
       assert(res.size > 0, s"res.size must be large than 0;  res=${res};  denySatisfied=${denySatisfied}  allowSatisfied=${allowSatisfied}  requireSatisfied=${requireSatisfied}")
      // If $Self is not defined, then substitute $Self_depth by $Self
      if(!envContext.contains(StrLit("Self"))) {
        val selfSubst: Term => Term = (x: Term) => x match {
          case v: Variable if(v.id.name.startsWith("$Self")) => Variable("$Self") // Constant("$Self")  // Qiang: we need a varialbe
          case _ => x
        }
        val subst: Term => Term = (x: Term) => x match {
          case v: Variable  => selfSubst(x)
          case s: Structure => s.copy(terms = s.terms.map(selfSubst))
          case _ => x
        }
        Seq(res.toSeq.map{s => Result(s.terms.map(subst))})   // ToDO: unnecessary wrapping into a Seq
      } else {
        val r = Seq(res.toSeq)
        val t2 = System.nanoTime()
        val elapsedTime = (t2 - t0)/Math.pow(10, 6)
        logger.info(s"Elapsed time for ending solveWithContext is: ${(t2 - t0)/Math.pow(10, 6)} ms")
        //println(s"Elapsed time for ending solveWithContext is: ${(t2 - t0)/Math.pow(10, 6)} ms")
        r
      }
    }
  }

  // success reports a value to REPL
  protected def success(statement: Statement, isInteractive: Boolean = false, more: Boolean = true): Boolean = {
    val ret: Boolean = if(isInteractive) {
      printLabel('success)
      println(statement)
      val next: Boolean = if(more) {
        printLabel('more)
        val reader = new jline.console.ConsoleReader(System.in, System.out)
        val input: Boolean = reader.readLine() match {
	  case s if s.toLowerCase.matches("""^[y;\n](es)?""") => true // no-op //TODO: \n does not work since the match expects to be given in double quotes
	  case _ => failure(statement); false // System.exit(0) // TODO: 
        }
        input
      } else false
      next
    } else false
    ret
  }

  protected def failure(query: Statement, msg: String = "", isInteractive: Boolean = false): Unit = {
    logger.error("[" + Console.RED + msg + Console.RESET + "]")
    if(isInteractive) {
      printLabel('failure)
      logger.error("[" + Console.RED + msg + Console.RESET + "]")
    }
  }

  private def times(n: Int)(f: => Unit) = 1 to n foreach {_ => f}
  private def indentAndPrint(depth: Int, level: Int, msg: String = ""): Unit = {
    val indentAndPrintSpace = "    "
    times(depth+level)(print(indentAndPrintSpace)) 
    println(msg)
  }

  @annotation.tailrec
  final def iterativePrint(result: Iterable[Term => Term], query: Statement, isInteractive: Boolean): Unit = result.headOption match {
    case None => printLabel('failure) // no-op
    case Some(subst) => 
      val solutionsRaw = query.terms.map(subst)
      val r = result.tail
      val moreRes = r.size > 0
      val more: Boolean = if(query.terms.length > 1) success(Assertion(Constant("query(_*)") +: solutionsRaw), isInteractive, moreRes)
        else success(Assertion(solutionsRaw), isInteractive, moreRes)
      if(more) iterativePrint(r, query, isInteractive)
   }

  // =============== Solver ==================== //

  protected def filterAndBind(goal: Term, freshVariable: StrLit => Term)(  
      subcontexts: Seq[Subcontext]): OrderedSet[Statement] = {

    def matchStatements(index: StrLit): OrderedSet[Statement] = { 
      var facts = OrderedSet[Statement]()
      var rules = OrderedSet[Statement]()
      for(subcnt <- subcontexts) {
        subcnt.facts.get(index).map{ case factset => facts ++= factset }
        subcnt.rules.get(index).map{ case ruleset => rules ++= ruleset }
      }
      facts ++= rules // Merge rules into facts; facts first
      facts
    }

    // no secondeary index  
    val index = goal.primaryIndex
    val stmtsOnPrimaryIndex: OrderedSet[Statement] = matchStatements(index)     
    val matchedStatements = stmtsOnPrimaryIndex


//    /**
//     * Secondary index:
//     * This assumes that any fact doesn't share the same predicate type (name+arity) with any rule head in slog
//     * The inference engine wouldn't give the correct answer if this assumption breaks
//     */
//
//    val goalFactIndex: StrLit = goal match {
//      case s @ Structure(pred, speaker +: xterms, _, _, _)  => StrLit(s.primaryIndex.name + "_fact")
//      case _ => StrLit("")  
//    }
// 
//    val isFactGoal = !matchStatements(goalFactIndex).isEmpty 
//
//    val mostSpecificIndex = isFactGoal match {
//      case true => goal match {
//        case s @ Structure(pred, speaker +: xterms, _, _, _)  =>
//          xterms.head match {
//            case Constant(_, _, _, _) => s.secondaryIndex  // If firstParam is a constant, use the secondary index 
//            case _ => s.primaryIndex
//          }
//        case _ => goal.primaryIndex
//      }
//      case false => goal.primaryIndex
//    }
//        
//    val matchedStatements = Seq() ++ matchStatements(mostSpecificIndex)
//    val index = goal.primaryIndex // this is used for localContext; no optimization yet 
                                    


    //// An old impl of secondary index (with vulnerabilities)
    //val stmtsOnSecondaryIndex: Set[Statement] = goal match {
    //  case s @ Structure(pred, speaker +: xterms, _, _, _)  =>
    //    xterms.head match {
    //      case Constant(_, _, _, _) => 
    //        val index = s.secondaryIndex    // If firstParam is a constant, use the secondary index
    //        matchStatements(index)
    //      case _ => Set[Statement]()
    //    }
    //  case _ => Set[Statement]() 
    //}
 
    //var stmtsOnPrimaryIndex: Set[Statement] = Set[Statement]() 
    //val index = goal.primaryIndex
    //if(stmtsOnSecondaryIndex.size == 0) {  // cannot be resolved to facts; try rules 
    //  stmtsOnPrimaryIndex = matchStatements(index)     
    //}
    /// preserve the order of the matched statements; statements on secondary index go first
    /// convert to a seq to preserve the order
    //val matchedStatements = Seq() ++ stmtsOnSecondaryIndex ++ (stmtsOnPrimaryIndex -- stmtsOnSecondaryIndex)




      //val index = goal match {
      //  case s @ Structure(pred, speaker +: xterms, _, _, _) if s.primaryIndex != s.secondaryIndex => 
      //    xterms.head match {
      //      case Constant(_, _, _, _) => s.secondaryIndex
      //      case _ => s.primaryIndex
      //    }
      //  case _ => goal.primaryIndex
      //}
      //val matchedStatements = matchStatements(index).toSeq


    // old code 
    //println(s"In filterAndBind -- contextIds: $contextIds; goal: $goal; goal.primaryIndex: ${goal.primaryIndex}")
    //val index = if(goal.primaryIndex == '_Nil) goal.secondaryIndex else goal.primaryIndex

    //val index = goal.primaryIndex
    //val statements: Set[Set[Statement]] = {
    //  for {
    //    id                   <-  contextIds
    //    //_                    =   println(s"contextId: $id")
    //    credentialSetMayBe   =   proofContext.get(id)
    //    //_                    =   println(s"contextId status in proofContext: $credentialSetMayBe")
    //    credentialSet        <-  credentialSetMayBe   
    //    //_                    =   println(s"(id, index): ($id, $index)")
    //    //_                    =   println(s"$credentialSet")
    //    stmtsMayBe           =   credentialSet.get(index)    // A matched set of statements
    //    //_                    =   println(s"stmtsMayBe: $stmtsMayBe")
    //    stmts                <-  stmtsMayBe 
    //  } yield for {
    //    stmt                 <-  stmts
    //  } yield (stmt.bind(freshVariable))
    //}

    //val localSlangContextStatements: OrderedSet[Statement] = localSlangContext.get(index) match { 
    //  case Some(stmts) =>
    //    stmts.map{stmt => stmt.bind(freshVariable)}
    //  case _  => OrderedSet.empty
    //}

    val res = matchedStatements.map{stmt => stmt.bind(freshVariable)} //++ localSlangContextStatements
    //val res = statements.flatten ++ localSlangContextStatements
    //println(s"In filterAndBind -- final statements: $res")
    res
  }


  /** 
   * Helpers for SRN. Shared with slang, as We need them in slang 
   * inference as well.
   */

  def getNameComponents(nameTerm: Term, delimiter: String = "/"): Seq[String] = {
    val _nameStr = nameTerm.id.name
    val components: Seq[String] = _nameStr.split(delimiter)
    components
  }

  // Check if a name term represents a single-component name
  def singleComponentName(nameTerm: Term): Term = {  // example name: a/b/c
    val components = getNameComponents(nameTerm) 
    //println(s"[slogInfernece singleComponentName] components.length: ${components.length}")
    if(components.length >= 2) Constant("_false") // not a single component
    else nameTerm
  }

  def splitHead(nameTerm: Term): Seq[Term] = {  // example name: edu/duke/cs
    val components = getNameComponents(nameTerm) 
    logger.info(s"components: ${components}")
    if(components.length < 2) {
      Seq(Constant("_false")) // not splittable
    } else {
      val head = components.head
      val tail = components.tail.mkString("/")
      Seq(Constant(head), Constant(tail))
    }
  }

  def splitLast(nameTerm: Term): Seq[Term] = {  // example name: edu/duke/cs
    val components = getNameComponents(nameTerm) 
    if(components.length < 2) {
      Seq(Constant("_false")) // not splittable
    } else {
      val init = components.init.mkString("/")
      val last = components.last
      Seq(Constant(init), Constant(last))
    }
  }

/* unused code
  def splittableName(nameTerm: Term): Term = {
    val singleComp = singleComponentName(nameTerm)
    singleComp match {
      case Constant(StrLit("_false"), _, _, _) => nameTerm
      case _ => Constant("_false")
    }
  }

  def splitLast(nameTerm: Term): Term = {
    val components = getNameComponents(nameTerm)
    if(components.length < 2) Constant("_false") // not splittable
    else Constant(components(components.length - 1)) // The last  component 
  }

  def splitTop(nameTerm: Term): Term = {
    val components = getNameComponents(nameTerm)
    if(components.length < 2) Constant("_false") // not splittable
    else Constant(components.slice(0, components.length -1).mkString("/")) // All but the last
  }

  def dropDirPrefix(nameTerm: Term, dirTerm: Term): Term = {
    val _name = nameTerm.id.name
    val _dir = dirTerm.id.name
    val prefixIndex = _name.indexOf(_dir.substring(1))
    if(prefixIndex == 0) // _dir is a valid prefix of _name
      Constant(nameTerm.id.name.drop(dirTerm.id.name.length))
    else Constant("_false")
  }
*/



  //@annotation.tailrec // TODO: make this tailrec
  protected def solveAQuery(
     query: Statement
   , isInteractive: Boolean = false
   , findAllSolutions: Boolean = true  // Specify whether to find all solutions or just find one solution.
  )( envContext: MutableMap[StrLit, EnvValue]   // Qiang: slog inference doesn't use envContext at all
   , subcontexts: Seq[Subcontext]
  ): Iterable[Term => Term] = {

    var totalEdges = 0
    var totalBranches = 0
    var totalMgus = 0
    var failedBranches = 0
    var failedMgus = 0
    var proofPathDepth = 0
   
    /*
    def getStatements(credentialSet: ProofSubContext, goal: Term): Option[Set[Statement]] = credentialSet.get(goal.primaryIndex)
    import scala.util.{Success, Failure, Try}
    import scala.concurrent.{Await, Future, Promise}
    def filterAndBindInParallel(contextIds: Set[SetId], goal: Term, freshVariable: StrLit => Variable): Set[Statement] = {
      val statements: Set[Set[Statement]] = for {
        credentialSetsMayBe  <-  traverseAndCollectSuccess(contextIds)(proofContext.get)
        stmtsSets            <-  traverseAndCollectSuccess(credentialSetsMayBe)(getStatements, goal)
      } yield (stmtsSets.flatten) map { stmt =>
        (stmt.bind(freshVariable))
      }
    }
    */


    //val mystmts: Set[Statement] = proofContext.get(StrLit("_object")).get.get(query.terms.head.primaryIndex).getOrElse(Set.empty)

    def incTotalMgus(): Unit = {
      //println(s"totalMgus = ${totalMgus};  after inc, totalMgus = ${totalMgus + 1}")
      totalMgus = totalMgus + 1
    }

    def solveGoalTerm(goal: Term, goals: Seq[Term], depth: Int): Iterable[Tuple2[Term => Term, Seq[Term]]] = {
      /**
      val start: Long = System.nanoTime()
      val delay: Long = 100000 //100 microseconds
      while(start + delay >= System.nanoTime()){}
      //Thread.sleep(1)
      return(Seq(Tuple2((x: Term) => x, goals.tail)))
      **/
      /** TODO:
        1. memo goals.head
        2. if goals.head is found again, then check for answers
          2a. If no answers found, then freeze stack
            -- execute other branches
            -- on completion, resume this branch
          2b. If answers found, then execute with those answers rather than doing an SLD
       */

      //println("[slogInference solveGoalTerm] contextIds " + contextIds)
      //println("[slogInference solveGoalTerm] goal " + goal + "   goals " + goals )
  
      val statements: OrderedSet[Statement] = filterAndBind(
          goal
        , variable => if(variable.name == "$Self" && envContext.contains(StrLit("Self"))) envContext.get(StrLit("Self")).get.asInstanceOf[Term] else Variable(s"${variable.name}_${depth}")   // Substitution for $Self if that hasn't been done 
        //, variable => Variable(s"${variable.name}_${depth}")
      )(subcontexts)

      val res = for {
        statement <- statements
        //_ = logger.debug("branch: statement: " + statement)
        //_ = logger.debug("branch: statement.terms.head: " + statement.terms.head)
        //_ = println("branch: statement.terms.head: " + statement.terms.head)
        _ = incTotalMgus()
        subst <- mostGenericUnifier(statement.terms.head, goal)
        newGoals = (statement.terms.tail ++ goals.tail).map(subst)
        //_ = logger.debug("newGoals: " + newGoals)
        //_ = println("[slogInference solveGoalTerm] goal: " + goal + "    newGoals: " + newGoals)
      } yield (subst, newGoals)

      val mguFailures = statements.size - res.size
      if(mguFailures > 0) {
        failedMgus = failedMgus + mguFailures
        //println(s"new mgu failures: ${mguFailures};  failedMgus = ${failedMgus}")
      }
 
        //if(findFreshGoals(goal)) Seq((x=>x, goals.tail)) // subsumes check
        //if(findFreshGoals(goal)) Seq((x=>x, goals.tail)) // subsumes check
      res
    }
 

    @annotation.tailrec
    def branch(goals: Seq[Term], depth: Int): Iterable[Tuple2[Term => Term, Seq[Term]]] = goals.head match {

      case Constant(StrLit("true"), _, _, _)     => Seq((x => x, goals.tail))
      case Constant(StrLit("false"), _, _, _)    => Nil
      case Structure(StrLit("spec"), _, _, _, _) => Seq((x => x, goals.tail))
      case Structure(StrLit("_subset"), leftTerm +: rightTerm +: Nil, _, _, _) =>
        //val leftValue  = solve(contextIds, Seq(Query(Seq(leftTerm))), false)
        //val rightValue = solve(contextIds, Seq(Query(Seq(rightTerm))), false)
        val leftValueTerms  = recurse(Seq(leftTerm))
        val rightValueTerms = recurse(Seq(rightTerm))
        val leftValue  = leftValueTerms.map{subst => subst(leftTerm)}
        val rightValue = rightValueTerms.map{subst => subst(rightTerm)}
        //println(s"leftValue: $leftValue")
        //println(s"rightValue: $rightValue")
        if(leftValue.toSet subsetOf rightValue.toSet) Seq((x => x, goals.tail)) else Nil
      case goal @ Structure(name, xterms, _, _, _) if (
           name == StrLit("_lt")
         | name == StrLit("_lteq")
         | name == StrLit("_gt")
         | name == StrLit("_gteq") 
         | name == StrLit("_subset") 
         | name == StrLit("_in")
       ) => if(goal.compare()) Seq((x => x, goals.tail)) else Nil

      case goal @ Structure(name, xterms, _, _, _) if (
          name == StrLit("_plus")
        | name == StrLit("_minus")
        | name == StrLit("_times")
        | name == StrLit("_div")
        | name == StrLit("_rem")
        | name == StrLit("_max")
        | name == StrLit("_min")
      ) =>
        val decimalFormat: java.text.DecimalFormat= new java.text.DecimalFormat("###.####")
        //val numericConstant = Term.numberFromString(decimalFormat.format(goal.eval())) // TODO
        val numericConstant = decimalFormat.format(goal.eval()) 
        val result = Constant(s"$numericConstant", StrLit("nil"), StrLit("NumericConstant")) // TODO: infer the type
        Seq((x => result, goals.tail)) // no need to evaluate; useful only for cases where direct arithmetic is requested, e.g., +(2, 3)

      case Structure(StrLit("_unify"), leftTerm +: rightTerm +: Nil, _, _, _) => mostGenericUnifier(leftTerm, rightTerm) map { subst => 
        val newGoals = goals.tail.map(subst)
        Seq((subst, newGoals))
      } getOrElse(Nil)

      case Structure(StrLit("_is"), xterms, _, _, _) => xterms match {
	case Variable(leftVar, _, _, _) +: rightTerm +: Nil => rightTerm match {
	  case x if isGrounded(x) => rightTerm match {
	    case Structure(_, _, _, _, _) =>
	      val rightTermValue = recurse(Seq(rightTerm))
              val result = rightTermValue.map(subst => Structure(StrLit("_unify"), Variable(leftVar) +: subst(rightTerm) +: Nil)).head // should have only one value
	      branch(result +: goals.tail, depth)
	    case _ => // a constant
	      val result = Structure(StrLit("_unify"), Variable(leftVar) +: rightTerm +: Nil)
	      branch(result +: goals.tail, depth)
          }
	  case _ => throw UnSafeException(s"RightTerm is not grounded: $xterms")
	}
	case other => throw UnSafeException(s"LeftTerm should be a variable: $other")
      }
      case Structure(StrLit("_interpolate"), Constant(body, attrName, tpe, _) +: Constant(termSeq, _, _, _) +: xterms, _, _, _) =>
        val res: String = Term.interpolate(body.name, termSeq.name, xterms)
        Seq((x=>Constant(StrLit(res), attrName, tpe, Encoding.AttrLiteral), goals.tail))
      case Structure(StrLit("rootId"), speaker +: xterms, attrName, tpe, _)  =>      // TODO: should rootId have speaker?
        if(xterms == Nil) throw UnSafeException(s"speaker undefined for the term: $speaker")
        val arg = recurse(xterms).map {
          case subst  => subst(xterms.head)
        }
        val xarg = if(arg.isEmpty) xterms.head.id.name else arg.head.id.name
        val argArray = xarg.split("\\:")
        val result = Constant(StrLit(s"${argArray(0)}"), attrName, tpe, Encoding.AttrLiteral)
	Seq(((x: Term) => result, goals.tail))

      case Structure(StrLit("rootPrincipal"), speaker +: xterms, attrName, tpe, _)  =>      // TODO: should rootId have speaker?
        if(xterms == Nil) throw UnSafeException(s"speaker undefined for the term: $speaker")
        val arg = recurse(xterms).map {
          case subst  => subst(xterms.head)
        }
        val xarg = if(arg.isEmpty) xterms.head.id.name else arg.head.id.name
        val argArray = xarg.split("\\:")
        val result = Constant(StrLit(s"${argArray(0)}"), attrName, tpe, Encoding.AttrLiteral)
	Seq(((x: Term) => result, goals.tail))

/** 
 * unused code
      case Structure(StrLit("guid"), speaker +: xterms, attrName, tpe, _)  =>      // TODO: should rootId have speaker?
        if(xterms == Nil) throw UnSafeException(s"An argument is needed")
        val arg = recurse(xterms).map {
          case subst  => subst(xterms.head)
        }
        val xarg = if(arg.isEmpty) xterms.head.id.name else arg.head.id.name
        val argArray = xarg.split("\\:")
        val result = Constant(StrLit(s"${argArray(1)}"), attrName, tpe, Encoding.AttrLiteral)
	Seq(((x: Term) => result, goals.tail))
*/

      /** 
       * Add built-ins in slog. Ideally, this could be co-designed 
       * with slang built-ins. Of course, a slang stmt doesn't have
       * a speaker.
       */
      case s @ Structure(StrLit("singleComponent"), speaker +: xterms, attrName, tpe, _) => 
        if(xterms == Nil) throw UnSafeException(s"singleComponent needs an argument: ${s}")
        xterms.head match {
          case name @ Constant(_,_,_,_) => 
            singleComponentName(name) match {
              case Constant(StrLit("_false"), _, _, _) => Iterable.empty  // Didn't get meaningful result (not a single component); terminate the inference on this branch
              case result =>
                // A structure and a constant cannot be unified
                //val binding = mostGenericUnifier(s, result)
                //println(s"[slogInference branch] singleComponent binding: ${s} => ${result}")
                //binding.map(subst => (subst, goals.tail.map(subst)))
                
                //val binding = ((x: Term) => if(x == s) result else x)
                //Seq((binding, goals.tail.map(binding)))
                Seq((x=>x, goals.tail))
            }
          case _ => Iterable.empty // not implemented yet
        }
   
       case s @ Structure(StrLit("splitHead"), speaker +: xterms, attrName, tpe, _) => 
        if(xterms.length < 3) throw UnSafeException(s"splitHead takes 3 arguments; xterms.length=${xterms.length}")
        xterms.head match {
          case name @ Constant(_,_,_,_) => 
            val pieces = splitHead(name)
            pieces.head match {
              case Constant(StrLit("_false"), _, _, _) => Iterable.empty  // Didn't get a meaningful result (not a single component); terminate the inference on this branch
              case result =>
                val binding = mguSeq(xterms.slice(1, 3), pieces, x=>x)
                //val binding = ((x: Term) => if(x == xterms(1)) pieces(0)  else if(x == xterms(2)) pieces(1)  else x)
                //println(s"[slog splitHead] binding: ${xterms(1)} => ${pieces(0)};    ${xterms(2)} => ${pieces(1)}")
                binding.map(subst => (subst, goals.tail.map(subst)))
            }
          case _ => Iterable.empty // not implemented yet
        }
  
      case s @ Structure(StrLit("splitLast"), speaker +: xterms, attrName, tpe, _) => 
        if(xterms.length < 3) throw UnSafeException(s"splitLast takes 3 arguments; xterms.length=${xterms.length}")
        xterms.head match { 
          case name @ Constant(_,_,_,_) => 
            val pieces = splitLast(name)
            pieces.head match {
              case Constant(StrLit("_false"), _, _, _) => Iterable.empty  
              case result =>
                val binding = mguSeq(xterms.slice(1, 3), pieces, x=>x)
                binding.map(subst => (subst, goals.tail.map(subst)))
                //val binding = ((x: Term) => if(x == xterms(1)) pieces(0) else if(x == xterms(2)) pieces(1) else x)
                //Seq((binding, goals.tail))
            }
          case _ => Iterable.empty // not implemented yet
        } 


        
/* unused code
      case s @ Structure(StrLit("splittable"), speaker +: xterms, attrName, tpe, _) => 
        if(xterms == Nil) throw UnSafeException(s"splittable needs an argument")
        xterms.head match { 
          case name @ Constant(_,_,_,_) => 
            splittableName(name) match { 
              case Constant(StrLit("_false"), _, _, _) => Iterable.empty  
              case result =>
                val binding = ((x: Term) => if(x == s) result else x)
                Seq((binding, goals.tail))
            }
          case _ => Iterable.empty // not implemented yet
        } 
  
      case s @ Structure(StrLit("splitLast"), speaker +: xterms, attrName, tpe, _) => 
        if(xterms == Nil) throw UnSafeException(s"splitLast needs an argument")
        xterms.head match { 
          case name @ Constant(_,_,_,_) => 
            splitLast(name) match {
              case Constant(StrLit("_false"), _, _, _) => Iterable.empty  
              case result =>
                val binding = ((x: Term) => if(x == s) result else x)
                Seq((binding, goals.tail))
            }
          case _ => Iterable.empty // not implemented yet
        } 

      case s @ Structure(StrLit("splitTop"), speaker +: xterms, attrName, tpe, _) => 
        if(xterms == Nil) throw UnSafeException(s"splitTop needs an argument")
        xterms.head match {
          case name @ Constant(_,_,_,_) => 
            splitTop(name) match { 
              case Constant(StrLit("_false"), _, _, _) => Iterable.empty  
              case result =>
                val binding = ((x: Term) => if(x == s) result else x)
                Seq((binding, goals.tail))
            }
          case _ => Iterable.empty // not implemented yet 
        }

      case s @ Structure(StrLit("dropDirPrefix"), speaker +: xterms, attrName, tpe, _) => 
        if(xterms == Nil || xterms.length == 1) throw UnSafeException(s"dropDirPrefix needs two arguments")
        (xterms(0), xterms(1)) match {
          case (name: Constant, dir: Constant) => 
            dropDirPrefix(name, dir) match { 
              case Constant(StrLit("_false"), _, _, _) => Iterable.empty  
              case result =>
                val binding = ((x: Term) => if(x == s) result else x)
                Seq((binding, goals.tail))
            }
          case _ => Iterable.empty // not implemented yet 
        }
*/

      case Structure(StrLit("speaksFor"), subjectSpeaker +: issuer +: subject +: other, _, _, _) if (
        (subjectSpeaker == issuer) && (issuer == subject)
      ) => Seq((x=>x, goals.tail))
      case Structure(StrLit("speaksForOn"), subjectSpeaker +: issuer +: subject +: other, _, _, _) if(
        (subjectSpeaker == issuer) && (issuer == subject)
      ) => Seq((x=>x, goals.tail))
      case Structure(printPred, xterms, _, _, _) if(
           (printPred == StrLit("print")) 
         | (printPred == StrLit("println"))
       ) => xterms.headOption match {

        case Some(c: Constant) =>
          if(printPred == StrLit("println")) println(c) else print(c)
          Seq((x=>x, goals.tail))
        case _                 =>
          val res = recurse(xterms).map(subst => subst(xterms.head))
          if(printPred == StrLit("println")) println(res.mkString(", ")) else print(res.mkString(", "))
          Seq((x=>x, goals.tail))
      }
      case Structure(StrLit("_compare"), xterms, _, _, _) => xterms match {
        case Constant(leftTerm, _, _, _) +: Constant(rightTerm, _, _, _) +: Nil => 
          if(leftTerm == rightTerm) Seq((x=>x, goals.tail)) else Nil
	case leftTerm +: rightTerm +: Nil =>
	  val leftResult = Structure(StrLit("_is"), Variable("%Left") +: leftTerm +: Nil)
	  val rightResult = Structure(StrLit("_is"), Variable("%Right") +: rightTerm +: Nil)
	  val result = Structure(StrLit("_compare"), Variable("%Left") +: Variable("%Right") +: Nil)
	  branch(leftResult +: rightResult +: result +: goals.tail, depth)
	case _ => throw UnSafeException(s"error in compare")
      }
      // case NegatedTerm(name, xterm) => branch(contextIds, Seq(xterm), depth) 
      // Note: using solveAQuery() here instead of branch() to keep the @tailrec satisfied for branch()
      // TODO: May overflow stack if number of negated terms are more.
      case NegatedTerm(name, xterm, _, _, _) => solveAQuery(Query(Seq(xterm)), false)(envContext, subcontexts) match {
	case Nil => Seq((x=>x, goals.tail))
	case _   => Nil
      }
      case other => solveGoalTerm(other, goals, depth)
    }

    def evalSeq(terms: Seq[Term]): Seq[Term] = {
      val evaledTerms = terms match {
	case Structure(StrLit("_seq"), subTerms, _, _, _) +: rest => subTerms match {
	  case Structure(StrLit("_seq"), moreSubTerms, _, _, _) +: tail => 
             Structure(StrLit("_seq"), 
               substTerm(subTerms.head, recurse(Seq(subTerms.head)).toIndexedSeq) ++: evalSeq(subTerms.tail) 
             ) +: evalSeq(rest)
	  case _ => Structure(StrLit("_seq"), evalSeq(subTerms)) +: evalSeq(rest)
	}
	case Structure(StrLit("[]"), _, _, _, _) +: rest => Structure(StrLit("[]"), Nil) +: evalSeq(rest)
	case Structure(_, _, _, _, _) +: rest => substTerm(terms.head, recurse(Seq(terms.head)).toIndexedSeq) ++: evalSeq(rest)
	case Constant(_, _, _, _) +: rest => Seq(terms.head) ++: evalSeq(rest)
	case x +: rest => substTerm(x, recurse(Seq(x)).toIndexedSeq) ++: evalSeq(rest) // FunTerm or SetTerm
	case Nil => Nil
      }
      evaledTerms
    }

    def substTerm(term: Term, substFns: Seq[Term => Term]): Seq[Term] = {
      substFns.map(subst => subst(term))
    }

    import scala.util.control.ControlThrowable
    //case class Returned[A](value: A) extends ControlThrowable {}
    case class Returned(value: Iterable[Term => Term]) extends ControlThrowable {}
    //def shortcut[A](a: => A) = try { a } catch { case Returned(v) => v }

    def incTotalEdges(): Unit = {
      //println(s"[recurse] totalEdges = ${totalEdges}; after inc,  totalEdges = ${totalEdges + 1}") 
      totalEdges = totalEdges + 1
    }

    //@annotation.tailrec
    def recurse(goals: Seq[Term], depth: Int = 1): Iterable[Term => Term] = {
      if(goals == Nil) {
       /*
        val solutionsRaw = for {
	  subst <- result
        } yield query.terms.map(subst)
        val solutions = solutionsRaw.map(x => Assertion(x))

        if(query.terms.length > 1) success(Assertion(Constant("query(..)") +: query.terms), isInteractive)
	else success(Assertion(query.terms), isInteractive)
	*/
	//Seq(x => x)
        //val f: Seq[Term => Term] = Seq(x => x)
	//throw Returned(f)
        Seq(x => x)
      }
      else if(depth > Config.config.maxDepth) {
	failure(query, "ran out of maximum depth ${depth}", isInteractive)
	//System.exit(1) // just quit
	Seq(x => x) // probably going to infinite recurse //TODO: fix this
      } else {
	  val endTime1 = System.nanoTime()
	  for {
	    //(solutionMayBe, freshGoalsMayBe) <- branch(contextIds, goals, depth) // branch for new goals
	    //_ = memoGoalTerm(goals.head, true)                                   // register the goal as visited
	    (solution, freshGoals) <- branch(goals, depth) // branch for new goals
            // this doesn't work
            //totalBranches = totalBranches + 1
           
            // comment to not interfere with findOne
            // That means we don't count the branch incurred by recursing the right hand side term of an _is 
            _ = incTotalEdges()             

	    //(solution, freshGoalsMayBe) <- branch(contextIds, goals, depth) // branch for new goals
	    //(solution, freshGoals) = findFreshGoals(solutionMayBe, freshGoalsMayBe)
	    //freshGoals = admissibilityTest(goals.head, freshGoalsMayBe)
	    endTime2 = System.nanoTime()
	    //_ = logger.info(s"Time for branch at depth $depth is: ${(endTime2 - endTime1)/Math.pow(10, 6)} ms")
	    //_ = println(s"Time for branch at depth $depth is: ${(endTime2 - endTime1)/Math.pow(10, 6)} ms")
	    //_ = println(s"[slog recurse] solution: ${solution};  goals.head: ${goals.head}; solution(goals.head): ${solution(goals.head)}; FreshGoals: $freshGoals ")
	    remainingSolutions <- recurse(freshGoals, depth + 1)
	    //_ = println(solution)
	    //_ = println(remainingSolutions)
	    //_ = println(s"$depth")
	    //_ = println(s"goal: $freshGoals")
	  } yield (solution andThen remainingSolutions)

          /*
	  val branchResult = branch(contextIds, goals, depth).iterator

          @annotation.tailrec
          @inline
	  def branchLoop(preGoals: Seq[Term], acc: Iterable[Term => Term]): Iterable[Term => Term] = { 
            if(branchResult.hasNext) {
	      val (solution, freshGoals) = branchResult.next
	      //val isMatch = admissibilityTest(goals.head, freshGoals)
	      //val isMatch = Term.subsumes(freshGoals.head, goals.head)
	      //if(isMatch == true) {
	      if(false) {
		//println(s"isMatch: $isMatch, ${goals.head}, ${freshGoals.head}")
		val newGoals = freshGoals.tail ++ Seq(freshGoals.head) ++ preGoals // reorder
		branchLoop(newGoals, acc)
	      } else {
		val newGoals = freshGoals ++ preGoals
		//println(s"newGoals: $newGoals")
		//scala.io.StdIn.readLine()
		val out = for {
		  remainingSolutions <- recurse(newGoals, depth + 1)
		} yield (solution andThen remainingSolutions)
		//out ++ branchLoop(Nil)
		branchLoop(Nil, out ++ acc)
	      }
	    } else {
	      acc
	    }
         }
         branchLoop(Nil, Nil)
         */
      }
    }

    /*
     * Check if the query can be resolved; 
     * find at most one solution for the query 
     */
 
    //@annotation.tailrec
    def findOne(goals: Seq[Term], depth: Int = 1): Tuple2[Term => Term, Boolean] = {
      if(goals == Nil) { // Solved
        //println("[slogInference] findOne   no more goals")
        proofPathDepth = depth
        (x => x, true)
      }
      else if(depth > Config.config.maxDepth) {
	failure(query, s"ran out of maximum depth ${depth}", isInteractive)
	(x => x, false)
      } 
      else {
        var res: Tuple2[Term => Term, Boolean] = (x => x, false)
        val endTime1 = System.nanoTime()
        val inferredAlternatives = branch(goals, depth)
        
        if(inferredAlternatives.size > 0) { // increase the total #branches we've explored out
          totalEdges = totalEdges + inferredAlternatives.size 
          //println(s"new edges: ${inferredAlternatives.size}; totalEdges: ${totalEdges}; totalMgus: ${totalMgus}")

          totalBranches = totalBranches + inferredAlternatives.size - 1
          //println(s"new branches: ${inferredAlternatives.size - 1}; totalBranches: ${totalBranches}; totalMgus: ${totalMgus}")
        } else { // a terminated branche; a terminated branch means a branch yields no sub-branches
          //println(s"failedBranches = ${failedBranches};  after inc, failedBranches = ${failedBranches + 1}")
          failedBranches = failedBranches + 1
        } 
       
        breakable {
          for( (partialSolution, freshGoals) <- inferredAlternatives ) {
            val endTime2 = System.nanoTime()
            //logger.info(s"Time for branch at depth $depth is: ${(endTime2 - endTime1)/Math.pow(10, 6)} ms")
            //println(s"[slog findOne] Time for branch at depth $depth is: ${(endTime2 - endTime1)/Math.pow(10, 6)} ms") //; goals.head: ${goals.head}")
            //println(s"[slog findOne] partialSolution: ${partialSolution};  goals.head: ${goals.head}; partialSolution(goals.head): ${partialSolution(goals.head)}; FreshGoals: $freshGoals ")
            logger.info(s"[slog findOne] partialSolution: ${partialSolution};  goals.head: ${goals.head}; partialSolution(goals.head): ${partialSolution(goals.head)}; FreshGoals: $freshGoals ")

            val (restSolution, solved) = findOne(freshGoals, depth+1)
            if(solved) { // Find a solution
              val solution = partialSolution andThen restSolution
              res = (solution, true)
              break 
            }
          }
        }
        res
      }
    }

    logger.info("\n[slogInference SolveAQuery] " + query)
    if(findAllSolutions) { // Find all 
      val result = try {recurse(query.terms)} catch { case Returned(v) => v }
      logger.info(s"[slogInference solveAQuery] totalBranches=${totalBranches}")
      logger.info(s"[slogInference solveAQuery] totalMgus=${totalMgus}")
      result.toSeq
    } else { // Find just one
      val (solution, solved) = findOne(query.terms) 
      logger.info(s"[slogInference solveAQuery] totalEdges=${totalEdges}")
      logger.info(s"[slogInference solveAQuery] totalMgus=${totalMgus}")
      logger.info(s"[slogInference solveAQuery] totalBranches=${totalBranches}")
      logger.info(s"[slogInference solveAQuery] failedBranches=${failedBranches}")
      logger.info(s"[slogInference solveAQuery] failedMgus=${failedMgus}")
      logger.info(s"[slogInference solveAQuery] proofPathDepth=${proofPathDepth}") 
      if(solved) Seq(solution)
      else Seq[Term => Term]()
    } 
  }

  def admissibilityTest(currentGoal: Term, freshGoals: Seq[Term]): Boolean = freshGoals match {
    case Nil => true
    case goal :: tail =>
      mostGenericUnifier(currentGoal, goal) match {
        case None => //println(s"no match: currentGoal: $currentGoal; freshGoal: $goal")
          //scala.io.StdIn.readLine()
          //freshGoals
          true
        case _    => //println(s"match: currentGoal: $currentGoal; freshGoal: $goal")
          //scala.io.StdIn.readLine()
          //admissibilityTest(currentGoal, tail) :+ goal
          false
      }
  }

  /*
  // TODO: this seems very expensive; analyze
  def findFreshGoals(solution: Term => Term, goals: Seq[Term]): (Term => Term, Seq[Term]) = goals match {
    case Nil => (solution, Nil)
    case goal :: tail =>
      //println(s"Goals: $goals; visitedGoals: ${__memoGoalTerm}")
      __memoGoalTerm.map {
	case (term: Term, visited: Boolean) => mostGenericUnifier(term, goal) collect {
	  case x => //println(s"Goal found: $goal"); return (x, tail)
	}
      }
      //println(s"goal not found: $goal")
      (solution, goals)
  }
  */

  /*
   * Seminaive Algorithm
   * Input: program, query
   * 
   *   p(i+1) :- p1(i), ..., pn(i), q1, ..., qm.
   * 
   *   foreach idb predicate p {
   *     p(0)       := Nil
   *     delta_p(0) := tuples produced by rules using only edb's
   *   
   *     i := 1
   *   
   *	 do {
   *	   p(i) := p(i - 1) union delta_p(i - 1)
   *	   compute bigDelta_p(i)
   *	   delta_p(i) := bigDelta_p(i) - p(i)
   *	   i := i + 1
   *	 } while delta_p(i) == Nil
   *   }
   * 
   *  
   *   evaluate(bigDelta_p(i)) {
   * 
   *     bigDelta_p(i) :- delta_p1(i-1), p2(i-1), ..., pn(i-1), q1, ..., qm.
   *     bigDelta_p(i) :- p1(i-1), delta_p2(i-1), ..., pn(i-1), q1, ..., qm.
   *     ...
   *     bigDelta_p(i) :- p1(i-1), p2(i-1), ..., delta_pn(i-1), q1, ..., qm.
   *
   *     p(i+1)        := p(i) union bigDelta_p(i)
   *   }
   */

  /*
  def solveWithForwardChaining(program: Seq[Statement], query: Statement, isInteractive: Boolean = false): Seq[Term] = {
    def findGroundRules(idb: Set[Statement], idbPredicates: Set[Term]): Set[Statement] = {
      import scala.collection.mutable.{Set => Set}
      var groundRules: Set[Statement] = Set.empty
      idb.foreach { idbRule =>
        val currentIdbRulePredicates: Set[Term] = idbRule.terms.toSet
        if(currentIdbRulePredicates.diff(idbPredicates) == Set.empty) groundRules += idbRule
      }
      groundRules.toSet
    }

    def partitionProgram(program: Set[Statement]): Tuple2[Set[Statement], Set[Statement]] = {
      import scala.collection.mutable.{Set => Set}
      var edb: Set[Statement] = Set.empty
      var idb: Set[Statement] = Set.empty
      program.foreach { statement =>
       if(statement.arity < 2) edb += statement else idb += statement
      }
      Tuple2(edb.toSet, idb.toSet)
    }

    def computeBigDelta(): Set[Term] = Set.empty // TODO

    val programSet: Set[Statement] = program.toSet
    val edb: Set[Statement] = programSet.filter(_.terms.length < 2) // all ground facts
    val idb: Set[Statement] = programSet diff edb
    val idbPredicates: Set[Term] = idb.map {statement => statement.terms.head}

    val groundRules: Set[Statement] = findGroundRules(idb, idbPredicates) // i.e., rules with no idb predicates in the body

    idbPredicates foreach { idbPredicate =>
      var pItr: Set[Term] = Set.empty
      var deltaItr: Set[Term] = {
        //val resultTerms: Iterable[Term => Term] = solveAQuery(groundRules.toSeq, Query(Seq(idbPredicate)), false)
        //resultTerms.map{subst => subst(idbPredicate)}.toSet
        Set.empty // TODO
      }
      var pNextItr: Set[Term] = Set.empty
      var deltaNextItr: Set[Term] = Set.empty
      var bigDeltaNextItr: Set[Term] = Set.empty
      while(!deltaItr.isEmpty) {
        pNextItr = pItr union deltaItr
        bigDeltaNextItr = computeBigDelta()
        deltaNextItr = bigDeltaNextItr diff pNextItr
        pItr = pNextItr
        deltaItr = deltaNextItr
      }
    }

    Nil
   */

    /*
    def buildModel(): Seq[Term] = {
      var knownFacts: Seq[Term] = Nil
      var increment: Seq[Term] = program.filter(_.arity < 2) // all ground facts
      while(increment != Nil) {
	val insts = eval(knownFacts, increment)
	knownFacts = increment ++: knownFacts
	increment = insts.head
      }
      knownFacts
    }
    */
  //}

  /*
  def importIfPresent(program: Map[Index, Set[Statement]]): Map[Index, Set[Statement]] = {
    val importStatements = program.get('import1)
    if(importStatements == null) program
    else {
       importStatements foreach { stmt =>
         importProgram(stmt, program)
       }
    }
    program //TODO: yield and flatten?
  }

  def importProgram(stmt: Statement, program: ConcurrentHashMap[Index, Set[Statement]]): ConcurrentHashMap[Index, Set[Statement]] = stmt.terms.head match {
    case Structure('import, Seq(Constant(fileName))) => printLabel('info); println(s"importing program with name $fileName ...") 
      val file = fileName.name
      val fStream = new java.io.InputStreamReader(this.getClass().getClassLoader().getResourceAsStream(file))
      val importedProgram = parse(fStream)
      dropStatement(stmt, program)
      importIfPresent(importedProgram)
    case _ => program
  }
  */

  /*
  lazy val __memoGoalTerm = scala.collection.mutable.Map.empty[Term, Boolean]
  def memoGoalTerm(goal: Term, visited: Boolean): Boolean = synchronized {
    __memoGoalTerm.getOrElseUpdate(goal, visited)
  }
  */
}

class Inference() extends InferenceService with InferenceImpl

