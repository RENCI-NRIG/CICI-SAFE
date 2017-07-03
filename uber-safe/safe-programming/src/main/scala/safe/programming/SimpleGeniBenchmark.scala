package safe.programming

import safe.safelang.Safelang
import safe.safelog.UnSafeException
import safe.safelog._

import scala.util.{Failure, Success}
import scala.concurrent.Await
import scala.concurrent.Future

import scala.collection.mutable.ListBuffer

/**
 * Simple benchmark for SafeGeni
 */
class SimpleGeniBench(inference: Safelang, jvmmapFile: String) extends SafeBench {

  def run(): Unit = {
    // JVMs
    val defaultJvm = "152.3.136.26:7777"

    // Principals and their ID sets and subject sets
    val principalList: ListBuffer[PrincipalStub] = loadPrincipals(jvmmapFile)
    buildIDSet(inference, principalList)
    buildSubjectSet(inference, principalList)

    val geniroot: PrincipalStub = getMatchingPrincipals("geniroot.pem".r, principalList) match {
      case principals: ListBuffer[PrincipalStub] if principals.length >= 1 => principals(0)  // take the first principal as geni root 
      case _ => throw new Exception("pem file for geniroot not found") 
    }
    val maList: Seq[PrincipalStub] = getMatchingPrincipals("ma1.pem".r, principalList).toSeq  
    val paList: Seq[PrincipalStub] = getMatchingPrincipals("pa1.pem".r, principalList).toSeq
    val saList: Seq[PrincipalStub] = getMatchingPrincipals("sa1.pem".r, principalList).toSeq
    val cpList: Seq[PrincipalStub] = getMatchingPrincipals("agg1.pem".r, principalList).toSeq
    val piList: Seq[PrincipalStub] = getMatchingPrincipals("prof1.pem".r, principalList).toSeq
    val userList: Seq[PrincipalStub] = getMatchingPrincipals("user1.pem".r, principalList).toSeq

    // pa projectMembershipRef
    val paProjectMembershipRefList = ListBuffer[ListBuffer[String]]()

    // pa project IDs
    val paProjectIdList = ListBuffer[ListBuffer[String]]()

    // sa SliceControlRef, SlicePrivRef
    val saSliceControlRefList = ListBuffer[ListBuffer[String]]()
    val saSlicePrivRefList = ListBuffer[ListBuffer[String]]()

    // sa slice IDs
    val saSliceIdList = ListBuffer[ListBuffer[String]]()

    // Envs
    val emptyEnvs = ":::" // format: [speaker]:[subject]:[object]:[bearerRef]

    // geniroot endorses MA, PA, SA, CP
    simpleDelegate(inference, "endorseMA", geniroot, maList(0))      
    simpleDelegate(inference, "endorsePA", geniroot, paList(0))    
    simpleDelegate(inference, "endorseSA", geniroot, saList(0)) 
    simpleDelegate(inference, "endorseCP", geniroot, cpList(0))

    // MA endorse PI and User    
    simpleDelegate(inference, "endorsePI", maList(0), piList(0))
    simpleDelegate(inference, "endorseUser", maList(0), userList(0))
    
    // PA postMemberSet(?ServerJVM, ?ServerPrincipal, ?Envs)
    // only once for each PA
    var token = paList(0).simpleRemoteCall(inference, "postMemberSet")
    paProjectMembershipRefList += ListBuffer(token)

    // PA adds project ID
    paProjectIdList += ListBuffer("project0") 

    // PA queryThenCreateProject(?PAJVM, ?PA, ?SubjectJVM, ?SubjectId, ?QueryEnvs, ?PostEnvs, ?UpdateEnvs, ?ProjectId, ?ProjectMembershipRef)
    // PI creates project
    // [speaker]:[subject]:[object]:[bearerRef]
    var envs: Seq[String] = Seq(":" + piList(0).getPid + ":" + ":" + piList(0).getSubjectSetTokens(0), emptyEnvs, emptyEnvs) 
    var projectId = paList(0).getPid + ":" + paProjectIdList(0)(0) 
    var args: Seq[String] = Seq(projectId, paProjectMembershipRefList(0)(0)) // project scids 
    simpleDelegate(inference, "queryThenCreateProject", paList(0), piList(0), envs, args) 

    // PI delegateProjectMembership(?PIJVM, ?PI, ?SubjectJVM, ?SubjectId, ?PostEnvs, ?UpdateEnvs, ?ProjectId, ?Delegatable)
    // PI delegates project membership to user
    simpleDelegate(inference, "delegateProjectMembership", piList(0), userList(0), args = Seq(projectId, "true"))

    // SA postStandardSliceControlSet(?ServerJVM, ?ServerPrincipal, ?Envs)
    // only once for each SA
    token = saList(0).simpleRemoteCall(inference, "postStandardSliceControlSet")
    saSliceControlRefList += ListBuffer(token)

    // SA postStandardSliceDefaultPrivilegeSet(?ServerJVM, ?ServerPrincipal, ?Envs)
    // only once for each SA
    token = saList(0).simpleRemoteCall(inference, "postStandardSliceDefaultPrivilegeSet")
    saSlicePrivRefList += ListBuffer(token)

    // SA adds slice ID
    saSliceIdList += ListBuffer("slice0")

    // SA queryThenCreateSlice(?SAJVM, ?SA, ?SubjectJVM, ?SubjectId, ?QueryEnvs, ?PostEnvs, ?UpdateEnvs, ?ProjectId,  ?SliceId, ?SliceControlRef, ?SlicePrivRef) 
    // User creates slice in project
    envs = Seq(":" + userList(0).getPid + ":" + ":" + userList(0).getSubjectSetTokens(0), emptyEnvs, emptyEnvs)
    var sliceId = saList(0).getPid + ":" + saSliceIdList(0)(0)
    args = Seq(projectId, sliceId, saSliceControlRefList(0)(0), saSlicePrivRefList(0)(0))
    simpleDelegate(inference, "queryThenCreateSlice", saList(0), userList(0), envs, args) 

    // CP querySliverCreation(?CPJVM, ?CP, ?Envs, ?SliceId)
    // User creates sliver in project
    var env: String = ":" + userList(0).getPid + ":" + ":" + userList(0).getSubjectSetTokens(0)
    args = Seq(sliceId)
    println(s"cpList length: ${cpList.length}")
    cpList(0).simpleRemoteCall(inference, "createSliver", env, args)
  }
}
