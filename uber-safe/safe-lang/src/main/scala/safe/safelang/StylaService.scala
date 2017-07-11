package safe.safelang

import scala.collection.mutable.{Map => MutableMap, Queue}
import scala.collection.mutable.{LinkedHashSet => OrderedSet, ListBuffer}
import safe.safelog.{Statement, Subcontext, StrLit, EnvValue, Term}
import safe.safelang.StyStmtHelper._
import safe.safelog.AnnotationTags._

import prolog.fluents.{DataBase => StyDataBase}
import prolog.{LogicEngine => StyLogicEngine}
import prolog.terms.{Term => StyTerm, Const => StyConst, Fun => StyFun}
import scala.util.control.Breaks._
import com.typesafe.scalalogging.LazyLogging

object StylaService extends LazyLogging{

  val styEngine: StyLogicEngine = new StyLogicEngine()
  private val stylaQ: Queue[StyLogicEngine] = Queue.empty[StyLogicEngine]
  var numStyla = 0
  
  def getStylaEngine(): StyLogicEngine = {
    if(stylaQ.size > 0)  stylaQ.dequeue  else null
  }

  def solveWithContext(
      queries: Seq[Statement]
    , isInteractive: Boolean
    , findAllSolutions: Boolean = true // by defaut to find all solutions; for legacy code. 
  )(  envContext: MutableMap[StrLit, EnvValue]
    , subcontexts: Seq[Subcontext]
  ): Seq[Seq[Statement]] = {

    var numsets = 0
    for(cnt <- subcontexts) {
      numsets += cnt.slogsetTokens.size
    }
    val timerStart = System.nanoTime()
    val stydb = buildStyDataBase(subcontexts)
    val timerEnd = System.nanoTime()
    slangPerfCollector.addDbBuiltTime((timerEnd-timerStart)/1000, s"${stydb.numstmts}  ${numsets}")
    //slangPerfCollector.addDbBuiltTime((timerEnd-timerStart)/1000, subcontexts(0).id.name)
    //logger.info(s"stydb.allByPrimary: ${stydb.allByPrimary}")
    //logger.info(s"stydb.factsBySecondary: ${stydb.factsBySecondary}")
    //logger.info(s"stydb.rulesByPrimary: ${stydb.rulesByPrimary}")
   
    val t0 = System.nanoTime()
    var styEngine: StyLogicEngine = null
    logger.info(s"numStyla=${numStyla}    stylaQ.size=${stylaQ.size}")
    this.synchronized {  // Get a styla engine
      styEngine = getStylaEngine() 
    }
    if(styEngine == null) {
      styEngine = new StyLogicEngine()
      numStyla += 1
      //if(numStyla % 100 == 0)  
      //  println(s"numStyla: ${numStyla}")
    }
    styEngine.setDataBase(stydb) // use the corresponding database
    val denyQueries = ListBuffer[StyQuery]()
    val requireQueries = ListBuffer[StyQuery]()
    val allowQueries = ListBuffer[StyQuery]()
    for(q <- queries) {
      assert(q.isInstanceOf[StyQuery], s"Query must be of type StyQuery: ${q}")
      val styq = q.asInstanceOf[StyQuery] 
      /**
       * Group queries into categories
       * By default queries without annotation belong to REQUIRE
       */
      if(styq.annotation == UNCLASSIFIED || styq.annotation == REQUIRE) requireQueries += styq
      else if(styq.annotation == ALLOW) allowQueries += styq
      else if(styq.annotation == DENY) denyQueries += styq
      else logger.error(s"Unrecognized query type: ${styq.annotation}   ${styq}") 
    }

    var denyRes = Seq[StyStmt]()
    var allowRes = Seq[StyStmt]()
    var requireRes = Seq[StyStmt]()

    var denySatisfied: Boolean = false
    var requireSatisfied: Boolean = false
    var allowSatisfied: Boolean = false

    breakable {
      for(query <- denyQueries) { // Process deny queries
        val t00 = System.nanoTime()
        val resultStmts = solveStyQuery(query, styEngine, findAllSolutions)
        val t01 = System.nanoTime()
        if(!resultStmts.isEmpty) {
          denyRes = denyRes ++ resultStmts
          denySatisfied = true
          break
        }
      }
    }

    if(!denySatisfied) {
      breakable {
        for(query <- allowQueries) { // Process allow queries
          val t00 = System.nanoTime()
          val resultStmts = solveStyQuery(query, styEngine, findAllSolutions)
          val t01 = System.nanoTime()
          if(resultStmts.isEmpty == false) {
            allowRes = allowRes ++ resultStmts
            allowSatisfied = true
            break
          } else {
            allowSatisfied = false
          }
        }
      }

      if(!allowSatisfied) {
        breakable {
          for(query <- requireQueries) { // Process require queries
            val t00 = System.nanoTime()
            val resultStmts = solveStyQuery(query, styEngine, findAllSolutions)
            val t01 = System.nanoTime()
            if(resultStmts.isEmpty == true) {
              requireSatisfied = false
              break
            } else {
              requireRes = requireRes ++ resultStmts
              requireSatisfied = true
            }
          }
        }
      }
    }

    // Release styla engine
    this.synchronized {
      stylaQ.enqueue(styEngine)
    }

    val res: Seq[Seq[StyStmt]] = Seq(denyRes.toSeq ++ allowRes.toSeq ++ requireRes.toSeq)
    val passed: Boolean = !( (denySatisfied == true) || 
                             (allowSatisfied == false && requireSatisfied == false) ) 


    val t = System.nanoTime()
    slangPerfCollector.addLogicInferTime((t-t0)/1000, queries.mkString("___"))

    // Return result
    if(!passed) { // don't pass
      Seq[Seq[StyStmt]]()
    } else {
      res
    }  
  }

  def solveStyQuery(q: StyQuery, styla: StyLogicEngine, 
                    findAllSolutions: Boolean): Seq[StyStmt] = { 
    val styq = StyLogicEngine.copyList(q.styterms)
    val solutions: List[StyTerm] = styla.solveQuery(styq, findAllSolutions)
    //solutions.toSeq.map(styterm => StyStmt(List(styterm), q.vmap))
    solutions.toSeq.map { styterm => StyStmt(List(styterm)) }
  }

  def buildStyDataBase(subcontexts: Seq[Subcontext]): StyDataBase = {
    val allstmts = mergeStmtsOfContexts(subcontexts) 
    //val stystmts: List[List[StyTerm]] = allstmts.map { stmt => 
    //    StyLogicEngine.copyList(stmt.asInstanceOf[StyStmt].styterms) }
    val stystmts: List[List[StyTerm]] = allstmts.map { 
        stmt => stmt.asInstanceOf[StyStmt].styterms }

    //val timerStart = System.nanoTime()
    //val stydb = new StyDataBase(stystmts)
    //val timerEnd = System.nanoTime()
    //slangPerfCollector.addDbBuiltTime((timerEnd-timerStart)/1000, subcontexts(0).id.name)
    //logger.info(s"stydb.allByPrimary: ${stydb.allByPrimary}")
    //logger.info(s"stydb.factsBySecondary: ${stydb.factsBySecondary}")
    //logger.info(s"stydb.rulesByPrimary: ${stydb.rulesByPrimary}")

    val stydb = new StyDataBase(stystmts)
    stydb
  }

  def mergeStmtsOfContexts(subcontexts: Seq[Subcontext]): List[Statement] = {
    val allstmts = ListBuffer[Statement]()
    for(subcnt <- subcontexts) {
      for( (idx, fs) <- subcnt.facts) {  
        //fs.foreach{ stmt => println(s"[StylaService mergeStmtsOfContexts facts] \ 
        //            hashCode=${stmt.hashCode}; subcnt.id=${subcnt.id}; idx=${idx}; stmt=${stmt}") }

        // retractions and links are needed for a post but not useful for inference
        // they're already filtered out when we build the subcontext
        //if(idx != StrLit("_retraction") && idx != StrLit("_link"))
          allstmts ++= fs
      }
      for( (idx, rs) <- subcnt.rules) {
        //rs.foreach{ stmt => println(s"[StylaService mergeStmtsOfContexts rules] \ 
        //            hashCode=${stmt.hashCode}; subcnt.id=${subcnt.id}; idx=${idx}; stmt=${stmt}") }
        //if(idx != StrLit("_retraction") && idx != StrLit("_link"))
          allstmts ++= rs
      }
    }
    allstmts.toList
  }

}
