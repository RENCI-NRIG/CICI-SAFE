package safe.safelang
package safesets

import safe.safelog.UnSafeException

import scala.util.{Try, Success, Failure}

import com.google.common.cache._

import org.apache.cassandra.config.DatabaseDescriptor
import org.apache.cassandra.config.EncryptionOptions._
import org.apache.cassandra.transport.ProtocolVersion
import org.apache.cassandra.transport.{ SimpleClient => CassandraNativeClient }
import org.apache.cassandra.transport.SimpleClient._

case class NetworkEndpoint(ip: String, port: Int)

/**
 * A client for a Cassandra-based "multi-domain storage"
 */

class CassandraStorageClient extends StorageClient { 
 
  DatabaseDescriptor.clientInitialization()

  val host: String = "127.0.0.1"
  val port: Int = 9042
  val protocolVersion: ProtocolVersion = ProtocolVersion.CURRENT 
  val encryptionOptions: ClientEncryptionOptions = new ClientEncryptionOptions()

  /**
   * A Cassandra's native client is per IP-port pair for a storage server.
   * We cache established channels to replicas locally and re-use them for
   * future reads and writes.
   */ 

  val spec = "maximumSize=10000,initialCapacity=10000,concurrencyLevel=10"

  val clientloader = new CacheLoader[NetworkEndpoint, CassandraNativeClient] {
    def load(e: NetworkEndpoint): CassandraNativeClient = { 
      val client: CassandraNativeClient = new CassandraNativeClient(e.ip, e.port, protocolVersion, encryptionOptions)

      //val eventHandler: CassandraNativeClient.SimpleEventHandler = new CassandraNativeClient.SimpleEventHandler()
      val eventHandler= new SimpleEventHandler()
      client.setEventHandler(eventHandler)
      client.connect(false);  // Connect with an uncompressed startup msg
      client
    }
  }

  val nativeClientCache: LoadingCache[NetworkEndpoint, CassandraNativeClient] = 
    CacheBuilder.from(spec).build[NetworkEndpoint, CassandraNativeClient](clientloader)


  def getClientForCert(certaddr: CertAddr): CassandraNativeClient = {
    val e: NetworkEndpoint = certaddr.getStoreNetworkEndpoint()
    println(s"[getClientForCert] certaddr:${certaddr}   e:${e}")
    val cassandraclient: CassandraNativeClient = Try(nativeClientCache.get(e)) match {
      case Success(c) => c
      case Failure(f) => throw UnSafeException(s"cannot find or build a client for server: ${e}")
    }
    cassandraclient
  }


  def fetchCert(certaddr: CertAddr): Option[String] = {
    val s = System.nanoTime
    logger.info(s"Fetch remote: ${certaddr}")
    
    val cassandraclient = getClientForCert(certaddr)
    val hashtoken: String = certaddr.getHashToken
    val fetchedStr: String = cassandraclient.readCert(hashtoken)

    val t = (System.nanoTime -s) / 1000
    val length = if(fetchedStr != null) fetchedStr.length else 0
    slangPerfCollector.addSetFetchTime(t.toString, s"$certaddr $length")

    if(fetchedStr == null || fetchedStr.isEmpty()) { 
      None
    } else {
      Some(fetchedStr)
    }
  }

  def postCert(certaddr: CertAddr,  content: String): String = {
    logger.info(s"Post cert ${CertAddr}:\n  $content")
    val s = System.nanoTime
   
    val cassandraclient = getClientForCert(certaddr)
    val hashtoken: String = certaddr.getHashToken
    cassandraclient.write(hashtoken, content)
    // TODO: check response

    // Checking by read
    cassandraclient.readCert(hashtoken)

    val t = (System.nanoTime -s) / 1000
    val length = content.length
    slangPerfCollector.addSetPostTime(t.toString, s"$certaddr $length") // collect set post time

    certaddr.toString
  }

  def deleteCert(certaddr: CertAddr): String = {
    logger.info(s"Delete cert ${CertAddr}")

    val cassandrclient = getClientForCert(certaddr)
    val hashtoken: String = certaddr.getHashToken
    cassandrclient.write(hashtoken, "")  // use an empty cert for now
    // TODO: check response

    certaddr.toString
  }

}

object CassandraStorageClient {
  def apply(): CassandraStorageClient = new CassandraStorageClient()
}
