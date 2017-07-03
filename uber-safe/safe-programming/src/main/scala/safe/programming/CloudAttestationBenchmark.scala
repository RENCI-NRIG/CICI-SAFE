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
 * Complete benchmark for cloud attestation
 * @param concurrency  number of concurrent requests
 */
class CloudAttestationBench(concurrency: Int, jvmmapFile: String, slangManager: SafelangManager) extends SafeBench with StateMachineSetHelper with LazyLogging {
  val operators = Seq("launchInstance", "postAttesterImage", "attestInstance", 
                      "postImageProperty", "postObjectAcl",   "appAccessesObject",
                      "attestAppProperty")

  val opcountMap = Map("launchInstance"->2, "postAttesterImage"->1, "attestInstance"->1, 
                       "postImageProperty"->1, "postObjectAcl"->1, "appAccessesObject"->1,
                       "attestAppProperty"->1)

  val targetServer: String = "152.3.136.26:7777"

  def makePrincipalSet(quantity: Int): ListBuffer[PrincipalStub] = {
    val principalList = ListBuffer[PrincipalStub]()
    for(c <- 0 to quantity) {
      val pid: String = "p" + c.toString
      val serverJvm: String = targetServer
      val p: PrincipalStub = new PrincipalStub(pid, "", serverJvm, Seq[String](), "")
      principalList += p 
      if(c % 100 == 0) {
        println(s"[makePrincipalSet] c=${c}   pid=${pid}")
      }
    }
    principalList
  }

  //val testingCacheJvm = "10.103.0.11:7777"  // for cache testing 
  //val defaultJvm = "152.3.136.26:7777" 
  val inferenceQ: LinkedBlockingQueue[Safelang] = buildSafelangQueue(slangManager, concurrency)

  // Load and initialize principals
  var allPrincipals: ListBuffer[PrincipalStub] = makePrincipalSet(10000)
  logger.info("========================== All principals ==========================")
  allPrincipals.foreach{ p => logger.info(s"${p.getPid}      ${p.getJvm}") }
  logger.info("====================================================================")
 
  def launchInstance(inference: Safelang, host: PrincipalStub, guest: PrincipalStub, instanceId: String, image: String, programType: String): Boolean = {
    val op = "launchInstance" 
    simpleDelegate(inference, op, host, guest, args=Seq(instanceId, image, programType))
    true
  }

  def postAttesterImage(inference: Safelang, endorser: PrincipalStub, image: String): Boolean = {
    val op = "postAttesterImage"
    endorser.simpleRemoteCall(inference, op, args=Seq(image))
    true
  }  

  def attestInstance(inference: Safelang, guard: PrincipalStub, appID: String): Boolean = {
    val op = "attestInstance"
    guard.simpleRemoteCall(inference, op, args=Seq(appID))
    true
  }  

  def postImageProperty(inference: Safelang, endorser: PrincipalStub, image: String, property: String): Boolean = {
    val op = "postImageProperty"
    endorser.simpleRemoteCall(inference, op, args=Seq(image, property))
    true
  }  

  def postObjectAcl(inference: Safelang, owner: PrincipalStub, objectId: String, property: String): Boolean = {
    val op = "postObjectAcl"
    owner.simpleRemoteCall(inference, op, args=Seq(objectId, property))
    true
  }  

  def appAccessesObject(inference: Safelang, guard: PrincipalStub, appId: String, objectId: String): Boolean = {
    val op = "appAccessesObject"
    guard.simpleRemoteCall(inference, op, args=Seq(appId, objectId))
    true
  }  

  def attestAppProperty(inference: Safelang, guard: PrincipalStub, appId: String, property: String): Boolean = {
    val op = "attestAppProperty"
    guard.simpleRemoteCall(inference, op, args=Seq(appId, property))
    true
  }  

  /**
   * op can be either accessObject or imgInstanceAccessesObject
   */
  def opAsync(inference: Safelang, op: String, guard: PrincipalStub, appId: String, objectId: String, property: String): Unit = {
    val future = Future {
      if(op == "attestInstance") {
        attestInstance(inference, guard, appId)
      } else if( op == "appAccessesObject") {
        appAccessesObject(inference, guard, appId, objectId)
      } else if( op == "attestAppProperty") { 
        attestAppProperty(inference, guard, appId, property)
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

  val numAcls = 100
  val numImageProperties = 100

  val IaaS: PrincipalStub = new PrincipalStub("152.3.145.38:444", "", targetServer, Seq[String](), "")

  /**
   * Benchmark: first perform necessary ops to set up 
   * the authorization context; then benchmark queries
   */
  def run(): Unit = {
    val numPrincipals = allPrincipals.size
    if(numPrincipals < 10) {
      throw UnSafeException(
        s"Available principals (${allPrincipals.size}) are too few")
    }
    var inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
    val objectName = "object0"
    val objectOwner = allPrincipals(0)
    val objectId = objectOwner.getPid + ":" + objectName

    val guard = allPrincipals(1)

    var principalCount = 4
    val programType = "image"

    val tapconPrincipal = allPrincipals(principalCount)
    var instanceId = "instance_" + principalCount
    val tapconImage = "tapcon_image_" + principalCount 
    principalCount += 1

    // cloud provider launches a tapcon 
    launchInstance(inference, IaaS, tapconPrincipal, instanceId, tapconImage, programType)

    val containerPrincipal = allPrincipals(principalCount)
    principalCount += 1
    instanceId = "instance_" + principalCount 
    val containerImage = "container_image_" + principalCount
    principalCount += 1

    // tapcon launches a container 
    launchInstance(inference, tapconPrincipal,containerPrincipal, instanceId, containerImage, programType)

    // IaaS attests attester images
    postAttesterImage(inference, IaaS, tapconImage)
    postAttesterImage(inference, IaaS, containerImage)

    // add image properties
    for( imagePropertyCount <- 0 to numImageProperties-1 ) {
      val imageProperty: String = "image_property_" + imagePropertyCount
      postImageProperty(inference, IaaS, containerImage, imageProperty)
    }

    // add acl to objects
    for ( aclCount <- 0 to numAcls-1 ) {
      val aclProperty: String = "acl_image_property_" + aclCount
      postObjectAcl(inference, objectOwner, objectId, aclProperty)
    } 

    // add effective acl
    val effectiveAclProperty: String = "image_property_" + (numImageProperties-1)
    postObjectAcl(inference, objectOwner, objectId, effectiveAclProperty)

    //// make context set to avoid re-computing subcontext tokens in slang, which is slow
    //val bearerRef = makeContextSet(inference, guard, ip, objectId)
    //println(s"bearerRef: ${bearerRef}")
    //val envs = REQ_ENV_DELIMITER*3 + bearerRef

    inferenceQ.offer(inference, timeout, TimeUnit.SECONDS)

    logger.info(s"\nPrime is done!")
    logger.info("=============================")
    println(s"\nPrime is done!")
    println("=============================")
    scala.io.StdIn.readLine()


    //def opAsync(inference: Safelang, op: String, guard: PrincipalStub, appId: String, objectId: String, property: String): Unit = {

    var opcount = 0

    while(opcount < 40000 ) {
      val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
      //opAsync(inference, "attestInstance", guard, containerPrincipal.getPid, objectId, effectiveAclProperty)
      opAsync(inference, "appAccessesObject", guard, containerPrincipal.getPid, objectId, effectiveAclProperty)
      //opAsync(inference, "attestAppProperty", guard, containerPrincipal.getPid, objectId, effectiveAclProperty)
      opcount += 1
    }

    logger.info(s"\nInitial requests done...")
    logger.info("=============================")
    println(s"\nInitial requests done...")
    println("=============================")
    scala.io.StdIn.readLine()

    // set up perf monitor
    initPerfMonitor(allowAutoPerfStats = true)


    opcount = 0
    while(opcount < 10000 ) {
      val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
      opAsync(inference, "attestInstance", guard, containerPrincipal.getPid, objectId, effectiveAclProperty)
      //opAsync(inference, "appAccessesObject", guard, containerPrincipal.getPid, objectId, effectiveAclProperty)
      //opAsync(inference, "attestAppProperty", guard, containerPrincipal.getPid, objectId, effectiveAclProperty)
      opcount += 1
    }

    logger.info(s"\nattestInstance done...")
    logger.info("=============================")
    println(s"\nattestInstance done...")
    println("=============================")
    scala.io.StdIn.readLine()


    // Get perfStats async
    val future = Future {
      processStatsNow()
    }



    opcount = 0
    while(opcount < 10000 ) {
      val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
      //opAsync(inference, "attestInstance", guard, containerPrincipal.getPid, objectId, effectiveAclProperty)
      opAsync(inference, "appAccessesObject", guard, containerPrincipal.getPid, objectId, effectiveAclProperty)
      //opAsync(inference, "attestAppProperty", guard, containerPrincipal.getPid, objectId, effectiveAclProperty)
      opcount += 1
    }

    logger.info(s"\nappAccessesObject done...")
    logger.info("=============================")
    println(s"\nappAccessesObject done...")
    println("=============================")
    scala.io.StdIn.readLine()


    // Get perfStats async
    val future = Future {
      processStatsNow()
    }



    opcount = 0
    while(opcount < 10000 ) {
      val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
      //opAsync(inference, "attestInstance", guard, containerPrincipal.getPid, objectId, effectiveAclProperty)
      //opAsync(inference, "appAccessesObject", guard, containerPrincipal.getPid, objectId, effectiveAclProperty)
      opAsync(inference, "attestAppProperty", guard, containerPrincipal.getPid, objectId, effectiveAclProperty)
      opcount += 1
    }

    logger.info(s"\nattestAppProperty done...")
    logger.info("=============================")
    println(s"\nattestAppProperty done...")
    println("=============================")
    scala.io.StdIn.readLine()


    // Wait for the responses of the last batch
    while(inferenceQ.size < concurrency) {
    }

    // Get perfStats async
    val future = Future {
      processStatsNow()
    }
  }

}
