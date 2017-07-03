package safe.programming

import safe.safelang.{Safelang, slangPerfCollector, SafelangManager}
import safe.safelog.{UnSafeException, Query, Structure}
import util.SlangObjectHelper
import safe.safelang.util.KeyPairManager

import scala.collection.mutable.ListBuffer
import scala.util.matching.Regex
import scala.io.Source
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.LinkedBlockingQueue

/**
 * Utils for benchmarking Safe applications
 */
trait SafeBench extends KeyPairManager with SlangObjectHelper {
  val effectiveOpcount = new AtomicInteger(0) 
  val successfulOpcount = new AtomicInteger(0)
  val failedOpcount = new AtomicInteger(0)
  var autoPerfStats: Boolean = true
  val autoStatsPeriod: Int = 1000   // Period for auto perf stats; every 1000 ops

  var testingCacheJvm = "10.103.0.25:7777"  // for cache testing

  def setTestingCacheJvm(jvm: String): Unit = {
    // jvm format: ip:port
    assert(jvm.split(":").length == 2, s"Invalid jvm: $jvm")
    testingCacheJvm = jvm
  }

  // for perf stats; it's not protected, assuming perf stats processing doesn't happen too frequently
  var t = System.currentTimeMillis  
  var lastEffectiveOpcount: Int = 0
  
  val timeout = 500L
  /** The Safelang queue controls the number of concurrent requests */
  def buildSafelangQueue(slangManager: SafelangManager, concurrency: Int): LinkedBlockingQueue[Safelang] = {
    val safequeue: LinkedBlockingQueue[Safelang] = new LinkedBlockingQueue[Safelang]
    for(i <- 1 to concurrency) {
      val safelang = slangManager.createSafelang()
      safequeue.offer(safelang, timeout, TimeUnit.SECONDS)
    }
    safequeue
  }

  def getMapFromFile(filepath: String): Map[String, String] = {
    Source.fromFile(filepath).getLines.map { 
      line: String => 
        val parts = line.split("\\s+")
        assert(parts.length == 2, s"Invalid JVM-principal map line: ${line}")
        parts(0).trim -> parts(1).trim 
    }.toMap
  }

  def loadPrincipals(jvmmapFile: String): ListBuffer[PrincipalStub] = {
    val jvmmap: Map[String, String] = getMapFromFile(jvmmapFile)
    println(s"[SafeBench] jvmmap: ${jvmmap}")
    loadPrincipals(jvmmap)
  }

  def loadPrincipals(jvmmap: Map[String, String]): ListBuffer[PrincipalStub] = {
    val principalList = ListBuffer[PrincipalStub]()
    for((keyPairDir, jvmAddr) <- jvmmap) {
      val plist = loadPrincipals(keyPairDir, jvmAddr)
      principalList ++= plist 
    }
    principalList
  }
 
  def loadPrincipals(keyPairDir: String, jvmAddr: String): ListBuffer[PrincipalStub] = {
    val principalList = ListBuffer[PrincipalStub]()
    val keyPaths: Seq[String] = filepathsOfDir(keyPairDir)
    var count = 0
    for(fname <- keyPaths) {
      val p: PrincipalStub = new PrincipalStub(fname, s"p${count}", jvmAddr)
      count += 1
      principalList += p
    }
    principalList
  }

  /** Build ID sets for a list of principals */
  def buildIDSet(inference: Safelang, principalList: ListBuffer[PrincipalStub]): Unit = {
    for(p <- principalList) {
      p.postIdSet(inference)
    }
  }

  /** Build subject sets for a list of principals */
  def buildSubjectSet(inference: Safelang, principalList: ListBuffer[PrincipalStub]): Unit = {
    for(p <- principalList) {
      p.postSubjectSetAndGetToken(inference)
    }
  }

  /**
   * Delegator issues delegation/endorsement to a principal. The principal puts
   * the token of the delegation into its subject set. The delegation is simple 
   * as the call follows a simple convention: 
   * entryPoint(?DelegateorJVM, ?Delegator, ?PrincipalJVM, 
   *            ?PrincipalJVM, ... ?Envs ..., ... ?Args ...)
   */
  def simpleDelegate(inference: Safelang, entryPoint: String, delegator: PrincipalStub,
      principal: PrincipalStub, envs: Seq[String] = Seq(emptyEnvs, emptyEnvs),
      args: Seq[String] = Seq()): Unit = {
    val queryArgs = (Seq(delegator.getJvm, delegator.getPid, principal.getJvm,
                     principal.getPid) ++ envs ++ args).map(s => buildConstant(s))

    val query = Query(Seq(Structure(entryPoint, queryArgs)))
    inference.solveSlang(Seq(query), false)
  }

  /**
   * Primitive for testing cache: a delegation is required to solve the subsequent query
   * The query heads to a different server, whose cache is being tested
   * The caller must ensure that the query server principal is installed on the specified
   * server
   */
  def delegateThenQuery(inference: Safelang, entryPoint: String, delegator: PrincipalStub,
      principal: PrincipalStub, qserverprincipal: PrincipalStub, queryJvm: String, 
      envs: Seq[String] = Seq(emptyEnvs, emptyEnvs, emptyEnvs), args: Seq[String] = Seq()): Unit = {

    val queryArgs = (Seq(delegator.getJvm, delegator.getPid, principal.getJvm,
                     principal.getPid, queryJvm, qserverprincipal.getPid) ++ envs ++ args).map(s => buildConstant(s))

    val query = Query(Seq(Structure(entryPoint, queryArgs)))
    inference.solveSlang(Seq(query), false)
  }

  /*
   * Primitive for building a query from a parameter list (including env variables,
   * such as speaker, subject, objectId, bearerRef, principal)
   */
  def buildAndQuery(inference: Safelang, entryPoint: String, 
    serverJvm: String, serverPrincipal: String,
    speaker: Option[String] = None, subject: Option[String] = None, 
    objectId: Option[String] = None, bearerRef: Option[String] = None, 
    args: Seq[String] = Seq()): Unit = {
    
    // envs format: "[speaker]:[subject]:[objectId]:[bearerRef]"
    var envs: StringBuilder = new StringBuilder()
    for(e <- Seq(speaker, subject, objectId, bearerRef)) {
      if(e.isDefined) {
        envs.append(e.get)
      } 
      if(e != bearerRef) {
        envs.append(":")
      }
    }
    
    val queryArgs = (Seq(serverJvm, serverPrincipal, envs.toString) ++ args).map(s => buildConstant(s))

    val query = Query(Seq(Structure(entryPoint, queryArgs)))
    inference.solveSlang(Seq(query), false)
  }


  /** Get matching principal stubs according to a pattern of the key filepath. */
  def getMatchingPrincipals(pattern: Regex, principalList: ListBuffer[PrincipalStub]
      ): ListBuffer[PrincipalStub] = {
    principalList.filter(p => pattern.findFirstIn(p.getKeyFile).isDefined)
  }

  def initPerfMonitor(allowAutoPerfStats: Boolean = true): Unit = {
    autoPerfStats = allowAutoPerfStats
    t = System.currentTimeMillis
    slangPerfCollector.init()
  }

  /** On an op succeeds, update perf stats */ 
  def opSuccessHandler(numops: Int): Unit = {
    val c = effectiveOpcount.addAndGet(numops)
    val s = successfulOpcount.addAndGet(numops)
    if(autoPerfStats == true) {
      processStatsPeriodic(c, numops)  // Process stats if needed
    }
  }

  def opFailureHandler(): Unit = {
    val c = effectiveOpcount.incrementAndGet()
    val f = failedOpcount.incrementAndGet()
    if(autoPerfStats == true) {
      processStatsPeriodic(c, 1)  // Process stats if needed 
    }
  }

  def processStatsPeriodic(effectiveOps: Int, lastnumops: Int): Unit = {
    if( (effectiveOps / autoStatsPeriod) > ((effectiveOps-lastnumops) / autoStatsPeriod) )  {
    // Process perf stats for each autoStatsPeriod effective ops
      processStats(effectiveOps) 
    }
  }

  def processStatsNow(): Unit = {
    val c = effectiveOpcount.get()
    processStats(c)
  }

  def processStats(effectiveOps: Int): Unit = { 
    val now = System.currentTimeMillis 
    val runtime = now - t
    val throughput = (effectiveOps - lastEffectiveOpcount)*1000 / runtime  // roughly autoStatsPeriod ops if autoPerfStats is on 
    val numfails = failedOpcount.get()
    val numsuccs = successfulOpcount.get()
    println(s"${lastEffectiveOpcount+1}--${effectiveOps} ops: 1000 in $runtime ms ($throughput ops/sec)   so far: $numfails ops failed    $numsuccs ops succeeded")
    t = now  // set t to now
    lastEffectiveOpcount  = effectiveOps // set lastEffectiveOpcount to effectiveOps 
    slangPerfCollector.addThroughput(throughput, s"${effectiveOps}")
    slangPerfCollector.persist(s"slang-perf-part-${effectiveOps/1000}", allRecords=false) // output perf measures
  }

}
