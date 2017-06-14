package safe.programming

import safe.safelang.{Safelang, SafelangManager, slangPerfCollector, REQ_ENV_DELIMITER}
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
 * Benchmark for attestation
 * @param concurrency  number of concurrent requests
 */
class AttestationBench(concurrency: Int, jvmmapFile: String, slangManager: SafelangManager) extends 
    SafeOperation with StateManagementHelper with HarnessPerfStatsController with LazyLogging {
  override val operators = Seq("postInstanceSet", "CertifyImg", "postImageProperty", 
                               "postObjectAcl", "postTrustedCertifier", "postTrustedCP",
                               "accessObject", "imgInstanceAccessesObject")

  override val opcountMap = Map("postInstanceSet"->1, "CertifyImg"->2, "postImageProperty"->1, 
                                "postObjectAcl"->1, "postTrustedCertifier"->1, "postTrustedCP"->1, 
                                "accessObject"->1, "imgInstanceAccessesObject"->1)

  val inferenceQ: LinkedBlockingQueue[Safelang] = buildSafelangQueue(slangManager, concurrency)

  // Load and initialize principals
  allPrincipals = loadPrincipals(jvmmapFile)

  // Assume a trusted cloud provider
  val cloudProvider: PrincipalStub = getMatchingPrincipals("cp.pem".r, allPrincipals) match {
    case principals: ListBuffer[PrincipalStub] if principals.length >= 1 => principals(0)  // take the first principal as geni root 
    case _ => throw new Exception("pem file for cloud provider (cp) not found")
  }
  allPrincipals -= cloudProvider

  val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
  buildIDSet(inference, allPrincipals)
  //buildSubjectSet(inference, allPrincipals)   // Subject set isn't needed
  inferenceQ.offer(inference, timeout, TimeUnit.SECONDS) // release

  allPrincipals = scala.util.Random.shuffle(allPrincipals) // Shuffle principals at different jvms
  logger.info("========================== All principals ==========================")
  allPrincipals.foreach{ p => logger.info(s"${p.getPid}      ${p.getJvm}") }
  logger.info("====================================================================")

  def postInstanceSet(inference: Safelang, cp: PrincipalStub,
                      ip: String, imgId: String, instanceId: String): Boolean = {
    val op = "postInstanceSet" 
    cp.simpleRemoteCall(inference, op, args=Seq(ip, imgId, instanceId))
    true
  }
 
  def certifyImg(inference: Safelang, certifier: PrincipalStub, imgOwner: PrincipalStub, 
                 imgId: String, property: String): Boolean = {
    val op = "certifyImg" 
    simpleDelegate(inference, op, certifier, imgOwner, args=Seq(imgId, property))
    true
  }

  def postImageProperty(inference: Safelang, certifier: PrincipalStub, 
                        imgId: String, property: String): Boolean = {
    val op = "postImageProperty" 
    certifier.simpleRemoteCall(inference, op, args=Seq(imgId, property))
    true
  }
 
  def postObjectAcl(inference: Safelang, objectOwner: PrincipalStub, 
                    objectId: String, property: String): Boolean = {
    val op = "postObjectAcl" 
    objectOwner.simpleRemoteCall(inference, op, args=Seq(objectId, property))
    true
  }
 
  def postTrustedCertifier(inference: Safelang, objectOwner: PrincipalStub, 
                           trustedCertifierId: String): Boolean = {
    val op = "postTrustedCertifier" 
    objectOwner.simpleRemoteCall(inference, op, args=Seq(trustedCertifierId))
    true
  }
 
  def postTrustedCP(inference: Safelang, objectOwner: PrincipalStub, 
                    trustedCP: String): Boolean = {
    val op = "postTrustedCP" 
    objectOwner.simpleRemoteCall(inference, op, args=Seq(trustedCP))
    true
  }

  def makeContextSet(inference: Safelang, fileserver: PrincipalStub, 
                   ip: String, objectId: String): String = {
    val op = "makeContextSet" 
    fileserver.simpleRemoteCall(inference, op, args=Seq(ip, objectId))
  }

  def accessObject(inference: Safelang, fileserver: PrincipalStub, 
                   envs: String, ip: String, objectId: String): Boolean = {
    val op = "accessObject" 
    fileserver.simpleRemoteCall(inference, op, envs, args=Seq(ip, objectId))
    true
  }

  def imgInstanceAccessesObject(inference: Safelang, fileserver: PrincipalStub, 
                                envs: String, imgId: String, objectId: String): Boolean = {
    val op = "imgInstanceAccessesObject" 
    fileserver.simpleRemoteCall(inference, op, envs, args=Seq(imgId, objectId))
    true
  }

  /**
   * op can be either accessObject or imgInstanceAccessesObject
   */
  def opAsync(inference: Safelang, op: String, guard: PrincipalStub, envs: String, subjectId: String, objectId: String): Unit = {
    val future = Future { 
      if(op == "imgInstanceAccessesObject") { 
        imgInstanceAccessesObject(inference, guard, envs, subjectId, objectId)        
      } else if( op == "accessObject") {
        accessObject(inference, guard, envs, subjectId, objectId)
      } else {
         throw new RuntimeException(s"Unrecognized op: ${op}")
      }
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


  val numAcls = 6
  val numCertifiedProperties = 5
 
  /**
   * Benchmark: first perform necessary ops to set up 
   * the authorization context; then benchmark queries
   */
  def run(): Unit = {
    val numPrincipals = allPrincipals.size
    if(numPrincipals < 4) {
      throw UnSafeException(
        s"Available principals (${allPrincipals.size}) are too few")
    }
    var inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
    val objectName = "object0"
    val objectOwner = allPrincipals(0)
    val objectId = objectOwner.getPid + ":" + objectName

    val imageName = "image0"
    val imageOwner = allPrincipals(1)
    val imageId = imageOwner.getPid + ":" + imageName

    val certifier = allPrincipals(2)
    val guard = allPrincipals(3)
 
    val instanceId = "instance0"
    val ip = "ip0"
    var certifiedPropertyCount = 0
    var aclPropertyCount = 0

    // cloud provider launches an instance   
    postInstanceSet(inference, cloudProvider, ip, imageId, instanceId)

    // Certifier certifies a property of the image
    certifyImg(inference, certifier, imageOwner, imageId, s"property${certifiedPropertyCount}") 
    certifiedPropertyCount += 1
 
    // Certifier certifies more property 
    for(i <- 0 to numCertifiedProperties-1) {
      val p = s"property${certifiedPropertyCount}"
      certifiedPropertyCount += 1
      postImageProperty(inference, certifier, imageId, p)
    }	


    // Add noisy image certification
    val numNoisyImgs = 5
    for(i <- 0 to numNoisyImgs-1) {
      val imgId = s"noisyImageOwner${i}:noisyImage${i}"
      val p = s"property${i}"
      postImageProperty(inference, certifier, imgId, p) 
    } 

    // Object owner adds acls
    for(i <- 0 to numAcls-1) {
      val p = s"aclproperty${aclPropertyCount}"
      aclPropertyCount += 1
      postObjectAcl(inference, objectOwner, objectId, p) 
    }

    val p = s"property${certifiedPropertyCount-1}"
    postObjectAcl(inference, objectOwner, objectId, p)

    // Object owner adds trusted certifier
    postTrustedCertifier(inference, objectOwner, certifier.getPid)
  
    // Object owner add trusted cp
    postTrustedCP(inference, objectOwner, cloudProvider.getPid)

    // make context set to avoid re-computing subcontext tokens in slang, which is slow
    val bearerRef = makeContextSet(inference, guard, ip, objectId)
    println(s"bearerRef: ${bearerRef}")
    val envs = REQ_ENV_DELIMITER*3 + bearerRef

    inferenceQ.offer(inference, timeout, TimeUnit.SECONDS)

    logger.info(s"\nPrime is done!")
    logger.info("=============================")
    println(s"\nPrime is done!")
    println("=============================")
    scala.io.StdIn.readLine()

    var opcount = 0

    while(opcount < 40000 ) {
      val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
      opAsync(inference, "accessObject", guard, envs, ip, objectId)
      opcount += 1
    }

    logger.info(s"\nInitial requests done...")     
    logger.info("=============================")
    println(s"\nInitial requests done...")     
    println("=============================")
    scala.io.StdIn.readLine()


    // set up perf monitor on the client
    startPerfMonitoring(allowAutoPerfStats = true)

    opcount = 0
    while(opcount <= 10000) {
      val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
      opAsync(inference, "accessObject", guard, envs, ip, objectId)
      //opAsync(inference, "imgInstanceAccessesObject", guard, envs, imageId, objectId)
      opcount += 1
      //if((opcount % 1000) == 0)  {
      //  println(s"opcount: ${opcount}    allPrincipals.length:${allPrincipals.length}    inferenceQ.size=${inferenceQ.size}")
      //}
    }

    // Wait for the responses of the last batch
    while(inferenceQ.size < concurrency) {
    }
    showThroughputStats()
    //// Get perfStats async
    //val future = Future {
    //  processStatsNow()
    //}
  }

}
