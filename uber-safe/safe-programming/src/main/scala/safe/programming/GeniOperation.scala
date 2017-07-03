package safe.programming

import safe.safelang.{Safelang, slangPerfCollector}
import safe.safelog.UnSafeException

import scala.util.Random
import scala.collection.mutable.{ListBuffer, Map => MutableMap, Set => MutableSet}
import scala.collection.mutable.{LinkedHashSet => OrderedSet}
import scala.collection.Set
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.LinkedBlockingQueue
import com.typesafe.scalalogging.LazyLogging

/** trait for operations in GENI authorization */
trait GeniOperation extends SafeBench with StateMachineSetHelper with RecordReplay with LazyLogging {

  /** 
   * Operation list of GENI
   * createSlice* and createSliver* are merely queries
   */
  val operators = Seq("endorseMA", "endorsePA", "endorseSA", "endorseCP", "endorsePI", "endorseUser",
                      "queryThenCreateProject", "delegateProjectMembership", "queryThenCreateSlice",
                      "delegateSliceControl", "createSlice", "createSliceAtServer", "createSliver",
                      "createSliverAtServer", "delegateSliceThenQuery", "delegateProjectThenQuery",
                      "addSliverAcl", "queryThenInstallSliverAcl",  "accessSliver", "stitchSlivers",
                      "postAdjacentCP", "queryThenCreateSliver", "queryThenCreateStitchport",
                      "queryName", "delegateObject", "stitchIntraSlice", "queryThenCreateNamedStitchport", "lookupThenStitch")

//  val opcountMap = Map("endorseMA"->1, "endorsePA"->1, "endorseSA"->1, "endorseCP"->1,
//                       "endorsePI"->1, "endorseUser"->1, "queryThenCreateProject"->2,
//                       "delegateProjectMembership"->1, "queryThenCreateSlice"->2,
//                       "delegateSliceControl"->1, "createProject"->1, 
//                       "createSlice"->1, "createSliceAtServer"->1,
//                       "createSliver"->1, "createSliverAtServer"->1,
//                       "delegateSliceThenQuery"->2, "delegateProjectThenQuery"->2,
//                       "addSliverAcl"->1, "queryThenInstallSliverAcl"->2,  "accessSliver"->1, 
//                       "stitchSlivers"->1, "postAdjacentCP"->4, "queryThenCreateSliver"->3, 
//                       "queryThenCreateStitchport"->3,
//                       "queryName"->1, "delegateObject"->2, "stitchIntraSlice"-> 5, "queryThenCreateNamedStitchport"->6, "lookupThenStitch"->2)


  val opcountMap = Map("endorseMA"->1, "endorsePA"->1, "endorseSA"->1, "endorseCP"->1,
                       "endorsePI"->1, "endorseUser"->1, "queryThenCreateProject"->1,
                       "delegateProjectMembership"->1, "queryThenCreateSlice"->1,
                       "delegateSliceControl"->1, "createProject"->1, 
                       "createSlice"->1, "createSliceAtServer"->1,
                       "createSliver"->1, "createSliverAtServer"->1,
                       "delegateSliceThenQuery"->1, "delegateProjectThenQuery"->1,
                       "addSliverAcl"->1, "queryThenInstallSliverAcl"->1,  "accessSliver"->1, 
                       "stitchSlivers"->1, "postAdjacentCP"->1, "queryThenCreateSliver"->1, 
                       "queryThenCreateStitchport"->1,
                       "queryName"->1, "delegateObject"->1, "stitchIntraSlice"-> 1, "queryThenCreateNamedStitchport"->1, "lookupThenStitch"->1)


  var allPrincipals = ListBuffer[PrincipalStub]() 
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
  val usersInProject = MutableMap[String, MutableMap[PrincipalStub, Int]]()         // project id ==> principal list with delegation hop
  val piInProject = MutableMap[String, MutableMap[PrincipalStub, Int]]()            // project id ==> pi with delegation hop 

  // For slices created by SAs
  val sliceCount = new AtomicInteger(-1)
  val usersInSlice = MutableMap[String, MutableMap[PrincipalStub, Int]]()           // slice id ==> principal list with delegation hop

  // For sliver created by CPs
  val sliverCount = new AtomicInteger(-1)
  val sliversInSlice = MutableMap[String, MutableMap[String, PrincipalStub]]()      // slice id ==> sliver list with the creating cp

  val aclsOfSliver = MutableMap[String, MutableMap[String, String]]()               // sliver id ==> acl list with the sliver's controlling slice 

  val zoneCount = new AtomicInteger(-1)
  val vtagCount = new AtomicInteger(-1)


  /** Operands are tuples of input seq and output seq */
  val operandsMap: Map[ String, Tuple2[Seq[AnyRef], Seq[AnyRef]] ] = Map(
  //val operandMap: Map[ String, Tuple2[Seq[ListBuffer[AnyRef]], Seq[ListBuffer[AnyRef]]] ] = Map(
    "endorseMA" -> (Seq(geniroot, allPrincipals), Seq(endorsedMAs)),
    "endorsePA" -> (Seq(geniroot, allPrincipals), Seq(endorsedPAs)),
    "endorseSA" -> (Seq(geniroot, allPrincipals), Seq(endorsedSAs)),
    "endorseCP" -> (Seq(geniroot, allPrincipals), Seq(endorsedCPs)),
    "endorsePI" -> (Seq(endorsedMAs, allPrincipals), Seq(endorsedPIs)),
    "endorseUser" -> (Seq(endorsedMAs, allPrincipals), Seq(endorsedUsers)),
    "queryThenCreateProject" -> (Seq(endorsedPAs, endorsedPIs), Seq(usersInProject)),
    "delegateProjectMembership" -> (Seq(usersInProject, endorsedUsers), Seq(usersInProject)),
    "queryThenCreateSlice" -> (Seq(endorsedSAs, usersInProject), Seq(usersInSlice)),
    "delegateSliceControl" -> (Seq(usersInSlice, endorsedUsers), Seq(usersInSlice)),
    "createSlice" -> (Seq(endorsedSAs, usersInProject), Seq()),
    "createSliceAtServer" -> (Seq(endorsedSAs, usersInProject), Seq()),
    "createSliver" -> (Seq(endorsedCPs, usersInSlice), Seq()),
    "createSliverAtServer" -> (Seq(endorsedCPs, usersInSlice), Seq()),
    "delegateSliceThenQuery" -> (Seq(usersInSlice, endorsedUsers), Seq(usersInSlice)),
    "delegateProjectThenQuery" -> (Seq(usersInProject, endorsedUsers), Seq(usersInProject)),
    "addSliverAcl" -> (Seq(), Seq()), 
    "queryThenInstallSliverAcl" -> (Seq(), Seq()),
    "accessSliver" -> (Seq(), Seq()),
    "stitchSlivers" -> (Seq(), Seq()),
    "postAdjacentCP" -> (Seq(), Seq()), 
    "queryThenCreateSliver" -> (Seq(), Seq()),
    "queryThenCreateStitchport" -> (Seq(), Seq()),
    "queryName" -> (Seq(), Seq()),
    "delegateObject" -> (Seq(), Seq()),
    "stitchIntraSlice" -> (Seq(), Seq()),
    "queryThenCreateNamedStitchport" -> (Seq(), Seq()),
    "lookupThenStitch" -> (Seq(), Seq())
  )

  var opDist = Seq("queryThenCreateProject", "delegateProjectMembership", "queryThenCreateSlice", "delegateSliceControl", "createSlice", "createSliceAtServer", "createSliver", "createSliverAtServer", "delegateProjectThenQuery", "delegateSliceThenQuery", 
"addSliverAcl", "queryThenInstallSliverAcl", "accessSliver", "stitchSlivers", "postAdjacentCP", "queryThenCreateSliver", "queryThenCreateStitchport", "queryName", "delegateObject", "stitchIntraSlice", "queryThenCreateNamedStitchport", "lookupThenStitch"
)

  var accumOpProbs: Seq[Double] = setOpCDF(Seq(1, 2, 1, 2, 0, 0, 0, 0, 0, 0, 
                                               0, 0, 0, 0, 0, 0, 1,
                                               0, 0, 0, 0, 0)) // get the CDF of ops

  /**
   * Given a share distribution of the ops, get the op CDF for roulette
   * Example op shares:   
   * val opShares = Seq(1, 2, 1, 2, 0, 0, 0, 0, 0, 0)
   */
  def setOpCDF(opShares: Seq[Int]): Seq[Double] = {
    assert(opDist.length == opShares.length, s"opDist and opShares must be of same length: ${opDist}   ${opShares}")
    val totalShare = opShares.sum.toDouble
    val normalizedShares = opShares.map(i => i.toDouble/totalShare)
    val _cdf = ListBuffer[Double]()
    var t = 0.0
    for(prob <- normalizedShares) {
      t = t + prob
      _cdf += t
    }
    logger.info(s"ops: ${opDist}")
    logger.info(s"opshares: ${opShares}")
    logger.info(s"cdf: ${_cdf.toSeq}")
    accumOpProbs = _cdf.toSeq
    accumOpProbs
  }

  /** Pick up a random op using roulette */
  def getRandomOp(): String = {
    val p = Random.nextDouble()
    var i = 0
    while(p >= accumOpProbs(i)) {
      i += 1
    }
    //logger.info(s"p=${p}   i=${i}    op=${opDist(i)}")
    opDist(i)
  }

  /**
   * @record  whether to record this request (for the purpose of replay later)
   * @return  a boolean indicating whether the op is done
   * An op might not be done if it's not a valid op or if there are no avaiable operands 
   * It throws out an exception if the op fails 
   */
  def performOperation(inference: Safelang, op: String, torecord: Boolean = false, objectType: String = "User", objectIdx: Int = 0, rootIdx: Int = 0): Boolean = {
    var res = false
    if(operandsMap.contains(op)) {
      val operands = operandsMap(op)
      val inputSeq: Seq[AnyRef] = operands._1
      val outputSeq: Seq[AnyRef] = operands._2
      if(op == "endorseMA" || op == "endorsePA" || op == "endorseSA" || op == "endorseCP") {
        res = rootEndorse(inference, op, outputSeq(0).asInstanceOf[OrderedSet[PrincipalStub]], torecord)
      } else if(op == "endorsePI" || op == "endorseUser") {
        res = maEndorse(inference, op, outputSeq(0).asInstanceOf[OrderedSet[PrincipalStub]], torecord)
      } else if(op == "queryThenCreateProject") {
        res = queryThenCreateProject(inference, torecord, objectType, objectIdx, rootIdx)
      } else if(op == "delegateProjectMembership") {
        res = delegateProjectMembership(inference, torecord, objectType, objectIdx, rootIdx)
      } else if(op == "queryThenCreateSlice") {
        res = queryThenCreateSlice(inference, torecord)
      } else if(op == "delegateSliceControl") {
        res = delegateSliceControl(inference, torecord)
      } else if(op == "createSlice") {
        res = createSlice(inference, torecord)
      } else if(op == "createSliceAtServer") {
        res = createSliceAtServer(inference, testingCacheJvm, torecord)
      } else if(op == "createSliver") {
        res = createSliver(inference, torecord)
      } else if(op == "createSliverAtServer") {
        res = createSliverAtServer(inference, testingCacheJvm, torecord)
      } else if(op == "delegateSliceThenQuery") {
        res = delegateSliceThenQuery(inference, testingCacheJvm, torecord)
      } else if(op == "delegateProjectThenQuery") {
        res = delegateProjectThenQuery(inference, testingCacheJvm, torecord) // TODO
      } else if(op == "addSliverAcl") {
        res = addSliverAcl(inference, torecord)
      } else if(op == "queryThenInstallSliverAcl") {
        res = queryThenInstallSliverAcl(inference, torecord)
      } else if(op == "accessSliver") {
        res = accessSliver(inference, torecord)
      } else if(op == "stitchSlivers") {
        res = stitchSlivers(inference, torecord)
      } else if(op == "postAdjacentCP") {
        res = postAdjacentCP(inference, torecord)
      } else if(op == "queryThenCreateSliver") {
        res = queryThenCreateSliver(inference, torecord)
      } else if(op == "queryThenCreateStitchport") {
        res = queryThenCreateStitchport(inference, torecord, objectType, objectIdx, rootIdx)
      } else if(op == "queryName") {
        res = queryName(inference, torecord, objectType, objectIdx, rootIdx)
      } else if(op == "delegateObject") {
        res = delegateObject(inference, torecord, objectType, objectIdx, rootIdx)
      } else if(op == "stitchIntraSlice") {
        res = stitchIntraSlice(inference, torecord, objectType, objectIdx, rootIdx)
      } else if(op == "queryThenCreateNamedStitchport") {
        res = queryThenCreateNamedStitchport(inference, torecord, objectType, objectIdx, rootIdx)
      } else if(op == "lookupThenStitch") {
        res = lookupThenStitch(inference, torecord)
      } else {
        println(s"unmatched op: ${op}")
      }
    } else {
      println(s"undefined op: ${op}")
    }
    res
  }

  /**
   * A delegation of slice is followed immediately by a query that requires the delegation
   * The caller must ensure that the query server principal is installed on the specified
   * server.
   * Example: queryserverJvm=152.3.145.3:7777 
   * Userful for cache testing
   */
  def delegateSliceThenQuery(inference: Safelang, queryserverJvm: String, torecord: Boolean = false): Boolean = {
    var res = false
    val op = "delegateSliceThenQuery"
    val sliceUserEntry: Option[Tuple3[String, PrincipalStub, Int]] = getRandomEntry(usersInSlice)
    if(sliceUserEntry.isDefined) {
      val (sliceId, delegator, delcount) = sliceUserEntry.get
      val userentry: Option[PrincipalStub] = getRandomEntry(endorsedUsers)
      if(userentry.isDefined) {
        val user: PrincipalStub = userentry.get
        //if(delegator.getPid != user.getPid) { // Don't allow self delegation  
        if(!usersInSlice(sliceId).contains(user)) { // Delegate to a user that doesn't have the delegation yet  
          val cpentry: Option[PrincipalStub] = getRandomEntry(endorsedCPs)
          if(cpentry.isDefined) {
            val cp: PrincipalStub = cpentry.get
            val queryEnvs: String = ":" + user.getPid + ":" + ":" + user.getSubjectSetTokens(0)
            delegateThenQuery(inference, op, delegator, user, cp, queryserverJvm,
                              envs=Seq(emptyEnvs, emptyEnvs, queryEnvs), args=Seq(sliceId, "true"))
            recordOperation("delegateThenQuery", 
              (op, delegator, user, cp, queryserverJvm, Seq(emptyEnvs, emptyEnvs, queryEnvs), Seq(sliceId, "true")),
              torecord)
            slangPerfCollector.addDelegation(0, s"${delegator.getJvm}_${op}")
            // Add user into usersInSlice
            addEntry(usersInSlice, sliceId, user, delcount+1)
            res = true
          }
        }
      }
    }
    res
  }

  /**
   * A delegation of project is followed immediately by a query that requires the delegation
   * The caller must ensure that the query server principal is installed on the specified
   * server.
   * Example: queryserverJvm=152.3.145.3:7777 
   * Userful for cache testing
   */
  def delegateProjectThenQuery(inference: Safelang, queryserverJvm: String, torecord: Boolean = false): Boolean = {
    var res = false
    val op = "delegateProjectThenQuery"
    val projectUserEntry: Option[Tuple3[String, PrincipalStub, Int]] = getRandomEntry(usersInProject)
    if(projectUserEntry.isDefined) {
      val (projectId, delegator, delcount) = projectUserEntry.get
      val userentry: Option[PrincipalStub] = getRandomEntry(endorsedUsers)
      if(userentry.isDefined) {
        val user: PrincipalStub = userentry.get
        //if(delegator.getPid != user.getPid) { // Don't allow self delegation 
        if(!usersInProject(projectId).contains(user)) { // Delegate to a user that doesn't have the delegation yet  
          val saentry: Option[PrincipalStub] = getRandomEntry(endorsedSAs)
          if(saentry.isDefined) {
            val sa: PrincipalStub = saentry.get
            val queryEnvs: String = ":" + user.getPid + ":" + ":" + user.getSubjectSetTokens(0)
            delegateThenQuery(inference, op, delegator, user, sa, queryserverJvm,
                              envs=Seq(emptyEnvs, emptyEnvs, queryEnvs), args=Seq(projectId, "true"))
            recordOperation("delegateThenQuery",
              (op, delegator, user, sa, queryserverJvm, Seq(emptyEnvs, emptyEnvs, queryEnvs), Seq(projectId, "true")), 
              torecord)
            slangPerfCollector.addDelegation(0, s"${delegator.getJvm}_${op}")
            // Add user into usersInProject
            addEntry(usersInProject, projectId, user, delcount+1)
            res = true
          }
        }
      }
    }
    res
  }

  /**
   * Query to a specified server to see if it is allowed to create a sliver
   * The caller must ensure that the query server principal is installed on the server
   * Useful for cache testing
   */
  def createSliverAtServer(inference: Safelang, serverjvm: String, torecord: Boolean = false): Boolean = {
    var res = false
    val op = "createSliver"
    val cpentry: Option[PrincipalStub] = getRandomEntry(endorsedCPs)
    if(cpentry.isDefined) {
      val cp: PrincipalStub = cpentry.get
      val sliceUserEntry: Option[Tuple3[String, PrincipalStub, Int]] = getRandomEntry(usersInSlice)
      if(sliceUserEntry.isDefined) {
        val (sliceId, user, delcount) = sliceUserEntry.get
        val env: String = ":" + user.getPid + ":" + ":" + user.getSubjectSetTokens(0)
        val args = Seq(sliceId)
        cp.remoteCallToServer(inference, op, serverjvm, env, args)
        recordOperation("remoteCallToServer", (cp, op, serverjvm, env, args), torecord)
        slangPerfCollector.addDelegation(delcount, s"${cp.getJvm}_${op}")
        // Upate usersInSliver when create the sliver slogset
        res = true
      }
    }
    res
  }

  def createSliver(inference: Safelang, torecord: Boolean = false): Boolean = {
    var res = false
    val op = "createSliver"
    val cpentry: Option[PrincipalStub] = getRandomEntry(endorsedCPs)
    if(cpentry.isDefined) {
      val cp: PrincipalStub = cpentry.get
      val sliceUserEntry: Option[Tuple3[String, PrincipalStub, Int]] = getRandomEntry(usersInSlice)
      if(sliceUserEntry.isDefined) {
        val (sliceId, user, delcount) = sliceUserEntry.get
        val env: String = ":" + user.getPid + ":" + ":" + user.getSubjectSetTokens(0)
        val args = Seq(sliceId)
        cp.simpleRemoteCall(inference, op, env, args)
        recordOperation("simpleRemoteCall", (cp, op, env, args), torecord)
        slangPerfCollector.addDelegation(delcount, s"${cp.getJvm}_${op}")
        // Upate usersInSliver when create the sliver slogset
        res = true
      }
    }
    res
  }

  def queryName(inference: Safelang, torecord: Boolean = false, objectType: String = "User", objectIdx: Int = 0, rootIdx: Int = 0): Boolean = {
    var res = false
    val op = "queryName"
    if(objectType == "User") {
      val ma: PrincipalStub = getEntryAt(endorsedMAs, rootIdx).asInstanceOf[PrincipalStub]
      val user: PrincipalStub = getEntryAt(endorsedUsers, objectIdx).asInstanceOf[PrincipalStub]     
      val rootDir = ma.getPid + ":root"
      val username = "Username" + user.getPid 
      val env = emptyEnvs
      val args = Seq(rootDir, username) 
      user.simpleRemoteCall(inference, op, env, args)
      recordOperation("simpleRemoteCall", (user, op, env, args), torecord)
      slangPerfCollector.addDelegation(1, s"${user.getJvm}_${op}")
      res = true
    } else if(objectType == "PI") {
      val ma: PrincipalStub = getEntryAt(endorsedMAs, rootIdx).asInstanceOf[PrincipalStub]
      val pi: PrincipalStub = getEntryAt(endorsedPIs, objectIdx).asInstanceOf[PrincipalStub] 
      val rootDir = ma.getPid + ":root"
      val piname = "PIname" + pi.getPid    
      val env = emptyEnvs
      val args = Seq(rootDir, piname) 
      pi.simpleRemoteCall(inference, op, env, args)
      recordOperation("simpleRemoteCall", (pi, op, env, args), torecord)
      slangPerfCollector.addDelegation(1, s"${pi.getJvm}_${op}")
      res = true
    } else if(objectType == "Project") {
      val pa: PrincipalStub = getEntryAt(endorsedPAs, rootIdx).asInstanceOf[PrincipalStub]
      val projectId: String = getEntryAt(usersInProject.keySet, objectIdx).asInstanceOf[String] 
      val rootDir = pa.getPid + ":root"
      val projectname = "Projectname" + projectId.split(":")(1)    
      val env = emptyEnvs
      val args = Seq(rootDir, projectname) 
      pa.simpleRemoteCall(inference, op, env, args)
      recordOperation("simpleRemoteCall", (pa, op, env, args), torecord)
      slangPerfCollector.addDelegation(1, s"${pa.getJvm}_${op}")
      res = true
    } else if(objectType == "Slice") {
      val sa: PrincipalStub = getEntryAt(endorsedSAs, rootIdx).asInstanceOf[PrincipalStub]
      val sliceId: String = getEntryAt(usersInSlice.keySet, objectIdx).asInstanceOf[String] 
      val rootDir = sa.getPid + ":root"
      val slicename = "Slicename" + sliceId.split(":")(1)    
      val env = emptyEnvs
      val args = Seq(rootDir, slicename) 
      sa.simpleRemoteCall(inference, op, env, args)
      recordOperation("simpleRemoteCall", (sa, op, env, args), torecord)
      slangPerfCollector.addDelegation(1, s"${sa.getJvm}_${op}")
      res = true
    } else if(objectType == "Sliver") {


    } else {
      logger.error(s"unrecognized objectType: ${objectType}") 
    } 
    res
  }

   var delegateObjCount = 0
   var delegateSliceCount = 0

  /* Install names on an aggregate */
  def delegateObject(inference: Safelang, torecord: Boolean = false, objectType: String = "Slice", objectIdx: Int = 0, rootIdx: Int = 0): Boolean = {
    var res = false
    val op = "delegateObject"
    delegateObjCount += 1
    if(objectType == "Slice") {
      val cp: PrincipalStub = getEntryAt(endorsedCPs, rootIdx).asInstanceOf[PrincipalStub]
      val rootDir = cp.getPid + ":root"
      val sliceId: String = getEntryAt(usersInSlice.keySet, objectIdx).asInstanceOf[String]    
      val slicename = "CPSlicename" + sliceId.split(":")(1)
      val scidOnAgg = cp.getPid + ":" + sliceId.split(":")(1)
      val envs = Seq(emptyEnvs, emptyEnvs)
      val args = Seq(slicename, scidOnAgg, rootDir)
      simpleDelegate(inference, op, cp, cp, envs, args)
      recordOperation("simpleDelegate", (op, cp, cp, envs, args), torecord) 
      slangPerfCollector.addDelegation(1, s"${cp.getJvm}_${op}")
      delegateSliceCount += 1
      res = true
    } else if(objectType == "Sliver") {

    } else {
      logger.error(s"unrecognized objectType: ${objectType}") 
    } 
    //println(s"delegateObjCount=${delegateObjCount}     delegateSliceCount=${delegateSliceCount}")
    //println("=============================")
    res
  }



  /**
   * Query to a specified server if it is allowed to create a slice
   * The caller must ensure that the query server principal is installed on the server
   * Useful for cache testing
   */
  def createSliceAtServer(inference: Safelang, serverjvm: String, torecord: Boolean = false): Boolean = {
    var res = false
    val op = "createSlice"
    val saentry: Option[PrincipalStub] = getRandomEntry(endorsedSAs)
    if(saentry.isDefined) {
      val sa = saentry.get.asInstanceOf[SAStub]
      val projectUserEntry: Option[Tuple3[String, PrincipalStub, Int]] = getRandomEntry(usersInProject)
      if(projectUserEntry.isDefined) {
        val (projectId, user, delcount) = projectUserEntry.get
        val env: String = ":" + user.getPid + ":" + ":" + user.getSubjectSetTokens(0)
        val args = Seq(projectId)
        sa.remoteCallToServer(inference, op, serverjvm, env, args)
        recordOperation("remoteCallToServer", (sa, op, serverjvm, env, args), torecord)
        slangPerfCollector.addDelegation(delcount, s"${sa.getJvm}_${op}")
        // Upate usersInSlice when create the slice slogset
        res = true
      }
    }
    res
  }

  /* Query if a user can add an acl on a sliver */
  def addSliverAcl(inference: Safelang, torecord: Boolean = false): Boolean = {
    var res = false
    val op = "addSliverAcl"
    val sliceSliverEntry: Option[Tuple3[String, String, PrincipalStub]] = getRandomEntry(sliversInSlice)
    if(sliceSliverEntry.isDefined) {  // randomly pick up a sliver
      val (sliceId, sliverId, cp) = sliceSliverEntry.get
      if(usersInSlice.contains(sliceId)) {
        val slicemembers = usersInSlice(sliceId)
        val mentry = getRandomEntry(slicemembers) 
        if(mentry.isDefined) {
          val (user, delcount) = mentry.get
          val env: String = ":" + user.getPid + ":" + ":" + user.getSubjectSetTokens(0)
          val args = Seq(sliverId)
          cp.simpleRemoteCall(inference, op, env, args)
          recordOperation("simpleRemoteCall", (cp, op, env, args), torecord)
          slangPerfCollector.addDelegation(delcount, s"${cp.getJvm}_${op}")
          // Upate usersInSliver when create the sliver slogset
          res = true 
        }
      }
    }
    res
  }

  def queryThenInstallSliverAcl(inference: Safelang, torecord: Boolean = false): Boolean = {
    var res = false
    val op = "queryThenInstallSliverAcl"
    val sliceSliverEntry: Option[Tuple3[String, String, PrincipalStub]] = getRandomEntry(sliversInSlice)
    if(sliceSliverEntry.isDefined) { // randomly pick up a sliver
      val (sliceId, sliverId, cp) = sliceSliverEntry.get
      if(usersInSlice.contains(sliceId)) {
        val slicemembers = usersInSlice(sliceId)
        val mentry = getRandomEntry(slicemembers) 
        if(mentry.isDefined) {
          val (user, delcount) = mentry.get
          val env: String = ":" + user.getPid + ":" + ":" + user.getSubjectSetTokens(0)
          val sliceSliverEntry2: Option[Tuple3[String, String, PrincipalStub]] = getRandomEntry(sliversInSlice)
          if(sliceSliverEntry2.isDefined) {
            val (sliceId2, sliverId2, cp2) = sliceSliverEntry2.get 
            if(sliceId2 != sliceId) {
              val args = Seq(sliverId, sliceId2)
              cp.simpleRemoteCall(inference, op, env, args)
              recordOperation("simpleRemoteCall", (cp, op, env, args), torecord)
              slangPerfCollector.addDelegation(delcount, s"${cp.getJvm}_${op}")
              // Add acl into aclsOfSliver
              addEntry(aclsOfSliver, sliverId, sliceId2, sliceId)
              res = true 
            } else {
              println(s"[queryThenInstallSliverAcl] acl slice ($sliceId2) and the containing slice ($sliceId) is the same")
            }
          }
        }
      }
    }
    res
  }

  /* Query if a user can access a sliver according to the sliver's acl list */
  def accessSliver(inference: Safelang, torecord: Boolean = false): Boolean = {
    var res = false
    val op = "accessSliver"
    val sliceSliverEntry: Option[Tuple3[String, String, PrincipalStub]] = getRandomEntry(sliversInSlice)
    if(sliceSliverEntry.isDefined) {  // randomly pick up a sliver
      val (sliceId, sliverId, cp) = sliceSliverEntry.get
      val acls = aclsOfSliver(sliverId)  // aclsOfSliver must contain sliverId
      val aclEntry = getRandomEntry(acls)
      if(aclEntry.isDefined) {
        val (groupId, controlSlice) = aclEntry.get
        if(usersInSlice.contains(groupId)) {
          val slicemembers = usersInSlice(groupId)
          val mentry = getRandomEntry(slicemembers) 
          if(mentry.isDefined) {
            val (user, delcount) = mentry.get
            val env: String = ":" + user.getPid + ":" + ":" + user.getSubjectSetTokens(0)
            val args = Seq(sliverId)
            cp.simpleRemoteCall(inference, op, env, args)
            recordOperation("simpleRemoteCall", (cp, op, env, args), torecord)
            slangPerfCollector.addDelegation(delcount, s"${cp.getJvm}_${op}")
            // Upate usersInSliver when create the sliver slogset
            res = true 
          }
        }
      } else {
        println(s"No acl entry for sliver ${sliverId}")
      }
    }
    res
  }

  def lookupThenStitch(inference: Safelang, torecord: Boolean = false): Boolean = {
    var res = false
    val op = "lookupThenStitch"
    val cp: CPStub = getEntryAt(endorsedCPs, endorsedCPs.size-2).asInstanceOf[CPStub]
    val sliceSliverEntry: Option[Tuple3[String, String, PrincipalStub]] = getRandomEntry(sliversInSlice)
    if(sliceSliverEntry.isDefined) {  // randomly pick up a sliver
      val (sliceId, sliverId, cp) = sliceSliverEntry.get
      val acls = aclsOfSliver(sliverId)  // aclsOfSliver must contain sliverId
      val aclEntry = getRandomEntry(acls)
      if(aclEntry.isDefined) {
        val (groupId, controlSlice) = aclEntry.get
        if(sliversInSlice.contains(groupId)) { // randomly pick up a sliver from the acl slice group
          val sliverSet = sliversInSlice(groupId)
          val sliverEntry: Option[Tuple2[String, PrincipalStub]] = getRandomEntry(sliverSet) 
          if(sliverEntry.isDefined) {
            val (srcSliverId, srcCP) = sliverEntry.get // assume all cps are adjacent
            if(srcSliverId != sliverId) { 
              if(usersInSlice.contains(groupId)) {  // pick up a user from the acl slice group
                val slicemembers = usersInSlice(groupId)
                val mentry = getRandomEntry(slicemembers) 
                if(mentry.isDefined) {
                  val (user, delcount) = mentry.get
                  val env: String = ":" + user.getPid + ":" + ":" + user.getSubjectSetTokens(0)
                  val args = Seq(srcSliverId, sliverId, sliceId)
                  cp.simpleRemoteCall(inference, op, env, args)
                  recordOperation("simpleRemoteCall", (cp, op, env, args), torecord)
                  slangPerfCollector.addDelegation(delcount, s"${cp.getJvm}_${op}")
                  // Upate usersInSliver when create the sliver slogset
                  res = true 
                }
              }
            } else {
               println(s"src (${srcSliverId}) and peer (${sliverId}) slivers are the same")
            }
          } 
        }
      }
    }
    res
  }

  /* Query: a user requests to stitch two slivers */
  def stitchSlivers(inference: Safelang, torecord: Boolean = false): Boolean = {
    var res = false
    val op = "stitchSlivers"
    val sliceSliverEntry: Option[Tuple3[String, String, PrincipalStub]] = getRandomEntry(sliversInSlice)
    if(sliceSliverEntry.isDefined) {  // randomly pick up a sliver
      val (sliceId, sliverId, cp) = sliceSliverEntry.get
      val acls = aclsOfSliver(sliverId)  // aclsOfSliver must contain sliverId
      val aclEntry = getRandomEntry(acls)
      if(aclEntry.isDefined) {
        val (groupId, controlSlice) = aclEntry.get
        if(sliversInSlice.contains(groupId)) { // randomly pick up a sliver from the acl slice group
          val sliverSet = sliversInSlice(groupId)
          val sliverEntry: Option[Tuple2[String, PrincipalStub]] = getRandomEntry(sliverSet) 
          if(sliverEntry.isDefined) {
            val (srcSliverId, srcCP) = sliverEntry.get // assume all cps are adjacent
            if(srcSliverId != sliverId) { 
              if(usersInSlice.contains(groupId)) {  // pick up a user from the acl slice group
                val slicemembers = usersInSlice(groupId)
                val mentry = getRandomEntry(slicemembers) 
                if(mentry.isDefined) {
                  val (user, delcount) = mentry.get
                  val env: String = ":" + user.getPid + ":" + ":" + user.getSubjectSetTokens(0)
                  val args = Seq(srcSliverId, sliverId)
                  srcCP.simpleRemoteCall(inference, op, env, args)
                  recordOperation("simpleRemoteCall", (srcCP, op, env, args), torecord)
                  slangPerfCollector.addDelegation(delcount, s"${srcCP.getJvm}_${op}")
                  // Upate usersInSliver when create the sliver slogset
                  res = true 
                }
              }
            } else {
               println(s"src (${srcSliverId}) and peer (${sliverId}) slivers are the same")
            }
          } 
        }
      }
    }
    res
  }

  /* Note: We'll do |endorsedCPs|*|endorsedCPs| operations! */
  def postAdjacentCP(inference: Safelang, torecord: Boolean = false): Boolean = {
    var res = false
    val op = "postAdjacentCP"
    for(cp <- endorsedCPs) { 
      for(adjacentcp <- endorsedCPs) {
        val env = emptyEnvs
        val args = Seq(adjacentcp.getPid) 
        cp.simpleRemoteCall(inference, op, env, args)
        recordOperation("simpleRemoteCall", (cp, op, env, args), torecord)
        slangPerfCollector.addDelegation(0, s"${cp.getJvm}_${op}")
        res = true
      }
    }
    res
  }

  def createSlice(inference: Safelang, torecord: Boolean = false): Boolean = {
    var res = false
    val op = "createSlice"
    val saentry: Option[PrincipalStub] = getRandomEntry(endorsedSAs)
    if(saentry.isDefined) {
      val sa = saentry.get.asInstanceOf[SAStub]
      val projectUserEntry: Option[Tuple3[String, PrincipalStub, Int]] = getRandomEntry(usersInProject)
      if(projectUserEntry.isDefined) {
        val (projectId, user, delcount) = projectUserEntry.get
        val env: String = ":" + user.getPid + ":" + ":" + user.getSubjectSetTokens(0)
        val args = Seq(projectId)
        sa.simpleRemoteCall(inference, op, env, args)
        recordOperation("simpleRemoteCall", (sa, op, env, args), torecord)
        slangPerfCollector.addDelegation(delcount, s"${sa.getJvm}_${op}")
        // Upate usersInSlice when create the slice slogset
        res = true
      }
    }
    res
  }

  def delegateSliceControl(inference: Safelang, torecord: Boolean = false): Boolean = {
    var res = false
    val op = "delegateSliceControl"
    val sliceUserEntry: Option[Tuple3[String, PrincipalStub, Int]] = getRandomEntry(usersInSlice)
    if(sliceUserEntry.isDefined) {
      val (sliceId, delegator, delcount) = sliceUserEntry.get
      val userentry: Option[PrincipalStub] = getRandomEntry(endorsedUsers)
      if(userentry.isDefined) {
        val user: PrincipalStub = userentry.get
        //if(delegator.getPid != user.getPid) { // Don't allow self delegation 
          //user = delegator // testing
        if(!usersInSlice(sliceId).contains(user)) { // Delegate to a user that doesn't have the delegation yet  
          simpleDelegate(inference, op, delegator, user, args=Seq(sliceId, "true"))
          recordOperation("simpleDelegate",
            (op, delegator, user, Seq(emptyEnvs, emptyEnvs), Seq(sliceId, "true")),
            torecord)
          slangPerfCollector.addDelegation(0, s"${delegator.getJvm}_${op}")
          // Add user into usersInSlice
          addEntry(usersInSlice, sliceId, user, delcount+1)
          res = true
        }
      }
    }
    res
  }

  def queryThenCreateSliver(inference: Safelang, torecord: Boolean = false): Boolean = {
    var res = false
    val op = "queryThenCreateSliver"
    val cpentry: Option[PrincipalStub] = getRandomEntry(endorsedCPs)
    if(cpentry.isDefined) {
      val cp: PrincipalStub = cpentry.get
      val sliceUserEntry: Option[Tuple3[String, PrincipalStub, Int]] = getRandomEntry(usersInSlice)
      if(sliceUserEntry.isDefined) {
        val (sliceId, user, delcount) = sliceUserEntry.get
        val env = ":" + user.getPid + ":" + ":" + user.getSubjectSetTokens(0)
        val sliverNo = sliverCount.incrementAndGet()
        val sliverName = "sliver" + sliverNo
        val sliverId = cp.getPid + ":" + sliverName
        val args = Seq(sliverId, sliceId)
        cp.simpleRemoteCall(inference, op, env, args)
        recordOperation("simpleRemoteCall", (cp, op, env, args), torecord) 
        slangPerfCollector.addDelegation(delcount, s"${cp.getJvm}_${op}")
        // Add sliver into sliversInSlice, indexed by sliceId
        addEntry(sliversInSlice, sliceId, sliverId, cp) // using cp: checking a sliver does need to check the slice
        // Add acl into aclsOfSliver,  indexed by sliverId
        addEntry(aclsOfSliver, sliverId, sliceId, sliceId)  
        res = true
      }
    }
    res
  }

  def queryThenCreateStitchport(inference: Safelang, torecord: Boolean = false, objectType: String = "", objectIdx: Int = 0, rootIdx: Int = 0): Boolean = { 
    var res = false
    val op = "queryThenCreateStitchport"
    val cp: CPStub = getEntryAt(endorsedCPs, rootIdx).asInstanceOf[CPStub]
    val sliceId: String = getEntryAt(usersInSlice.keySet, objectIdx).asInstanceOf[String]
    val userset = usersInSlice(sliceId)
    val userentry = getRandomEntry(userset)
    if(userentry.isDefined) {
      val (user, delcount) = userentry.get
      val env = ":" + user.getPid + ":" + ":" + user.getSubjectSetTokens(0)
      val sliverNo = sliverCount.incrementAndGet()
      val sliverName = "sliver" + sliverNo
      val sliverId = cp.getPid + ":" + sliverName
      val zoneId = cp.getZoneId
      val vtagNo = vtagCount.incrementAndGet()
      val vlantag = "vtag" + vtagNo 
      val args = Seq(sliverId, sliceId, zoneId, vlantag)
      cp.simpleRemoteCall(inference, op, env, args)
      recordOperation("simpleRemoteCall", (cp, op, env, args), torecord) 
      slangPerfCollector.addDelegation(delcount, s"${cp.getJvm}_${op}")
      // Add sliver into sliversInSlice, indexed by sliceId
      addEntry(sliversInSlice, sliceId, sliverId, cp) // checking a sliver does need to check the slice
      // Add acl into aclsOfSliver,  indexed by sliverId
      addEntry(aclsOfSliver, sliverId, sliceId, sliceId)  
      res = true
    } else {
       logger.error(s"User entry is not defined. ${userset.keySet}")
    }
    res
  }

  def queryThenCreateNamedStitchport(inference: Safelang, torecord: Boolean = false, objectType: String = "", objectIdx: Int = 0, rootIdx: Int = 0): Boolean = { 
    var res = false
    val op = "queryThenCreateNamedStitchport"
    val cp: CPStub = getEntryAt(endorsedCPs, endorsedCPs.size-2).asInstanceOf[CPStub]
    val sliceId: String = getEntryAt(usersInSlice.keySet, objectIdx).asInstanceOf[String]
    val sliceId2: String = getEntryAt(usersInSlice.keySet, rootIdx).asInstanceOf[String]
    val userset = usersInSlice(sliceId)
    val userentry = getRandomEntry(userset)
    if(userentry.isDefined) {
      val (user, delcount) = userentry.get
      val env = ":" + user.getPid + ":" + ":" + user.getSubjectSetTokens(0)
      val sliverNo = sliverCount.incrementAndGet()
      val sliverName = "sliver" + sliverNo
      val sliverId = cp.getPid + ":" + sliverName
      val zoneId = cp.getZoneId
      val vtagNo = vtagCount.incrementAndGet()
      val vlantag = "vtag" + vtagNo 
      val args = Seq(sliverId, sliceId, zoneId, vlantag, sliceId2)
      cp.simpleRemoteCall(inference, op, env, args)
      recordOperation("simpleRemoteCall", (cp, op, env, args), torecord) 
      slangPerfCollector.addDelegation(delcount, s"${cp.getJvm}_${op}")
      // Add sliver into sliversInSlice, indexed by sliceId
      addEntry(sliversInSlice, sliceId, sliverId, cp) // checking a sliver does need to check the slice
      // Add acl into aclsOfSliver,  indexed by sliverId
      addEntry(aclsOfSliver, sliverId, sliceId2, sliceId)  
      res = true
    } else {
       logger.error(s"User entry is not defined. ${userset.keySet}")
    }
    res
  }


  def stitchIntraSlice(inference: Safelang, torecord: Boolean = false, objectType: String = "", objectIdx: Int = 0, rootIdx: Int = 0): Boolean = { 
    var res = false
    val op = "stitchIntraSlice"
    val cp: CPStub = getEntryAt(endorsedCPs,  endorsedCPs.size-1).asInstanceOf[CPStub]    
    val sliceIdx = objectType.toInt
    val sliceId: String = getEntryAt(sliversInSlice.keySet, sliceIdx).asInstanceOf[String]
    val sliverset = sliversInSlice(sliceId)
    val sliverId1 = getEntryAt(sliverset.keySet, objectIdx).asInstanceOf[String]
    val sliverId2 = getEntryAt(sliverset.keySet, rootIdx).asInstanceOf[String] 

    val userset = usersInSlice(sliceId)
    val userentry = getRandomEntry(userset)
    if(userentry.isDefined) {
      val (user, delcount) = userentry.get
      val env = ":" + user.getPid + ":" + ":" + user.getSubjectSetTokens(0)
      val sliverNo = sliverCount.incrementAndGet()
      val sliverName = "sliver" + sliverNo
      val sliverId = cp.getPid + ":" + sliverName
      val zoneId = cp.getZoneId
      val vtagNo = vtagCount.incrementAndGet()
      val vlantag = "vtag" + vtagNo 
      val args = Seq(sliverId1, sliverId2, sliceId, sliverId, zoneId, vlantag)
      cp.simpleRemoteCall(inference, op, env, args)
      recordOperation("simpleRemoteCall", (cp, op, env, args), torecord) 
      slangPerfCollector.addDelegation(delcount, s"${cp.getJvm}_${op}")
      // Add sliver into sliversInSlice, indexed by sliceId
      addEntry(sliversInSlice, sliceId, sliverId, cp) // checking a sliver does need to check the slice
      // Add acl into aclsOfSliver,  indexed by sliverId
      addEntry(aclsOfSliver, sliverId, sliceId, sliceId)  
      res = true
    }
    res
  }



  def queryThenCreateSlice(inference: Safelang, torecord: Boolean = false): Boolean = {
    var res = false
    val op = "queryThenCreateSlice"
    val saentry: Option[PrincipalStub] = Some(getEntryAt(endorsedSAs, 0).asInstanceOf[PrincipalStub])
    if(saentry.isDefined) {
      val sa = saentry.get.asInstanceOf[SAStub]
      val projectUserEntry: Option[Tuple3[String, PrincipalStub, Int]] = getRandomEntry(usersInProject)
      if(projectUserEntry.isDefined) {
        val (projectId, user, delcount) = projectUserEntry.get
        val envs = Seq(":" + user.getPid + ":" + ":" + user.getSubjectSetTokens(0), emptyEnvs, emptyEnvs)
        val sliceNo = sliceCount.incrementAndGet()
        val sliceName = "slice" + sliceNo
        val sliceId = sa.getPid + ":" + sliceName
        val args = Seq(projectId, sliceId, sa.getSliceDefaultPrivilegeToken)
        simpleDelegate(inference, op, sa, user, envs, args)
        recordOperation("simpleDelegate", (op, sa, user, envs, args), torecord) 
        slangPerfCollector.addDelegation(delcount, s"${sa.getJvm}_${op}")
        // Add user into usersInSlice, indexed by sliceId
        addEntry(usersInSlice, sliceId, user, 0)
        //addEntry(usersInSlice, sliceId, user, delcount+1)   // checking a slice doesn't need to check the project      
        res = true
      }
    }
    res
  }

  def delegateProjectMembership(inference: Safelang, torecord: Boolean = false, objectType: String = "User", objectIdx: Int = 0, rootIdx: Int = 0): Boolean = {
    var res = false
    val op = "delegateProjectMembership"
    val projectId = getEntryAt(piInProject.keySet, objectIdx).asInstanceOf[String]
    val piset = piInProject(projectId)
    val pi: PrincipalStub = getEntryAt(piset.keySet, 0).asInstanceOf[PrincipalStub]
    val delcount = piset(pi) 
    val user: PrincipalStub = getEntryAt(endorsedUsers, objectIdx).asInstanceOf[PrincipalStub]
    simpleDelegate(inference, op, pi, user, args=Seq(projectId, "true"))
    recordOperation("simpleDelegate", 
            (op, pi, user, Seq(emptyEnvs, emptyEnvs), Seq(projectId, "true")), 
            torecord)
    slangPerfCollector.addDelegation(0, s"${pi.getJvm}_${op}")
    // Add user into usersInProject
    addEntry(usersInProject, projectId, user, delcount+1)
    res = true
    res
  }

  def queryThenCreateProject(inference: Safelang, torecord: Boolean = false, objectType: String = "", objectIdx: Int = 0, rootIdx: Int = 0): Boolean = {
    var res = false
    val op = "queryThenCreateProject"
    val pa: PAStub = getEntryAt(endorsedPAs, rootIdx).asInstanceOf[PAStub]
    val pi: PrincipalStub = getEntryAt(endorsedPIs, objectIdx).asInstanceOf[PrincipalStub]
    val envs: Seq[String] = Seq(":" + pi.getPid + ":" + ":" + pi.getSubjectSetTokens(0), emptyEnvs, emptyEnvs)
    val projectNo: Int = projectCount.incrementAndGet()
    val projectName = "project" + projectNo
    val projectId = pa.getPid + ":" + projectName
    val args: Seq[String] = Seq(projectId, pa.getMemberPolicyToken) // project scids
    simpleDelegate(inference, op, pa, pi, envs, args)
    recordOperation("simpleDelegate", (op, pa, pi, envs, args), torecord)
    slangPerfCollector.addDelegation(0, s"${pa.getJvm}_${op}")

    // Add pi into usersInProject, indexed by projectId
    addEntry(piInProject, projectId, pi, 0)
    res = true
    res
  }

  /** geniroot endorses MA, PA, SA, CP */
  def rootEndorse(inference: Safelang, op: String, endorsedSubjectSet: OrderedSet[PrincipalStub], 
      torecord: Boolean = false): Boolean = {
    var res = false
    val pos = principalCount.incrementAndGet()
    if(pos < allPrincipals.length) {
      var p: PrincipalStub = allPrincipals(pos)
      if(op == "endorsePA") {
        val pa = new PAStub(p)
        pa.postMemberSetAndGetToken(inference)
        p = pa
      } else if(op == "endorseSA") {
        val sa = new SAStub(p)
        sa.postBasicMemberSet(inference)
        sa.postStandardSliceControlSetAndGetToken(inference)
        sa.postStandardSliceDefaultPrivilegeSetAndGetToken(inference)
        p = sa
      } else if(op == "endorseCP") {
        val cp = new CPStub(p)
        val zoneNo = zoneCount.incrementAndGet()
        val zoneName = "zone" + zoneNo
        cp.postZone(inference, zoneName)
        p = cp 
      }
      simpleDelegate(inference, op, geniroot, p)
      recordOperation("simpleDelegate", (op, geniroot, p, Seq(emptyEnvs, emptyEnvs), Seq[String]()), torecord)
      slangPerfCollector.addDelegation(0, s"${geniroot.getJvm}_${op}")
      // Update the endorsed subject set
      addEntry(endorsedSubjectSet, p)
      res = true
    }
    res
  }

  /** MA endorses PIs, users */
  def maEndorse(inference: Safelang, op: String, endorsedSubjectSet: OrderedSet[PrincipalStub],
      torecord: Boolean = false): Boolean = {
    var res = false
    val pos = principalCount.incrementAndGet()
    if(pos < allPrincipals.length) {
      val p: PrincipalStub = allPrincipals(pos)
      val entry: Option[PrincipalStub] = getRandomEntry(endorsedMAs)
      if(entry.isDefined) {
        val ma: PrincipalStub = entry.get
        simpleDelegate(inference, op, ma, p)
        recordOperation("simpleDelegate", (op, geniroot, p, Seq(emptyEnvs, emptyEnvs), Seq[String]()), torecord) 
        slangPerfCollector.addDelegation(0, s"${ma.getJvm}_${op}")
        // Add p into endorsedSubjectList
        addEntry(endorsedSubjectSet, p)
        res = true
      }
    }
    res
  }

}
