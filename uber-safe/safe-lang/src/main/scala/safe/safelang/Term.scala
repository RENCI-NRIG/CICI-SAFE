package safe.safelang

import safe.runtime.JVMInterpreter
import safe.safelog._
import safe.cache.SafeTable
import scala.concurrent.ExecutionContext.Implicits.global
import prolog.terms.{Fun => StyFun, Const => StyConstant}

import model._
import setcache.SetCache

import akka.actor.ActorRef
import akka.util.Timeout
import akka.pattern.ask
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import scala.collection.mutable.{ListBuffer, LinkedHashSet => OrderedSet, Map => MutableMap}
import com.typesafe.scalalogging.LazyLogging

trait FunctionLike {
  /** evalFunction() executes arbitrary code for jvm-based languages */
  def evalFunction(func: StrLit)(envContext: MutableMap[StrLit, EnvValue]): Term = Constant(StrLit("nil"))
  def evalSet(func: StrLit)(
      envContext: MutableMap[StrLit, EnvValue]
    , slangCallClient: SlangRemoteCallClient
    , setCache: SetCache
    , contextCache: ContextCache
  ): Term = Constant(StrLit("nil"))
}

case class SlogResult(
    statements: Seq[Set[Statement]]
  , id: StrLit = StrLit("_")
  , attrName: StrLit = StrLit("nil")
  , tpe: StrLit = termType
  , indexAndEncode: Encoding = Encoding.Attr
) extends Term {
  override def toString(): String = {
    //println(s"[SlogResult] toString")
    "{ " + statements.flatten.map(x => x.toStringWithSays()).mkString(", ") + " }"
  }
}

import scala.language.existentials // needed for existential type Class[_] due to a bug in Scala 
                                   // (Ref: https://issues.scala-lang.org/browse/SI-6541)
case class FunTerm(
    id: StrLit
  , code: String
  , args: Seq[Term]
  , compiledRef: Class[_]
  , attrName: StrLit = StrLit("nil")
  , tpe: StrLit = StrLit("scala")
  , indexAndEncode: Encoding = Encoding.Attr
)(implicit context: JVMInterpreter) extends Term with FunctionLike {

  override def primaryIndex(): StrLit = {
    val digest: Array[Byte] = model.Identity.hash(code.getBytes(StringEncoding), "SHA-1")
    // take the most significant 16 bits

    // BigInteger(int signum, byte[] magnitude)
    // Translates the sign-magnitude representation of a BigInteger into a
    // BigInteger. The sign is represented as an integer signum value: -1 for
    // negative, 0 for zero, or 1 for positive. The magnitude is a byte array
    // in big-endian byte-order: the most significant byte is in the zeroth
    // element. A zero-length magnitude array is permissible, and will result
    // in a BigInteger value of 0, whether signum is -1, 0 or 1. 
    StrLit(new java.math.BigInteger(1, digest).toString(16) + args.length)
  }
  override def bind(f: StrLit => Term): FunTerm = this.copy(args = args.map(_.bind(f)))
  override def toString(): String = "`main( args(" + args.mkString(", ") + ") ) {" + code + "}`"
  override def eval(): NumericConstant = {
    val boundedValues: Array[String] = args.map(arg => arg.id.name).toArray
    val result = context.eval(compiledRef, boundedValues).asInstanceOf[NumericConstant]
    result
  }
  override def evalFunction(func: StrLit = StrLit("_"))(envContext: MutableMap[StrLit, EnvValue]): Term = {
    val boundedValues: Array[String] = args.map {
      case arg: Constant => arg.id.name
      case arg: Variable => 
        envContext.get(arg.simpleName).getOrElse(
          throw UnSafeException(s"Unbound variable passed to a function: $arg")
        ).toString
    }.toArray
    val result = context.eval(compiledRef, boundedValues).toString()
    // if result starts with [ .. ], then treat it as a list
    SlangTerm.toSlangSeq(result)
  }
}

case class SetTerm(
    id: StrLit
  , argRefs: Seq[StrLit]
  , args: Seq[Term]
  , template: SlogSetTemplate
  , attrName: StrLit = StrLit("nil")
  , tpe: StrLit = StrLit("safe")
  , indexAndEncode: Encoding = Encoding.Attr
)(implicit slogContext: SafelogContext) extends Term with FunctionLike with LazyLogging {

  override def bind(f: StrLit => Term): SetTerm = this.copy(
    args = args.map {
      case c: Constant => c         // if already substituted locally, return
      case v @ _       => v.bind(f) 
    }
  )

  import scala.language.postfixOps

  override def evalSet(func: StrLit = StrLit("defguard"))(
      envContext: MutableMap[StrLit, EnvValue]
    , slangCallClient: SlangRemoteCallClient
    , setCache: SetCache
    , contextCache: ContextCache
  ): Term = {
    
    import SlangRemoteCallService._

    //println(s"[Safelang Term] argsRefs: $argRefs; args: $args")
    val bindings = argRefs.zip(args).toMap
    //println(s"[Safelang Term] evalSet bindings: ${bindings}")
    val slogset: SlogSet = template.instantiate(bindings, envContext) 
    //println(s"[Safelang Term] argsRefs: $argRefs; args: $args")
    //println(s"[Safelang Term] evalSet bindings: ${bindings}")
    //println(s"[Safelang Term] stmts: ${slogset.statements}")

    func match {
      case StrLit("defcall")   => 
        val stmts = slogset.statements.head._2 // TODO: process only the first stmt for now 
        //println(s"defcall statements: $stmts")
 
        var respMsg = ""
        for(callStmt <- stmts) { // for each call stmt, send a request 
          val s = System.nanoTime
          var entrypoint = ""
          val respFuture: Future[SlangCallResponse] = callStmt match { 
            case stystmt: StyStmt => stystmt.styterms match {
              case (f: StyFun) +: Nil =>
                entrypoint = s"${f.getArg(0).asInstanceOf[StyConstant].sym}_${f.sym}" 
                slangCall(f, slangCallClient)
              case _ => throw UnSafeException(s"Invalid defcall statement: ${callStmt}")
            }

            case stmt: Statement => callStmt.terms match {
              case (s @ Structure(_,_,_,_,_)) +: Nil => 
                entrypoint = s"${s.terms(1).id.name}_${s.id.name}"
                slangCall(s, slangCallClient)
              case _  => throw UnSafeException(s"Invalid defcall statement: ${callStmt}")
            }
            case _ => throw UnSafeException(s"statement expected, but something else found: ${callStmt}")
          }
          if(respFuture != null) {
            respFuture.onFailure {
              case t => logger.error(s"defcall ${callStmt} failed: ${t.getMessage}")
            }
            val response: SlangCallResponse = Await.result(respFuture, 500 seconds)
            respMsg = response.message
          }
          val latency = (System.nanoTime -s) / 1000
          slangPerfCollector.addLatency(latency, entrypoint)
        }
        //val trimmedResp = respMsg.replaceAll("""(?m)\s+$""", "")   // trim before extracting the token
        //val tokensOrQueryres = parseSlangCallResponse(trimmedResp)  
        logger.info(s"respMsg: $respMsg")
        val tokensOrQueryres = parseSlangCallResponse(respMsg)
        logger.info(s"tokensOrQueryres: ${tokensOrQueryres}")
        //println(s"tokensOrQueryres: ${tokensOrQueryres}")
        val defcallRes = if(tokensOrQueryres.isEmpty) Constant("false") 
                         else tokensOrQueryres.head   
                         // return the first token or the query results
        defcallRes
    
      case StrLit("defcon")   => 
        val self: Option[Principal] = envContext.get(StrLit("Selfie")).map{_.asInstanceOf[Principal]}
        val token: Token = Token(slogset.computeToken(self))
        var s: Option[SlogSet] = setCache.get(token)
        var tomerge: Boolean = true 
        if(!s.isDefined) { // Create a new slogset
          s = Some(slogset)
          val local: Option[SlogSet] = setCache.putLocalIfAbsent(token, s.get)
          if(!local.isDefined) { // done: set the entry in the local set table; no need to merge
            tomerge = false
          }
          s = setCache.get(token)
          // For debugging: in very rare cases, the entry in the local set table isn't immediately
          // available through cache loading. It's possibly because of the loading cache.
          while(!s.isDefined) {
            //val getLocal = setCache.getLocal(token)
            //val get = setCache.getWithError(token)              
            //println(s"[Slang Term] get from cache shouldn't fail (token: ${token.name})   getLocal=${getLocal}")
            //println(s"[Slang Term] 2nd_get=${get}")
            ////assert(false, s"Get from cache shouldn't fail (token: ${token.name})  getLocal=${getLocal}  get=${get}")
            s = setCache.get(token)
          }
        }

        // merge the set if needed
        if(tomerge) {
          val existingset: SlogSet = s.get
          //logger.info(s"[Slang Term evalSet defcon] slogset=${slogset}")
          //logger.info(s"[Slang Term evalSet defcon] existingset=${existingset}")
          existingset.mergeSlogset(slogset)
          // Invalidate entries of the containing contexts in cache
          for(t <- existingset.getContainingContexts) {
            contextCache.invalidate(Token(t))
          } 
        }
        Constant(StrLit(token.name), StrLit("nil"), StrLit("StrLit"), Encoding.AttrBase64) 
    
      case StrLit("defguard") =>
        val guardcontext: Subcontext = Subcontext("_guard", slogset.statements, slogset.queries)

        def executeDefguard(isRetry: Boolean = false): Seq[Seq[Statement]] = {

          val subcontexts: Seq[Subcontext] = guardcontext +: slogset.links.map{ case t: String => contextCache.getValidContext(Token(t)) }
                                             .collect{ case cnt: Option[Subcontext] if cnt.isDefined => cnt.get }
          /**
           * The following synthesizes the set of queries
           * Queries can be produced by builtins such as 
           * InferSet, stored in a local slogset. 
           */ 
          val queries = ListBuffer[Statement]()
          subcontexts.foreach( cnt => queries ++= cnt.queries ) 

          //println(s"[SetTerm defguard] queries: ${queries}")
          //if(queries.isEmpty) return Seq(Seq[Statement]())

          // Checking
          //println("==================================================")
	  //println("[slang Term] use slogContext to solve slog queries")
          //println(s"queries: ${queries}")
          //println(s"subcontexts: ${subcontexts}")
          logger.info(s"queries: ${queries}")
          logger.info(s"subcontexts: ${subcontexts}") 
          //println("==================================================")
          if(isRetry) { // logging
            println("[" + Console.RED + "Query retry" + Console.RESET + "]") 
            logger.info("[" + Console.RED + "Query retry" + Console.RESET + "]: " + queries + "   \ncontexts: " + subcontexts)
            val queryStr = queries.mkString("___") 
            for(cnt <- subcontexts.tail) {  // not include the _guard subcontext
              slangPerfCollector.addRetry(cnt.slogsetTokens.size, queryStr)
            }
          }
          //val timerStart = System.nanoTime()
          var res: Seq[Seq[Statement]] = if(Config.config.logicEngine == "styla") {
            StylaService.solveWithContext(
                queries = queries.toSeq
              , isInteractive = false
              , findAllSolutions = false 
            )(envContext, subcontexts)
          } else { 
            slogContext.solveWithContext(
                queries = queries.toSeq
              , isInteractive = false
              , findAllSolutions = false 
            )(envContext, subcontexts)
          }
          //val timerEnd = System.nanoTime()
          //slangPerfCollector.addLogicInferTime((timerEnd-timerStart)/1000, queries.mkString("___"))

          if(res.flatten.isEmpty) {
            if(!isRetry) {
              //println("[" + Console.RED + "Query failure" + Console.RESET + "]: " + queries + "   \ncontexts: " + subcontexts)
              logger.info("[" + Console.RED + "Query failure" + Console.RESET + "]: " + queries + "   \ncontexts: " + subcontexts)
            } else { 
              logger.error("[" + Console.RED + "Retry failure" + Console.RESET + "]: " + queries)
            }
          }
          //System.gc
          res
        }
 
        //for(i <- 0 to 1000000) {
        //  executeDefguard() 
        //}

        var res: Seq[Seq[Statement]] = executeDefguard()
        if(res.flatten.isEmpty) {  // Refresh subcontexts and retry on a query failure 
          slogset.links.foreach{ case t: String => 
            contextCache.refresh(Token(t))
          }
          res = executeDefguard(isRetry = true) // retry        
        }
        val slogResult = if(res.flatten.isEmpty) Constant("false") else SlogResult(res.map(x => x.toSet))
        //println("[slang Term] defguard evalSet slogResult: " + slogResult)
        //res = null
        slogResult
    
      case other     => throw UnSafeException(s"Undefined func, $other in evalSet")
    }
  }

  override def toString(): String = { 
    s"""SetTerm(id = $id; argRefs = ${argRefs.mkString(",")}; args = ${args.mkString(",")}; template = ${template.toString}"""
  }
}

object SetTerm {
  def apply(id: String, template: SlogSetTemplate)(implicit slogContext: SafelogContext) = 
    new SetTerm(StrLit(id), Nil, Nil, template)(slogContext)
  def apply(id: String, argRefs: Seq[StrLit], argTerms: Seq[Term], template: SlogSetTemplate)(implicit slogContext: SafelogContext) = 
    new SetTerm(StrLit(id), argRefs, argTerms, template)(slogContext)
}

object SlangTerm extends safe.safelog.TermLike {
  def toSlangSeq(str: String): Term = {
    val listExpr = """^\[(.*?)\]$""".r
    val listTerm = str match {
      case listExpr(elemStr) =>
        val elems = elemStr.split("""\s*,\s*""").toSeq.map(x => Constant(x))
        val test = elems.foldRight(Structure(StrLit("nil"), Nil)) { (x,y) => Structure(StrLit("_seq"), Seq(x) ++: Seq(y)) }
        normalizeTerms(Seq(test)).head // TODO: move this?
      case _ => Constant(str)
    }
   listTerm
  }

  // [a,b] to Seq(a, b)
  def unfoldSeq(term: Term): Seq[Term] = term match {
    case Structure(StrLit("nil"), Nil, _, _, _) =>  Nil
    case Structure(StrLit("_seq"), term +: more +: Nil, _, _, _) => term +: unfoldSeq(more)
    //case _ => sys.error(s"unexpected term: $term") // should never happen
    case _ => Seq(term)
  }

  /* listify: converts a canonical list to its standard form
   * Examples: .(a,.(b,[])) == [a,b]. 
   *           .(.(.(a,[]),[]),[]) == [[[a]]].
   *           .(.(a,.(b,[])),.(c,[])) == [[a,b],c].
   *             .(.(a,[]),.(.(b,.(c,[])),[])) == [[a],[b,c]].
   *           .(a,.(b,.(f(d,e),[]))) == [a,b,f(d,e)].
   */
  private def listify(terms: Seq[Term]): Seq[Term] = {
    //@annotation.tailrec
    def loop(terms: Seq[Term]): Seq[Term] = terms match {
      case Structure(StrLit("_seq"), subTerms, _, _, _) +: rest => subTerms match {
        case Structure(StrLit("_seq"), moreSubTerms, _, _, _) +: tail => listify(Seq(subTerms.head)) ++: loop(subTerms.tail) ++: loop(rest)
        case _ => loop(subTerms) ++: loop(rest)
      }
      case Structure(name, subTerms, _, _, _) +: rest => Structure(name, loop(subTerms)) +: loop(rest) // if name, apply the predicate
      case head +: rest => head +: loop(rest) // else append the head to the list and loop
      case Nil => Nil
    }
    Seq(Structure(StrLit("_seq"), loop(terms)))
  }

  /* 
   * normalizeTerms: build the term tokens from the given statement (Structure, Constant, List)
   */
  def normalizeTerms(stmt: Seq[Term]): Seq[Term] = stmt match {
    case term :: rest => term match {
      case Structure(StrLit("_seq"), _, _, _, _) => listify(Seq(term)) ++: normalizeTerms(rest) // is it a list?
      case Structure(name, terms, _, _, _) => Structure(name, normalizeTerms(terms)) +: normalizeTerms(rest) // is it a predicate name?
      case _ => Seq(term) ++: normalizeTerms(rest) // then simply a Constant
    }
    case Nil => Nil
  }

  override def isGrounded(term: Term): Boolean = term match {
    case c: Constant => true
    case v: Variable => false
    case Structure(id, terms, _, _, _) =>
      val resMap = terms.map{x => isGrounded(x)} // TODO: doing more computation than necessary
      if(resMap.contains(false)) false else true
    case f: FunTerm => true
    case s: SetTerm => true
    case _ => false
  }

  def getFromEnvContext(varTerm: Variable)(envContext: MutableMap[StrLit, EnvValue]): Term = {
    val term = envContext.get(varTerm.simpleName) match { 
      case None                 => throw UnSafeException(s"Env variable $varTerm not defined")
      case Some(c: Constant)    => Constant(StrLit(c.id.name), c.attrName, c.tpe, c.indexAndEncode)
      case Some(c)              => Constant(c.toString)
    }
    term
  }

  // find the most general unifier for two terms
  def mostGenericUnifier(lhs: Term, rhs: Term)(envContext: MutableMap[StrLit, EnvValue]): Option[Term => Term] = {
    @inline
    def mguEnvVar(varTerm: Variable, lhs: Seq[Term], rhs: Seq[Term], unifier: Term => Term): Option[Term => Term] = {
      val const: Term = getFromEnvContext(varTerm)(envContext)
      val r = subst(varTerm.id, const)_ compose unifier
      recurse(lhs.map(r), rhs.map(r), r)
    }
    @annotation.tailrec
    def recurse(lhs: Seq[Term], rhs: Seq[Term], unifier: Term => Term): Option[Term => Term] = (lhs, rhs) match {
      // empty lists? no more work
      case (Nil, Nil) => Some(unifier)

      // TODO: occurs check which is necessary to prevent unification of the
      // terms s(X) and X that can lead to infinite recursion

      // anything unifies with a variable
      case (term +: tail1, (v @ Variable(id, _, _, _)) +: tail2) => // this filters nothing: if(id.name != "Self") =>
	if(id.name.startsWith("^")) { // regex
	  mguRegex(id, term, tail1, tail2, unifier)
	} else if(id.name.startsWith("$")) { // envVar
	  mguEnvVar(v, lhs, rhs, unifier)
	} else {
	  val r =  subst(id, term)_ compose unifier // given two partial functions, f and g, compose will return g(f(x))
	  recurse(tail1.map(r), tail2.map(r), r)
	}
      case ((v @ Variable(id, _, _, _)) +: tail1, term +: tail2) => // this filters nothing: if(id.name != "Self")  =>
	if(id.name.startsWith("^")) { // regex
	  mguRegex(id, term, tail1, tail2, unifier)
	} else if(id.name.startsWith("$")) { // envVar;  Substitute global var
	  mguEnvVar(v, lhs, rhs, unifier)
	} else {
	  val r = subst(id, term)_ compose unifier // given two partial functions, f and g, compose will return g(f(x))
	  recurse(tail1.map(r), tail2.map(r), r)
	}
      // constants must match
      case (Constant(id1, _, _, _) +: tail1, Constant(id2, _, _, _) +: tail2) =>
	if (id1 == id2) recurse(tail1, tail2, unifier) else None

      // compounds must have matching atoms and matching arity
      // then arguments can be added to the list of values to check
      case (Structure(id1, term1, _, _, _) +: tail1, Structure(id2, term2, _, _, _) +: tail2) =>
        if (lhs.head.arity == rhs.head.arity && lhs.head.id == rhs.head.id) {
	  id1 match {
	    case StrLit("_is") | StrLit("_unify") =>
	      recurse(Seq(term1.head), term1.tail, unifier) // special case for is and =
	    case _ => recurse(term1 ++: tail1, term2 ++: tail2, unifier)
	  }
	}
	else None

      // anything else cannot be unified
      case (_, _) => None
    }
    recurse(Seq(lhs), Seq(rhs), x => x)
  }
}
