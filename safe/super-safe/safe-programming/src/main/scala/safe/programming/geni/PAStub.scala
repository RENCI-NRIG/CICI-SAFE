package safe.programming.geni

import safe.programming.PrincipalStub
import safe.safelang.Safelang

class PAStub(
    pid: String,                                                  // Principal id: hash of public key
    canonicalName: String,                                        // Symbolic name of the principal
    serverJvm: String,                                            // Address of the principal's JVM
    subjectSetTokens: Seq[String],                                // Tokens of his subject sets 
    keyFile: String,                                              // pem filepath
    private var memberPolicyToken: String = null                  // Token of PA's membership policy set
    ) extends PrincipalStub(pid, canonicalName, serverJvm, subjectSetTokens, keyFile) {

  def this(keyFile: String, cn: String, serverJvm: String) {
    this(PrincipalStub.pidFromFile(keyFile), cn, serverJvm, Seq[String](), keyFile)
  }

  def this(p: PrincipalStub) {
    this(p.getPid, p.getCN, p.getJvm, p.getSubjectSetTokens, p.getKeyFile)
  }

  def getMemberPolicyToken: String = memberPolicyToken

  /** Post memberSet for PA */
  def postMemberSet(inference: Safelang, entryPoint: String): String = {
    simpleRemoteCall(inference, entryPoint)
  }

  /** Post memberSet and store the token into memberPolicyToken */
  def postMemberSetAndGetToken(inference: Safelang): Unit = {
    //postMemberSet(inference, "postUserGroupMemberSet")
    //val token = postMemberSet(inference, "postProjectMemberSet")
    val token = postMemberSet(inference, "postMemberSet")
    memberPolicyToken = token
  }
  
  override def init(inference: Safelang): Unit = {
    postMemberSetAndGetToken(inference)
  }

}
