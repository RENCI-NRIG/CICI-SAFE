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
class StrongBench(concurrency: Int, jvmmapFile: String, slangManager: SafelangManager) 
    extends SafeOperation with StateManagementHelper with HarnessPerfStatsController with LazyLogging {

  override val operators = Seq("groupMember", "nestGroup", "delegateMembership", "postGroupSet", 
                      "delegateName", "postObjectSet", 
                      "postDirectoryAccess", 
                      "queryMembership", "queryName", 
                      "accessNamedObjectSingleContext", "accessNamedObject", 
                      "accessNamedObjectSingleContextAtServer", "accessNamedObjectAtServer",
                      "delegateNameThenQuery")

  override val opcountMap = Map("groupMember"->2, "nestGroup"->2, "delegateMembership"->2, "postGroupSet"->1, 
                      "delegateName"->2, "postObjectSet"->1, 
                      "postDirectoryAccess"->1, 
                      "queryMembership"->1, "queryName"->1,
                      "accessNamedObjectSingleContext"->1, "accessNamedObject"->1,
                      "accessNamedObjectSingleContextAtServer"->1, "accessNamedObjectAtServer"->1,
                      "delegateNameThenQuery"->3)


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

  def delegateName(inference: Safelang, delegator: PrincipalStub, user: PrincipalStub, objectName: String, toScid: String, homeScid: String): Boolean = {
    val op = "delegateName"
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
    val queryEnvs: String = REQ_ENV_DELIMITER*3 + user.getSubjectSetTokens(0) // BearerRef is required
    guard.simpleRemoteCall(inference, op, env=queryEnvs, args=Seq(groupId, user.getPid))
    true
  }

  def queryName(inference: Safelang, guard: PrincipalStub, name: String): Boolean = {
    val op = "queryName"
    guard.simpleRemoteCall(inference, op, args=Seq(name))
    true
  }

  def delegateNameThenQuery(inference: Safelang, delegator: PrincipalStub, user: PrincipalStub, queryPrincipal: PrincipalStub, queryJvm: String, objectName: String, toScid: String, homeScid: String, queryName: String): Boolean = {
    val op = "delegateNameThenQuery"
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


  /** Perform necessary ops to set up the settings */
  def run(): Unit = {
    val numSubjects = 1000
    val numObjects = 1000
    val numPrincipals = allPrincipals.size
    if( numPrincipals < (numSubjects*3+numObjects*1+2) ) {
      throw UnSafeException(
        s"Too few available principals: ${allPrincipals.size}")
    }
    var inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
    val rootGroupAuthority = allPrincipals(0)
    val rootGroupId = allPrincipals(0).getPid + ":" + "rgroup"
    val rootObjectId = rootObjectAuthority.getPid + ":" + "root"
    val terminalSubjects = ListBuffer[PrincipalStub]()
    val terminalObjects = ListBuffer[String]() 
    val terminalObjectNames = ListBuffer[String]()
    val guard = allPrincipals(numPrincipals - 1)
    postGroupSet(inference, rootGroupAuthority, rootGroupId)
    postObjectSet(inference, rootObjectAuthority, rootObjectId)
    postDirectoryAccess(inference, rootObjectAuthority, rootGroupId, rootObjectId) // access policy

    var nextPrincipal = 1
    for(i <- 1 to numSubjects) {  // Build the subject hierarchy
      val groupNo = i 
      val groupName = s"group${groupNo}"
      val ga = allPrincipals(nextPrincipal)
      nextPrincipal += 1      
      val groupId = ga.getPid + ":" + groupName
      nestGroup(inference, rootGroupAuthority, ga, rootGroupId, groupId) 
      val guser = allPrincipals(nextPrincipal)
      nextPrincipal += 1
      groupMember(inference, ga, groupName, guser)
      val duser = allPrincipals(nextPrincipal)
      nextPrincipal += 1
      delegateMembership(inference, guser, groupId, duser)
      terminalSubjects += duser
      if(i % 1000 == 0) {
        println(s"${i}th subject")
        val future = Future {
          processStatsNow()
        }
      }
    }
   
   for(i <- 1 to numObjects) { // Build the object hierarchy
     val objectNo = i
     val objectName = s"object${objectNo}"
     val objectRoot = allPrincipals(nextPrincipal)
     nextPrincipal += 1
     val objectId = objectRoot.getPid + ":" + objectName
     delegateName(inference, rootObjectAuthority, objectRoot, objectName, objectId, rootObjectId)
     terminalObjects += objectId
     terminalObjectNames += objectName 
     if(i % 1000 == 0) {
       println(s"${i}th object")
       val future = Future {
         processStatsNow()
       }
     }
   }

    inferenceQ.offer(inference, timeout, TimeUnit.SECONDS)

    val future = Future {
      processStatsNow()
    }

    logger.info(s"\nPrime is done!")
    logger.info("=============================")
    val numTSubjects = terminalSubjects.length
    val numTObjects = terminalObjects.length
    println(s"\nPrime is done! \nTerminal subjects: $numTSubjects     Terminal objects: $numTObjects")
    println("=============================")
    scala.io.StdIn.readLine()

    var opcount = 0

    // set up perf monitor
    startPerfMonitoring(allowAutoPerfStats = false)

    val r = scala.util.Random
    val op = "accessNamedObjectSingleContext"
    //val op = "accessNamedObject"
    while(opcount <= 20000) {
      val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
      val rdmSubject = terminalSubjects(r.nextInt(numTSubjects)) 
      val rdmObjectName = terminalObjectNames(r.nextInt(numTObjects))
      opAsync(inference, guard, rdmSubject, rdmObjectName, op)
      opcount = opcount + 1
      if((opcount % 1000) == 0)  {
        println(s"opcount: ${opcount}    allPrincipals.length:${allPrincipals.length}    inferenceQ.size=${inferenceQ.size}")
        // Get perfStats async
        val future = Future {
          processStatsNow()
        }
      }
    }
  }


  /** Bench name parsing */
  def benchNameParsing(): Unit = {
    val numSubjects = 1000 
    val numObjects = 1000
    val numPrincipals = allPrincipals.size
    if( numPrincipals < (numSubjects*3+numObjects*1+2) ) {
      throw UnSafeException(
        s"Too few available principals: ${allPrincipals.size}")
    }
    var inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
    val rootGroupAuthority = allPrincipals(0)
    val rootGroupId = allPrincipals(0).getPid + ":" + "rgroup"
    val rootObjectId = rootObjectAuthority.getPid + ":" + "rootnew"
    val terminalSubjects = ListBuffer[PrincipalStub]()
    val terminalObjects = ListBuffer[String]() 
    val terminalObjectNames = ListBuffer[String]()
    val terminalObjectRoots = ListBuffer[PrincipalStub]()
    val guard = allPrincipals(numPrincipals - 1)
    postGroupSet(inference, rootGroupAuthority, rootGroupId)
    postObjectSet(inference, rootObjectAuthority, rootObjectId)
    postDirectoryAccess(inference, rootObjectAuthority, rootGroupId, rootObjectId) // access policy

    var nextPrincipal = 1
    for(i <- 1 to numSubjects) {  // Build the subject hierarchy
      val groupNo = i 
      val groupName = s"group${groupNo}"
      val ga = allPrincipals(nextPrincipal)
      nextPrincipal += 1      
      val groupId = ga.getPid + ":" + groupName
      nestGroup(inference, rootGroupAuthority, ga, rootGroupId, groupId) 
      val guser = allPrincipals(nextPrincipal)
      nextPrincipal += 1
      groupMember(inference, ga, groupName, guser)
      val duser = allPrincipals(nextPrincipal)
      nextPrincipal += 1
      delegateMembership(inference, guser, groupId, duser)
      terminalSubjects += duser
      if(i % 1000 == 0) {
        println(s"${i}th subject")
        val future = Future {
          processStatsNow()
        }
      }
    }
   
   for(i <- 1 to numObjects) { // Build the object hierarchy
     val objectNo = i
     val objectName = s"object${objectNo}"
     val objectRoot = allPrincipals(nextPrincipal)
     nextPrincipal += 1
     val objectId = objectRoot.getPid + ":" + objectName
     delegateName(inference, rootObjectAuthority, objectRoot, objectName, objectId, rootObjectId)
     terminalObjects += objectId
     terminalObjectNames += objectName 
     terminalObjectRoots += objectRoot
     if(i % 1000 == 0) {
       println(s"${i}th object")
       val future = Future {
         processStatsNow()
       }
     }
   }

    inferenceQ.offer(inference, timeout, TimeUnit.SECONDS)

    val future = Future {
      processStatsNow()
    }

    logger.info(s"\nPrime is done!")
    logger.info("=============================")
    val numTSubjects = terminalSubjects.length
    val numTObjects = terminalObjects.length
    println(s"\nPrime is done! \nTerminal subjects: $numTSubjects     Terminal objects: $numTObjects")
    println("=============================")
    scala.io.StdIn.readLine()


    var opcount = 0

    // set up perf monitor
    startPerfMonitoring(allowAutoPerfStats = false)

    val r = scala.util.Random
    val op = "accessNamedObjectAtServer"
    //val op = "accessNamedObject"
    while(opcount <= numTObjects-1) {
      val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
      val subject = terminalSubjects(opcount) 
      val objectName = terminalObjectNames(opcount)
      accessNamedObjectAtServerAsync(inference, guard, subject, objectName, "10.103.0.11:7777")
      opcount = opcount + 1
      if((opcount % 1000) == 0)  {
        println(s"opcount: ${opcount}    allPrincipals.length:${allPrincipals.length}    inferenceQ.size=${inferenceQ.size}")
        // Get perfStats async
        val future = Future {
          processStatsNow()
        }
      }
    }


    opcount = 0
    //val op = "accessNamedObjectSingleContext"
    //val op = "accessNamedObject"
    // delegateThenQueryName
    while(opcount <= numTObjects - 1) {
      val inference = inferenceQ.poll(timeout, TimeUnit.SECONDS)
      val delegator = terminalObjectRoots(opcount) 
      val user = allPrincipals(nextPrincipal)
      nextPrincipal = nextPrincipal + 1  
      val objectName = s"obj${opcount}"
      val queryName = terminalObjectNames(opcount) + "/" + objectName
      val toScid = user.getPid + ":" + objectName
      val homeScid = terminalObjects(opcount)
      delegateThenQueryNameAsync(inference, delegator, user, guard, "10.103.0.11:7777", objectName, toScid, homeScid, queryName)
      opcount = opcount + 1
      if((opcount % 1000) == 0)  {
        println(s"opcount: ${opcount}    allPrincipals.length:${allPrincipals.length}    inferenceQ.size=${inferenceQ.size}")
        // Get perfStats async
        val future = Future {
          processStatsNow()
        }
      }
    }
  }


  def delegateThenQueryNameAsync(inference: Safelang, delegator: PrincipalStub, user: PrincipalStub, queryPrincipal: PrincipalStub, queryJvm: String, objectName: String, toScid: String, homeScid: String, queryName: String): Unit = {
    val op = "delegateNameThenQuery"
    val future = Future { 
      delegateNameThenQuery(inference, delegator, user, queryPrincipal, queryJvm, objectName, toScid, homeScid, queryName)
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

  def accessNamedObjectAtServerAsync(inference: Safelang, guard: PrincipalStub, user: PrincipalStub, name: String, serverJvm: String): Unit = {
    val op = "accessNamedObjectAtServer"
    val future = Future { 
      accessNamedObjectAtServer(inference, guard, user, name, serverJvm)
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
