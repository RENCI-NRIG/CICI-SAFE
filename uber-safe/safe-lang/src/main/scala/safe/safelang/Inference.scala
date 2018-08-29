package safe.safelang

import scala.collection.{GenIterable, GenSeq, GenSet}
import scala.collection.parallel.immutable.ParSet
import scala.concurrent.ExecutionContext

import safe.safelog._

import safesets._
import model._

import util._

import SlangTerm._

import com.typesafe.config.ConfigFactory

import safe.cache.SafeTable
import setcache.SetCache

import akka.actor.{Actor, ActorRef, ActorLogging}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

import safe.safelog.AnnotationTags._
import scala.collection.mutable.{Set => MutableSet, Map => MutableMap, LinkedHashSet => OrderedSet}

import java.io.{File, PrintWriter}

import scala.concurrent.ExecutionContext.Implicits.global
import com.typesafe.scalalogging.LazyLogging

/* Crypto libs */
import java.security.spec._
import javax.crypto._
import javax.crypto.spec._


trait InferenceImpl extends safe.safelog.InferenceImpl with KeyPairManager with LazyLogging {
  this: InferenceService with SlangRemoteCallService with SafeSetsService =>

  /**
   * These are predicates that can be resolved to a term. 
   * Resolving the structure, including the predicate and
   * and the parameters, results in a binding between the
   * entire structure and that returned term. 
   *
   * The most common use case of such predicates is to put
   * each of them on the right hand side of an "_is" term
   * Examples:
   *   1) ?Token := computeIdFromName(?P, ?Guid),
   *   2) ?slogResult := inferSet(?RulesRef, ?FactsRef), 
   * 
   * An extension made to the handler of these predicates
   * is that it can return an empty iterator of binding 
   * and new goals, if resolving the predicate results in 
   * nothing meaningful. Together with how we handle an _is 
   * term, this can effectively terminate an inference
   * branch in face of execeptions.  
   *
   * Note that we're not able to generate bindings between 
   * the parameters and some values through this resolving.
   * We need a holistic desgin in order to achieve that. 
   * The temporary solution is to put those builtin predicates
   * in to a different category (specialPred) and handle 
   * them differently, so that we can get the binding of the 
   * parameters of a predicate.    
   */

  val predef: Set[StrLit] = Set(
      StrLit("getId2"), StrLit("getId3"), StrLit("getId1")
    , StrLit("rootId1")
    , StrLit("rootPrincipal1")
    , StrLit("computeId2")
    , StrLit("computeIdFromName2")
    , StrLit("label1")               // we'll use label, instead of computeId
    , StrLit("label2")
//    , StrLit("principal0"), StrLit("principal1"), StrLit("principal2")
    , StrLit("signingKey1"), StrLit("key1"), StrLit("id1")
    , StrLit("getSubject1")
    , StrLit("getEncoding1")
    , StrLit("getVersion1")
    , StrLit("getSignedData1")
    , StrLit("getSpeaker1")
    , StrLit("getPrincipal1")
    , StrLit("getSpeakerKey1")
    , StrLit("getSubjectKey1")
    , StrLit("getSpeakerRef1")
    , StrLit("getValidity1")
    , StrLit("getValidityFrom1")
    , StrLit("getValidityUntil1")
    , StrLit("getSignature1")
    , StrLit("getSignatureAlgorithm1")
    , StrLit("getStatus1")
    , StrLit("getSlotSet1")
    , StrLit("getName1")
    , StrLit("verifySignature2")
    , StrLit("parseSet1")
    , StrLit("concat2")
    , StrLit("scid0")
    //, StrLit("print1"), StrLit("println1")
    , StrLit("simplePost2")
    , StrLit("simpleGet1")
    , StrLit("simpleDelete1")
    , StrLit("inferSet2")
    , StrLit("inferQuerySet2")
    //, StrLit("singleComponent1")
    //, StrLit("splittable1")
    //, StrLit("splitHead1")
    //, StrLit("splitTail1")
    , StrLit("reapId1")
    , StrLit("getNthArgFromSlogset2")
    , StrLit("parseAndGetNthArg2")
    , StrLit("switchSelfTo1")
    //, StrLit("applyInferenceBinding2")
    //, StrLit("nonEmpty1")
    //, StrLit("appendName2")
    //, StrLit("guid1")
    , StrLit("objectFromScid1")
    , StrLit("ipFromNetworkID1")
    , StrLit("portFromNetworkID1")
    //, StrLit("querySet2")
    , StrLit("encryptBearerRef2") 
    , StrLit("decrypt2")
    , StrLit("createAESKey1")
  )
 
  /**
   * These predicates returns bindings between parameters and 
   * some values, which influence the rest of the inference 
   * on the same branch.
   */ 
 
  val specialPred: Set[StrLit] = Set(
      StrLit("singleComponent1")
    , StrLit("splitHead3")
  )


  // =============== Solver ==================== //
  //@annotation.tailrec // TODO: make this tailrec
  override def solveAQuery(
     query: Statement
   , isInteractive: Boolean = false
   , findAllSolutions: Boolean = true  // Because slang Inference extends slog Inference, we need the "findAllSolutions"
  )( envContext: MutableMap[StrLit, EnvValue]
   , subcontexts: Seq[Subcontext]
  ): Iterable[Term => Term] = {

    def solveSetTerm(
        setType: StrLit
      , setTerm: SetTerm
      , goals: Seq[Term]
      , defhead: Term
    ): Iterable[Tuple2[Term => Term, Seq[Term]]] = {
      
      //println(s"[slangInference] solveSetTerm     setType: ${setType}  setTerm: ${setTerm}   goals: ${goals}   defhead:${defhead}")

      val constantTerms: Seq[Term] = bindEnvVar(setTerm.args)(envContext)
      //println("[slangInference solveSetTerm] passed bindEvnVar")
      // check for empty arguments, i.e., check if the seq is empty
      val constantTermsMayBe: Option[Seq[Term]] = if(constantTerms == Nil) None else Some(constantTerms)
      //println("[slangInference solveSetTerm] constantTermsMayBe: " + constantTermsMayBe)
      val res = for {
	constantArgs  <- constantTermsMayBe
	sTerm  = setTerm.copy(args = constantArgs)(safelogContext)
        //_ = println("[slangInference solveSetTerm] about to eval set: " + sTerm)

	result = sTerm.evalSet(setType)(envContext, slangCallClient, setCache, contextCache)
	if(result.toString != "false")
      } yield((x: Term) => if(x == defhead) result else x, goals.tail)
      res
    }

    def solveFunTerm(funTerm: FunTerm, goals: Seq[Term], defhead: Term): Iterable[Tuple2[Term => Term, Seq[Term]]] = {
      /* This is complicated by two things:
       * -- Each argument to a defun may in turn be another defun
       * -- There can be multiple definitions for each defun signature since defun is a rule rather than a function
       */
      /* First evaluate all arguments so that they all are constants */
      val constantTerms: Seq[Term] = bindEnvVar(funTerm.args)(envContext)

      // check for empty arguments, i.e., check if the seq is empty
      val constantTermsMayBe: Option[Seq[Term]] = if(constantTerms == Nil) None else Some(constantTerms)
      for {
	constantArgs <- constantTermsMayBe
	fTerm = funTerm.copy(args = constantArgs)(jvmContext(funTerm.tpe.name))
	result  = fTerm.evalFunction()(envContext)
	if(result.toString != "false")
      } yield((x: Term) => if(x == defhead) result else x, goals.tail)
    }

    def solveEnvTerm(envVar: Variable, envTerm: Term, goals: Seq[Term]): Iterable[Tuple2[Term => Term, Seq[Term]]] = envTerm match {
      case s @ Structure(StrLit("principal"), args, _, _, _) if Set(0, 1, 2).contains(s.arity) => 
	val principal = s.arity match {
	  case 0 => Principal()
	  case 1 => Principal(pemFile = args(0).id.name)
	  case 2 => Principal(algorithm = args(0).id.name, keyLength = args(1).id.name.toInt)
	}
	envContext.put(envVar.simpleName, principal)
        
	if(envVar.id == StrLit("Selfie")) {
	  envContext.put(StrLit("Self"), Constant(StrLit(s"${principal.pid}"), StrLit("nil"), StrLit("StrLit"), Encoding.AttrBase64))
	  envContext.put(StrLit("SelfKey"), Constant(StrLit(s"${principal.fullPublicKey}"), StrLit("nil"), StrLit("StrLit"), Encoding.AttrBase64))
	}
	val result = Constant(StrLit(principal.pid), StrLit("nil"), StrLit("StrLit"), Encoding.AttrBase64)
        //println(s"envVar: ${envVar.simpleName}, value: $result")
	Seq(((x: Term) => x match {
          case v: Variable if v.simpleName == envVar.simpleName => result
          case _ => x
        }, goals.tail))
	//Seq(((x: Term) => result, goals.tail))
      case s @ Structure(stdlibdef, args, _, _, _) if predef.contains(s.primaryIndex) => 
	val result = handleDefTerm(stdlibdef, args, s.arity)
	envContext.put(envVar.simpleName, result)
        //println(s"envVar: ${envVar.simpleName}, value: $result")
	//Seq(((x: Term) => if(x.id == envVar.id) result else x, goals.tail))
	//Seq(((x: Term) => result, goals.tail))
	Seq(((x: Term) => x match {
          case v: Variable if v.simpleName == envVar.simpleName => result
          case _ => x
        }, goals.tail))
      case _ =>
        //println(s"[slang Inference solveEnvTerm] resolve  envTerm=${envTerm}")
        //A constant envTerm is resolved to nothing; and the result is envTerm itself
	val envValue = recurse(Seq(envTerm))
	val result = {
           val xRes = envValue.map(subst => subst(envTerm))   // only one value should be present
           if(xRes.isEmpty) envTerm else xRes.head
        }
	envContext.put(envVar.simpleName, result)
        //println(s"envVar: ${envVar.simpleName}, value: $result")
	//Seq(((x: Term) => if(x.id == envVar.id) {println(s"X: $x"); result} else {println(s"Y: $x"); x}, goals.tail))
	//Seq(((x: Term) => result, goals.tail))
	Seq(((x: Term) => x match {
          case v: Variable if v.simpleName == envVar.simpleName => result
          case _ => x
        }, goals.tail))
    }

    def solvePostTerm(args: Seq[Term], goals: Seq[Term], defhead: Term): Iterable[Tuple2[Term => Term, Seq[Term]]] = {
      //println(s"[slang Inference solvePostTerm] args=${args}  goals=${goals}    defhead=${defhead}")
      //println(s"[slang Inference solvePostTerm] envContext:")
      //for((k, v) <- envContext) println(s"Key: ${k}   Value: ${v}")

      val constantTerms = bindEnvVar(args)(envContext) // Qiang: will resolve args using recurse
      //println(s"[slang Inference solvePostTerm] constantTerms=${constantTerms}")

      if(!envContext.contains(StrLit("Self"))) {
        throw UnSafeException(s"No speaker since Self not defined ${envContext.keySet}")
      }

      //val principal: Principal = envContext.get(StrLit("Selfie")) match {
      //  case Some(p: Principal) => p
      //  case _                  => throw UnSafeException(s"cannot sign since principal (Selfie) not defined ${envContext.keySet}")
      //}

      val principal: Principal = envContext.get(StrLit("Selfie")) match {
        case Some(p: Principal) => p
        case _                  => null    // Since there is not signing key, the certificate will be posted without signature
      }

      val unfoldedTerms: Seq[Term] = constantTerms.map(term => SlangTerm.unfoldSeq(term)).flatten 
      //println(s"[slang Inference solvePostTerm] unfoldedTerms=${unfoldedTerms}   unfoldedTerms.size=${unfoldedTerms.size}")

      // post slogsets
      val setsToPost: Seq[SlogSet] = unfoldedTerms.map { term =>  
        //Thread.sleep(1000L)
        //println(s"[slang inference solvePostTerm] setsToPost  token=${term.id.name}") 
        setCache.get(Token(term.id.name)) match { 
          case Some(slogset: SlogSet) =>
            //println(s"[slang Inference solvePostTerm] set in local set table with token ${term.id}: ${slogset}")
            slogset
          case _ => throw UnSafeException(s"No slogset found in local set table with token: ${term.id}")
        }
      }

      val tokens: Seq[String] = setsToPost.map{ case set => safeSetsClient.postSlogSet(set, principal) }
      // Don't remove from local set table
      // Inform set cache of the posted slogsets: to remove the entry in the local set table
      //tokens.foreach( t => setCache.postedLocal(Token(t)) )
      val postedScids = tokens.map{ token =>  Constant(StrLit(token), StrLit("nil"), StrLit("StrLit"), Encoding.AttrBase64) }
      val result: Term = postedScids.foldRight(Structure(StrLit("nil"), Nil)) { (x,y) => Structure(StrLit("_seq"), Seq(x) ++: Seq(y))}
      //println(s"[slang Inference solvePostTerm] result=${result}")
      Seq(((x: Term) => result, goals.tail))
    }

    def solveGoalTerm(goal: Term, goals: Seq[Term], depth: Int): Iterable[Tuple2[Term => Term, Seq[Term]]] = {
      /** TODO:
	1. memo goals.head
	2. if goals.head is found again, then check for answers
	  2a. If no answers found, then freeze stack
	    -- execute other branches
	    -- on completion, resume this branch
	  2b. If answers found, then execute with those answers rather than doing an SLD
       */
      //println("[slangInference solveGoalTerm] goal " + goal + "   goals " + goals )
      val statements: OrderedSet[Statement] = filterAndBind(   // This is inherited from slog
	  goal
	, variable => Variable(s"${variable.name}_${depth}")
      )(subcontexts)

      //println(s"[slangInference solveGoalTerm] Statements: $statements")
      //println(s"proofContext: $proofContext")

      val res = for {
	statement <- statements
	//_ = logger.debug("branch: statement: " + statement)
	//_ = logger.debug("branch: statement.terms.head: " + statement.terms.head)
	//_ = println("branch: statement.terms.head: " + statement.terms.head)
	subst <- mostGenericUnifier(statement.terms.head, goal)(envContext)
	newGoals = (statement.terms.tail ++ goals.tail).map(subst)
	//_ = logger.debug("newGoals: " + newGoals)
	//_ = println("newGoals: " + newGoals)
      } yield (subst, newGoals)
      res
    }

    def evalConditionTerm(term: Term): Term = term match {
      case c: Constant => c
      case _           => 
         val termFn = recurse(Seq(term))
	 termFn.map(subst => subst(term)).toSeq.head
    }

    def evalCondition(term: Term): Boolean = term match {
      case Constant(StrLit("true"), _, _, _)  => true
      case Constant(StrLit("false"), _, _, _) => false
      case Structure(cond, leftTerm +: rightTerm +: Nil, _, _, _) if (
         cond == StrLit("_unify")
       | cond == StrLit("_lt")
       | cond == StrLit("_lteq")
       | cond == StrLit("_gt")
       | cond == StrLit("_gteq") 
      ) =>
	 val rightTermValue: Term = evalConditionTerm(rightTerm)
	 val leftTermValue: Term  = evalConditionTerm(leftTerm)
         if(rightTermValue.toString == leftTermValue.toString) true
         else {
           val simpleStructure = Structure(cond, leftTermValue +: rightTermValue +: Nil)
           simpleStructure.compare()
         }
      case _ => throw UnSafeException(s"Unrecognized term passed in ifThenElse: $term")
    }

    //@annotation.tailrec // TODO: if we support := divergence, tailrec is compromised
    def branch(goals: Seq[Term], depth: Int): Iterable[Tuple2[Term => Term, Seq[Term]]] = goals.head match { 
     //{ println(s"[slangInference branch] goal.head: ${goals.head}   goals: ${goals}"); goals.head match {

      //case x => return(Seq(Tuple2((x: Term) => x, goals.tail)))

      case Structure(StrLit("defcall"), defhead +: setTerm +: Nil, _, _, _) => setTerm match {
        case s: SetTerm => //println("[slangInference] solve a defguard term " + setTerm); 
          solveSetTerm(StrLit("defcall"), s, goals, defhead)
        case _          => throw UnSafeException(s"SetTerm expected but something else found: $setTerm")
      }

      case Structure(StrLit("defguard"), defhead +: setTerm +: Nil, _, _, _) => setTerm match {
        case s: SetTerm => //println("[slangInference] solve a defguard term " + setTerm); 
          solveSetTerm(StrLit("defguard"), s, goals, defhead)
        case _          => throw UnSafeException(s"SetTerm expected but something else found: $setTerm")
      }
        
      case Structure(StrLit("defun"), defhead +: funTerm +: Nil, _, _, _)   => funTerm match {
        case f: FunTerm => solveFunTerm(f, goals, defhead)
        case _          => throw UnSafeException(s"FunTerm expected but something else found: $funTerm")
      }

      case Structure(StrLit("definit"), xterms, _, _, _) => branch(xterms ++ goals.tail, depth)

      case Structure(StrLit("defcon"), defhead +: setTerm +: Nil, _, _, _) => setTerm match {
        case s: SetTerm => solveSetTerm(StrLit("defcon"), s, goals, defhead)
        case _          => throw UnSafeException(s"SetTerm expected but something else found: $setTerm")
      }

      case Structure(StrLit("defenv"), (envVar @ Variable(_, _, _, _)) +: xterm +: Nil, _, _, _) => 
        //println(s"[slang Inference branch] defenv  envVar=${envVar};      xterm=${xterm};    xterm.getClass=${xterm.getClass}")
        //println(s"[slang Inference branch] defenv  goals=${goals}")
        solveEnvTerm(envVar, xterm, goals)
      case Structure(StrLit("post"), args, _, _, _)    => //println("[slang branch] solve a post term"); 
        solvePostTerm(args, goals, Constant("defpost"))  // safesets API
      case Structure(StrLit("defpost"), defhead +: args, _, _, _) => //println("[slang branch] solve a defpost term"); 
        solvePostTerm(args, goals, defhead)  // safesets API
      //case Structure(StrLit("fetch"), args, _, _, _)   => solveFetchTerm(args, goals, Constant("defetch")) // safesets API
      //case Structure(StrLit("defetch"), defhead +: args, _, _, _) => solveFetchTerm(args, goals, defhead) // safesets API

      case Constant(StrLit("true"), _, _, _)     => Seq((x=>x, goals.tail))
      case Constant(StrLit("false"), _, _, _)    => Nil
      case envVar: Variable if envVar.isEnvVariable() =>
        val bindedEnvVar = bindEnvVar(Seq(Variable(envVar.id.name.substring(1))))(envContext)
        if(bindedEnvVar.isEmpty) {
          Nil
        } else {
          Seq(((x: Term)=>if(x.id == envVar.id) bindedEnvVar.head else x, goals.tail))
        }
      case Structure(StrLit("spec"), _, _, _, _) => Seq((x=>x, goals.tail))
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
      case Structure(StrLit("_subset"), leftTerm +: rightTerm +: Nil, _, _, _) =>
        //val leftValue  = solve(contextIds, Seq(Query(Seq(leftTerm))), false)
        //val rightValue = solve(contextIds, Seq(Query(Seq(rightTerm))), false)
        val leftValueTerms  = recurse(Seq(leftTerm))
        val rightValueTerms = recurse(Seq(rightTerm))
        val leftValue  = leftValueTerms.map{subst => subst(leftTerm)}
        val rightValue = rightValueTerms.map{subst => subst(rightTerm)}
        //println(s"leftValue: $leftValue")
        //println(s"rightValue: $rightValue")
        if(leftValue.toSet subsetOf rightValue.toSet) Seq((x=>x, goals.tail)) else Nil
      case goal @ Structure(name, xterms, _, _, _) if (Set(StrLit("_lt"), StrLit("_lteq"), StrLit("_gt"), StrLit("_gteq"), StrLit("_subset"), StrLit("_in")).contains(name)) =>
        if(goal.compare()) Seq((x=>x, goals.tail)) else Nil
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
        val result = Constant(StrLit(s"${decimalFormat.format(goal.eval())}"), StrLit("nil"), StrLit("NumericConstant"))
        Seq((x => result, goals.tail)) // no need to evaluate; useful only for cases where direct arithmetic is requested, e.g., +(2, 3)
      case Structure(StrLit("_unify"), leftTerm +: rightTerm +: Nil, _, _, _) => 
        mostGenericUnifier(leftTerm, rightTerm)(envContext) map { subst => 
	  val newGoals = goals.tail.map(subst) // The subst returned by mostGenericUnifier eventually calls the bind function of a term
	  Seq((subst, newGoals))
	} getOrElse(Nil)
      case isStrt @ Structure(StrLit("_is"), xterms, _, _, _) =>  xterms match { //println("[slangParser branch] _is structure: " + isStrt); xterms match {
	case Variable(leftVar, _, _, _) +: rightTerm +: Nil => rightTerm match {
	  case f: FunTerm    =>
	    val result = Structure(StrLit("_unify"), Variable(leftVar) +: f.evalFunction()(envContext) +: Nil)
	    branch(result +: goals.tail, depth)
	  case s: SetTerm    =>
	    val result = Structure(StrLit("_unify"), Variable(leftVar) +: s.evalSet(StrLit("defcon"))(envContext, slangCallClient, setCache, contextCache) +: Nil)
	    //val result = Structure(StrLit("_unify"), Variable(leftVar) +: s.evalSet(StrLit("defcon"))(envContext, proofContext) +: Nil)
	    branch(result +: goals.tail, depth)
	  case s: Structure if (s.id.name == "_seq" & isGrounded(s)) =>  // if not _seq, then it is a function which needs to be solved
	    val result = Structure(StrLit("_unify"), Variable(leftVar) +: rightTerm +: Nil)
	    branch(result +: goals.tail, depth)
	  case s: Structure  =>
            //println("rightTerm " + rightTerm)
	    val rightTermValue = recurse(Seq(rightTerm))  // The result is just that of SetTerm.evalSet()
            // Note: rightTerm can yield more than one value due to the presence of a funTerm with multiple defintions.
            // In such cases, we pick the head value and proceed rather than branching the term
            // Any value of the resulting term rightTerm should be okay
	    // val result = rightTermValue.map(subst => Structure(StrLit("_unify"), Variable(leftVar) +: subst(rightTerm) +: Nil)).head
	    // branch(contextIds, result +: goals.tail, depth)
            //println("[slangInference branch] rightTermValue " + rightTermValue) // def*_method => proofsubcontextID (can be defcon_name)
	    val result: Seq[Term] = rightTermValue.map(subst => Structure(StrLit("_unify"), Variable(leftVar) +: subst(rightTerm) +: Nil)).toSeq
            //println("[slangInference branch] result " + result)
	    result.map{res => branch(res +: goals.tail, depth)}.flatten // tailrec issue here
	  case x if isGrounded(x) => // a constant
	    val result = Structure(StrLit("_unify"), Variable(leftVar) +: rightTerm +: Nil)
	    branch(result +: goals.tail, depth)
	  case _ => throw UnSafeException(s"RightTerm is not grounded: $rightTerm")
	}
	case _ => throw UnSafeException(s"LeftTerm should be a variable: $xterms")
      }
      case s @ Structure(StrLit("_interpolate"), Constant(body, attrName, tpe, _) +: Constant(termSeq, _, _, _) +: terms, _, _, _) =>
        val res: String = Term.interpolate(body.name, termSeq.name, bindEnvVar(terms)(envContext))
        Seq((x=>Constant(StrLit(res), attrName, tpe, Encoding.AttrLiteral), goals.tail))

      // handle special predicates that returns bindings
      case s @ Structure(spredicate, args, _, _, _) if specialPred.contains(s.primaryIndex) =>
        val bindings = handleSpecialPred(spredicate, args, s.arity)
        logger.info(s"[slang branch] ${goals.tail}")
        bindings.map{ binding => logger.info(s"[slang branch] ${goals.tail.map(binding)}"); (binding, goals.tail.map(binding)) }  // FASTER: unit binding (x=>x) still causes a map on goals.tail

      case s @ Structure(stdlibdef, args, _, _, _) if predef.contains(s.primaryIndex) =>
        //println("[slangInference branch] stdlibdef " + stdlibdef  + "       s.primaryIndex " + s.primaryIndex + "   args " + args + "   s.arity" + s.arity) 
        val result = handleDefTerm(stdlibdef, args, s.arity)
        result match {
          case Constant(StrLit("_false"), _, _, _) => Iterable.empty  // Qiang: didn't get meaningful result; terminate the inference on this branch
          case _ => 
            val binding = ((x: Term) => if(x == s) result else x)
            Seq((binding, goals.tail))
            //Seq(((x: Term) => result, goals.tail))  // Maybe this's better.  Seq(((x: Term) => if(x== stdlibdef) result else x, goals.tail))
        }
      case f: FunTerm => solveFunTerm(f, goals, f)
      case s: SetTerm => solveSetTerm(StrLit("defguard"), s, goals, s)
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
      case Structure(StrLit("ifThenElse"), xterms, _, _, _) => xterms match {
        case Constant(StrLit("true"), _, _, _) +: Constant(leftVal, _, _, _) +: Constant(rightVal, _, _, _) +: Nil =>
          Seq(((x: Term) => xterms.tail.head, goals.tail)) // leftVal
        case Constant(StrLit("false"), _, _, _) +: Constant(leftVal, _, _, _) +: Constant(rightVal, _, _, _) +: Nil =>
          Seq(((x: Term) => xterms.last, goals.tail))      // rightVal
        case cond +: leftVar +: rightVar +: Nil =>
          val isTrue   = if(evalCondition(cond)) "true" else "false"
      	  val leftVal  = Structure(StrLit("_is"), Variable("%LeftVar") +: leftVar +: Nil)
	  val rightVal = Structure(StrLit("_is"), Variable("%RightVar") +: rightVar +: Nil)
	  val result   = Structure(StrLit("ifThenElse"), Constant(isTrue) +: Variable("%LeftVar") +: Variable("%RightVar") +: Nil)
	  branch(leftVal +: rightVal +: result +: goals.tail, depth)
	case _ => throw UnSafeException(s"error in ifThenElse")
      }
      // case NegatedTerm(name, xterm) => branch(contextIds, Seq(xterm), depth) 
      // Note: using solveAQuery() here instead of branch() to keep the @tailrec satisfied for branch()
      // TODO: May overflow stack if number of negated terms are more.
      case NegatedTerm(name, xterm, _, _, _) => solveAQuery(Query(Seq(xterm)), false)(envContext, subcontexts) match {
	case Nil => Seq((x=>x, goals.tail))
	case _   => Nil
      }

      case other                                         => solveGoalTerm(other, goals, depth)  // A goal of a contant also exits from here
    }
    //}

    /** bind variables to constants if found in envContext; apply the same for lists */
    //@annotation.tailrec
    def bindEnvVar(terms: Seq[Term])(implicit envContext: MutableMap[StrLit, EnvValue]): Seq[Term] = {
      val evaledTerms = terms match {
	case Structure(StrLit("_seq"), subTerms, _, _, _) +: rest => subTerms match {
	  case Structure(StrLit("_seq"), moreSubTerms, _, _, _) +: tail =>
            Structure(
              StrLit("_seq") , substTerm(subTerms.head, recurse(Seq(subTerms.head)).toIndexedSeq) ++: bindEnvVar(subTerms.tail) 
            ) +: bindEnvVar(rest)
	  case _ => 
            Structure(StrLit("_seq"), bindEnvVar(subTerms)) +: bindEnvVar(rest)
	}
	case Structure(StrLit("nil"), _, _, _, _) +: rest   => Structure(StrLit("nil"), Nil) +: bindEnvVar(rest)
	case (s @ Structure(StrLit("_ipv4"), _, _, _, _)) +: rest   => s +: bindEnvVar(rest)
                               // add an case for builtins
	case Structure(idLit, tms, attrName, tpe, encode) +: rest      => 
          //println(s"[slang Inference bindEnvVar] recurse(Seq(terms.head))   terms.head=${terms.head}") 
          //val bindings = recurse(Seq(terms.head))
          //println(s"======================================================== \n [slang inference bindEnvVar]  binding of terms.head (${terms.head}) is done!\n  =====================================================")
          //println(s"bindings.size = ${bindings.size}")
          //val resolvedHeadTerm = bindings.map{ subst => 
          //  val boundTms = tms.map(t => subst(t))
          //  Structure(idLit, boundTms, attrName, tpe, encode)
          //}  
          //println(s"[slang Inference bindEnvVar] resolvedHeadTerm=${resolvedHeadTerm}")
          //resolvedHeadTerm ++: bindEnvVar(rest)
          substTerm(terms.head, recurse(Seq(terms.head)).toIndexedSeq) ++: bindEnvVar(rest)
	case Constant(_, _, _, _) +: rest          => 
          Seq(terms.head) ++: bindEnvVar(rest)
	case (v @ Variable(varId, _, _, _)) +: rest  => 
          val envVar = v.simpleName
          // Changed by Qiang 
          //val envVar = v.simpleName match {
          //  case StrLit("Self") => StrLit(executiveSelf)
          //  case StrLit("SelfKey") => StrLit(executiveSelfKey)
          //  case other => other
          //}

	  val res = envContext.get(envVar) match {
	    case Some(value: Constant)    => value +: bindEnvVar(rest)
	    case other if (
                 envVar == StrLit("Self")
               | envVar == StrLit("SelfKey")
            )                             => v +: bindEnvVar(rest) // Self can be bound lately at post time
	    case other                    => 
              //terms.foreach(t => println(s"t.getClass=${t.getClass};   t=${t}"));  println(s"===================================\n Unbound variable found: ${v}; ${varId}; ${terms}; ${other}; envContext: ${envContext.keySet}; envVar: $envVar \n===================================");
              SafelogException.printLabel('warn)
              println(s"Undefined variable: ${varId.name}")
              Constant(StrLit("")) +: bindEnvVar(rest) 
              //terms //throw UnSafeException(s"Unbound variable found: ${terms}; ${other}; envContext: ${envContext.keySet()}; $envVar")
	  }
          res
	case s @ SetTerm(_,_,_,_: SlogSetTemplate,_,_,_) +: rest => s ++: bindEnvVar(rest) // TODO: check?
        case SlogResult(_,_,_,_,_) +: rest => terms  // added by Qiang
	case x +: rest =>
          substTerm(x, recurse(Seq(x)).toIndexedSeq) ++: bindEnvVar(rest) // FunTerm or Variable
	case Nil       => Nil
      }
      evaledTerms
    }


    /**
     * We have a local set cache now. Modify inferSet accordingly.
     */

    /**
     * mintACombinedId is an auxiliary function for InferSet.
     * InferSet creats a new set in localSetCache. mintCombinedId 
     * generates an id for indexing that local set. It takes two 
     * setIds and concatenates their names with a "_".
     */

    def mintACombinedId(setId0: Term, setId1: Term): Term = {
      Constant(StrLit(setId0.id.name + "_" + setId1.id.name))
    }

    /** 
     * Pick up the first set Id from a term, which can be a 
     * a term of a setId, or a "_seq" term that includes a 
     * seqence of set Ids. We only need one setId. To 
     * support the legacy code, we consider the case of a 
     * "_seq" term as well. We only take the first setId 
     * anyway.
     */

    def getFirstSetId(setIdsWrapper: Term): Term = {
      setIdsWrapper match {
        case Structure(StrLit("_seq"), importSeq, _, _, _) => importSeq.head
        case other => other  
      }
    }

    /** 
     * inferQuerySet infers a set and smashes it to a require-query set,
     * wraps them into a slogset, and puts it into the local set table.
     * It returns the index of the local set. It does nothing,
     * if it has been called before with the same rule set and fact set. 
     * In that case, it only checks whether the expected local set already 
     * exists.
     */

    def inferQuerySet(ruleSetId: Term, factSetId: Term): Term ={ 
      val ruleId = getFirstSetId(ruleSetId)
      val factId = getFirstSetId(factSetId)
      val resultSetId = mintACombinedId(ruleId, factId)
      if(setCache.get(Token(resultSetId.id.name)).isDefined == false) { // Haven't done it before

        val inferredStmts: Seq[Seq[Statement]] = inferSet(ruleId, factId)

        /** 
         * Smash the results into require queries and
         * wrap them into a slogset; finally put the
         * the slogset into the local set cache, so that 
         * the queries can be passed by for later 
         * inference.
         */
        val newSetStmts: Seq[Statement] = inferredStmts.flatten
        val convertedQueries: Seq[Statement] = 
          if(Config.config.logicEngine != "styla") {
            newSetStmts.map(result => result.terms.head match {
              case Constant(StrLit("query(_*)"), _, _, _) => AnnotatedQuery(result.terms.tail, REQUIRE)
              case _ => AnnotatedQuery(result.terms, REQUIRE)
            })
          } else {   
            newSetStmts.map(result => result match {
              case s: StyStmt => StyQuery(s.styterms, s.vmap, REQUIRE)
              //case _ => logger.error("Unrecognized inferset resulting stmt: ${result}")
            })
          }
       
        convertedQueries.foreach(query => logger.info(s"[inferQuerySet] ${query.getClass}   query: ${query}")) 
        val slogset = SlogSet(convertedQueries, resultSetId.id.name)
        setCache.putLocalIfAbsent(Token(resultSetId.id.name), slogset)
      }

      // Return the index of result set 
      resultSetId
    }

    /**
     * inferSet (with rules and facts) infers a set of statements using
     * a rule set and a fact set. It assumes that the rule set id
     * points to a local set, with queries inside it.  The fact
     * set id can either point to a remote set or to a local set 
     * in the local set cache. We assume the required sets have 
     * been correctly set up before inferSet is called.
     */ 

    def inferSet(ruleSetId: Term, factSetId: Term): Seq[Seq[Statement]] = {
      val ruleId: Term = getFirstSetId(ruleSetId)
      val factId: Term = getFirstSetId(factSetId)
      
      /**
       * Assume the rule set is defined in a local defcon.
       * This defcon needs to include one or more queries.
       * The defcon has been called, resulting a set in 
       * the local set cache.  We assume these happened
       * before reaching the slang line of inferSet.
       */
      val ruleSet = setCache.get(Token(ruleId.id.name)) 
      if(ruleSet.isDefined == false) {
        throw UnSafeException(s"Rule set ${ruleId.id.name} is expected, but not found in the set cache: run the defcon first")  
      } 

      val queries: Seq[Statement] = ruleSet.get.queries
      logger.info(s"Queries for inferSet: $queries")

      //logger.info(s"fact context: ${contextCache.get(Token(factId.id.name))}")
      val factSet = setCache.get(Token(factId.id.name))

      if(factSet.isDefined == false) {
        throw UnSafeException(s"Fact set ${factId.id.name} is expected, but not found in the set cache: check the set token")  
      } 

      val inferenceCntIds: Seq[StrLit] = Seq(factId.id, ruleId.id) 
      val subcontexts: Seq[Subcontext] = inferenceCntIds.map{ case id: StrLit => contextCache.get(Token(id.name)) }
                                         .collect{ case cnt: Option[Subcontext] if cnt.isDefined => cnt.get}

      logger.info(s"inferset subcontexts: ${subcontexts}")

      val t0 = System.nanoTime()
      // Use the inference engine to solve slog queries
      val res: Seq[Seq[Statement]] = if(Config.config.logicEngine == "styla") {
        StylaService.solveWithContext(
          queries,
          isInteractive = false
        )(envContext, subcontexts)
      } else {
        safelogContext.solveWithContext(
          queries,
          isInteractive = false
        )(envContext, subcontexts)
      }
      val t = System.nanoTime()
      //slangPerfCollector.addStarPerfStats((t-t0)/1000, s"inferset_${factId.id.name}")
      //val runtime = System.currentTimeMillis - t0
      logger.info(s"inferset results: $res")
      //println(s"inferset results: $res")
      res
    }  
 
    def inferSetReturnTerm(ruleSetId: Term, factSetId: Term): Term = {
      val inferredSet: Seq[Seq[Statement]] = inferSet(ruleSetId, factSetId)
      if(inferredSet.flatten.isEmpty) Constant("_false")
      else SlogResult(inferredSet.map(x => x.toSet))
    }

    /**
     * This only deals with slog infer result
     */
    def reapIdFromSlogResult(slogRes: Term): Term = {
      //println("slogRes: " + slogRes)
      slogRes match {
        case SlogResult(stmtSets, _, _, _, _) if stmtSets.length > 0 =>
          val firstStmtSet = stmtSets(0) 
          if(firstStmtSet.size > 0) {
            val firstStmt = firstStmtSet.head
            val terms = firstStmt.terms
              if(terms.length > 0) terms(0)
              else Constant("_false")
          } else {
            Constant("_false")
          }
        case _ => Constant("_false")
      }
    }

    /**
     * This only deals with styla inference results
     */
    def getNthArgFromSlogResult(slogRes: Term, pos: Term): Term = {
      if(!isConstantOfInt(pos)) throw UnSafeException(s"Invalid position ${pos}")
      slogRes match {
        case SlogResult(stmtSets, _, _, _, _) if stmtSets.length > 0 =>
          val firstStmtSet = stmtSets(0) 
          if(firstStmtSet.size > 0) {
            val firstStmt = firstStmtSet.head
            assert(firstStmt.isInstanceOf[StyStmt], s"getNthArgFromSlogResult only takes care of StyStmt: ${firstStmt}")
            val arg = firstStmt.asInstanceOf[StyStmt].getHeadArgument(pos.id.name.toInt) // The pos argument of the head atom
            if(arg.isDefined) Constant(arg.get)
            else Constant("_false")
          } else {
            Constant("_false")
          }
        case _ => Constant("_false")
      }
    }

    def isConstantOfInt(t: Term): Boolean = {
      def isAllDigits(x: String) = x forall Character.isDigit
      if(t.isInstanceOf[Constant]) {
        val c = t.asInstanceOf[Constant]
        if(isAllDigits(c.id.name)) {
          return true
        }        
      }
      false
    }

    def parseAndGetNthArg(c: Term, pos: Term): Term = {
      if(!isConstantOfInt(pos)) throw UnSafeException(s"Invalid position ${pos}")
      c match {
        case Constant(atomsInStr, _, _, _) =>
          val pos_int = pos.id.name.toInt
          val slogParser = safe.safelog.Parser() // use slog parser; no need to enable speaker in slang parser for this
          val plaintext = atomsInStr.name
          val atoms: Seq[Term] = slogParser.parseLiterals(plaintext)
          if(atoms.length > 0) {
            atoms.head match { // get arg from the first atom
              case Structure(pred, speaker +: args, _, _, _) if args.length > pos_int => args(pos_int)     
              case _ => logger.error(s"Invalid atom or unmatched arg: ${atoms.head}   ${atoms}"); Constant("_false")              
            }
          } else {
            Constant("_false")
          }
        case _ => Constant("_false")
      }
    }

    /**
     * Builtin for slang shell
     */
    def switchSelfTo(s: Term): Term = {
      assert(s.isInstanceOf[Constant], s"New self must be a constant: ${s}")
      envContext.put(StrLit("Self"), s)
      s
    }
 
    def appendName(dirTerm: Term, nameTerm: Term): Term = {
      Constant(dirTerm.id.name + "/" + nameTerm.id.name)
    } 

    def handleSpecialPred(name: StrLit, args: Seq[Term], arity: Int): Iterable[Term => Term] = { 
      (name, arity) match {
        case (StrLit("singleComponent"), 1) => 
          val isSC = singleComponentName(args(0))
          isSC match {
            case Constant(StrLit("_false"), _, _, _) => Iterable.empty  // not a single component
            case result => Seq(x => x)
          }
        case (StrLit("splitHead"), 3) =>  
          val pieces = splitHead(args(0))
          pieces.head match {
            case Constant(StrLit("_false"), _, _, _) => Iterable.empty // not splittable
            case result =>
              logger.info(s"[slang handleSpecialPred] args(1): ${args(1)} => ${pieces(0)};    args(2): ${args(2)} => ${pieces(1)}")
              val binding = ( (x: Term) => x.bind(n => if(n == args(1).id) pieces(0)  else if(n == args(2).id) pieces(1) else Variable(n)) )
              Seq(binding)
          }
      }
    }
 
    def handleDefTerm(name: StrLit, args: Seq[Term], arity: Int): Term = {
      //println(s"[slang handleDefTerm]")
      val constantTerms = bindEnvVar(args)(envContext)
      //println(s"name=${name}; args=${args}; args(0).getClass=${args(0).getClass}; arity=${arity}; constantTerms=${constantTerms}")
      handleDefTermConst(name, constantTerms, arity)
    }

    def handleDefTermConst(name: StrLit, args: Seq[Term], arity: Int): Term = (name, arity) match {
      case (StrLit("reapId"), 1) => reapIdFromSlogResult(args(0)) //println("reapIdFromSlogResult    args(0)=" + args(0)); 
      case (StrLit("getNthArgFromSlogset"), 2) => getNthArgFromSlogResult(args(0), args(1))
      case (StrLit("parseAndGetNthArg"), 2) => parseAndGetNthArg(args(0), args(1))
      case (StrLit("switchSelfTo"), 1) => switchSelfTo(args(0))
      case (StrLit("inferSet"), 2) => inferSetReturnTerm(args(0), args(1))
      case (StrLit("inferQuerySet"), 2) => inferQuerySet(args(0), args(1))
      case (StrLit("label"), 2) =>
        //val t0 = System.nanoTime()
        val pid = args(0).id.name
        val label = args(1).id.name
        val setId = Identity.computeSetToken(pid, label)
        //val tokenLat = (System.nanoTime() - t0)/1000
        //slangPerfCollector.addStarPerfStats(tokenLat, s"label2_$setId")
	Constant(StrLit(setId), StrLit("nil"), StrLit("StrLit"), Encoding.AttrBase64) 
      case (StrLit("label"), 1) =>
        //val t0 = System.nanoTime()
        var pid = ""
        envContext.get(StrLit("Self")) match {
          case Some(p: Constant) => 
            pid = p.id.name 
          case _ =>
        }
        val label = args(0).id.name
        val setId = Identity.computeSetToken(pid, label)
        //val tokenLat = (System.nanoTime() - t0)/1000
        //slangPerfCollector.addStarPerfStats(tokenLat, s"label1_$setId")
        Constant(StrLit(setId), StrLit("nil"), StrLit("StrLit"), Encoding.AttrBase64) 
      case (StrLit("computeIdFromName"), 2) =>
        //println(s"[slang inference] computeIdFromName    args(0)=${args(0)}    args(1)=${args(1)}")
	val nameHash = Identity.encode(Identity.hash(args(1).id.name.getBytes(StringEncoding), "MD5"), "base64URLSafe")
        val namespace     = s"${args(0).id.name}:${nameHash}"
        val setIdHash = Identity.hash(namespace.getBytes(StringEncoding), "SHA-256")
	val setId = StrLit(Identity.encode(setIdHash, "base64URLSafe"))
        logger.info(s"namespace: $namespace; name: ${args(1).id}; setId: $setId")
	Constant(setId, StrLit("nil"), StrLit("StrLit"), Encoding.AttrBase64)
      //case (StrLit("guid"), 1) =>
      //  val argArray = args(0).id.name.split("\\:")
      //  Constant(StrLit(s"${argArray(1)}"), StrLit("nil"), StrLit("StrLit"), Encoding.AttrBase64)
      case (StrLit("objectFromScid"), 1) =>
        val argArray = args(0).id.name.split("\\:")
        assert(argArray.length == 2, s"Invalid network ID: ${args(0).id.name}")
        logger.info(s"[slang inference] objectFromScid: ${argArray(1)}")
        Constant(StrLit(s"${argArray(1)}"), StrLit("nil"), StrLit("StrLit"), Encoding.AttrBase64)
      case (StrLit("ipFromNetworkID"), 1) =>
        val argArray = args(0).id.name.split("\\:", -1)
        assert(argArray.length == 2, s"Invalid network ID: ${args(0).id.name}")
        logger.info(s"[slang inference] ipFromNetworkID: ${argArray(0)}")
        Constant(StrLit(s"""ipv4"${argArray(0)}""""), StrLit("nil"), StrLit("StrLit"), Encoding.AttrBase64)
      case (StrLit("portFromNetworkID"), 1) =>
        val argArray = args(0).id.name.split("\\:", -1)
        logger.info(s"[slang inference] portFromNetworkID: ${argArray(1)}")
        Constant(StrLit(s"""port"${argArray(1)}""""), StrLit("nil"), StrLit("StrLit"), Encoding.AttrBase64)
      case (StrLit("decrypt"), 2) =>
        args(0) match {
          case Constant(StrLit("AES"), _, _, _) => 
            val accesskey = envContext.get(StrLit("AccessKey")) 
            if(accesskey.isDefined) {
              val k = accesskey.get.asInstanceOf[SecretKeySpec]
              val plaindata: Array[Byte] = decrypt(Identity.base64Decode(args(1).id.name), k) 
	      Constant(StrLit(Identity.encode(plaindata, "base64URLSafe")), StrLit("nil"), StrLit("StrLit"), Encoding.AttrBase64)
            } else {
              throw UnSafeException(s"cannot decrypt because of no access key") 
            }
          case _ => throw UnSafeException(s"${args(0)} is not implemented") 
        }
      case (StrLit("createAESKey"), 1) =>
        args(0) match {
          case Constant(StrLit(keylen), _, _, _) if keylen.toInt == 256 => 
            val secretkey: SecretKey = createAESKey(keylen.toInt)
            val subjectID = envContext.get(StrLit("Subject")) 
            if(subjectID.isDefined) {
              val s = subjectID.get.asInstanceOf[Constant]
              val keyname = s.id.name
              val encodedKey = saveAESKey(secretkey, keyname)
	      Constant(StrLit(encodedKey), StrLit("nil"), StrLit("StrLit"), Encoding.AttrBase64)
            } else { 
              throw UnSafeException(s"cannot create a key because no subject is specified") 
            }
          case _ => throw UnSafeException(s"Key length ${args(0)} is not implemented") 
        }
      case (StrLit("encryptBearerRef"), 2) =>
        args(0) match {
          case Constant(StrLit("AES"), _, _, _) => 
            val accesskey = envContext.get(StrLit("AccessKey")) 
            if(accesskey.isDefined) {
              val k = accesskey.get.asInstanceOf[SecretKeySpec]
              val envs = SlangRemoteCallService.parseSlangCallEnvs(args(1).id.name)
              val cipherBearerRef: Array[Byte] = encrypt(Identity.base64Decode(envs(3)), k) // envs(3): bearerRef 
              val cipherenvs = s"{envs(0)}:{envs(1)}:{envs(2)}:{cipherBearerRef}"
	      Constant(StrLit(cipherenvs), StrLit("nil"), StrLit("StrLit"), Encoding.AttrBase64)
            } else {
              throw UnSafeException(s"cannot encrypt because of no access key") 
            }
          case _ => throw UnSafeException(s"${args(0)} is not implemented") 
        } 
      case (StrLit("computeId"), 2) =>
        val namespace     = s"${args(0).id.name}:${args(1).id.name}"
        //println(s"namespace: $namespace")
	val setId = Identity.hash(namespace.getBytes(StringEncoding), "SHA-256")
	Constant(StrLit(Identity.encode(setId, "base64URLSafe")), StrLit("nil"), StrLit("StrLit"), Encoding.AttrBase64)
      case (StrLit("getId"), 2) =>
        //println(s"myargs: $args; ${args.length}")
        val subject = Subject(args(0).toString())
        val id = subject.computeId(name = args(1).toString).value
	Constant(StrLit(id.name), StrLit("nil"), StrLit("StrLit"), Encoding.AttrLiteral)
      case (StrLit("rootId"), 1) =>
        //val argArray = args(0).id.name.split(":")
	//Constant(StrLit(s"${argArray(0)}"), StrLit("nil"), StrLit("StrLit"), Encoding.AttrLiteral)
        val delimiterIndex = args(0).id.name.lastIndexOf(":")
        if(delimiterIndex == -1)
          throw UnSafeException(s"Invalid token: ${args(0).id.name}")
	Constant(StrLit(s"${args(0).id.name.substring(0, delimiterIndex)}"), StrLit("nil"), StrLit("StrLit"), Encoding.AttrLiteral)
      case (StrLit("rootPrincipal"), 1) =>
        //val argArray = args(0).id.name.split(":")
        val delimiterIndex = args(0).id.name.lastIndexOf(":")
        if(delimiterIndex == -1)
          throw UnSafeException(s"Invalid token: ${args(0).id.name}")
	//Constant(StrLit(s"${argArray(0)}"), StrLit("nil"), StrLit("StrLit"), Encoding.AttrLiteral)
	Constant(StrLit(s"${args(0).id.name.substring(0, delimiterIndex)}"), StrLit("nil"), StrLit("StrLit"), Encoding.AttrLiteral)
      case (StrLit("println"), 1) =>
        println(args.mkString(", "))
        Constant(StrLit("true"))
      case (StrLit("print"), 1)   => 
        print(args.mkString(", "))
        Constant(StrLit("true"))
      case (StrLit("concat"), 2)  =>
        val result = args(0).id.name + args(1).id.name
	Constant(StrLit(result), StrLit("nil"), StrLit("StrLit"), Encoding.AttrLiteral)
      case (StrLit("scid"), 0)  =>
        val principal: Principal = envContext.get(StrLit("Selfie")) match {
	  case Some(p: Principal) => p
	  case _                  => throw UnSafeException(s"cannot sign since principal (Selfie) not defined")
	}
        val result = Object(rootId = Id(principal.pid)).scid.value.name
	Constant(StrLit(result), StrLit("nil"), StrLit("StrLit"), Encoding.AttrLiteral)
      // [QiangTODO]
      //case (StrLit("parseSet"), 1)  =>
      //  val setTerm: SetTerm = (Parser()).parseCertificate(args(0).id.name)
      //  setTerm
      //case (StrLit("getSpeaker"), 1)  =>
      //  val sc: SignedCredential = toSignedCredentialTerm(args(0))
      //  Constant(StrLit(sc.speaker.id.toString), StrLit("nil"), StrLit("StrLit"), Encoding.AttrBase64)
      //case (StrLit("getSubject"), 1)  =>
      //  val sc: SignedCredential = toSignedCredentialTerm(args(0))
      //  Constant(StrLit(sc.subject.id.toString), StrLit("nil"), StrLit("StrLit"), Encoding.AttrBase64)
      //case (StrLit("getSpeakerKey"), 1)  =>
      //  val sc: SignedCredential = toSignedCredentialTerm(args(0))
      //  val spkrKey: String = sc.speaker.speaker match {
      //    case Some(value) => value.toString
      //    case None        => // check whether it is an identity set
      //      if(sc.speaker.id.toString == sc.id.toString) { // now check for principal statement
      //        sc.statements.get(StrLit("principal")) match {
      //          case Some(stmts) => stmts.head.toString
      //          case _           => "nil"
      //        }
      //      } else {
      //        "nil"
      //      }
      //  }
      //  Constant(spkrKey)
      //case (StrLit("getPrincipal"), 1)  => 
      //  val setId: StrLit = args(0) match {
      //      case Structure(StrLit("_seq"), xargs +: other, _, _, _) => xargs.id
      //      case _ => throw UnSafeException(s"Seq expected but something else found, ${args(0)}")
      //    }
      //  val proofSubContext = contextCache.get(setId).getOrElse(throw UnSafeException(s"Fetch seems to be failed for the set $setId"))
      //  val principalKey: String = proofSubContext.statements.get(StrLit("_principal")) match {
      //      case Some(stmts) => stmts.head.terms.head match {
      //        case Structure(StrLit("principal"), speaker +: speakerKey +: Nil, _, _, _) => speakerKey.id.name
      //        case _ => throw UnSafeException(s"principal not found in set $setId")
      //      }
      //      case _           => throw UnSafeException(s"principal not found in set $setId")
      //    }
      //  Constant(StrLit(principalKey), StrLit("nil"), StrLit("StrLit"), Encoding.AttrBase64)
      //case (StrLit("getName"), 1)  =>
      //  val sc: SignedCredential = toSignedCredentialTerm(args(0))
      //  Constant(sc.name)
      //case (StrLit("getId"), 1)  =>
      //  val sc: SignedCredential = toSignedCredentialTerm(args(0))
      //  Constant(sc.id)
      //case (StrLit("getSpeakerRef"), 1)  =>
      //  val sc: SignedCredential = toSignedCredentialTerm(args(0))
      //  Constant(sc.speaker.speaksForRef.getOrElse("nil").toString)
      //case (StrLit("getSignature"), 1)  =>
      //  val sc: SignedCredential = toSignedCredentialTerm(args(0))
      //  Constant(StrLit(sc.signature), StrLit("nil"), StrLit("StrLit"), Encoding.AttrBase64)
      //case (StrLit("getSignedData"), 1)  =>
      //  val sc: SignedCredential = toSignedCredentialTerm(args(0))
      //  Constant(StrLit(sc.signedData))
      //case (StrLit("getSignatureAlgorithm"), 1)  =>
      //  val sc: SignedCredential = toSignedCredentialTerm(args(0))
      //  Constant(StrLit(sc.signatureAlgorithm))
      //case (StrLit("getValdityFrom"), 1)  =>
       // val sc: SignedCredential = toSignedCredentialTerm(args(0))
        //Constant(sc.validity.notBefore.toString)
      //case (StrLit("getValdityUntil"), 1)  =>
       // val sc: SignedCredential = toSignedCredentialTerm(args(0))
        //Constant(sc.validity.notAfter.toString)
      //case (StrLit("verifySignature"), 2)  =>
      //  val sc: SignedCredential = toSignedCredentialTerm(args(0))
      //  val speaker: Subject = Subject(args(1).id.name)
      //  // check whether the ids match
      //  if(speaker.id.toString != sc.speaker.id.toString) {
      //    throw UnSafeException(s"Given speaker and speaker on the set does not match: ${speaker.id}, ${sc.speaker.id}")
      //  }
      //  val isSignatureValid: Boolean = sc.verify(speaker)
      //  //println(s"speaker: $speaker")
      //  Constant(isSignatureValid.toString)
      case (_, _)  => 
        println(s"handleDefTerm: ") 
        //Constant(s"Not yet done")
        Constant(StrLit("nil"))
    }

    //[QiangTODO]
    //def toSignedCredentialTerm(term: Term): SignedCredential = term match {
    //  case SetTerm(id, argRefs, args, credential: SignedCredential, _, _, _) => credential
    //  case _                    => 
    //    throw UnSafeException(s"Expected Signed Credential argument on function but $term found")
    //}

    def substTerm(term: Term, substFns: Seq[Term => Term]): Seq[Term] = {
      substFns.map(subst => subst(term))
    }

    //@annotation.tailrec
    def recurse(goals: Seq[Term], depth: Int = 1): Iterable[Term => Term] = {
      //println(s"goals: $goals")
      if(goals == Nil) {
/*
        val solutionsRaw = for {
	  subst <- result
        } yield query.terms.map(subst)
        val solutions = solutionsRaw.map(x => Assertion(x))

        if(query.terms.length > 1) success(Assertion(Constant("query(..)") +: query.terms), isInteractive)
	else success(Assertion(query.terms), isInteractive)
	*/

	Seq(x => x)
      }
      else if(depth > Config.config.maxDepth) {
	failure(query, "ran out of maximum depth", isInteractive)
	//System.exit(1) // just quit
	Seq(x => x) // probably going to infinite recurse //TODO: fix this
      } else {
        val endTime1 = System.currentTimeMillis()
        val res = for {
	  (solution, freshGoals) <- branch(goals, depth) // branch for new goals
	  endTime2 = System.currentTimeMillis()
	  //_ = logger.info(s"Time for branch at depth $depth is: ${endTime2 - endTime1} ms")
	  //_ = println(s"[slangInference] Time for branch at depth $depth is: ${endTime2 - endTime1} ms")
          _ = logger.info(s"[slangInference] recurse solution: ${solution};  goals.head: ${goals.head}; solution(goals.head): ${solution(goals.head)}; FreshGoals: $freshGoals")
	  remainingSolutions <- recurse(freshGoals, depth + 1)
	} yield (solution andThen remainingSolutions)
        res
      }
    }
    //println("[slangInference] solveAQuery     query.terms: " + query.terms)
    val result = recurse(query.terms)
    result.toSeq
  }

  // NOTE: The only difference between solve and solveSlang is the proofContext.put() is taken to init for solveSlang
  def solveSlang(
      queries: Seq[Statement]
    , isInteractive: Boolean = false
  ): Seq[Seq[Statement]] = {

    /**=== DEBUG Start=====
    if(true) {
      return Seq(Seq(Result(Constant("slogPong") +: Nil)))
    } 
    ====== DEBUG End==== **/
   
    //logger.info(s"Starting inference")
    val t0 = System.nanoTime()
    val subcontexts = Seq(contextCache.get(Token("_object")).get)  // _object has been populated
    //println(s"[solveSlang] slang queries: ${queries}")
    logger.info(s"[solveSlang] slang queries: ${queries}")

    val res = for {
      query <- queries
      //_ = println(s"[solveSlang] slang query: ${query}")
      t00 = System.nanoTime()
      //_   =  proofContext.put(StrLit("_object"), ProofSubContext(id = StrLit("_object"), statements = program))  // Qiang: already did this in Safelang.scala before solveSlang()
      //_ = println("[slangInference solveSlang] proofContext.keySet  " + proofContext.keySet)
      //_ = println("[slangInference solveSlang] proofContext.values " +  proofContext.values)
      resultTerms = solveAQuery(query, isInteractive)(envContext, subcontexts)
      t01 = System.nanoTime()
      //_ = logger.info(s"Elapsed time for SOLVE is: ${(t01 - t00)/Math.pow(10, 6)} ms\n\n")
      //_ = println(s"[solveSlang] Elapsed time for SOLVE is: ${(t01 - t00)/Math.pow(10, 6)} ms\n\n") // DEBUG
      _ = if(isInteractive) iterativePrint(resultTerms, query, isInteractive)
      subst <- resultTerms
      //if(!(subst(Constant(StrLit("defenvMayBe"))) == Constant(StrLit("nil")))) // check to verify if the output is nil
      //_ = println(s"query.terms: ${query.terms} -> ${query.terms.map(subst)}")
    } yield query.terms.length match {
      case 1 => 
        //println(s"""[safelang inference solveSlang] resultTerms=${query.terms.map(subst).mkString(" ;")}""")
        //println(s"""[safelang inference solveSlang] resultTerms.getClass""")
        //query.terms.map(subst).foreach(t => println(t.getClass))
        Result(query.terms.map(subst))
      case _ => Result(Constant("query(_*)") +: query.terms.map(subst)) // > 1
    }
    val slangLat = (System.nanoTime() - t0)/1000
    slangPerfCollector.addSlangQueryLatency(slangLat, s"$queries")
    //val elapsedTime = (t1 - t0)/Math.pow(10, 6)
    //logger.info(s"Elapsed time for solveSlang is: ${(t1 - t0)/Math.pow(10, 6)} ms")
    //println(s"Elapsed time for solveSlang: ${(t1 - t0)/Math.pow(10, 6)} ms")
    if(res.isEmpty && isInteractive == true)
      SafelogException.printLabel('failure)
 
    if(!queries.isEmpty && res.isEmpty) { 
       throw new RuntimeException(s"Unsatisfied queries: ${queries}  ${res}")                  
    }

    //println(s"""[safelang inference solveSlang] res=${res}  res.mkString = ${res.mkString("; ")}""")
    Seq(res.toSeq)
  }

}

class Inference(val slangCallClient: SlangRemoteCallClient, val safeSetsClient: SafeSetsClient, 
    val setCache: SetCache, val contextCache: ContextCache, val safelangId: Int) extends InferenceService 
    with SlangRemoteCallService with SafeSetsService with InferenceImpl
