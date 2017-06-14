package safe.programming

import safe.safelang.{Safelang, SafelangManager, REQ_ENV_DELIMITER}
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
 * Complete benchmark for SAFE Strong
 * @param concurrency  number of concurrent requests
 */
class NamingBench(concurrency: Int, jvmmapFile: String, slangManager: SafelangManager) 
    extends SafeOperation with StateManagementHelper with HarnessPerfStatsController with LazyLogging {

  override val operators = Seq("groupMember", "nestGroup", "delegateMembership", "postGroupSet", 
                      "delegateObject", "postObjectSet", 
                      "postDirectoryAccess", 
                      "queryMembership", "queryName", 
                      "accessNamedObjectSingleContext", "accessNamedObject", 
                      "accessNamedObjectSingleContextAtServer", "accessNamedObjectAtServer",
                      "delegateObjectThenQuery", "queryNameAtServer")

  override val opcountMap = Map("groupMember"->2, "nestGroup"->2, "delegateMembership"->2, "postGroupSet"->1, 
                      "delegateObject"->2, "postObjectSet"->1, 
                      "postDirectoryAccess"->1, 
                      "queryMembership"->1, "queryName"->1,
                      "accessNamedObjectSingleContext"->1, "accessNamedObject"->1,
                      "accessNamedObjectSingleContextAtServer"->1, "accessNamedObjectAtServer"->1,
                      "delegateObjectThenQuery"->3, "queryNameAtServer"->1)


  //val defaultJvm = "152.3.136.26:7777" 
  val inferenceQ: LinkedBlockingQueue[Safelang] = buildSafelangQueue(slangManager, concurrency)

  allPrincipals = loadPrincipals(jvmmapFile)

  // Assume the directories shared a root; the principal of that root object is specified by geniroot.pem
  val rootObjectAuthority: PrincipalStub = getMatchingPrincipals("geniroot.pem".r, allPrincipals) match {
    case principals: ListBuffer[PrincipalStub] if principals.length >= 1 => principals(0)  // take the first principal as geni root 
    case _ => throw new Exception("pem file for geniroot not found")
  }
  allPrincipals -= rootObjectAuthority

  val inference = inferenceQ.poll(60L, TimeUnit.SECONDS)
  buildIDSet(inference, allPrincipals)
  buildSubjectSet(inference, allPrincipals)
  inferenceQ.offer(inference, 60L, TimeUnit.SECONDS) // release

  //allPrincipals = scala.util.Random.shuffle(allPrincipals) // Shuffle principals from different jvms
  logger.info("========================== All principals ==========================")
  allPrincipals.foreach{ p => logger.info(s"${p.getPid}      ${p.getJvm}") }
  logger.info("====================================================================")

  def groupMember(inference: Safelang, ga: PrincipalStub, groupName: String, user: PrincipalStub): Boolean = {
    val op = "groupMember"
    val groupId = ga.getPid + ":" + groupName
    simpleDelegate(inference, op, ga, user, args=Seq(groupId, "true"))
    true
  }

  def nestGroup(inference: Safelang, ga: PrincipalStub, toGa: PrincipalStub, groupId: String, toGroupId: String): Boolean = {
    val op = "nestGroup"
    simpleDelegate(inference, op, ga, toGa, args=Seq(groupId, toGroupId, "true"))
    true
  }

  def delegateMembership(inference: Safelang, delegator: PrincipalStub, groupId: String, user: PrincipalStub): Boolean = {
    val op = "delegateMembership"
    simpleDelegate(inference, op, delegator, user, args=Seq(groupId, "true"))
    true
  }

  def postGroupSet(inference: Safelang, ga: PrincipalStub, groupId: String): Boolean = {
    val op = "postGroupSet"
    ga.simpleRemoteCall(inference, op, args=Seq(groupId))
    true
  }

  def delegateObject(inference: Safelang, delegator: PrincipalStub, user: PrincipalStub, objectName: String, toScid: String, homeScid: String): Boolean = {
    val op = "delegateObject"
    simpleDelegate(inference, op, delegator, user, args=Seq(objectName, toScid, homeScid))
    true
  }

  def postObjectSet(inference: Safelang, objectRoot: PrincipalStub, objectId: String): Boolean = {
    val op = "postObjectSet"
    objectRoot.simpleRemoteCall(inference, op, args=Seq(objectId))
    true
  }

  def postDirectoryAccess(inference: Safelang, objectRoot: PrincipalStub, groupId: String, objectId: String): Boolean = {
    val op = "postDirectoryAccess"
    objectRoot.simpleRemoteCall(inference, op, args=Seq(groupId, objectId))
    true
  }

  def queryMembership(inference: Safelang, guard: PrincipalStub, groupId: String, user: PrincipalStub): Boolean = {
    val op = "queryMembership"
    val queryEnvs: String = REQ_ENV_DELIMITER * 3 + user.getSubjectSetTokens(0) // BearerRef is required
    guard.simpleRemoteCall(inference, op, env=queryEnvs, args=Seq(groupId, user.getPid))
    true
  }

  def queryName(inference: Safelang, guard: PrincipalStub, name: String): Boolean = {
    val op = "queryName"
    guard.simpleRemoteCall(inference, op, args=Seq(name))
    true
  }

  def queryNameAtServer(inference: Safelang, guard: PrincipalStub, name: String, serverJvm: String): Boolean = {
    val op = "queryName"
    guard.remoteCallToServer(inference, op, serverJvm, args=Seq(name))
    true
  }

  def delegateObjectThenQuery(inference: Safelang, delegator: PrincipalStub, user: PrincipalStub, queryPrincipal: PrincipalStub, queryJvm: String, objectName: String, toScid: String, homeScid: String, queryName: String): Boolean = {
    val op = "delegateObjectThenQuery"
    delegateThenQuery(inference, op, delegator, user, queryPrincipal, queryJvm, args=Seq(objectName, toScid, homeScid, queryName))
    true
  }

  def accessNamedObjectSingleContext(inference: Safelang, guard: PrincipalStub, user: PrincipalStub, name: String): Boolean = {
    val op = "accessNamedObjectSingleContext"
    val queryEnvs: String = REQ_ENV_DELIMITER*3 + user.getSubjectSetTokens(0) // BearerRef is required
    guard.simpleRemoteCall(inference, op, env=queryEnvs, args=Seq(user.getPid, name))
    true
  }

  def accessNamedObject(inference: Safelang, guard: PrincipalStub, user: PrincipalStub, name: String): Boolean = {
    val op = "accessNamedObject"
    val queryEnvs: String = REQ_ENV_DELIMITER*3 + user.getSubjectSetTokens(0) // BearerRef is required
    guard.simpleRemoteCall(inference, op, env=queryEnvs, args=Seq(user.getPid, name))
    true
  }

  def accessNamedObjectSingleContextAtServer(inference: Safelang, guard: PrincipalStub, user: PrincipalStub, name: String, serverJvm: String): Boolean = {
    val op = "accessNamedObjectSingleContext"
    val queryEnvs: String = REQ_ENV_DELIMITER*3 + user.getSubjectSetTokens(0) // BearerRef is required
    guard.remoteCallToServer(inference, op, serverJvm, env=queryEnvs, args=Seq(user.getPid, name))
    true
  }

  def accessNamedObjectAtServer(inference: Safelang, guard: PrincipalStub, user: PrincipalStub, name: String, serverJvm: String): Boolean = {
    val op = "accessNamedObject"
    val queryEnvs: String = REQ_ENV_DELIMITER*3 + user.getSubjectSetTokens(0) // BearerRef is required
    guard.remoteCallToServer(inference, op, serverJvm, env=queryEnvs, args=Seq(user.getPid, name))
    true
  }

  var principalCount = 1
  val version = "v1"
  var nodeId = 0
  val leafnames = ListBuffer[String]()

  def createNamingTree(dirAuthority: PrincipalStub, dirId: String, namePrefix: String, branchFactor: Int, depth: Int): Unit = {
    if(depth == 0) {
      leafnames += namePrefix.substring(1)
      // We're done
    }
    else {
      var _depth = depth - 1
      for(i <- 0 to branchFactor-1) {
        nodeId += 1
        var nameComponent = s"name${nodeId}_${version}" 
        val objectAuthority = allPrincipals(nodeId) 
        val objectId = objectAuthority.getPid + ":" + nameComponent
        delegateObject(inference, dirAuthority, objectAuthority, nameComponent, objectId, dirId)
        val _namePrefix = namePrefix + "/" + nameComponent
        createNamingTree(objectAuthority, objectId, _namePrefix, branchFactor, _depth)
      }
    }
  }

  /** Bench name parsing on a naming tree */
  def benchNameTreeParsing(): Unit = {
    val branchFactor = 4 
    val height = 5
    val numPrincipals = allPrincipals.size
    val treeSize = (branchFactor^(height+1) -1)/(branchFactor -1)
    if( numPrincipals < (branchFactor^(height+1)-1)/(branchFactor -1) ) {
      throw UnSafeException(
        s"Too few available principals: ${allPrincipals.size}: should be at least ${treeSize}")
    }
    var inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
    val rootObjectId = rootObjectAuthority.getPid + ":" + "root"
    val guard = allPrincipals(numPrincipals - 1) // the last principal is the guard
    postObjectSet(inference, rootObjectAuthority, rootObjectId)

    // build the name tree
    createNamingTree(rootObjectAuthority, rootObjectId, "", branchFactor, height)

    inferenceQ.offer(inference, timeout, TimeUnit.SECONDS)

    logger.info(s"\nPrime is done!")
    logger.info("=============================")
    println(s"\nPrime is done! \nNumber of leaf names: ${leafnames}")
    println("=============================")
    scala.io.StdIn.readLine()


    var opcount = 0
    val serverJvm = "10.103.0.42:7777"

    // set up perf monitor
    startPerfMonitoring(allowAutoPerfStats = true)

    // skip the period where server is slow
    opcount = 0
    while(opcount < 40000 ) {
      val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
      queryNameAtServerAsync(inference, guard, s"name1_${version}", serverJvm)
      opcount += 1
    }

    println(s"\nInitial requests done")
    println("=============================")
    scala.io.StdIn.readLine()


    opcount = 0
    var last_requested_name = ""
    for(n <- leafnames) {
      last_requested_name  = n
      val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
      queryNameAtServerAsync(inference, guard, n, serverJvm)
      opcount = opcount + 1
      if((opcount % 1000) == 0)  {
        println(s"opcount: ${opcount}    allPrincipals.length:${allPrincipals.length}    inferenceQ.size=${inferenceQ.size}")
        // Get perfStats async
        //val future = Future {
        //  processStatsNow()
        //}
      }
    }

    while(opcount % 1000 != 0) {
      val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
      queryNameAtServerAsync(inference, guard, last_requested_name, serverJvm)
      opcount = opcount + 1
    }
    println(s"In total ${opcount} queries")

  }

  def queryNameAtServerAsync(inference: Safelang, guard: PrincipalStub, name: String, serverJvm: String): Unit = {
    val op = "queryNameAtServer"
    val future = Future { 
      queryNameAtServer(inference, guard, name, serverJvm)
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

  def opAsync(inference: Safelang, guard: PrincipalStub, user: PrincipalStub, name: String, op: String): Unit = {
    val future = 
      if(op == "accessNamedObject") {
        Future {
          accessNamedObject(inference, guard, user, name)
        }
      } else if(op == "accessNamedObjectSingleContext") {
        Future {
          accessNamedObjectSingleContext(inference, guard, user, name) 
        }
      } else {
        throw UnSafeException(s"Unrecognized op: ${op}")
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
