package safe.programming

import safe.safelang.{Safelang, SafelangManager, slangPerfCollector}
import safe.safelog.UnSafeException

import scala.util.{Failure, Success, Random}
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
 * Complete benchmark for DIFC file access control
 * @param concurrency  number of concurrent requests
 */
class DifcBench(concurrency: Int, jvmmapFile: String, slangManager: SafelangManager) extends SafeBench with StateMachineSetHelper with LazyLogging {
  val operators = Seq("grantTagAccess", "labelFile", "delegateTagAccess", "checkFileAccess")

  val opcountMap = Map("grantTagAccess"->2, "labelFile"->1, "delegateTagAccess"->2, "checkFileAccess"->1)

  //val testingCacheJvm = "10.103.0.11:7777"  // for cache testing 
  //val defaultJvm = "152.3.136.26:7777" 
  val inferenceQ: LinkedBlockingQueue[Safelang] = buildSafelangQueue(slangManager, concurrency)

  // Load and initialize principals
  var allPrincipals: ListBuffer[PrincipalStub] = loadPrincipals(jvmmapFile)
  val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
  buildIDSet(inference, allPrincipals)
  buildSubjectSet(inference, allPrincipals)
  inferenceQ.offer(inference, timeout, TimeUnit.SECONDS) // release

  allPrincipals = scala.util.Random.shuffle(allPrincipals) // Shuffle principals at different jvms
  logger.info("========================== All principals ==========================")
  allPrincipals.foreach{ p => logger.info(s"${p.getPid}      ${p.getJvm}") }
  logger.info("====================================================================")
 
  def grantTagAccess(inference: Safelang, ta: PrincipalStub, tagName: String, user: PrincipalStub): Boolean = {
    val op = "grantTagAccess" 
    val tagId = ta.getPid + ":" + tagName 
    simpleDelegate(inference, op, ta, user, args=Seq(tagId, "true"))
    true
  }

  def postTagSet(inference: Safelang, ta: PrincipalStub, tagId: String): Boolean = {
    val op = "postTagSet"
    ta.simpleRemoteCall(inference, op, args=Seq(tagId))
    true
  }  

  def delegateTagAccess(inference: Safelang, delegator: PrincipalStub, tagId: String, user: PrincipalStub): Boolean = {
    val op = "delegateTagAccess"
    simpleDelegate(inference, op, delegator, user, args=Seq(tagId, "true"))
    true
  }  

  def labelFile(inference: Safelang, fileOwner: PrincipalStub, fileName: String, tagId: String): Boolean = {
    val op = "labelFile"
    val fileId = fileOwner.getPid + ":" + fileName
    fileOwner.simpleRemoteCall(inference, op, args=Seq(fileId, tagId))
    true
  }  

  def checkFileAccess(inference: Safelang, guard: PrincipalStub, user: PrincipalStub, fileId: String): Boolean = {
    val op = "checkFileAccess"
    val queryEnvs: String = ":" + ":" + ":" + user.getSubjectSetTokens(0) // BearerRef is required
    guard.simpleRemoteCall(inference, op, env=queryEnvs, args=Seq(user.getPid, fileId))
    true
  } 
  val numtags = 6
  val delegationdepth = 5
 
  /** Perform necessary ops to set up the settings */
  def run(): Unit = {
    val numPrincipals = allPrincipals.size
    if(numPrincipals < numtags*(delegationdepth+1)+3) {
      throw UnSafeException(
        s"Available principals (${allPrincipals.size}) are too few")
    }
    var inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
    val fileName = "file"
    val fileOwner = allPrincipals(0)
    val fileId = fileOwner.getPid + ":" + fileName
    val requester = allPrincipals(numPrincipals-2) 
    val guard = allPrincipals(numPrincipals-1)
    var nextPrincipal = 1
    for(i <- 0 to numtags-1) {
      val tagNo = i
      val tagOwner = allPrincipals(nextPrincipal)
      nextPrincipal = nextPrincipal + 1
      val tagName = "tag" + tagNo
      val tagId = tagOwner.getPid + ":" + tagName
      postTagSet(inference, tagOwner, tagId)
      labelFile(inference, fileOwner, fileName, tagId)
      if(delegationdepth == 0) {
        grantTagAccess(inference, tagOwner, tagName, requester)
      } else {
        var currentUser = allPrincipals(nextPrincipal)
        nextPrincipal = nextPrincipal + 1
        grantTagAccess(inference, tagOwner, tagName, currentUser) 
        for(delcount <- 0 to delegationdepth-1) {
          val nextUser = allPrincipals(nextPrincipal)
          nextPrincipal = nextPrincipal + 1
          delegateTagAccess(inference, currentUser, tagId, nextUser)      
          currentUser = nextUser 
        }
        delegateTagAccess(inference, currentUser, tagId, requester)
      }
    }

    inferenceQ.offer(inference, timeout, TimeUnit.SECONDS)

    logger.info(s"\nPrime is done!")
    logger.info("=============================")
    println(s"\nPrime is done!")
    println("=============================")
    scala.io.StdIn.readLine()

    var opcount = 0

    // set up perf monitor
    initPerfMonitor(allowAutoPerfStats = false)

    while(opcount <= 1000) {
      val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
      opAsync(inference, guard, requester, fileId)
      opcount += 1
      if((opcount % 1000) == 0)  {
        println(s"opcount: ${opcount}    allPrincipals.length:${allPrincipals.length}    inferenceQ.size=${inferenceQ.size}")
      }
    }
    // Get perfStats async
    val future = Future {
      processStatsNow()
    }
  }

  def opAsync(inference: Safelang, guard: PrincipalStub, user: PrincipalStub, fileId: String): Unit = {
    val op = "checkFileAccess"
    val future = Future { 
      checkFileAccess(inference, guard, user, fileId)
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
        println("[" + Console.RED + s"${op} failed: ${e.printStackTrace}     effectiveOpcount: ${effectiveOpcount.get()}     failedops: ${f}" + Console.RESET + "]")      
        logger.error("[" + Console.RED + s"${op} failed: ${e.printStackTrace}      effectiveOpcount: ${effectiveOpcount.get()}  failedops: ${f}" + Console.RESET + "]")
        inferenceQ.offer(inference, timeout, TimeUnit.SECONDS)
        opFailureHandler()
        throw UnSafeException(s"${op} failed: ${e.printStackTrace}") 
    }
  } 

}
