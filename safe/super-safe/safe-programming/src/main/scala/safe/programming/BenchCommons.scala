package safe.programming

import safe.safelang.{Safelang, SafelangManager, REQ_ENV_DELIMITER}
import safe.safelog.{UnSafeException, Query, Structure, Statement}
import util.SlangObjectHelper
import safe.safelang.util.KeyPairManager

import scala.collection.mutable.ListBuffer
import scala.util.matching.Regex
import scala.io.Source
import java.util.concurrent.TimeUnit
import java.util.concurrent.LinkedBlockingQueue

/**
 * Benchmark commons
 */
trait BenchCommons extends KeyPairManager with SlangObjectHelper {

  var testingCacheJvm = "10.103.0.25:7777"  // Call specific server JVM for testing its cache

  def setTestingCacheJvm(jvm: String): Unit = {
    // jvm format: ip:port
    assert(jvm.split(":").length == 2, s"Invalid jvm: $jvm")
    testingCacheJvm = jvm
  }
  
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

  def getPrincipalJVMMapFromFile(filepath: String): Map[String, String] = {
    Source.fromFile(filepath).getLines.map { 
      line: String => 
        val parts = line.split("\\s+")
        assert(parts.length == 2, s"Invalid JVM-principal map line: ${line}")
        parts(0).trim -> parts(1).trim 
    }.toMap
  }

  def loadPrincipals(jvmmapFile: String): ListBuffer[PrincipalStub] = {
    val jvmmap: Map[String, String] = getPrincipalJVMMapFromFile(jvmmapFile)
    println(s"[BenchCommons] jvmmap: ${jvmmap}")
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
    for(fpath <- keyPaths) {
      val pname = fpath.substring(fpath.lastIndexOf('/')+1, fpath.lastIndexOf('.'))
      val p: PrincipalStub = new PrincipalStub(fpath, s"${pname}", jvmAddr)
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
      args: Seq[String] = Seq()): Boolean = {
    val queryArgs = (Seq(delegator.getJvm, delegator.getPid, principal.getJvm,
                     principal.getPid) ++ envs ++ args).map(s => buildConstant(s))

    val query = Query(Seq(Structure(entryPoint, queryArgs)))
    val res: Seq[Seq[Statement]] = inference.solveSlang(Seq(query), false)
    !(res.flatten.isEmpty) 
  }

  /**
   * Primitive for testing cache: a delegation is required to solve the subsequent query
   * The query heads to a different server, whose cache is being tested
   * The caller must ensure that the query server principal is installed on the specified
   * server
   */
  def delegateThenQuery(inference: Safelang, entryPoint: String, delegator: PrincipalStub,
      principal: PrincipalStub, qserverprincipal: PrincipalStub, queryJvm: String, 
      envs: Seq[String] = Seq(emptyEnvs, emptyEnvs, emptyEnvs), args: Seq[String] = Seq()): Boolean = {

    val queryArgs = (Seq(delegator.getJvm, delegator.getPid, principal.getJvm,
                     principal.getPid, queryJvm, qserverprincipal.getPid) ++ envs ++ args).map(s => buildConstant(s))

    val query = Query(Seq(Structure(entryPoint, queryArgs)))
    val res: Seq[Seq[Statement]] = inference.solveSlang(Seq(query), false)
    !(res.flatten.isEmpty)
  }

  /*
   * Primitive for building a query from a parameter list (including env variables,
   * such as speaker, subject, objectId, bearerRef, principal)
   */
  def buildAndQuery(inference: Safelang, entryPoint: String, 
    serverJvm: String, serverPrincipal: String,
    speaker: Option[String] = None, subject: Option[String] = None, 
    objectId: Option[String] = None, bearerRef: Option[String] = None, 
    args: Seq[String] = Seq()): Boolean = {
    
    // envs format: "[speaker]:[subject]:[objectId]:[bearerRef]"
    var envs: StringBuilder = new StringBuilder()
    for(e <- Seq(speaker, subject, objectId, bearerRef)) {
      if(e.isDefined) {
        envs.append(e.get)
      } 
      if(e != bearerRef) {
        envs.append(REQ_ENV_DELIMITER)
      }
    }
    
    val queryArgs = (Seq(serverJvm, serverPrincipal, envs.toString) ++ args).map(s => buildConstant(s))

    val query = Query(Seq(Structure(entryPoint, queryArgs)))
    val res: Seq[Seq[Statement]] = inference.solveSlang(Seq(query), false)
    !(res.flatten.isEmpty)
  }

  /** Get matching principal stubs according to a pattern of the key filepath. */
  def getMatchingPrincipals(pattern: Regex, principalList: ListBuffer[PrincipalStub]
      ): ListBuffer[PrincipalStub] = {
    principalList.filter(p => pattern.findFirstIn(p.getKeyFile).isDefined)
  }

}
