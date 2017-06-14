package safe.programming
package geni

import safe.safelang.{Safelang, SafelangManager, slangPerfCollector, REQ_ENV_DELIMITER}
import safe.safelog.UnSafeException

import scala.util.Random
import scala.collection.mutable.{ListBuffer, Map => MutableMap, Set => MutableSet}
import scala.collection.mutable.{LinkedHashSet => OrderedSet}
import scala.collection.Set
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.LinkedBlockingQueue
import com.typesafe.scalalogging.LazyLogging

/** trait for operations in GENI authorization */
trait GeniOperation extends SafeOperation with StateManagementHelper with LazyLogging {

  /** 
   * Operation list of GENI
   * createSlice* and createSliver* are merely queries
   */
  override val operators = Seq("endorseMA", "endorsePA", "endorseSA", "endorseCP", "endorsePI", "endorseUser",
                      "queryThenCreateProject", "delegateProjectMembership", "queryThenCreateSlice",
                      "delegateSliceControl", "createSlice", "createSliceAtServer", "createSliver",
                      "createSliverAtServer", "delegateSliceThenQuery", "delegateProjectThenQuery")


  println(s"[GeniOperation] operators: ${operators}")

  override val opcountMap = Map("endorseMA"->1, "endorsePA"->1, "endorseSA"->1, "endorseCP"->1,
                       "endorsePI"->1, "endorseUser"->1, "queryThenCreateProject"->2,
                       "delegateProjectMembership"->1, "queryThenCreateSlice"->2,
                       "delegateSliceControl"->1, 
                       "createSlice"->1, "createSliceAtServer"->1,
                       "createSliver"->1, "createSliverAtServer"->1,
                       "delegateSliceThenQuery"->2, "delegateProjectThenQuery"->2)

  val genirootPool = ListBuffer[PrincipalStub]()  // only one root
  var geniroot: PrincipalStub = null

  /**
   * The folllowing are used to keep track of the state of the subjects and objects
   */
  val principalCount =  new AtomicInteger(-1)

  val endorsedMAs = OrderedSet[PrincipalStub]()
  val endorsedPAs = OrderedSet[PrincipalStub]()
  val endorsedSAs = OrderedSet[PrincipalStub]()
  val endorsedCPs = OrderedSet[PrincipalStub]()
  val endorsedPIs = OrderedSet[PrincipalStub]()
  val endorsedUsers = OrderedSet[PrincipalStub]()

  // For projects created by PAs
  val projectCount = new AtomicInteger(-1)
  //val zoneCount = new AtomicInteger(-1)
  val usersInProject = MutableMap[String, MutableMap[PrincipalStub, Int]]()         // project id ==> principal list with delegation hop
  val piInProject = MutableMap[String, MutableMap[PrincipalStub, Int]]()            // project id ==> pi with delegation hop 

  // For slices created by SAs
  val sliceCount = new AtomicInteger(-1)
  val usersInSlice = MutableMap[String, MutableMap[PrincipalStub, Int]]()           // slice id ==> principal list with delegation hop

  /**
   * Table of all possible operations in an application.
   * The table maps an operation name to a operation description. An operation description
   * contains four parts:
   *   - Operand pools from which the operation (randomly) draws input. 
   *     We call them Ready Operand Pools (rops) 
   *   - Operand pools to which the operation's output is written
   *   - Pre-processing function that draws inputs from rops and creates a local slang call desc 
   */
  override val opTable: Map[ String, 
                    Tuple3[ Seq[AnyRef], Seq[AnyRef],
                            (String, Seq[AnyRef]) => OperationFSMSignature] ] = Map(
    "endorseMA" -> ( Seq(genirootPool, allPrincipals), Seq(endorsedMAs), pre_rootEndorse ),
    "endorsePA" -> ( Seq(genirootPool, allPrincipals), Seq(endorsedPAs), pre_rootEndorse ),
    "endorseSA" -> ( Seq(genirootPool, allPrincipals), Seq(endorsedSAs), pre_rootEndorse ),
    "endorseCP" -> ( Seq(genirootPool, allPrincipals), Seq(endorsedCPs), pre_rootEndorse ),
    "endorsePI" -> ( Seq(endorsedMAs, allPrincipals), Seq(endorsedPIs), pre_maEndorse ),
    "endorseUser" -> ( Seq(endorsedMAs, allPrincipals), Seq(endorsedUsers), pre_maEndorse ),
    "queryThenCreateProject" -> ( Seq(endorsedPAs, endorsedPIs), Seq(usersInProject), pre_queryThenCreateProject ),
    "delegateProjectMembership" -> ( Seq(usersInProject, endorsedUsers), Seq(usersInProject), pre_delegateProjectMembership ),
    "queryThenCreateSlice" -> ( Seq(endorsedSAs, usersInProject), Seq(usersInSlice), pre_queryThenCreateSlice ),
    "delegateSliceControl" -> ( Seq(usersInSlice, endorsedUsers), Seq(usersInSlice), pre_delegateSliceControl ),
    "createSlice" -> ( Seq(endorsedSAs, usersInProject), Seq(), pre_createSlice ),
    "createSliceAtServer" -> ( Seq(endorsedSAs, usersInProject), Seq(), pre_createSliceAtServer ),
    "createSliver" -> ( Seq(endorsedCPs, usersInSlice), Seq(), pre_createSliver ),
    "createSliverAtServer" -> ( Seq(endorsedCPs, usersInSlice), Seq(), pre_createSliverAtServer ),
    "delegateSliceThenQuery" -> ( Seq(usersInSlice, endorsedUsers, endorsedCPs), Seq(usersInSlice), pre_delegateSliceThenQuery ),
    "delegateProjectThenQuery" -> ( Seq(usersInProject, endorsedUsers, endorsedSAs), Seq(usersInProject), pre_delegateProjectThenQuery )
  )

  // Not used any more
  //var opDist = Seq("queryThenCreateProject", "delegateProjectMembership", "queryThenCreateSlice",
  //                 "delegateSliceControl", "createSlice", "createSliceAtServer", "createSliver",
  //                 "createSliverAtServer", "delegateProjectThenQuery", "delegateSliceThenQuery" 
  //                )

  /**
   * A preprocessing function takes inputs from a list of ready operand pools (rops).
   * It return a tuple2 which includes a local slang call desc, a list of operands 
   * to update, and delegation chain info.
   */
  def pre_rootEndorse(op: String, rops: Seq[AnyRef]): OperationFSMSignature = {
    assert(rops.length == 2, s"rops must be of length 2: ${rops}") 
    val r = rops(0).asInstanceOf[ListBuffer[PrincipalStub]](0) // geniroot
    val allps = rops(1).asInstanceOf[ListBuffer[PrincipalStub]] // allPrincipals
    val pos = principalCount.incrementAndGet()
    if(pos % 1000 == 0) {
      println(s"pre_rootEndorse: ${pos}    ${allps.length}   ${r}")
    }
    if(pos < allps.length) {
      var p: PrincipalStub = allps(pos)
      val inference = slangManager.createSafelang()
      if(op == "endorsePA") {
        val pa = new PAStub(p)
        pa.init(inference)
        p = pa
      } else if(op == "endorseSA") {
        val sa = new SAStub(p)
        sa.init(inference)
        p = sa
      }
      //} else if(op == "endorseCP") {
      //  val cp = new CPStub(p)
      //  val zoneNo = zoneCount.incrementAndGet()
      //  val zoneName = "zone" + zoneNo
      //  cp.postZone(inference, zoneName)
      //  p = cp
      //}
      val localCallDesc = SlangCallDescription( op, r, p, Seq(emptyEnvs, emptyEnvs), Seq[String]() ) 
      val operandsToUpdate = Seq(Seq(p))
      val chainInfo = (0, s"${r.getJvm}_${op}")
      return (localCallDesc, operandsToUpdate, chainInfo) 
    }  
    null
  }
 
  def pre_maEndorse(op: String, rops: Seq[AnyRef]): OperationFSMSignature = {
    assert(rops.length == 2, s"rops must be of length 2: ${rops}")
    val mas = rops(0).asInstanceOf[OrderedSet[PrincipalStub]]
    val allps = rops(1).asInstanceOf[ListBuffer[PrincipalStub]] // allPrincipals
    val pos = principalCount.incrementAndGet()
    if(pos % 1000 == 0) {
      println(s"pre_maEndorse: ${pos}  ${allps.length}")
    }
    if(pos < allps.length) {
      var p: PrincipalStub = allps(pos)
      val entry: Option[PrincipalStub] = getRandomEntry(mas)
      if(entry.isDefined) {
        val ma: PrincipalStub = entry.get
        val localCallDesc = SlangCallDescription( op, ma, p, Seq(emptyEnvs, emptyEnvs), Seq[String]() )
        val operandsToUpdate = Seq(Seq(p))
        val chainInfo = (0, s"${ma.getJvm}_${op}")
        return (localCallDesc, operandsToUpdate, chainInfo)
      }
    }
    null
  }

  def pre_queryThenCreateProject(op: String, rops: Seq[AnyRef]): OperationFSMSignature = {
    assert(rops.length == 2, s"rops must be of length 2: ${rops}")
    val pas = rops(0).asInstanceOf[OrderedSet[PrincipalStub]]
    val pis = rops(1).asInstanceOf[OrderedSet[PrincipalStub]]
    val paentry: Option[PrincipalStub] = getRandomEntry(pas)
    if(paentry.isDefined) {
      val pa: PAStub = paentry.get.asInstanceOf[PAStub]
      val pientry: Option[PrincipalStub] = getRandomEntry(pis)
      if(pientry.isDefined) {
        val pi: PrincipalStub = pientry.get
        val envs: Seq[String] = Seq(REQ_ENV_DELIMITER + pi.getPid + REQ_ENV_DELIMITER + REQ_ENV_DELIMITER + pi.getSubjectSetTokens(0), emptyEnvs, emptyEnvs)
        val projectNo: Int = projectCount.incrementAndGet()
        val projectName = "project" + projectNo
        val projectId = pa.getPid + ":" + projectName
        val args: Seq[String] = Seq(projectId, pa.getMemberPolicyToken) // project scids

        val localCallDesc = SlangCallDescription( op, pa, pi, envs, args )
        val operandsToUpdate = Seq(Seq(projectId, pi, 0))
        val chainInfo = (0, s"${pa.getJvm}_${op}")
        return (localCallDesc, operandsToUpdate, chainInfo)
      }
    }
    null
  }

  def pre_delegateProjectMembership(op: String, rops: Seq[AnyRef]): OperationFSMSignature = {
    assert(rops.length == 2, s"rops must be of length 2: ${rops}")
    val usofp = rops(0).asInstanceOf[MutableMap[String, MutableMap[PrincipalStub, Int]]] // Users in a project
    val users = rops(1).asInstanceOf[OrderedSet[PrincipalStub]]
    val projectUserEntry: Option[Tuple3[String, PrincipalStub, Int]] = getRandomEntry(usofp)
    if(projectUserEntry.isDefined) {
      val (projectId, delegator, delcount) = projectUserEntry.get
      val userentry: Option[PrincipalStub] = getRandomEntry(users)
      if(userentry.isDefined) {
        val user: PrincipalStub = userentry.get
        if( delegator.getPid != user.getPid && !usofp(projectId).contains(user) ) { // Don't allow self or circular delegation 
        //if( delegator.getPid != user.getPid ) { // Don't allow self or circular delegation 
          val localCallDesc = SlangCallDescription( op, delegator, user, Seq(emptyEnvs, emptyEnvs), Seq(projectId, "true") )
          val operandsToUpdate = Seq(Seq(projectId, user, delcount+1))
          val chainInfo = (0, s"${delegator.getJvm}_${op}")
          return (localCallDesc, operandsToUpdate, chainInfo)
        }
      }
    }
    null
  }


  def pre_queryThenCreateSlice(op: String, rops: Seq[AnyRef]): OperationFSMSignature = {
    assert(rops.length == 2, s"rops must be of length 2: ${rops}")
    val sas = rops(0).asInstanceOf[OrderedSet[PrincipalStub]]
    val usofp = rops(1).asInstanceOf[MutableMap[String, MutableMap[PrincipalStub, Int]]] // Users in a project
    val saentry: Option[PrincipalStub] = getRandomEntry(sas)
    if(saentry.isDefined) {
      val sa = saentry.get.asInstanceOf[SAStub]
      val projectUserEntry: Option[Tuple3[String, PrincipalStub, Int]] = getRandomEntry(usofp)
      if(projectUserEntry.isDefined) {
        val (projectId, user, delcount) = projectUserEntry.get
        val envs = Seq(REQ_ENV_DELIMITER + user.getPid + REQ_ENV_DELIMITER + REQ_ENV_DELIMITER + user.getSubjectSetTokens(0), emptyEnvs, emptyEnvs)
        val sliceNo = sliceCount.incrementAndGet()
        val sliceName = "slice" + sliceNo
        val sliceId = sa.getPid + ":" + sliceName
        val args = Seq(projectId, sliceId, sa.getSliceControlPolicyToken, sa.getSliceDefaultPrivilegeToken)
        val localCallDesc = SlangCallDescription( op, sa, user, envs, args )
        val operandsToUpdate = Seq(Seq(sliceId, user, 0))
        val chainInfo = (0, s"${sa.getJvm}_${op}")
        return (localCallDesc, operandsToUpdate, chainInfo)
      }
    }
    null
  }

  def pre_delegateSliceControl(op: String, rops: Seq[AnyRef]): OperationFSMSignature = {
    assert(rops.length == 2, s"rops must be of length 2: ${rops}")
    val usofs = rops(0).asInstanceOf[MutableMap[String, MutableMap[PrincipalStub, Int]]] // Users in slice
    val users = rops(1).asInstanceOf[OrderedSet[PrincipalStub]]
    val sliceUserEntry: Option[Tuple3[String, PrincipalStub, Int]] = getRandomEntry(usersInSlice)
    if(sliceUserEntry.isDefined) {
      val (sliceId, delegator, delcount) = sliceUserEntry.get
      val userentry: Option[PrincipalStub] = getRandomEntry(endorsedUsers)
      if(userentry.isDefined) {
        val user: PrincipalStub = userentry.get
        if( delegator.getPid != user.getPid && !usofs(sliceId).contains(user) ) { // Don't allow self or circular delegation
        //if( delegator.getPid != user.getPid ) { // Don't allow self or circular delegation
          val localCallDesc = SlangCallDescription( op, delegator, user, Seq(emptyEnvs, emptyEnvs), Seq(sliceId, "true") )
          val operandsToUpdate = Seq(Seq(sliceId, user, delcount+1))
          val chainInfo = (0, s"${delegator.getJvm}_${op}")
          return (localCallDesc, operandsToUpdate, chainInfo)
        }
      }
    }
    null
  }

  def pre_createSlice(op: String, rops: Seq[AnyRef]): OperationFSMSignature = {
    assert(rops.length == 2, s"rops must be of length 2: ${rops}")
    val sas = rops(0).asInstanceOf[OrderedSet[PrincipalStub]]
    val usofp = rops(1).asInstanceOf[MutableMap[String, MutableMap[PrincipalStub, Int]]] // Users in project
    val saentry: Option[PrincipalStub] = getRandomEntry(sas)
    if(saentry.isDefined) {
      val sa = saentry.get.asInstanceOf[SAStub]
      val projectUserEntry: Option[Tuple3[String, PrincipalStub, Int]] = getRandomEntry(usofp)
      if(projectUserEntry.isDefined) {
        val (projectId, user, delcount) = projectUserEntry.get
        val env: String = REQ_ENV_DELIMITER + user.getPid + REQ_ENV_DELIMITER + REQ_ENV_DELIMITER + user.getSubjectSetTokens(0)
        val args = Seq(projectId)
        val localCallDesc = SlangCallDescription( op, sa, env, args )
        val operandsToUpdate = Seq() // only query
        val chainInfo = (delcount, s"${sa.getJvm}_${op}")
        return (localCallDesc, operandsToUpdate, chainInfo)
      }
    }
    null
  }

  def pre_createSliceAtServer(opName: String, rops: Seq[AnyRef]): OperationFSMSignature = {
    val op = "createSlice"
    val serverjvm = testingCacheJvm
    assert(rops.length == 2, s"rops must be of length 2: ${rops}")
    val sas = rops(0).asInstanceOf[OrderedSet[PrincipalStub]]
    val usofp = rops(1).asInstanceOf[MutableMap[String, MutableMap[PrincipalStub, Int]]] // Users in project
    val saentry: Option[PrincipalStub] = getRandomEntry(sas)
    if(saentry.isDefined) {
      val sa = saentry.get.asInstanceOf[SAStub]
      val projectUserEntry: Option[Tuple3[String, PrincipalStub, Int]] = getRandomEntry(usersInProject)
      if(projectUserEntry.isDefined) {
        val (projectId, user, delcount) = projectUserEntry.get
        val env: String = REQ_ENV_DELIMITER + user.getPid + REQ_ENV_DELIMITER + REQ_ENV_DELIMITER + user.getSubjectSetTokens(0)
        val args = Seq(projectId)
        val localCallDesc = SlangCallDescription( op, sa, serverjvm, env, args )
        val operandsToUpdate = Seq() // only query
        val chainInfo = (delcount, s"${serverjvm}_${op}")
        return (localCallDesc, operandsToUpdate, chainInfo)
      }
    }
    null
  }

  def pre_createSliver(op: String, rops: Seq[AnyRef]): OperationFSMSignature = {
    assert(rops.length == 2, s"rops must be of length 2: ${rops}")
    val cps = rops(0).asInstanceOf[OrderedSet[PrincipalStub]]
    val usofs = rops(1).asInstanceOf[MutableMap[String, MutableMap[PrincipalStub, Int]]] // Users in slice
    val cpentry: Option[PrincipalStub] = getRandomEntry(cps)
    if(cpentry.isDefined) {
      val cp: PrincipalStub = cpentry.get
      val sliceUserEntry: Option[Tuple3[String, PrincipalStub, Int]] = getRandomEntry(usofs)
      if(sliceUserEntry.isDefined) {
        val (sliceId, user, delcount) = sliceUserEntry.get
        val env: String = REQ_ENV_DELIMITER + user.getPid + REQ_ENV_DELIMITER + REQ_ENV_DELIMITER + user.getSubjectSetTokens(0)
        val args = Seq(sliceId)
        val localCallDesc = SlangCallDescription( op, cp, env, args )
        val operandsToUpdate = Seq() // only query
        val chainInfo = (delcount, s"${cp.getJvm}_${op}")
        return (localCallDesc, operandsToUpdate, chainInfo)
      }
    }
    null
  }

  def pre_createSliverAtServer(opName: String, rops: Seq[AnyRef]): OperationFSMSignature = {
    val op = "createSliver"
    val serverjvm = testingCacheJvm 
    assert(rops.length == 2, s"rops must be of length 2: ${rops}")
    val cps = rops(0).asInstanceOf[OrderedSet[PrincipalStub]]
    val usofs = rops(1).asInstanceOf[MutableMap[String, MutableMap[PrincipalStub, Int]]] // Users in slice
    val cpentry: Option[PrincipalStub] = getRandomEntry(cps)
    if(cpentry.isDefined) {
      val cp: PrincipalStub = cpentry.get
      val sliceUserEntry: Option[Tuple3[String, PrincipalStub, Int]] = getRandomEntry(usofs)
      if(sliceUserEntry.isDefined) {
        val (sliceId, user, delcount) = sliceUserEntry.get
        val env: String = REQ_ENV_DELIMITER + user.getPid + REQ_ENV_DELIMITER + REQ_ENV_DELIMITER + user.getSubjectSetTokens(0)
        val args = Seq(sliceId)
        val localCallDesc = SlangCallDescription( op, cp, serverjvm, env, args )
        val operandsToUpdate = Seq() // only query
        val chainInfo = (delcount, s"${serverjvm}_${op}")
        return (localCallDesc, operandsToUpdate, chainInfo)
      }
    }
    null
  }

  def pre_delegateSliceThenQuery(op: String, rops: Seq[AnyRef]): OperationFSMSignature = {
    assert(rops.length == 3, s"rops must be of length 3: ${rops}")
    val queryserverJvm = testingCacheJvm
    val usofs = rops(0).asInstanceOf[MutableMap[String, MutableMap[PrincipalStub, Int]]] // Users in slice
    val users = rops(1).asInstanceOf[OrderedSet[PrincipalStub]]
    val cps = rops(2).asInstanceOf[OrderedSet[PrincipalStub]]
    val sliceUserEntry: Option[Tuple3[String, PrincipalStub, Int]] = getRandomEntry(usofs)
    if(sliceUserEntry.isDefined) {
      val (sliceId, delegator, delcount) = sliceUserEntry.get
      val userentry: Option[PrincipalStub] = getRandomEntry(users)
      if(userentry.isDefined) {
        val user: PrincipalStub = userentry.get
        if( delegator.getPid != user.getPid && !usofs(sliceId).contains(user) ) { 
        //if( delegator.getPid != user.getPid ) { 
        // Don't allow self or circular delegation  
          val cpentry: Option[PrincipalStub] = getRandomEntry(cps)
          if(cpentry.isDefined) {
            val cp: PrincipalStub = cpentry.get
            val queryEnvs: String = REQ_ENV_DELIMITER + user.getPid + REQ_ENV_DELIMITER + REQ_ENV_DELIMITER + user.getSubjectSetTokens(0)

            val localCallDesc = SlangCallDescription( op, delegator, user, cp, queryserverJvm, Seq(emptyEnvs, emptyEnvs, queryEnvs), Seq(sliceId, "true") )
            val operandsToUpdate = Seq(Seq(sliceId, user, delcount+1))
            val chainInfo = (0, s"${delegator.getJvm}_${op}")
            return (localCallDesc, operandsToUpdate, chainInfo)
          }
        }
      }
    }
    null
  }

  def pre_delegateProjectThenQuery(op: String, rops: Seq[AnyRef]): OperationFSMSignature = {
    assert(rops.length == 3, s"rops must be of length 3: ${rops}")
    val queryserverJvm = testingCacheJvm
    val usofp = rops(0).asInstanceOf[MutableMap[String, MutableMap[PrincipalStub, Int]]] // Users in project
    val users = rops(1).asInstanceOf[OrderedSet[PrincipalStub]]
    val sas = rops(2).asInstanceOf[OrderedSet[PrincipalStub]]
    val projectUserEntry: Option[Tuple3[String, PrincipalStub, Int]] = getRandomEntry(usofp)
    if(projectUserEntry.isDefined) {
      val (projectId, delegator, delcount) = projectUserEntry.get
      val userentry: Option[PrincipalStub] = getRandomEntry(users)
      if(userentry.isDefined) {
        val user: PrincipalStub = userentry.get
        if( delegator.getPid != user.getPid && !usofp(projectId).contains(user) ) { 
        //if( delegator.getPid != user.getPid ) { 
        // Don't allow self or circular delegation 
          val saentry: Option[PrincipalStub] = getRandomEntry(sas)
          if(saentry.isDefined) {
            val sa: PrincipalStub = saentry.get
            val queryEnvs: String = REQ_ENV_DELIMITER + user.getPid + REQ_ENV_DELIMITER + REQ_ENV_DELIMITER + user.getSubjectSetTokens(0)
            val localCallDesc = SlangCallDescription( op, delegator, user, sa, queryserverJvm, Seq(emptyEnvs, emptyEnvs, queryEnvs), Seq(projectId, "true") )
            val operandsToUpdate = Seq(Seq(projectId, user, delcount+1))
            val chainInfo = (0, s"${delegator.getJvm}_${op}")
            return (localCallDesc, operandsToUpdate, chainInfo)
          }
        }
      }
    }
    null
  }

}
