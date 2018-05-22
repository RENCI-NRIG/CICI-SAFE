package safe.safelang
package safesets

import safe.safelog.UnSafeException
import scala.concurrent.Future

// For multipart cert content
import spray.http.{MultipartContent, BodyPart}
import spray.httpx.unmarshalling._
import spray.http.{MediaTypes, MediaType, HttpResponse}

import com.typesafe.scalalogging.LazyLogging

/**
 * An instance of a CertAddr consist of two parts:
 * the address of the store (storeAddr) and the
 * token in the store (token). This faciliates
 * a distributed deployment of SafeSets 
 */ 
class CertAddr(val storeDesc: SetStoreDesc, val hashToken: String) {
  def getUrl(): String = {
    val storeAddr = getStoreAddr
    val certurl = storeAddr.last match {
      case '/' => s"${storeAddr}${hashToken}"
      case _   => s"${storeAddr}/${hashToken}"
    }
    if(certurl.last == '/')
      certurl.substring(0, certurl.length-1)
    else certurl
  }

  override def toString(): String = {
    getUrl
  }

  def getStoreNetworkEndpoint(): NetworkEndpoint = {
    storeDesc.getNetworkEndpoint
  }

  def getStoreAddr(): String = {
    storeDesc.getStoreAddr
  }

  def getStoreProtocol(): Int = {
    storeDesc.getProtocol
  }

  def getStoreID(): String = {
    storeDesc.getStoreID
  }

  def getHashToken(): String = {
    hashToken
  }

}

object CertAddr { 
  def apply(storeDesc: SetStoreDesc, hashToken: String): CertAddr = {
    val ca = new CertAddr(storeDesc, hashToken)
    ca
  } 
}

trait StorageClient extends CRDTAPI with LazyLogging {
  def fetchCert(certaddr: CertAddr): Option[String]
  def postCert(certaddr: CertAddr, content: String): String 
  def deleteCert(certaddr: CertAddr): String 
  // def fetchCertMult(certaddr: CertAddr): Option[String]    
  // fetch multiple siblings

  /** 
   * @DeveloperAPI 
   * Check if the posted is actually on the server
   * Fetch on the same token and check if we get previously posted
   * Can be called immediately after a post
   */
  def checkPosted(certaddr: CertAddr, content: String): Unit = {
    val fetched = fetchCert(certaddr)
    if(!fetched.isDefined)
      throw UnSafeException(s"posted cert ${certaddr} must exist: ${fetched}")
    val materialized: Boolean = subsume(fetched.get, content) // CRDT subsume: fetched.get==content
    logger.info(s"posted cert ${certaddr} materialized: ${materialized}")
    if(materialized == false) {
      logger.info(s"materialized cert ${certaddr} is different: \n${fetched.get}")
      throw UnSafeException(s"""| Posted cert ${certaddr} not materialized on storage  
                                | posted cert: ${content} 
                                | cert fetched from storage: ${fetched.get}""".stripMargin)
    }
  }

}

trait StorageAsyncClient {
  def fetchCertAsync(certaddr: CertAddr): Future[HttpResponse]
  def postCertAsync(certaddr: CertAddr, content: String): Future[HttpResponse]
  def deleteCertAsync(certaddr: CertAddr): Future[HttpResponse]
  //  def fetchCertMultAsync(certaddr: CertAddr): Future[HttpResponse]
}

/** Multipart content helper specific to Riak */
object HttpMultipartContentHelper extends LazyLogging {
  /** 
   * Get the boundary of a multipart entity 
   * Format of an entity as a string:
   *
   * --T26u9rronuXcNguz0ZhBr7Yr4Yz
   * Content-Type: text/plain; charset=UTF-8
   * Link: </buckets/safe>; rel="up"
   * Etag: 2eXmgu7FMoafCq8Px6ZXSk
   * Last-Modified: Fri, 04 Mar 2016 17:55:40 GMT
   * ......
   * --T26u9rronuXcNguz0ZhBr7Yr4Yz
   * Content-Type: text/plain; charset=UTF-8
   * Link: </buckets/safe>; rel="up"
   * Etag: 2OfgTx1bKG9G9fNXqJNKyE
   * Last-Modified: Mon, 07 Mar 2016 07:20:17 GMT
   * ......
   * --T26u9rronuXcNguz0ZhBr7Yr4Yz--
   */
  def getMultipartBoundary(entity: String): String = {
    val endFirstLine = entity.indexOf('\n', 2)
    entity.substring(4, endFirstLine).trim()
  }

  /** Check if a string isn't a multipart entity */
  def notMultipartEntity(entity: String): Boolean = {
    logger.info(s"entity.substring(2,4): ${entity.substring(2,4)}")
    // CR: 13     LF: 10
    val res = entity(0).toInt!=13 || entity(1).toInt!=10 || entity.substring(2,4) != "--"
    logger.info(s"notMultipart:${res}  entity(0):${entity(0).toInt}  entity(1):${entity(1).toInt}")
    res
  }

  /** 
   * Check if a GET response from riak indicates 
   * multiple siblings
   */
  def isMultipleSiblings(resmsg: String): Boolean = {
    resmsg.startsWith("Siblings:")
  }

  def unmarshalMultipartContent(c: String): Deserialized[MultipartContent] = {
    val boundary = getMultipartBoundary(c)
    logger.info(s"boundary:${boundary}")
    val certEntity = spray.http.HttpEntity(MediaTypes.`multipart/mixed`.withBoundary(boundary), c)
    logger.info(s"certEntity: $certEntity")
    certEntity.as[MultipartContent]
  }
}
