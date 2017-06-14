package safe.programming

import safe.safelang.slangPerfCollector
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable.ListBuffer

/**
 * Performance stats controller on harness
 */
trait HarnessPerfStatsController {
  val effectiveOpcount = new AtomicInteger(0)
  val successfulOpcount = new AtomicInteger(0)
  val failedOpcount = new AtomicInteger(0)
  var autoPerfStats: Boolean = true

  // Period in ops for auto perf stats
  val autoStatsPeriod: Int = Config.config.autoPerfStatsPeriod  
  println(s"autoStatsPeriod=${autoStatsPeriod}")

  /**
   * For perf stats: it's not protected from concurrent access, 
   * assuming perf stats doesn't happen very frequently
   */
  var t = System.currentTimeMillis
  var lastEffectiveOpcount: Int = 0

  // ToDo: initMonitoring
  def startPerfMonitoring(allowAutoPerfStats: Boolean = true): Unit = {
    autoPerfStats = allowAutoPerfStats
    t = System.currentTimeMillis
    slangPerfCollector.init()
  }

  /** On an op succeeds, update perf stats */
  def opSuccessHandler(numops: Int = 1): Unit = {
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
    //showThroughputStats()
  }

  val throughputInMemory = ListBuffer[Tuple7[Int, Int, Int, Long, Long, Int, Int]]()
  def processStats(effectiveOps: Int): Unit = { 
    val now = System.currentTimeMillis 
    val runtime = now - t
    // roughly autoStatsPeriod ops if autoPerfStats is on
    val numops = effectiveOps - lastEffectiveOpcount
    val throughput = numops*1000 / runtime 
    val numfails = failedOpcount.get()
    val numsuccs = successfulOpcount.get()
    val statsTuple = (lastEffectiveOpcount, effectiveOps, numops, runtime, throughput, numfails, numsuccs)
    throughputInMemory += statsTuple 
    //println(s"${lastEffectiveOpcount+1}--${effectiveOps} ops: " + 
    //        s"$numops in $runtime ms ($throughput ops/sec)    " +
    //        s"so far: $numfails ops failed      $numsuccs ops succeeded")
    t = now  // Set t to now
    // Set lastEffectiveOpcount to effectiveOps
    lastEffectiveOpcount  = effectiveOps  
    slangPerfCollector.addThroughput(throughput, s"${effectiveOps}")
    // Persist perf measures
    slangPerfCollector.persist(s"slang-perf-part-${effectiveOps/1000}", allRecords=false) 
  }


  def showThroughputStats(): Unit = {
    for((lastEffectiveOpcount, effectiveOps, numops, runtime, throughput, numfails, numsuccs) <- throughputInMemory) {
      println(s"${lastEffectiveOpcount+1}--${effectiveOps} ops: " + 
              s"$numops in $runtime ms ($throughput ops/sec)    " +
              s"so far: $numfails ops failed      $numsuccs ops succeeded")
    }
  }

}
