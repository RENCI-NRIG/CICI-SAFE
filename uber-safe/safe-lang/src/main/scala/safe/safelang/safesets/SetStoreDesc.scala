package safe.safelang
package safesets

import safe.safelog.UnSafeException
import com.google.common.net.InetAddresses

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

  /**
   * Cassandra uses a native protocol in which a storage server is 
   * identified by a network endpoint, i.e., an ip-port pair. In this
   * case, a value of storeAddr represents an endpoint in string: 
   * [IP]:[port] 
   */
  def getNetworkEndpoint(): NetworkEndpoint = {
    val parts: Array[String] = storeAddr.split(":")
    if(parts.length == 2) {
      if(InetAddresses.isInetAddress(parts(0))) {
        val ip = parts(0)
        val port = toInt(parts(1))
        if(port.isDefined) {
          NetworkEndpoint(ip, port.get)
        } else {
          throw UnSafeException(s"Invalid port for cassandra server: ${parts(1)}  ${port}") 
        }
      } else {
        throw UnSafeException(s"Invalid ip for cassandra server: ${parts(0)}") 
      }   
    } else {
      throw UnSafeException(s"Invalid cassandra storage endpoint: ${storeAddr}  ${parts}") 
    } 
  } 

  private def toInt(s: String): Option[Int] = {
    try {
      Some(s.toInt)
    } catch {
      case e: Exception => None
    }
  }

}

object SetStoreDesc {
  /** Supported protocols */
  val HTTP:  Int = 1
  val HTTPS: Int = 2
  val CASSANDRA_NATIVE: Int = 3
  val LDAP: Int = 4

  def apply(sa: String, p: Int, sid: String): SetStoreDesc = new SetStoreDesc(sa, p, sid)
  def apply(sa: String, p: String, sid: String): SetStoreDesc = {
    val ss = if(p.equalsIgnoreCase("http")) {
        new SetStoreDesc(sa, HTTP, sid)
      } else if(p.equalsIgnoreCase("https")) {
        new SetStoreDesc(sa, HTTPS, sid)
      } else if(p.equalsIgnoreCase("cassandra_native")) {
        new SetStoreDesc(sa, CASSANDRA_NATIVE, sid) 
      } else if(p.equalsIgnoreCase("ldap")) {
        new SetStoreDesc(sa, LDAP, sid) 
      } else {
        throw UnSafeException(s"Unrecognized set store protocol: ${p}")
      }
    ss
  }
}
