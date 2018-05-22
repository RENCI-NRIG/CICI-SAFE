package org.apache.cassandra.transport

import org.apache.cassandra.config.EncryptionOptions._
import org.apache.cassandra.transport.ProtocolVersion

/**
 * A terminating placeholder for Cassandra storage client.
 */

class SimpleClient(host: String, port: Int,
    version: ProtocolVersion, encryptionOptions: ClientEncryptionOptions) {

  import SimpleClient._

  /** Dummy method */
  def readCert(id: String): String = {
    ""
  }

  def write(id: String, text: String): Unit = {
 
  }

  def connect(useCompression: Boolean): Unit = {

  }

  def setEventHandler(eventHandler: SimpleEventHandler): Unit = {

  } 

}

object SimpleClient {
  /**
   * Work around because nested class in scala is referred by
   * objectName.SubClassName, rather than ClassName.SubClassName
   * as java does.
   */
  class SimpleEventHandler   
}
