package safe.safelang

import scala.collection.mutable.{Set => MutableSet, Map => MutableMap, ListBuffer}
import scala.collection.mutable.{LinkedHashSet => OrderedSet, Queue}
import java.util.concurrent.atomic.AtomicInteger
import java.nio.file.{Path, Paths}

import safe.cache.SafeTable
import setcache.SetCache
import safesets._
import safe.safelog.{SetId, Index, MutableCache, Statement, Subcontext, StrLit, 
  UnSafeException, Assertion, Query, EnvValue, Constant, Encoding, SafeProgram}
import model.Principal
import akka.actor.ActorSystem
import util.KeyPairManager
import com.typesafe.scalalogging.LazyLogging
import javax.crypto.spec.SecretKeySpec

trait SafelangImpl extends safe.safelog.SafelogImpl  {
  slangInference: InferenceService with ParserService with SafeSetsService =>

  def compileSlang(slangFile: String, fileArgs: Option[String]): SafeProgram 
}

trait SafelangService extends InferenceService 
  with ParserService
  with SafeSetsService 
  with SlangRemoteCallService
  with SafelangImpl 
  with InferenceImpl
  with parser.ParserImpl with LazyLogging { 

  /**
   * @DeveloperAPI
   */
  def introspect(): Unit = {
    val localSetTable = setCache.getLocalSetTable
    println(s"""Safelang introspection:""")
    println(s"""================================================""")
    println(s"""safelangId=${safelangId}""")
    println(s"""envContext.size=${envContext.size}   
             contextCache=${contextCache}   localSetTable.size=${localSetTable.size}""") 
    println(s"""envContext keys: ${envContext.keySet}""")
    println(s"""envContext values: ${envContext.values}""")
    println(s"""localSetTable keys: ${localSetTable.keySet}""")
    println(s"""localSetTable values: ${localSetTable.values}""")
    println(s"""================================================""")
  }

  /** Execute defenvs and definits */
  def doInitialExecution(skipDefinit: Boolean): Unit = {
    val cnt: Option[Subcontext] = contextCache.get(Token("_object"))   
    if(cnt.isDefined) {
      val facts = cnt.get.facts
      val rules = cnt.get.rules
      val defenvGoals = facts.get(StrLit("defenv0")).getOrElse(Nil) ++ rules.get(StrLit("defenv0")).getOrElse(Nil)
      logger.info(s"defenvGoals: ${defenvGoals}")
      logger.info(s"""ServerPrincipal/SelfID = ${envContext.get(StrLit("Self"))}""")
      var allGoals = ListBuffer[Assertion]()
      allGoals ++= defenvGoals.map(s => Assertion(s.terms.tail))
      logger.info(s"skipDefinit: $skipDefinit")
      if(skipDefinit == false) {
        val definitGoals = facts.get(StrLit("definit0")).getOrElse(Nil) ++ rules.get(StrLit("definit0")).getOrElse(Nil)
        logger.info(s"definitGoals: ${definitGoals}")
        allGoals ++= definitGoals.map(s => Assertion(s.terms.tail))
      }
      logger.info(s"allGoals: $allGoals")
      solveSlang(allGoals.toSeq, false)
      //println(s"Safelang init env terms: ${envContext.keySet}")
      //println(s"""Safelang init env terms: ${envContext.values}""")
    }
  }


  /** 
   * For dynamic import of slang code, we do initial execution only for 
   * the imported program.
   */
  def doInitialExecution(program: SafeProgram, skipDefinit: Boolean): Unit = {
    val defenvGoals = program.get(StrLit("defenv0")).getOrElse(Nil)
    logger.info(s"defenvGoals: ${defenvGoals}")
    logger.info(s"""ServerPrincipal/SelfID = ${envContext.get(StrLit("Self"))}""")
    var allGoals = ListBuffer[Assertion]()
    allGoals ++= defenvGoals.map(s => Assertion(s.terms.tail))
    logger.info(s"skipDefinit: $skipDefinit")
    if(skipDefinit == false) {
      val definitGoals = program.get(StrLit("definit0")).getOrElse(Nil)
      logger.info(s"definitGoals: ${definitGoals}")
      allGoals ++= definitGoals.map(s => Assertion(s.terms.tail))
    }
    logger.info(s"allGoals: $allGoals")
    solveSlang(allGoals.toSeq, false)
  }

  private[this] def loadProgram(stmts: SafeProgram): Unit = {
    val c = contextCache.get(Token("_object"))
    assert(c.isDefined, s"Slang program context must be in context cache: ${c}")
    val slangcnt: Subcontext = c.get
    slangcnt.addStatements(stmts)
  }

  /**
   * Compile slang, and link imported code when applicable.
   */ 
  def compileSlang(slangFile: String, fileArgs: Option[String]): SafeProgram = {
    val slangSource = substituteAndGetFileContent(slangFile, fileArgs)
    val p = Paths.get(slangFile)
    compileSlangWithSource(slangSource, p)
  }

  def compileSlangWithSource(slangSource: String, referencePath: Path = Paths.get(".")): SafeProgram = {
    val slang = compileAndLinkWithSource(slangSource, referencePath)
    // Do initalization execution for the program
    loadProgram(slang)
    doInitialExecution(slang, false)
    slang
  }

  def compileAndGetGuards(slangFile: String, fileArgs: Option[String] = None): Map[String, Tuple2[Int, Seq[String]]] = {
    val slangSource = substituteAndGetFileContent(slangFile, fileArgs)
    val p = Paths.get(slangFile)
    compileAndGetGuardsWithSource(slangSource, p)
  } 

  def compileAndGetGuardsWithSource(slangSource: String, referencePath: Path = Paths.get(".")): Map[String, Tuple2[Int, Seq[String]]] = {
    val compiledSlangProgram = compileSlangWithSource(slangSource, referencePath)
    /**
     * Add defguard, defpost, and defetch into guardSet
     * so that all of them are invokable via requests
     */
    val defguardSet: OrderedSet[Statement] = compiledSlangProgram.getOrElse(StrLit("defguard0"), OrderedSet.empty[Statement])
    val defpostSet: OrderedSet[Statement] = compiledSlangProgram.getOrElse(StrLit("defpost0"), OrderedSet.empty[Statement])
    val defetchSet: OrderedSet[Statement] = compiledSlangProgram.getOrElse(StrLit("defetch0"), OrderedSet.empty[Statement])
    val defunSet: OrderedSet[Statement] = compiledSlangProgram.getOrElse(StrLit("defun0"), OrderedSet.empty[Statement])
    //val guardSet = defguardSet ++ defpostSet ++ defetchSet ++ defunSet
    val allGuards = MutableMap[String, Tuple2[Int, Seq[String]]]() 
    val tally = Map[OrderedSet[Statement], Int](defguardSet -> DEF_GUARD,  defpostSet -> DEF_POST, defetchSet -> DEF_FETCH, defunSet -> DEF_FUN)
    for((guardSet, gtype) <- tally) {
      val guards: Map[String, Tuple2[Int, Seq[String]]] = guardSet.collect {
        case safe.safelog.Assertion(safe.safelog.Structure(method, args, _, _, _) +: other) =>
          (method.name, (gtype, args.map(arg => arg.toString)))
      }.toMap
      allGuards ++= guards
    }
    allGuards.toMap
  } 
}

class Safelang(
    val self: String,
    val saysOperator: Boolean,
    val slangCallClient: SlangRemoteCallClient,
    val safeSetsClient: SafeSetsClient,
    val setCache: SetCache,
    val contextCache: ContextCache,
    val safelangId: Int) extends SafelangService {
}
  
object Safelang {  
  def apply(slangCallClient: SlangRemoteCallClient, safeSetsClient: SafeSetsClient, 
      setCache: SetCache, contextCache: ContextCache, safelangId: Int): Safelang = {
    new Safelang(Config.config.self, Config.config.saysOperator,
      slangCallClient, safeSetsClient, setCache, contextCache, safelangId)
  }
}

/**
 * Manage Safelang instances and their inference contexts. The safelang instances share internal 
 * components, but each with a different envContext.
 *
 * Shared components: localSetTable, setCache, contextCache, serverPrincipals, slangCallClient,
 * and safeSetsClient.
 */ 
class SafelangManager(keypairDir: String) extends KeyPairManager with LazyLogging {

  private val localSetTable: SafeTable[SetId, SlogSet] = new SafeTable[SetId, SlogSet](
    1024 * 1024,
    0.75f,
    16
  )
  private val system: ActorSystem = ActorSystem("Safelang")
  private val slangCallClient = new SlangRemoteCallClient(system)
  private val safeSetsClient: SafeSetsClient = new SafeSetsClient(system)
  private val setCache: SetCache = new SetCache(localSetTable, safeSetsClient)
  // Let safesetsClient share the idcache from setCache
  safeSetsClient.attachIdSetCache(setCache.idcache)
  private val contextCache: ContextCache = new ContextCache(setCache)
  
  private val safelangCount: AtomicInteger = new AtomicInteger(-1)

  val slangcnt = Subcontext("_object")
  contextCache.put(Token("_object"), slangcnt)  // placeholder

  def clear(): Unit = {
    system.shutdown()
  }

  /**
   * We use a server principal pool to store all the keys of the principals that a server 
   * can behave on behalf of. A server principal is indexed by the hash of its public key. 
   * An incoming request can specify the server principal with the principal's index.
   */
  private val principalNameToID: MutableMap[String, String] = MutableMap[String, String]()
  private val serverPrincipals: MutableMap[String, Principal] = loadKeyPairs(keypairDir, principalNameToID) 

  private val clientAccessKeys: MutableMap[String, SecretKeySpec] = loadAccessKeys(Config.config.accessKeyDir)
  
  /** 
   * Server principal declared in the slang code through defenv Selfie. Request is served 
   * with this principal as self if no server principal is specified in the request.
   */
  private var defaultServerPrincipal: Option[Principal] = None

  /** If we make safelang instances stateless, we may only need one safelang instance */ 
  private val safelangs: Queue[Safelang] = Queue.empty[Safelang] 
  private val referenceEnvContexts = MutableMap.empty[String, MutableMap[StrLit, EnvValue]]
  private val workableEnvContexts = MutableMap.empty[String, Queue[MutableMap[StrLit, EnvValue]]] 
  private val maximumEnvcontexts = Config.config.maxEnvcontextsOnServer
  println(s"[SafelangManager] maximumEnvcontexts: ${maximumEnvcontexts}")
  private val referenceEnvcntMonitor = Queue[String]()
  private val workableEnvcntMonitor = Queue[String]()

  private def isValidPID(pid: String): Boolean = serverPrincipals.contains(pid) 

  def createSafelang(): Safelang = {
    val safelangId: Int = safelangCount.incrementAndGet()
    Safelang(slangCallClient, safeSetsClient, setCache, contextCache, safelangId) 
  }

  def getSafelang(): Safelang = {
    if(safelangs.size > 0) safelangs.dequeue  else null 
  }
  
  private def getWorkableEnvContexts(pid: String): Queue[MutableMap[StrLit, EnvValue]] = {
    if(workableEnvContexts.contains(pid)) {
      workableEnvContexts(pid)
    } else { // If no workable envContext Q, create one and insert it into the table
      val envcntQ = Queue[MutableMap[StrLit, EnvValue]]()      
      if(workableEnvcntMonitor.size >= maximumEnvcontexts) {  // reached maximum
        val pidToEvict = workableEnvcntMonitor.dequeue 
        workableEnvContexts -= pidToEvict
      }
      workableEnvContexts.put(pid, envcntQ)
      workableEnvcntMonitor.enqueue(pid)
      envcntQ
    }
  }
 
  /** Clone an envContext from a reference */
  private def cloneEnvContext(ref: MutableMap[StrLit, EnvValue]): MutableMap[StrLit, EnvValue] = {
    val envcnt = MutableMap[StrLit, EnvValue]()
    for(key <- ref.keySet) { // cloning
      envcnt.put(key, ref(key))
    }
    envcnt
  }

  /** We could have an EnvContext class with the these methods */
  private def insertEnvKV(envContext: MutableMap[StrLit, EnvValue], key: String, value: String): Unit = {
    val envKey = StrLit(key)
    val envValue = Constant(StrLit(value), StrLit("nil"), StrLit("StrLit"), Encoding.AttrBase64)
    envContext.put(envKey, envValue)
  }

  private def removeEnvKey(envContext: MutableMap[StrLit, EnvValue], key: String): Unit = {
    val envKey = StrLit(key)
    envContext.remove(envKey)
  }

  /**
   * Set self envs, including $Selfie, $Self, $SelfKey.
   * @param p  A principal for self
   */
  private def setSelfEnvs(envContext: MutableMap[StrLit, EnvValue], p: Principal): Unit = {
    envContext.put(StrLit("Selfie"), p)
    insertEnvKV(envContext, "Self", s"${p.pid}")
    insertEnvKV(envContext, "SelfKey", s"${p.fullPublicKey}") 
  }

  private def setSelfEnvs(envContext: MutableMap[StrLit, EnvValue], p: String): Unit = {
    if(p != "_default") {
      insertEnvKV(envContext, "Self", s"${p}")
    }
  }

  private def clearSelf(envContext: MutableMap[StrLit, EnvValue]): Unit = {
    removeEnvKey(envContext, "Selfie")
    removeEnvKey(envContext, "Self")
    removeEnvKey(envContext, "SelfKey")
  }

  /**
   * Reset self envs according to the self principle specified in the slang code (default self)
   * Clear self envs if no valid default self is specified in the slang code 
   */
  private def resetOrClearSelf(envContext: MutableMap[StrLit, EnvValue]): Unit = {
    if(!defaultServerPrincipal.isDefined) {  // No Selfie in slang code; remove self envs
      clearSelf(envContext)
    } else {
      setSelfEnvs(envContext, defaultServerPrincipal.get)
    }
  }

  /** Set up requested environment variables */
  private def setRequestEnvs(envContext: MutableMap[StrLit, EnvValue], requestedEnv: Map[String, Option[String]]): Unit = {
    val envs = Seq("Subject", "Speaker", "Object", "BearerRef")
    envs.foreach{ name: String =>
      val envValue: Option[String] = requestedEnv(name)
      if(envValue.isDefined) {
        insertEnvKV(envContext, name, envValue.get)
      } else { // remove any existing value if the KV is not specified in the request envs
        removeEnvKey(envContext, name)
      }
    }

    // Server principal
    val penv: Option[String] = requestedEnv("Principal")
    val p: Option[String] = if(!penv.isDefined) penv else { Some(principalNameToID.getOrElse(penv.get, penv.get))}
    if(p.isDefined && isValidPID(p.get)) { // Valid principal 
      val pid = p.get
      setSelfEnvs(envContext, serverPrincipals(pid))
    } else if(p.isDefined) { // defined but not a principal with keypair
      setSelfEnvs(envContext, p.get) 
    } else { // Reset or clear self envs if server principal isn't specified 
      resetOrClearSelf(envContext)
    }

    // Set access key
    val subject: Option[String] = requestedEnv("Subject")
    if(subject.isDefined) {
      val s = subject.get
      val k = clientAccessKeys.get(s)
      if(k.isDefined) {
        envContext.put(StrLit("AccessKey"), k.get)
      } 
    }
  }

  private def updateReferenceEnvContexts(pid: String, envcnt: MutableMap[StrLit, EnvValue]): Unit = {
    if(!referenceEnvContexts.contains(pid)) {  // Populate referenceEnvContexts
      referenceEnvContexts.synchronized {
        val cloned = cloneEnvContext(envcnt)
        if(referenceEnvcntMonitor.size >= maximumEnvcontexts) {  // reached maximum
          val pidToEvict = referenceEnvcntMonitor.dequeue
          referenceEnvContexts -= pidToEvict
        }
        referenceEnvContexts.put(pid, cloned)
        referenceEnvcntMonitor.enqueue(pid)
        // Also update the default server principal if we haven't done that and we need 
        if(envcnt.contains(StrLit("Selfie")) && !defaultServerPrincipal.isDefined && pid == "_default") {
          defaultServerPrincipal = Some(envcnt(StrLit("Selfie")).asInstanceOf[Principal])
        }
      }
    } 
  }

  def solveSlangQuery(query: Query, requestedEnv: Map[String, Option[String]], guardType: Option[Int]=Some(DEF_GUARD)): Seq[Seq[Statement]] = {
    val penv: Option[String] = requestedEnv("Principal")  // Consider the case where the value can be a principal name
    val p: Option[String] = if(!penv.isDefined) penv else { Some(principalNameToID.getOrElse(penv.get, penv.get))}
    //val p: Option[String] = requestedEnv("Principal")
    logger.info(s"p: $p \n requestedEnv: $requestedEnv")
    var pid: String = if(p.isDefined) p.get  else "_default"
    //var pid: String = if(p.isDefined && isValidPID(p.get)) p.get 
    //                  else if(defaultServerPrincipal.isDefined) defaultServerPrincipal.get.pid
    //                  else "_default"  // No specified pid in request; No Selfie declared in slang code or declare hasn't been executed 
    logger.info(s"pid: $pid")
    //if(p.isDefined) {
    //  logger.info(s"isValidPID(p.get): ${isValidPID(p.get)} \n serverPrincipals.keySet: ${serverPrincipals.keySet}")
    //}

    // Skip definit in initial execution when it's a post
    val skipDefinit: Boolean = if(guardType.isDefined && guardType.get == DEF_POST) true else false
    // val skipDefinit = false  // always not skip definit

    var envcnt: MutableMap[StrLit, EnvValue] = null
    var inference: Safelang = null
    this.synchronized {
      val envcntQ: Queue[MutableMap[StrLit, EnvValue]] = getWorkableEnvContexts(pid)
      if(envcntQ.size > 0) {
        envcnt = envcntQ.dequeue
      }
      inference = getSafelang()
    }
    if(inference == null) { // run out of safelang instances
      inference = createSafelang()
    }
    if(envcnt == null) { // run out envcnts for principal pid
      if(referenceEnvContexts.contains(pid)) { // clone from a reference envcontext
        envcnt = cloneEnvContext(referenceEnvContexts(pid))
      } else { // Reference envcontext hasn't been setup yet. That means the server didn't see this server principal before. 
               // Make a ref envcontext and set it up
        val _cnt = MutableMap[StrLit, EnvValue]()
        if(serverPrincipals.contains(pid)) { // Request specified a valide server principal 
          setSelfEnvs(_cnt, serverPrincipals(pid)) 
        } else {  // Set Self only
          setSelfEnvs(_cnt, pid)
        }
        inference.bindEnvContext(_cnt)
        inference.doInitialExecution(skipDefinit)  // Executing definit and defenv
        envcnt = inference.getEnvContext           // Reference envcontext 
        if(!skipDefinit) { // env context under skipDefinit is incomplete; only complete envcnt can be a reference
          updateReferenceEnvContexts(pid, envcnt)
        }
        // We now know the default server PID after initial execution is done
        if(pid == "_default" && defaultServerPrincipal.isDefined) { 
          val defaultPID: String = defaultServerPrincipal.get.pid
          pid = defaultPID
        }
      }
    }

    setRequestEnvs(envcnt, requestedEnv)
    inference.bindEnvContext(envcnt)
    // Checking
    checkInferenceEnv(inference)
    val res = inference.solveSlang(Seq(query), false)
      
    this.synchronized { // release envcont and safelang
      // We shouldn't recycle envcnt if envcnt is incomplete due to skipDefinit 
      if(skipDefinit == false) { // reusable context
        getWorkableEnvContexts(pid).enqueue(envcnt) 
      }
      safelangs.enqueue(inference)
    }      
    res
  }

  /**
   * @DeveloperApi
   */
  def checkInferenceEnv(inference: Safelang): Unit = {
    val envContext = inference.getEnvContext
    logger.info(s""" ========= Checking envs of this inference instance =========
            |    Subject                = ${envContext.get(StrLit("Subject"))}
            |    Speaker                = ${envContext.get(StrLit("Speaker"))}
            |    Object                 = ${envContext.get(StrLit("Object"))}
            |    BearerRef              = ${envContext.get(StrLit("BearerRef"))}
            |    ServerPrincipal/SelfID = ${envContext.get(StrLit("Self"))}
            |    safelangId             = ${inference.safelangId}""".stripMargin)
  }
}


object SafelangManager {
  var _instance: Option[SafelangManager] = None 
  
  def instance(): SafelangManager = {
    if(!_instance.isDefined) {
      _instance = Some(new SafelangManager(""))
    }
    _instance.get
  }

  def instance(keydir: String): SafelangManager = {
    if(!_instance.isDefined) {
      _instance = Some(new SafelangManager(keydir))
    }   
    _instance.get
  }

} 
