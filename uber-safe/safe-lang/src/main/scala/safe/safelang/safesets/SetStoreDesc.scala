package safe.safelang
package safesets

import safe.safelog.UnSafeException

/**
 * SetStoreDesc keep server info for each set store, including
 * server address, protocol, and server ID (hash of public 
 * key in the certificate of a store server)
 */

class SetStoreDesc(
    private val storeAddr: String, 
    private val protocol: Int, 
    private val storeID: String) {
  def getStoreAddr(): String = storeAddr
  def getProtocol(): Int = protocol
  def getStoreID(): String = storeID
  override def toString(): String = {
    val p = if(protocol == SetStoreDesc.HTTP) { "http" } 
            else if(protocol == SetStoreDesc.HTTPS) { "https" } 
            else { "Unknown" }
    s"storeAddr: ${storeAddr}   protocol: ${p}  storeID: ${storeID}"
  }
}

object SetStoreDesc {
  /** Supported protocols */
  val HTTP:  Int = 1
  val HTTPS: Int = 2

  def apply(sa: String, p: Int, sid: String): SetStoreDesc = new SetStoreDesc(sa, p, sid)
  def apply(sa: String, p: String, sid: String): SetStoreDesc = {
    val ss = if(p.equalsIgnoreCase("http")) {
        new SetStoreDesc(sa, HTTP, sid)
      } else if(p.equalsIgnoreCase("https")) {
        new SetStoreDesc(sa, HTTPS, sid)
      } else {
        throw UnSafeException(s"Unrecognized set store protocol: ${p}")
      }
    ss
  }
}
