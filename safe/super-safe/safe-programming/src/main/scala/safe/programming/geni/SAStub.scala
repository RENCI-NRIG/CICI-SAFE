package safe.programming.geni

import safe.programming.PrincipalStub
import safe.safelang.Safelang

class SAStub(
    pid: String,                                                   // Principal id: hash of public key
    canonicalName: String,                                         // Symbolic name of the principal
    serverJvm: String,                                             // Address of the principal's JVM
    subjectSetTokens: Seq[String],                                 // Tokens of his subject sets 
    keyFile: String,                                               // pem filepath
    private var sliceControlPolicyToken: String = null,            // Token of SA's slice control policy set
    private var sliceDefaultPrivilegeToken: String = null          // Token of SA's slice default privilege policy set 
    ) extends PrincipalStub(pid, canonicalName, serverJvm, subjectSetTokens, keyFile) {

  def this(keyFile: String, cn: String, serverJvm: String) {
    this(PrincipalStub.pidFromFile(keyFile), cn, serverJvm, Seq[String](), keyFile)
  }

  def this(p: PrincipalStub) {
    this(p.getPid, p.getCN, p.getJvm, p.getSubjectSetTokens, p.getKeyFile)
  }

  def getSliceControlPolicyToken = sliceControlPolicyToken
  def getSliceDefaultPrivilegeToken = sliceDefaultPrivilegeToken

  /** Post standard slice control set for SA */
  def postStandardSliceControlSet(inference: Safelang): String = {
    simpleRemoteCall(inference, "postStandardSliceControlSet")
  }

  /** Post standard slice control set and store the token into sliceControlPolicyToken */
  def postStandardSliceControlSetAndGetToken(inference: Safelang): Unit = {
    val token = postStandardSliceControlSet(inference)
    sliceControlPolicyToken = token
  }

  /** Post standard slice default privilege set for SA */
  def postStandardSliceDefaultPrivilegeSet(inference: Safelang): String = {
    simpleRemoteCall(inference, "postStandardSliceDefaultPrivilegeSet")
  }

  /** Post standard slice default privilege set and store the token into sliceDefaultPrivilegeToken */
  def postStandardSliceDefaultPrivilegeSetAndGetToken(inference: Safelang): Unit = {
    val token = postStandardSliceDefaultPrivilegeSet(inference)
    sliceDefaultPrivilegeToken = token
  }

  /** Post memberSet for PA */
  def postBasicMemberSet(inference: Safelang): String = {
    simpleRemoteCall(inference, "postUserGroupMemberSet")
  }

  override def init(inference: Safelang): Unit = {
    //postBasicMemberSet(inference)
    postStandardSliceControlSetAndGetToken(inference)
    postStandardSliceDefaultPrivilegeSetAndGetToken(inference)
  }
}
