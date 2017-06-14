package safe.programming.geni

import safe.programming.PrincipalStub
import safe.safelang.Safelang

class CPStub(
    pid: String,                                                  // Principal id: hash of public key
    canonicalName: String,                                        // Symbolic name of the principal
    serverJvm: String,                                            // Address of the principal's JVM
    subjectSetTokens: Seq[String],                                // Tokens of his subject sets 
    keyFile: String,                                              // pem filepath
    private var zoneName: String = null,                          // Zone name of the aggregate
    private var zoneId: String = null                             // Zone ID
    ) extends PrincipalStub(pid, canonicalName, serverJvm, subjectSetTokens, keyFile) {

  def this(keyFile: String, cn: String, serverJvm: String) {
    this(PrincipalStub.pidFromFile(keyFile), cn, serverJvm, Seq[String](), keyFile)
  }

  def this(p: PrincipalStub) {
    this(p.getPid, p.getCN, p.getJvm, p.getSubjectSetTokens, p.getKeyFile)
  }

  /** Post zone for CP */
  def postZone(inference: Safelang, n: String): String = {
    zoneName = n
    zoneId = pid + ":" + zoneName
    simpleRemoteCall(inference, "postZoneSet", args=Seq(zoneId))
  }

  def getZoneName: String = zoneName
  def getZoneId: String = zoneId
}
