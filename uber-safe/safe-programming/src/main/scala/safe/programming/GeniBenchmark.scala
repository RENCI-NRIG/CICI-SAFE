package safe.programming

import safe.safelang.{Safelang, SafelangManager, slangPerfCollector}
import safe.safelog.UnSafeException

import scala.util.{Failure, Success}
import scala.concurrent.Await
import scala.concurrent.Future

import scala.collection.mutable.{ListBuffer, Map => MutableMap, Set => MutableSet}
import scala.collection.mutable.{LinkedHashSet => OrderedSet}
import scala.collection.Set
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext.Implicits.global
import com.typesafe.scalalogging.LazyLogging

class PAStub(
    pid: String,                                                  // Principal id: hash of public key
    canonicalName: String,                                        // Symbolic name of the principal
    serverJvm: String,                                            // Address of the principal's JVM
    subjectSetTokens: Seq[String],                                // Tokens of his subject sets 
    keyFile: String,                                              // pem filepath
    private var memberPolicyToken: String = null                  // Token of PA's membership policy set
    ) extends PrincipalStub(pid, canonicalName, serverJvm, subjectSetTokens, keyFile) {

  def this(keyFile: String, cn: String, serverJvm: String) {
    this(PrincipalStub.pidFromFile(keyFile), cn, serverJvm, Seq[String](), keyFile) 
  } 

  def this(p: PrincipalStub) {
    this(p.getPid, p.getCN, p.getJvm, p.getSubjectSetTokens, p.getKeyFile)
  }

  def getMemberPolicyToken: String = memberPolicyToken

  /** Post memberSet for PA */
  def postMemberSet(inference: Safelang, entryPoint: String): String = {
    simpleRemoteCall(inference, entryPoint)
  }

  /** Post memberSet and store the token into memberPolicyToken */ 
  def postMemberSetAndGetToken(inference: Safelang): Unit = {
    postMemberSet(inference, "postUserGroupMemberSet") 
    val token = postMemberSet(inference, "postProjectMemberSet")
    memberPolicyToken = token
  }
}

class SAStub(
    pid: String,                                                   // Principal id: hash of public key
    canonicalName: String,                                         // Symbolic name of the principal
    serverJvm: String,                                             // Address of the principal's JVM
    subjectSetTokens: Seq[String],                                 // Tokens of his subject sets 
    keyFile: String,                                               // pem filepath
    private var sliceControlPolicyToken: String = null,            // Token of SA's slice control policy set
    private var sliceDefaultPrivilegeToken: String = null          // Token of SA's slice default privilege policy set 
    ) extends PrincipalStub(pid, canonicalName, serverJvm, subjectSetTokens, keyFile) {

  def this(keyFile: String, cn: String, serverJvm: String) {
    this(PrincipalStub.pidFromFile(keyFile), cn, serverJvm, Seq[String](), keyFile) 
  } 

  def this(p: PrincipalStub) {
    this(p.getPid, p.getCN, p.getJvm, p.getSubjectSetTokens, p.getKeyFile)
  }

  def getSliceControlPolicyToken = sliceControlPolicyToken
  def getSliceDefaultPrivilegeToken = sliceDefaultPrivilegeToken

  /** Post standard slice control set for SA */
  def postStandardSliceControlSet(inference: Safelang): String = {
    simpleRemoteCall(inference, "postStandardSliceControlSet")
  }

  /** Post standard slice control set and store the token into sliceControlPolicyToken */ 
  def postStandardSliceControlSetAndGetToken(inference: Safelang): Unit = {
    val token = postStandardSliceControlSet(inference)
    sliceControlPolicyToken = token 
  }

  /** Post standard slice default privilege set for SA */
  def postStandardSliceDefaultPrivilegeSet(inference: Safelang): String = {
    simpleRemoteCall(inference, "postStandardSliceDefaultPrivilegeSet")
  }

  /** Post standard slice default privilege set and store the token into sliceDefaultPrivilegeToken */ 
  def postStandardSliceDefaultPrivilegeSetAndGetToken(inference: Safelang): Unit = {
    val token = postStandardSliceDefaultPrivilegeSet(inference)
    sliceDefaultPrivilegeToken = token 
  }
 
  /** Post memberSet for PA */
  def postBasicMemberSet(inference: Safelang): String = {
    simpleRemoteCall(inference, "postUserGroupMemberSet")
  } 
}

class CPStub(
    pid: String,                                                  // Principal id: hash of public key
    canonicalName: String,                                        // Symbolic name of the principal
    serverJvm: String,                                            // Address of the principal's JVM
    subjectSetTokens: Seq[String],                                // Tokens of his subject sets 
    keyFile: String,                                              // pem filepath
    private var zoneName: String = null,                          // Zone name of the aggregate
    private var zoneId: String = null                             // Zone ID
    ) extends PrincipalStub(pid, canonicalName, serverJvm, subjectSetTokens, keyFile) {

  def this(keyFile: String, cn: String, serverJvm: String) {
    this(PrincipalStub.pidFromFile(keyFile), cn, serverJvm, Seq[String](), keyFile) 
  } 

  def this(p: PrincipalStub) {
    this(p.getPid, p.getCN, p.getJvm, p.getSubjectSetTokens, p.getKeyFile)
  }

  /** Post zone for CP */
  def postZone(inference: Safelang, n: String): String = {
    zoneName = n
    zoneId = pid + ":" + zoneName
    simpleRemoteCall(inference, "postZoneSet", args=Seq(zoneId))
  }

  def getZoneName: String = zoneName
  def getZoneId: String = zoneId
}

/**
 * Complete benchmark for SafeGeni
 * @param concurrency  number of concurrent requests
 */
class GeniBench(concurrency: Int, jvmmapFile: String, slangManager: SafelangManager) 
    extends GeniOperation with QueryTableLoader with LazyLogging {

  //val testingCacheJvm = "10.103.0.11:7777"  // for cache testing; used in GeniOperation 
  //val defaultJvm = "152.3.136.26:7777" 
  val inferenceQ: LinkedBlockingQueue[Safelang] = buildSafelangQueue(slangManager, concurrency)

  def initPrincipals(inferenceQ: LinkedBlockingQueue[Safelang], jvmmapFile: String): Unit = {  
    allPrincipals = loadPrincipals(jvmmapFile)

    val _inferenceQ: LinkedBlockingQueue[Safelang] = buildSafelangQueue(slangManager, 30)

    var c = 0
    for(p <- allPrincipals) {
      val inference = _inferenceQ.poll(timeout, TimeUnit.SECONDS)
      val future = Future {
        p.postIdSet(inference)
        p.postSubjectSetAndGetToken(inference)
        c += 1
        if((c % 1000) == 0)  {
          println(s"c: ${c}    _inferenceQ.size=${_inferenceQ.size}")
        }
      }
      future.onComplete {
        case Success(res) =>
        logger.info(s"_inferenceQ.size:${_inferenceQ.size}")
        _inferenceQ.offer(inference, timeout, TimeUnit.SECONDS)  // Release the inference engine

        case Failure(e) =>
        logger.error(s"${e}")
        _inferenceQ.offer(inference, timeout, TimeUnit.SECONDS)
      }
    }

    while(_inferenceQ.size < 30) { // wait until finish
    }

    //val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS) // This is single threaded
    //buildIDSet(inference, allPrincipals)
    //buildSubjectSet(inference, allPrincipals)
    //inferenceQ.offer(inference, timeout, TimeUnit.SECONDS) // release

    geniroot = getMatchingPrincipals("geniroot.pem".r, allPrincipals) match {
      // If multiple, take the first as geniroot
      case principals: ListBuffer[PrincipalStub] if principals.length >= 1 => principals(0)  
      case _ => throw new Exception("pem file for geniroot not found")
    }
    allPrincipals -= geniroot  // exclude geniroot from allPrincipals
    allPrincipals = scala.util.Random.shuffle(allPrincipals) // Shuffle principals at different jvms
    logger.info("========================== All principals ==========================")
    allPrincipals.foreach{ p => logger.info(s"${p.getPid}      ${p.getJvm}") }
    logger.info("====================================================================")
  }
 
  /** 
   * @DeveloperAPI 
   * simple test for post 
   */ 
  def test(): Unit = {
    initPrincipals(inferenceQ, jvmmapFile)  // Initialize allPrincipals and geniroot 

    val op = "updateSubjectSet"
    println(s"\nStart testing?")
    println("=============================")
    scala.io.StdIn.readLine()

    for(i <- 1 to 10) {
      val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
      val future = Future { 
        val env: String = emptyEnvs 
        val token: String = s"token_${i}"
        val args = Seq(token)
        allPrincipals(0).simpleRemoteCall(inference, op, env, args)
      }
      future.onComplete {
        case Success(res) =>
          inferenceQ.offer(inference, timeout, TimeUnit.SECONDS)  // Release the inference engine
          println(s"[test] inferenceQ.size:${inferenceQ.size}")
        case Failure(e) =>
          println("[" + Console.RED + s"${op} ${i} failed: ${e.printStackTrace}" + Console.RESET + "]")      
          logger.error("[" + Console.RED + s"${op} ${i} failed: ${e.printStackTrace}" + Console.RESET + "]")
          inferenceQ.offer(inference, timeout, TimeUnit.SECONDS)
          throw UnSafeException(s"${op} ${i} failed: ${e.printStackTrace}") 
      }
    }
  }

  /** Perform necessary ops to set up the role for each principal */
  def prime(): Unit = {
    val prime = Seq("endorseMA", //"endorseMA", "endorseMA",
                    "endorsePA", "endorsePA", //"endorsePA", "endorsePA",
                    "endorseSA", "endorseSA", //"endorseSA", "endorseSA",
                    "endorseCP", "endorseCP",  "endorseCP", "endorseCP",
                    "endorseCP", "endorseCP",  "endorseCP", "endorseCP"
                    ) 
    if(allPrincipals.size < prime.size) {
      throw UnSafeException(
        s"Available principals (${allPrincipals.size}) are less than required authorities (${prime.size})")
    }
    var inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
    for(op <- prime) {
      performOperation(inference, op)
    }
    
    val nonAuthorities = allPrincipals.size - prime.size
    for(i <- 0 to nonAuthorities-1) { // half and half (PI and user)
      if(i % 2000 == 0) { println(s"Priming non-authority $i") }
      if(i < (nonAuthorities/2)) {
        performOperation(inference, "endorsePI")
      } else {
        performOperation(inference, "endorseUser")
      } 
    } 

    // A small set of init ops
    //for(i <- 0 to 20) {
    //  val op = opDist(i % 4)  // round-robin the first four
    //  performOperation(inference, op)
    //}

    inferenceQ.offer(inference, timeout, TimeUnit.SECONDS)

    logger.info(s"\nPrime is done!")
    logger.info("=============================")
    println(s"\nPrime is done!")
    println("=============================")
    scala.io.StdIn.readLine()
  }


  /** 
   * Replay queries that are previously recorded
   */
  def replayQueries(): Unit = {
    loadRecords()
    println(s"Loading records completes")

    logger.info(s"\nTo replay operations..." + 
      s"simpleDelegate(${simpleDelegateRecords.length})   delegateThenQuery(${delegateThenQueryRecords.length})    " +
      s"simpleRemoteCall(${simpleRemoteCallRecords.length})   remoteCallToServer(${remoteCallToServerRecords.length})")
    logger.info("=============================")
    println(s"\nTo replay operations..." + 
      s"simpleDelegate(${simpleDelegateRecords.length})   delegateThenQuery(${delegateThenQueryRecords.length})    " +
      s"simpleRemoteCall(${simpleRemoteCallRecords.length})   remoteCallToServer(${remoteCallToServerRecords.length})")
    println("=============================")
    scala.io.StdIn.readLine()

    initPerfMonitor(allowAutoPerfStats = false) 
    var opcount = 0

    for(round <- 0 to 3) {
      println(s"Replay round: ${round}")
      opcount = 0
      while(opcount < remoteCallToServerRecords.length) {
        val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
        replayOpAsync(inference, "remoteCallToServer", opcount)
        opcount += 1
        if((opcount % 1000) == 0)  {
          println(s"opcount: ${opcount}    inferenceQ.size=${inferenceQ.size}")
          // Get perfStats async
          val future = Future { 
            processStatsNow()
          }
        }
      }
    }
  }


  /** 
   * Replay queries with param lists stored in a table
   */
  def replayParametrizedQueries(): Unit = {
    loadTables("independent-contexts")
    println("=============================")
    scala.io.StdIn.readLine()

    initPerfMonitor(allowAutoPerfStats = false) 
    setTestingCacheJvm("10.103.0.11:7777")
    var opcount = 0

    for(round <- 0 to 3) {
      println(s"Replay round: ${round}")
      opcount = 0
      while(opcount < createSliverTable.length) {
        val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
        replayParamQueryAsync(inference, "createSliver", opcount)
        opcount += 1
        if((opcount % 1000) == 0)  {
          println(s"opcount: ${opcount}    inferenceQ.size=${inferenceQ.size}")
          // Get perfStats async
          val future = Future { 
            processStatsNow()
          }
        }
      }
    }
  }

  /** 
   * Replay parametrized queries stored in a table
   * Queries are clustered in groups
   * Within the same group, delegation chains have overlapp
   */
  def replayParametrizedQueriesOverlappingChain(): Unit = {
    loadTables("overlap-contexts-riak-34")
    val numgroups = 100
    val pergroup = createSliverTable.length / numgroups 
    println(s"Queries per group: ${pergroup}    numgroups=${numgroups}")
    println("=============================")
    scala.io.StdIn.readLine()

    initPerfMonitor(allowAutoPerfStats = false) 
    setTestingCacheJvm("10.103.0.11:7777") 

    var opcount = 0
    for(i <- 0 to pergroup - 1) {
      for(j <- 0 to numgroups -1) {
        val opidx = j*pergroup + i
        val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
        replayParamQueryAsync(inference, "createProject", opidx)
        opcount += 1
        if((opcount % 1000) == 0)  {
          println(s"opcount: ${opcount}    inferenceQ.size=${inferenceQ.size}")
          // Get perfStats async
          val future = Future { 
            processStatsNow()
          }
        }
      }
    }
  }

  /**
   * Testing cache from with query replay
   * Test with a cold start, with a GENI workload mix
   */
  def testCacheWithReplay(): Unit = { 
    initPrincipals(inferenceQ, jvmmapFile)  // Initialize allPrincipals and geniroot 
    prime()
    var opcount = 0
    val t0 = System.currentTimeMillis 

    // set up perf monitor
    initPerfMonitor(allowAutoPerfStats = false) // Disable auto perf stats
    setTestingCacheJvm("10.103.0.11:7777")

    // Set up op distribution
    //opShares = Seq(2, 1, 2, 1, 0, 0, 0, 0, 0, 0)
    setOpCDF(Seq(1, 10, 1, 10, 0, 0, 0, 0, 0, 0))

    // 6 sets of put-only mix
    while(opcount <= 6000) {
      val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
      var op = getRandomOp()   
      opAsync(op, inference)
      opcount += 1
      if((opcount % 1000) == 0)  {
        println(s"opcount: ${opcount}    inferenceQ.size=${inferenceQ.size}    op: ${op}")
      }
    }
    // Get perfStats async
    val future = Future { 
      processStatsNow()
    }
 

    // Reset op distribution
    setOpCDF(Seq(0, 0, 0, 0, 0, 1, 0, 1, 0, 0))
    //// Direct to the same server
    //setOpCDF(Seq(0, 0, 0, 0, 1, 0, 1, 0, 0, 0))
    opcount = 0

    // 3 sets of query-only mix
    while(opcount <= 20000) {
      val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
      var op = getRandomOp()   
      opAsync(op, inference, torecord=true)
      opcount += 1
      if((opcount % 1000) == 0)  {
        println(s"opcount: ${opcount}    inferenceQ.size=${inferenceQ.size}    op: ${op}")
        // Get perfStats async
        val future = Future { 
          processStatsNow()
        }
      }
    }

    persistRecords()
    println(s"Persisting records completes")

    logger.info(s"\nTo replay operations..." + 
      s"simpleDelegate(${simpleDelegateRecords.length})   delegateThenQuery(${delegateThenQueryRecords.length})    " +
      s"simpleRemoteCall(${simpleRemoteCallRecords.length})   remoteCallToServer(${remoteCallToServerRecords.length})")
    logger.info("=============================")
    println(s"\nTo replay operations..." + 
      s"simpleDelegate(${simpleDelegateRecords.length})   delegateThenQuery(${delegateThenQueryRecords.length})    " +
      s"simpleRemoteCall(${simpleRemoteCallRecords.length})   remoteCallToServer(${remoteCallToServerRecords.length})")
    println("=============================")
    scala.io.StdIn.readLine()

    opcount = 0

    // 3 sets of query-only mix
    while(opcount < remoteCallToServerRecords.length) {
      val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
      replayOpAsync(inference, "remoteCallToServer", opcount)
      opcount += 1
      if((opcount % 1000) == 0)  {
        println(s"opcount: ${opcount}    inferenceQ.size=${inferenceQ.size}")
        // Get perfStats async
        val future = Future { 
          processStatsNow()
        }
      }
    }

    // Reset op distribution
    setOpCDF(Seq(0, 0, 0, 0, 0, 4, 0, 4, 1, 1))
    opcount = 0

    logger.info(s"\nUpdate+query operations...")
    logger.info("=============================")
    println(s"\nUpdate+query operations...")
    println("=============================")
    scala.io.StdIn.readLine()

    // 2 sets of query-update mix
    while(opcount <= 2000) {
    //while(opcount <= -1) {
      val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
      var op = getRandomOp()   
      opAsync(op, inference)
      opcount += 1
      if((opcount % 1000) == 0)  {
        println(s"opcount: ${opcount}    inferenceQ.size=${inferenceQ.size}    op: ${op}")
        // Get perfStats async
        val future = Future { 
          processStatsNow()
        }
      }
    }

    // Wait for the responses of the last batch
    while(inferenceQ.size < concurrency) {
    }
    val runtime = System.currentTimeMillis - t0    
    val c = effectiveOpcount.get()
    val f = failedOpcount.get()
    val s = successfulOpcount.get()
    val throughput = c*1000 / runtime
    println(s"Overall: $c in $runtime ms ($throughput ops/sec)   $f ops failed   $s ops succeeded")
    slangPerfCollector.persist("slang-perf-overall") // output perf measures
  }


  /**
   * Testing cache from a cold start, with a GENI workload mix
   */
  def testCache(): Unit = { 
    initPrincipals(inferenceQ, jvmmapFile)  // Initialize allPrincipals and geniroot 

    prime()
    var opcount = 0
    val t0 = System.currentTimeMillis 

    // set up perf monitor
    initPerfMonitor(allowAutoPerfStats = false) // Disable auto perf stats
    setTestingCacheJvm("10.103.0.11:7777")

    // Set up op distribution
    //opShares = Seq(2, 1, 2, 1, 0, 0, 0, 0, 0, 0)
    setOpCDF(Seq(1, 10, 1, 10, 0, 0, 0, 0, 0, 0))

    // 6 sets of put-only mix
    while(opcount <= 6000) {
      val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
      var op = getRandomOp()   
      opAsync(op, inference)
      opcount += 1
      if((opcount % 1000) == 0)  {
        println(s"opcount: ${opcount}    inferenceQ.size=${inferenceQ.size}    op: ${op}")
      }
    }
    // Get perfStats async
    val future = Future { 
      processStatsNow()
    }
 

    // Reset op distribution
    setOpCDF(Seq(0, 0, 0, 0, 0, 1, 0, 1, 0, 0))
    //// Direct to the same server
    //setOpCDF(Seq(0, 0, 0, 0, 1, 0, 1, 0, 0, 0))
    opcount = 0

    // 3 sets of query-only mix
    while(opcount <= 20000) {
      val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
      var op = getRandomOp()   
      opAsync(op, inference)
      opcount += 1
      if((opcount % 1000) == 0)  {
        println(s"opcount: ${opcount}    inferenceQ.size=${inferenceQ.size}    op: ${op}")
        // Get perfStats async
        val future = Future { 
          processStatsNow()
        }
      }
    }

    // Reset op distribution
    setOpCDF(Seq(0, 0, 0, 0, 0, 4, 0, 4, 1, 1))
    opcount = 0

    // 2 sets of query-update mix
    while(opcount <= 2000) {
    //while(opcount <= -1) {
      val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
      var op = getRandomOp()   
      opAsync(op, inference)
      opcount += 1
      if((opcount % 1000) == 0)  {
        println(s"opcount: ${opcount}    inferenceQ.size=${inferenceQ.size}    op: ${op}")
        // Get perfStats async
        val future = Future { 
          processStatsNow()
        }
      }
    }

    // Wait for the responses of the last batch
    while(inferenceQ.size < concurrency) {
    }
    val runtime = System.currentTimeMillis - t0    
    val c = effectiveOpcount.get()
    val f = failedOpcount.get()
    val s = successfulOpcount.get()
    val throughput = c*1000 / runtime
    println(s"Overall: $c in $runtime ms ($throughput ops/sec)   $f ops failed   $s ops succeeded")
    slangPerfCollector.persist("slang-perf-overall") // output perf measures
  }

  /** Test with federation workload */
  def testFederationOps(): Unit = { 
    initPrincipals(inferenceQ, jvmmapFile)  // Initialize allPrincipals and geniroot 
    val opDist = Seq("queryThenCreateProject", "delegateProjectMembership", "queryThenCreateSlice", 
                     "delegateSliceControl", "createSlice", "createSliver", 
                     "queryThenCreateStitchport",
                     "addSliverAcl", "queryThenInstallSliverAcl", "accessSliver", "stitchSlivers"
                     // "postAdjacentCP", "queryThenCreateSliver"
                    ) // Excluded the ops that target a specific server (for testing cache)

    var opcount = 0
    val t0 = System.currentTimeMillis 
    initPerfMonitor(allowAutoPerfStats = false) 

    val prime = Seq("endorseMA", //"endorseMA", "endorseMA",
                    "endorsePA", "endorsePA", //"endorsePA", "endorsePA",
                    "endorseSA", "endorseSA", //"endorseSA", "endorseSA",
                    "endorseCP", "endorseCP",  "endorseCP", "endorseCP",
                    "endorseCP", "endorseCP",  "endorseCP", "endorseCP", "endorseCP", "endorseCP"
                    ) 
    if(allPrincipals.size < prime.size) {
      throw UnSafeException(
        s"Available principals (${allPrincipals.size}) are less than required authorities (${prime.size})")
    }
    var inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
    for(op <- prime) {
      performOperation(inference, op)
    }
    opAsync("postAdjacentCP", inference)
    //inferenceQ.offer(inference, timeout, TimeUnit.SECONDS)
    logger.info(s"\nPrime is done!")
    logger.info("=============================")
    println(s"\nPrime is done!")
    println("=============================")
    scala.io.StdIn.readLine()

    slangPerfCollector.persist("slang-perf-Prime", allRecords=false) // output perf measures
    println("=============================")
    scala.io.StdIn.readLine()


    /* Create and name PI/user */ 
    val nonAuthorities = allPrincipals.size - prime.size
    val a1_t0 = System.nanoTime
    for(i <- 0 to nonAuthorities-1) { // half and half (PI and user)
      val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
      if(i % 2000 == 0) { println(s"Priming non-authority $i") }
      if(i < (nonAuthorities/2)) {
        opAsync("endorsePI", inference)
      } else {
        opAsync("endorseUser", inference)
      } 
    } 
    var runtime = System.nanoTime - a1_t0
    var effectiveOps = effectiveOpcount.get()
    var numOps = effectiveOps  - lastEffectiveOpcount
    var throughput = numOps * 1000000000L / runtime
    lastEffectiveOpcount = effectiveOps
    slangPerfCollector.addThroughput(throughput, s"${effectiveOps}:  ${numOps} in ${runtime} microseconds")

    logger.info(s"\nA1 is done!")
    logger.info("=============================")
    println(s"\nA1 is done!")
    println("=============================")
    scala.io.StdIn.readLine()

    slangPerfCollector.persist("slang-perf-A1", allRecords=false) // output perf measures
    println("=============================")
    scala.io.StdIn.readLine()


    /* Query user names */
    val a2p1_t0 = System.nanoTime
    for(i <- 0 to endorsedUsers.size -1) { 
      val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
      val op = "queryName"
      //println(s"\nNext op $op  $i...")
      //println("=============================")
      //scala.io.StdIn.readLine()

      opAsync(op, inference, torecord=false, objectType="User", objectIdx=i, rootIdx=0)     
      opcount += 1
      if((opcount % 1000) == 0)  {
        logger.info(s"opcount: ${opcount}   inferenceQ.size=${inferenceQ.size}    op:${op}")
      }
    }

    runtime = System.nanoTime - a2p1_t0
    effectiveOps = effectiveOpcount.get()
    numOps = effectiveOps  - lastEffectiveOpcount
    throughput = numOps * 1000000000L / runtime
    lastEffectiveOpcount = effectiveOps
    slangPerfCollector.addThroughput(throughput, s"${effectiveOps}:  ${numOps} in ${runtime} microseconds")


    logger.info(s"\nUser name queries (A2 part1) are done!")
    logger.info("=============================")
    println(s"\nUser name queries (A2 part1) are done!")
    println("=============================")
    scala.io.StdIn.readLine()

    slangPerfCollector.persist("slang-perf-A2P1", allRecords=false) // output perf measures
    println("=============================")
    scala.io.StdIn.readLine()


    /* Query PI names */
    val a2p2_t0 = System.nanoTime
    for(i <- 0 to endorsedPIs.size -1) { 
      val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
      val op = "queryName"
      opAsync(op, inference, torecord=false, objectType="PI", objectIdx=i, rootIdx=0)     
      opcount += 1
      if((opcount % 1000) == 0)  {
        logger.info(s"opcount: ${opcount}   inferenceQ.size=${inferenceQ.size}    op:${op}")
      }
    }

    runtime = System.nanoTime - a2p2_t0
    effectiveOps = effectiveOpcount.get()
    numOps = effectiveOps  - lastEffectiveOpcount
    throughput = numOps * 1000000000L / runtime
    lastEffectiveOpcount = effectiveOps
    slangPerfCollector.addThroughput(throughput, s"${effectiveOps}:  ${numOps} in ${runtime} microseconds")


    logger.info(s"\nPI name queries (A2 part2) are done!")
    logger.info("=============================")
    println(s"\nPI name queries (A2 part2) are done!")
    println("=============================")
    scala.io.StdIn.readLine()

    slangPerfCollector.persist("slang-perf-A2P2", allRecords=false) // output perf measures
    println("=============================")
    scala.io.StdIn.readLine()


    /* Create and name projects */
    val b1p1_t0 = System.nanoTime 
    val numProjects = if(endorsedUsers.size < endorsedPIs.size) endorsedUsers.size  else endorsedPIs.size
    for(i <- 0 to numProjects -1) { 
      val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
      val op = "queryThenCreateProject"
      opAsync(op, inference, torecord=false, objectType="", objectIdx=i, rootIdx=0)     
      opcount += 1
      if((opcount % 1000) == 0)  {
        logger.info(s"opcount: ${opcount}   inferenceQ.size=${inferenceQ.size}    op:${op}")
      }
    }

    runtime = System.nanoTime - b1p1_t0
    effectiveOps = effectiveOpcount.get()
    numOps = effectiveOps  - lastEffectiveOpcount
    throughput = numOps * 1000000000L / runtime
    lastEffectiveOpcount = effectiveOps
    slangPerfCollector.addThroughput(throughput, s"${effectiveOps}:  ${numOps} in ${runtime} microseconds")


    logger.info(s"\nCreating and naming projects (B1 part1) is done!")
    logger.info("=============================")
    println(s"\nCreating and naming projects (B1 part1) is done!")
    println("=============================")
    scala.io.StdIn.readLine()

    slangPerfCollector.persist("slang-perf-B1P1", allRecords=false) // output perf measures
    println("=============================")
    scala.io.StdIn.readLine()


    /* Delegate projects */
    val b1p2_t0 = System.nanoTime
    for(i <- 0 to numProjects -1) { 
      val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
      val op = "delegateProjectMembership"
      opAsync(op, inference, torecord=false, objectType="", objectIdx=i, rootIdx=0)     
      opcount += 1
      if((opcount % 1000) == 0)  {
        logger.info(s"opcount: ${opcount}   inferenceQ.size=${inferenceQ.size}    op:${op}")
      }
    }

    runtime = System.nanoTime - b1p2_t0
    effectiveOps = effectiveOpcount.get()
    numOps = effectiveOps  - lastEffectiveOpcount
    throughput = numOps * 1000000000L / runtime
    lastEffectiveOpcount = effectiveOps
    slangPerfCollector.addThroughput(throughput, s"${effectiveOps}:  ${numOps} in ${runtime} microseconds")


    logger.info(s"\nDelegating projects (B1 part2) is done!")
    logger.info("=============================")
    println(s"\nDelegating projects (B1 part2) is done!")
    println("=============================")
    scala.io.StdIn.readLine()

    slangPerfCollector.persist("slang-perf-B1P2", allRecords=false) // output perf measures
    println("=============================")
    scala.io.StdIn.readLine()



    /* Query project names */
    val b2_t0 = System.nanoTime
    for(i <- 0 to usersInProject.keySet.size -1) {
      val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
      val op = "queryName"
      opAsync(op, inference, torecord=false, objectType="Project", objectIdx=i, rootIdx=0)
      opcount += 1
      if((opcount % 1000) == 0)  {
        logger.info(s"opcount: ${opcount}   inferenceQ.size=${inferenceQ.size}    op:${op}")
      }
    }

    runtime = System.nanoTime - b2_t0
    effectiveOps = effectiveOpcount.get()
    numOps = effectiveOps  - lastEffectiveOpcount
    throughput = numOps * 1000000000L / runtime
    lastEffectiveOpcount = effectiveOps
    slangPerfCollector.addThroughput(throughput, s"${effectiveOps}:  ${numOps} in ${runtime} microseconds")


    logger.info(s"\nProject name queries (B2) are done!")
    logger.info("=============================")
    println(s"\nProject name queries (B2) are done!")
    println("=============================")
    scala.io.StdIn.readLine()

    slangPerfCollector.persist("slang-perf-B2", allRecords=false) // output perf measures
    println("=============================")
    scala.io.StdIn.readLine()



    /* Create and name slices */
    val c1_t0 = System.nanoTime
    val numSlices = 5000 
    for(i <- 0 to numSlices -1) { 
      val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
      val op = "queryThenCreateSlice"
      opAsync(op, inference, torecord=false)     
      opcount += 1
      if((opcount % 1000) == 0)  {
        logger.info(s"opcount: ${opcount}   inferenceQ.size=${inferenceQ.size}    op:${op}")
      }
    }

    runtime = System.nanoTime - c1_t0
    effectiveOps = effectiveOpcount.get()
    numOps = effectiveOps  - lastEffectiveOpcount
    throughput = numOps * 1000000000L / runtime
    lastEffectiveOpcount = effectiveOps
    slangPerfCollector.addThroughput(throughput, s"${effectiveOps}:  ${numOps} in ${runtime} microseconds")

    logger.info(s"\nCreating and naming slices (C1) is done!")
    logger.info("=============================")
    println(s"\nCreating and naming slices (C1) is done!")
    println("=============================")
    scala.io.StdIn.readLine()

    slangPerfCollector.persist("slang-perf-C1", allRecords=false) // output perf measures
    println("=============================")
    scala.io.StdIn.readLine()

    //assert(usersInSlice.keySet.size == numSlices, s"${usersInSlice.keySet.size}, ${numSlices},  ${usersInSlice.keySet}")
    
    if(usersInSlice.keySet.size != numSlices) {
      println(s"usersInSlice.keySet.size=${usersInSlice.keySet.size};  numSlices=${numSlices}")
    }

    /* Query slice names */
    val c2_t0 = System.nanoTime
    for(i <- 0 to usersInSlice.keySet.size -1) {
      val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
      val op = "queryName"
      opAsync(op, inference, torecord=false, objectType="Slice", objectIdx=i, rootIdx=0)
      opcount += 1
      if((opcount % 1000) == 0)  {
        logger.info(s"opcount: ${opcount}   inferenceQ.size=${inferenceQ.size}    op:${op}")
      }
    }

    runtime = System.nanoTime - c2_t0
    effectiveOps = effectiveOpcount.get()
    numOps = effectiveOps  - lastEffectiveOpcount
    throughput = numOps * 1000000000L / runtime
    lastEffectiveOpcount = effectiveOps
    slangPerfCollector.addThroughput(throughput, s"${effectiveOps}:  ${numOps} in ${runtime} microseconds")


    logger.info(s"\nSlice name queries (C2) are done!")
    logger.info("=============================")
    println(s"\nSlice name queries (C2) are done!")
    println("=============================")
    scala.io.StdIn.readLine()

    slangPerfCollector.persist("slang-perf-C2", allRecords=false) // output perf measures
    println("=============================")
    scala.io.StdIn.readLine()



    //assert(false, s"endorsedCPs.size=${endorsedCPs.size}     numSlices=${numSlices}")
    //var count = 0 
    /* Install slice name on every aggregate */
    val c3_t0 = System.nanoTime
    for(j <- 0 to endorsedCPs.size-1) { 
      for(i <- 0 to numSlices -1) { 
        val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
        val op = "delegateObject"
        opAsync(op, inference, torecord=false, objectType="Slice", objectIdx=i, rootIdx=j)     
        opcount += 1
        if((opcount % 1000) == 0)  {
          logger.info(s"opcount: ${opcount}   inferenceQ.size=${inferenceQ.size}    op:${op}")
        }
        //count += 1
        //if(count % 100 == 0) {
        //  println(s"Next op...   j=${j}; i=${i}; count=${count}")
        //  println("=============================")
        //  scala.io.StdIn.readLine()
        //}
      }
    }

    runtime = System.nanoTime - c3_t0
    effectiveOps = effectiveOpcount.get()
    numOps = effectiveOps  - lastEffectiveOpcount
    throughput = numOps * 1000000000L / runtime
    lastEffectiveOpcount = effectiveOps
    slangPerfCollector.addThroughput(throughput, s"${effectiveOps}:  ${numOps} in ${runtime} microseconds")


    logger.info(s"\nInstalling slice name on aggregates (C3) is done!")
    logger.info("=============================")
    println(s"\nInstalling slice name on aggregates (C3) is done!")
    println("=============================")
    scala.io.StdIn.readLine()

    slangPerfCollector.persist("slang-perf-C3", allRecords=false) // output perf measures
    println("=============================")
    scala.io.StdIn.readLine()


    /////* Create slivers */
    //val d1_t0 = System.nanoTime
    //for(j <- 0 to 7) { 
    //  for(i <- 0 to numSlices -1) { 
    //    val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
    //    val op = "queryThenCreateStitchport"
    //    opAsync(op, inference, torecord=false, objectType="Slice", objectIdx=i, rootIdx=j)     
    //    opcount += 1
    //    if((opcount % 1000) == 0)  {
    //      logger.info(s"opcount: ${opcount}   inferenceQ.size=${inferenceQ.size}    op:${op}")
    //    }
    //  }
    //}

    //runtime = System.nanoTime - d1_t0
    //effectiveOps = effectiveOpcount.get()
    //numOps = effectiveOps  - lastEffectiveOpcount
    //throughput = numOps * 1000000000L / runtime
    //lastEffectiveOpcount = effectiveOps
    //slangPerfCollector.addThroughput(throughput, s"${effectiveOps}:  ${numOps} in ${runtime} microseconds")


    //logger.info(s"\nInstalling slice name on aggregates (D1) is done!")
    //logger.info("=============================")
    //println(s"\nInstalling slice name on aggregates (D1) is done!")
    //println("=============================")
    //scala.io.StdIn.readLine()
    //
    //slangPerfCollector.persist("slang-perf-D1", allRecords=false) // output perf measures
    //println("=============================")
    //scala.io.StdIn.readLine()


    ////assert(sliversInSlice.keySet.size == numSlices, s"${sliversInSlice.keySet.size}, ${numSlices},  ${sliversInSlice.keySet}")
    ///* Intraslie stitching */
    //val d2_t0 = System.nanoTime
    //for(s <- 0 to numSlices -1) { 
    //  for(i <- 0 to 7) {
    //    for(j <- i+1 to 7) {
    //      val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
    //      val op = "stitchIntraSlice"
    //      opAsync(op, inference, torecord=false, objectType=s.toString, objectIdx=i, rootIdx=j)     
    //      opcount += 1
    //      if((opcount % 1000) == 0)  {
    //        logger.info(s"opcount: ${opcount}   inferenceQ.size=${inferenceQ.size}    op:${op}")
    //      }
    //    }
    //  }
    //}
    //
    //runtime = System.nanoTime - d2_t0
    //effectiveOps = effectiveOpcount.get()
    //numOps = effectiveOps  - lastEffectiveOpcount
    //throughput = numOps * 1000000000L / runtime
    //lastEffectiveOpcount = effectiveOps
    //slangPerfCollector.addThroughput(throughput, s"${effectiveOps}:  ${numOps} in ${runtime} microseconds")

    //logger.info(s"\nInstalling slice name on aggregates (D2) is done!")
    //logger.info("=============================")
    //println(s"\nInstalling slice name on aggregates (D2) is done!")
    //println("=============================")
    //scala.io.StdIn.readLine()
    //
    //slangPerfCollector.persist("slang-perf-D2", allRecords=false) // output perf measures
    //println("=============================")
    //scala.io.StdIn.readLine()


    /* Create named stitchports */ 
    val e1_t0 = System.nanoTime
    for(i <- 0 to numSlices -1) { 
      val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
      val op = "queryThenCreateNamedStitchport"
      val j = (i+1) % numSlices
      opAsync(op, inference, torecord=false, objectType="Slice", objectIdx=i, rootIdx=j)     
      opcount += 1
      if((opcount % 1000) == 0)  {
        logger.info(s"opcount: ${opcount}   inferenceQ.size=${inferenceQ.size}    op:${op}")
      }
    }

    runtime = System.nanoTime - e1_t0
    effectiveOps = effectiveOpcount.get()
    numOps = effectiveOps  - lastEffectiveOpcount
    throughput = numOps * 1000000000L / runtime
    lastEffectiveOpcount = effectiveOps
    slangPerfCollector.addThroughput(throughput, s"${effectiveOps}:  ${numOps} in ${runtime} microseconds")

    logger.info(s"\nCreating named stitchport (E1) is done!")
    logger.info("=============================")
    println(s"\nCreating named stitchport (E1) is done!")
    println("=============================")
    scala.io.StdIn.readLine()

    slangPerfCollector.persist("slang-perf-E1", allRecords=false) // output perf measures
    println("=============================")
    scala.io.StdIn.readLine()

    /* Interslice stitching */ 
    val e2_t0 = System.nanoTime
    val numStitches = 5000
    for(i <- 0 to numStitches -1) { 
      val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
      val op = "lookupThenStitch"
      opAsync(op, inference, torecord=false)     

      //println(s"\nNext op...")
      //println("=============================")
      //scala.io.StdIn.readLine()

      opcount += 1
      if((opcount % 1000) == 0)  {
        logger.info(s"opcount: ${opcount}   inferenceQ.size=${inferenceQ.size}    op:${op}")
      }
    }

    runtime = System.nanoTime - e2_t0
    effectiveOps = effectiveOpcount.get()
    numOps = effectiveOps  - lastEffectiveOpcount
    throughput = numOps * 1000000000L / runtime
    lastEffectiveOpcount = effectiveOps
    slangPerfCollector.addThroughput(throughput, s"${effectiveOps}:  ${numOps} in ${runtime} microseconds")



    logger.info(s"\nInterslice stitching (E2) is done!")
    logger.info("=============================")
    println(s"\nInterslice stitching (E2) is done!")
    println("=============================")
    scala.io.StdIn.readLine()

    slangPerfCollector.persist("slang-perf-E2", allRecords=false) // output perf measures
    println("=============================")
    scala.io.StdIn.readLine()

    // Wait for the responses of the last batch
    while(inferenceQ.size < concurrency) {
    }
    runtime = System.currentTimeMillis - t0    
    val c = effectiveOpcount.get()
    val f = failedOpcount.get()
    val s = successfulOpcount.get()
    throughput = c*1000 / runtime
    println(s"Overall: $c in $runtime ms ($throughput ops/sec)   $f ops failed   $s ops succeeded")
    slangPerfCollector.persist("slang-perf-overall") // output perf measures
  }


  /** Test with Random op mix */
  def testRandomOps(): Unit = { 
    initPrincipals(inferenceQ, jvmmapFile)  // Initialize allPrincipals and geniroot 
    val opDist = Seq("queryThenCreateProject", "delegateProjectMembership", "queryThenCreateSlice", 
                     "delegateSliceControl", "createSlice", "createSliver", 
                     "queryThenCreateStitchport",
                     "addSliverAcl", "queryThenInstallSliverAcl", "accessSliver", "stitchSlivers"
                     // "postAdjacentCP", "queryThenCreateSliver"
                    ) // Excluded the ops that target a specific server (for testing cache)
    prime()
    var opcount = 0
    val t0 = System.currentTimeMillis 
    initPerfMonitor()
    while(opcount <= 10000) {
      logger.info(s"[run while] inferenceQ.size=${inferenceQ.size}")
      val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)

      //var op = opDist(opDist.length - 1)
      var op = getRandomOp()
      if(opcount == 0) {
        op = "postAdjacentCP"
      } else if(opcount < 200) { // avoid subcontexts that are too large
        op = opDist(opcount % opDist.length)
      } 

      //println(s"\nnext OP: ${op}...")
      //println("=============================")
      //scala.io.StdIn.readLine()
 
      opAsync(op, inference)
      opcount += 1
      if((opcount % 1000) == 0)  {
        logger.info(s"opcount: ${opcount}   inferenceQ.size=${inferenceQ.size}    op:${op}")
      }
    }
    // Wait for the responses of the last batch
    while(inferenceQ.size < concurrency) {
    }
    val runtime = System.currentTimeMillis - t0    
    val c = effectiveOpcount.get()
    val f = failedOpcount.get()
    val s = successfulOpcount.get()
    val throughput = c*1000 / runtime
    println(s"Overall: $c in $runtime ms ($throughput ops/sec)   $f ops failed   $s ops succeeded")
    slangPerfCollector.persist("slang-perf-overall") // output perf measures
  }

  def replayParamQueryAsync(inference: Safelang, op: String, idx: Int): Unit = {
    val future = Future {  
      replayParamQuery(inference, op, idx)
    }
    future.onComplete {
      case Success(res) =>
        //println(s"[opAsync] ${op} COMPLETES   inferenceQ.size:${inferenceQ.size}")
        logger.info(s"[replayParamQueryAsync] ${op} COMPLETES   inferenceQ.size:${inferenceQ.size}")
        inferenceQ.offer(inference, timeout, TimeUnit.SECONDS)  // Release the inference engine
        if(res == true) { // An effective op and it succeeded
          opSuccessHandler(opcountMap(op))
        } else { // A non-effective op
          // do nothing
        }
      case Failure(e) =>
        val f = failedOpcount.incrementAndGet()
        //println("[" + Console.RED + s"${op} failed: ${e.printStackTrace}     effectiveOpcount: ${effectiveOpcount.get()}     failedops: ${f}" + Console.RESET + "]")      
        logger.error("[" + Console.RED + s"${op} failed: ${e.printStackTrace}      effectiveOpcount: ${effectiveOpcount.get()}  failedops: ${f}" + Console.RESET + "]")
        inferenceQ.offer(inference, timeout, TimeUnit.SECONDS)
        opFailureHandler()
        throw UnSafeException(s"${op} failed: ${e.printStackTrace}") 
    }
  }



  def replayOpAsync(inference: Safelang, optype: String, idx: Int): Unit = {
    val op = optype match {
      case "simpleDelegate" => simpleDelegateRecords(idx)._1
      case "delegateThenQuery" => delegateThenQueryRecords(idx)._1
      case "simpleRemoteCall" => simpleRemoteCallRecords(idx)._2
      case "remoteCallToServer" => remoteCallToServerRecords(idx)._2
      case _ => throw UnSafeException(s"Invalid optype: ${optype}")
    }
    val future = Future {  
      replayOperation(inference, optype, idx)
    }
    future.onComplete {
      case Success(res) =>
        //println(s"[opAsync] ${op} COMPLETES   inferenceQ.size:${inferenceQ.size}")
        logger.info(s"[replayOpAsync] ${op} COMPLETES   inferenceQ.size:${inferenceQ.size}")
        inferenceQ.offer(inference, timeout, TimeUnit.SECONDS)  // Release the inference engine
        if(res == true) { // An effective op and it succeeded
          opSuccessHandler(opcountMap(op))
        } else { // A non-effective op
          // do nothing
        }
      case Failure(e) =>
        val f = failedOpcount.incrementAndGet()
        //println("[" + Console.RED + s"${op} failed: ${e.printStackTrace}     effectiveOpcount: ${effectiveOpcount.get()}     failedops: ${f}" + Console.RESET + "]")      
        logger.error("[" + Console.RED + s"${op} failed: ${e.printStackTrace}      effectiveOpcount: ${effectiveOpcount.get()}  failedops: ${f}" + Console.RESET + "]")
        inferenceQ.offer(inference, timeout, TimeUnit.SECONDS)
        opFailureHandler()
        throw UnSafeException(s"${op} failed: ${e.printStackTrace}") 
    }
  }

  def opAsync(op: String, inference: Safelang, torecord: Boolean = false, objectType: String = "User", objectIdx: Int = 0, rootIdx: Int = 0): Unit = {
    val future = Future { 
      performOperation(inference, op, torecord, objectType, objectIdx, rootIdx)
    }
    future.onComplete {
      case Success(res) =>
        //println(s"[opAsync] ${op} COMPLETES   inferenceQ.size:${inferenceQ.size}")
        logger.info(s"[opAsync] ${op} COMPLETES   inferenceQ.size:${inferenceQ.size}")
        inferenceQ.offer(inference, timeout, TimeUnit.SECONDS)  // Release the inference engine
        if(res == true) { // An effective op and it succeeded
          opSuccessHandler(opcountMap(op))
        } else { // A non-effective op
          // do nothing
        }
      case Failure(e) =>
        val f = failedOpcount.incrementAndGet()
        //println("[" + Console.RED + s"${op} failed: ${e.printStackTrace}     effectiveOpcount: ${effectiveOpcount.get()}     failedops: ${f}" + Console.RESET + "]")      
        logger.error("[" + Console.RED + s"${op} failed: ${e.printStackTrace}      effectiveOpcount: ${effectiveOpcount.get()}  failedops: ${f}" + Console.RESET + "]")
        inferenceQ.offer(inference, timeout, TimeUnit.SECONDS)
        opFailureHandler()
        throw UnSafeException(s"${op} failed: ${e.printStackTrace}") 
    }
  }

}
