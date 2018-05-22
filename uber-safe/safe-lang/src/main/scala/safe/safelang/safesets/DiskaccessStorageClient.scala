package safe.safelang
package safesets

import safe.safelog.UnSafeException

import scala.util.{Success, Failure, Try}
import scala.io.Source
import java.io.{File, PrintWriter}

/**
 * DiskaccessStorageClient is for accessing SafeSets
 * that sits on local disk. SafeSetsService uses
 * this type of cert storage client when the var
 * localSafeSets in config is set.
 */

class DiskaccessStorageClient extends StorageClient {

  // If localSafeSet is set, safeSetsDir points to the local dir
  val safeSetsDir = Config.config.safeSetsDir

  /**
   * When storage of local disk is used, whatever store address
   * specified in certaddr will be ignored.
   */
  def getCertFilepath(certaddr: CertAddr): String = {
    // safeSetsDir + "cert_" + certaddr.getHashToken
    // changed to url to handle multiple domains using, i.e., self certifying tokens
    safeSetsDir + "cert_" + certaddr.getUrl
  }

  def fetchCert(certaddr: CertAddr): Option[String] = {
    val filepath = getCertFilepath(certaddr)
    val certAsString = Try(Source.fromFile(filepath).getLines.mkString("\n"))
    val content: Option[String] = certAsString match {
      case Success(rawCert) => Some(rawCert)
      case Failure(e) =>
        throw UnSafeException(s"Failed to find cert ${certaddr} on local disk")
        None
    }
    content
  }

  def postCert(certaddr: CertAddr, content: String): String = {
    val filepath = getCertFilepath(certaddr)
    //println(s"[DiskaccessStorageClient] cert file path: $filepath")
    val pw = new PrintWriter(new File(filepath))
    pw.write(content)
    pw.close
    certaddr.toString
  }

  def deleteCert(certaddr: CertAddr): String = {
    val filepath = getCertFilepath(certaddr)
    val certFile = new File(filepath)
    if(certFile.exists) {
      certFile.delete
    } else {
      logger.info(s"WARN: local file ${filepath} for cert ${certaddr.getHashToken} doesn't exist!")
    }
    certaddr.toString
  }
}

object DiskaccessStorageClient {
  def apply(): DiskaccessStorageClient = new DiskaccessStorageClient()
}
