package safe.programming
package geni

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

/**
 * Complete benchmark for SafeGeni
 * @param concurrency  number of concurrent requests
 */
class GeniBench(concurrency: Int, jvmmapFile: String, slangManager: SafelangManager) extends HarnessPerfStatsController 
    with GeniOperation with LazyLogging {

  val inferenceQ: LinkedBlockingQueue[Safelang] = buildSafelangQueue(slangManager, concurrency)

  def initPrincipals(jvmmapFile: String): Unit = {
    val loadedPrincipals = loadPrincipals(jvmmapFile)

    //val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
    //buildIDSet(inference, loadedPrincipals)
    //buildSubjectSet(inference, loadedPrincipals)
    //inferenceQ.offer(inference, timeout, TimeUnit.SECONDS) // release

    var c = 0
    for(p <- loadedPrincipals) {
      val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
      val future = Future {
        p.postIdSet(inference)
        p.postSubjectSetAndGetToken(inference)
        c += 1
        if((c % 1000) == 0)  {
          println(s"c: ${c}    inferenceQ.size=${inferenceQ.size}")
        }
      }
      future.onComplete {
        case Success(res) =>
        logger.info(s"inferenceQ.size:${inferenceQ.size}")
        inferenceQ.offer(inference, timeout, TimeUnit.SECONDS)  // Release the inference engine
 
        case Failure(e) =>
        logger.error(s"${e}")
        inferenceQ.offer(inference, timeout, TimeUnit.SECONDS)
      }
    }

    while(inferenceQ.size < concurrency) { // wait until finish
    }

    geniroot = getMatchingPrincipals("geniroot.pem".r, loadedPrincipals) match {
      // If multiple, take the first as geniroot
      case principals: ListBuffer[PrincipalStub] if principals.length >= 1 => principals(0)  
      case _ => throw new Exception("pem file for geniroot not found")
    }
    genirootPool += geniroot
    loadedPrincipals -= geniroot  // exclude geniroot from allPrincipals
    allPrincipals ++= scala.util.Random.shuffle(loadedPrincipals) // Shuffle principals at different jvm
    logger.info("========================== All principals ==========================")
    allPrincipals.foreach{ p => logger.info(s"${p.getPid}      ${p.getJvm}") }
    logger.info("====================================================================")

    logger.info(s"\nInitializing principals is done!")
    logger.info("=============================")
    println(s"\nInitializing  principals is done!")
    println("=============================")
    scala.io.StdIn.readLine()
  }
 
  /** 
   * @DeveloperAPI 
   * simple test for post 
   */ 
  def test(): Unit = {
    initPrincipals(jvmmapFile)  // Initialize allPrincipals and geniroot 

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
  def primeNoSeeding(): Unit = {
    val prime = Seq("endorseMA", //"endorseMA", //"endorseMA", "endorseMA",
                    "endorsePA", //"endorsePA", //"endorsePA", "endorsePA",
                    "endorseSA", //"endorseSA", //"endorseSA", "endorseSA",
                    "endorseCP" //"endorseCP"  //, "endorseCP", "endorseCP"
                    ) 
    if(allPrincipals.size < prime.size) {
      throw UnSafeException(
        s"Available principals (${allPrincipals.size}) are less than required authorities (${prime.size})")
    }
    var inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
    for(op <- prime) {
      doOperation(inference, op)
    }
    inferenceQ.offer(inference, timeout, TimeUnit.SECONDS)
    
    val nonAuthorities = allPrincipals.size - prime.size
    for(i <- 0 to nonAuthorities-1) { // half and half (PI and user)
      inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
      if(i % 2000 == 0) { println(s"Priming non-authority $i") }
      //if(i < (nonAuthorities/2)) {
      if(i < 1) {  // 100K PIs; 900K users
        opAsync("endorsePI", inference)
      } else {
        opAsync("endorseUser", inference)
      } 
    } 
    while(inferenceQ.size < concurrency) {
    }

    logger.info(s"\nPrimeNoSeeding is done!")
    logger.info("=============================")
    println(s"\nPrimeNoSeeding is done!")
    println("=============================")
    scala.io.StdIn.readLine()
  }


  /** Perform necessary ops to set up the role for each principal */
  def prime(): Unit = {
    val prime = Seq("endorseMA", "endorseMA", //"endorseMA", "endorseMA",
                    "endorsePA", "endorsePA", //"endorsePA", "endorsePA",
                    "endorseSA", "endorseSA", //"endorseSA", "endorseSA",
                    "endorseCP", "endorseCP"  //, "endorseCP", "endorseCP"
                    ) 
    if(allPrincipals.size < prime.size) {
      throw UnSafeException(
        s"Available principals (${allPrincipals.size}) are less than required authorities (${prime.size})")
    }
    var inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
    for(op <- prime) {
      doOperation(inference, op)
    }
    inferenceQ.offer(inference, timeout, TimeUnit.SECONDS)
    
    val nonAuthorities = allPrincipals.size - prime.size
    for(i <- 0 to nonAuthorities-1) { // half and half (PI and user)
      inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
      if(i % 2000 == 0) { println(s"Priming non-authority $i") }
      //if(i < (nonAuthorities/2)) {
      if(i < 100000) {  // 100K PIs; 900K users
        opAsync("endorsePI", inference)
      } else {
        opAsync("endorseUser", inference)
      } 
    } 
    while(inferenceQ.size < concurrency) {
    }

    inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
    // A small set of seeding ops
    for(i <- 0 to 200) {
      val op = operators(i % 4 + 6)  // round-robin the first four
      doOperation(inference, op)
    }
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
    startPerfMonitoring(allowAutoPerfStats = true) 
    replayMan.loadRecords()
    println(s"Loading records completes")

    logger.info(s"\nInitial requests (skewed perf)...") 
    logger.info("=============================")
    println(s"\nInitial requests (skewed perf)...") 
    println("=============================")
    scala.io.StdIn.readLine()

    var opcount = 0

    // skip the period where server is slow
    opcount = 0
    while(opcount < 40000 ) {
      val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
      replayOpAsync(inference, 31)
      opcount += 1
    }

    logger.info(s"\nTo replay operations..." + 
      s"SlangCallRecords(${replayMan.getNumRecords})")
    logger.info("=============================")
    println(s"\nTo replay operations..." + 
      s"SlangCallRecords(${replayMan.getNumRecords})")
    println("=============================")
    scala.io.StdIn.readLine()

    // replay five times
    for(round <- 0 to 4) {
      //println(s"Replay round: ${round}")
      opcount = 0
      while(opcount < replayMan.getNumRecords ) {
        val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
        //replayOpAsync(inference, 31)
        replayOpAsync(inference, opcount)
        opcount += 1
        //if((opcount % 2000) == 0)  {
        //  //println(s"opcount: ${opcount}    inferenceQ.size=${inferenceQ.size}")
        //  // Get perfStats async
        //  val future = Future { 
        //    processStatsNow()
        //  }
        //}
      }
    }

    // Wait for the responses of the last batch
    while(inferenceQ.size < concurrency) {
    }
    showThroughputStats()
  }


  /**
   * Evaluate the cost of basic operation components using GENI project delegation 
   */
  def EvaluateBasicOperation(): Unit = { 
    initPrincipals(jvmmapFile)  // Initialize allPrincipals and geniroot 
    primeNoSeeding()  // prime without seeding operands
    val t0 = System.currentTimeMillis 

    // set up perf monitor
    startPerfMonitoring(allowAutoPerfStats = false) // Disable auto perf stats
    //setTestingCacheJvm("152.3.136.26:7777")
    setTestingCacheJvm("10.103.0.42:7777")

    setOpCDF(Seq(0, 0, 0, 0, 0, 0,
                 1, 0, 0, 0, 0, 0, 0, 0, 0, 0))

    var opcount = 0
    while(opcount < 1) {
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
    println(s"Create project done")
    println("=============================")
    scala.io.StdIn.readLine()


    // Reset op distribution
    setOpCDF(Seq(0, 0, 0, 0, 0, 0,
                 0, 1, 0, 0, 0, 0, 0, 0, 0, 0))
    opcount = 0
    while(opcount < 19999) {
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

    println(s"\nDelegate project done")
    println("=============================")
    scala.io.StdIn.readLine()


    // Reset op distribution
    setOpCDF(Seq(0, 0, 0, 0, 0, 0,
                 0, 0, 0, 0, 0, 1, 0, 0, 0, 0))
    opcount = 0
    while(opcount <= 1000) {
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

    println(s"\nDelegate project done")
    println("=============================")
    scala.io.StdIn.readLine()

    // Wait for the responses of the last batch
    while(inferenceQ.size < concurrency) {
    }
    val runtime = System.currentTimeMillis - t0    
    val c = effectiveOpcount.get()
    val f = failedOpcount.get()
    val s = successfulOpcount.get()
    val throughput = c*1000 / runtime
    println(s"Overall: $c in $runtime ms ($throughput ops/sec)   $f ops failed   $s ops succeeded")
    slangPerfCollector.persist("slang-perf-last") // output perf measures
    showThroughputStats()
  }


  /**
   * Testing cache from with query replay
   * Test from a cold start, with a GENI workload mix
   */
  def testCacheWithReplay(): Unit = { 
    initPrincipals(jvmmapFile)  // Initialize allPrincipals and geniroot 
    prime()  // Seeding operands
    var opcount = 0
    val t0 = System.currentTimeMillis 

    // set up perf monitor
    startPerfMonitoring(allowAutoPerfStats = false) // Disable auto perf stats
    //setTestingCacheJvm("152.3.136.26:7777")
    setTestingCacheJvm("10.103.0.42:7777")

    // Set up op distribution
    //opShares = Seq(2, 1, 2, 1, 0, 0, 0, 0, 0, 0)
    //setOpCDF(Seq(0, 0, 0, 0, 0, 0,
    //             1, 10, 1, 10, 0, 0, 0, 0, 0, 0))
    setOpCDF(Seq(0, 0, 0, 0, 0, 0,
                 1, 4, 1, 4, 0, 0, 0, 0, 0, 0))

    // 6 sets of put-only mix
    //while(opcount <= 3000) {
    //while(opcount <= 6000) {
    //while(opcount <= 15000) {
    while(opcount <= 500000) {
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
    setOpCDF(Seq(0, 0, 0, 0, 0, 0,
                 0, 0, 0, 0, 0, 1, 0, 1, 0, 0))
    //// Direct to the same server
    //setOpCDF(Seq(0, 0, 0, 0, 1, 0, 1, 0, 0, 0))
    opcount = 0

    // 3 sets of query-only mix
    //while(opcount <= 5000) {
    //while(opcount <= 20000) {
    while(opcount <= 100000) {
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

    replayMan.persistRecords()
    println(s"Persisting records completes")

    logger.info(s"\nTo replay operations..." + 
      s"SlangCallRecords(${replayMan.getNumRecords})")
    logger.info("=============================")
    println(s"\nTo replay operations..." + 
      s"SlangCallRecords(${replayMan.getNumRecords})")
    println("=============================")
    scala.io.StdIn.readLine()

    opcount = 0

    // replay
    while(opcount < replayMan.getNumRecords) {
      val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
      replayOpAsync(inference, opcount)
      opcount += 1
      if((opcount % 1000) == 0)  {
        println(s"opcount: ${opcount}    inferenceQ.size=${inferenceQ.size}")
        // Get perfStats async
        val future = Future { 
          processStatsNow()
        }
      }
    }
    println(s"\nTo replay operations..." + 
      s"SlangCallRecords(${replayMan.getNumRecords})")
    println("=============================")
    scala.io.StdIn.readLine()


    // Reset op distribution
    setOpCDF(Seq(0, 0, 0, 0, 0, 0,
                 0, 0, 0, 0, 0, 4, 0, 4, 1, 1))
    opcount = 0

    logger.info(s"\nUpdate+query operations...")
    logger.info("=============================")
    println(s"\nUpdate+query operations...")
    println("=============================")
    scala.io.StdIn.readLine()

    // 2 sets of query-update mix
    //while(opcount <= -1) {
    //while(opcount <= 2000) {
    while(opcount <= 5000) {
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
    slangPerfCollector.persist("slang-perf-last") // output perf measures
    showThroughputStats()
  }


  /**
   * Testing cache from a cold start, with a GENI workload mix
   */
  def testCache(): Unit = { 
    initPrincipals(jvmmapFile)  // Initialize allPrincipals and geniroot 

    prime()
    var opcount = 0
    val t0 = System.currentTimeMillis 

    // set up perf monitor
    startPerfMonitoring(allowAutoPerfStats = false) // Disable auto perf stats
    setTestingCacheJvm("10.103.0.11:7777")

    // Set up op distribution
    //opShares = Seq(2, 1, 2, 1, 0, 0, 0, 0, 0, 0)
    //setOpCDF(Seq(0, 0, 0, 0, 0, 0,
    //             2, 8, 2, 8, 0, 0, 0, 0, 0, 0))
    setOpCDF(Seq(0, 0, 0, 0, 0, 0,
                 1, 10, 1, 10, 0, 0, 0, 0, 0, 0))

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
    setOpCDF(Seq(0, 0, 0, 0, 0, 0,
                 0, 0, 0, 0, 0, 1, 0, 1, 0, 0))
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
    setOpCDF(Seq(0, 0, 0, 0, 0, 0,
                 0, 0, 0, 0, 0, 4, 0, 4, 1, 1))
    opcount = 0

    // 2 sets of query-update mix
    //while(opcount <= 2000) {
    while(opcount <= -1) {
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

  /** Test with Random op mix */
  def testRandomOps(): Unit = { 
    initPrincipals(jvmmapFile)  // Initialize allPrincipals and geniroot 
    val opDist = Seq("queryThenCreateProject", "delegateProjectMembership", "queryThenCreateSlice", 
                     "delegateSliceControl", "createSlice", "createSliver"
                    ) // Excluded the ops that target a specific server (for testing cache)
    prime()
    var opcount = 0
    val t0 = System.currentTimeMillis 
    startPerfMonitoring()
    while(opcount <= 10000) {
      logger.info(s"[run while] inferenceQ.size=${inferenceQ.size}")
      val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)

      //var op = opDist(opDist.length - 1)
      var op = getRandomOp()
      if(opcount < 200) { // avoid subcontexts that are too large
        op = opDist(opcount % opDist.length)
      } 
    
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

  def replayOpAsync(inference: Safelang, idx: Int): Unit = {
    val op: SlangCallDescription = replayMan.getSlangCallRecord(idx)
    val future = Future {  
      replayMan.replayOperation(inference, op)
    }
    future.onComplete {
      case Success(res) =>
        //println(s"[opAsync] ${op} COMPLETES   inferenceQ.size:${inferenceQ.size}")
        logger.info(s"[replayOpAsync] ${op} COMPLETES   inferenceQ.size:${inferenceQ.size}")
        inferenceQ.offer(inference, timeout, TimeUnit.SECONDS)  // Release the inference engine
        if(res == true) { // An effective op and it succeeded
          opSuccessHandler(getOpCount(op))
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

  def opAsync(op: String, inference: Safelang, torecord: Boolean = false): Unit = {
    val future = Future { 
      doOperation(inference, op, torecord)
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
