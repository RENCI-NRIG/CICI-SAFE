package safe.safelang
package safesets

import safe.safelog.UnSafeException

import akka.actor.{Actor, ActorSystem}

import spray.http._
import spray.client.pipelining._

import scala.concurrent.{Await, Future}

/**
 * Spray provides async primitives for http requests. 
 * We implement a Spray client with both synchronous
 * and asynchronous operations.
 */

class SprayStorageClient(system: ActorSystem) 
    extends StorageClient with StorageAsyncClient {

  implicit val s = system
  import system.dispatcher
  import HttpMultipartContentHelper._

  val pipeline: HttpRequest => Future[HttpResponse] = sendReceive

  /** Accept riak siblings in the response */
  val multPipeline: HttpRequest => Future[HttpResponse] = (
    addHeader("Accept", "multipart/mixed")
    ~> sendReceive
  )

  def fetchCertAsync(certaddr: CertAddr): Future[HttpResponse] = {
    pipeline(Get(certaddr.getUrl()))
  }

  /** Fetch cert, allowing multiple siblings */
  def fetchCertMultAsync(certaddr: CertAddr): Future[HttpResponse] = {
    multPipeline(Get(certaddr.getUrl()))
  }

  def postCertAsync(certaddr: CertAddr, content: String): Future[HttpResponse] = {
    //pipeline(Post(certaddr.getUrl(), content))
    pipeline(Put(certaddr.getUrl(), content))
  } 

  def deleteCertAsync(certaddr: CertAddr): Future[HttpResponse] = {
    pipeline(Delete(certaddr.getUrl()))
  } 

  /** Extract raw cert as string from HttpResponse */
  private def certFromFetchResponse(fetchResponse: HttpResponse): Option[String] = {
    fetchResponse.status.intValue match {
      case 200 =>
        Some(fetchResponse.entity.asString)
      case 300 => // Multiple choices
        Some(fetchResponse.entity.asString)
      case statusCode @ (301 | 302 | 303 | 307) => // redirect
        logger.info(s"WARN fetch cert async failed: redirect detected ($statusCode)")
        None
      case statusCode => // other errors
        logger.info(s"WARN fetch cert async failed: unknown status code ${statusCode}")
        None
    }
  }

  def fetchCert(certaddr: CertAddr): Option[String] = {
    val s = System.nanoTime
    logger.info(s"Fetch remote: ${certaddr}")
    val future: Future[HttpResponse] = fetchCertAsync(certaddr)
    val fetchResponse: HttpResponse = Await.result(future, timeout.duration)
    var fetchedStr: Option[String] = certFromFetchResponse(fetchResponse)
    if(fetchedStr.isDefined && isMultipleSiblings(fetchedStr.get)) {
    // Fetch again and accept multipart/mixed
      val f: Future[HttpResponse] = fetchCertMultAsync(certaddr)
      val fresp: HttpResponse = Await.result(f, timeout.duration)
      fetchedStr = certFromFetchResponse(fresp)
    }
    val t = (System.nanoTime -s) / 1000
    val length = if(fetchedStr.isDefined) fetchedStr.get.length else 0
    slangPerfCollector.addSetFetchTime(t.toString, s"$certaddr $length")
    fetchedStr
  }

  /** post synchronously */
  def postCert(certaddr: CertAddr, content: String): String = {
    logger.info(s"Post cert ${CertAddr}:\n  $content")
    val s = System.nanoTime
    val future: Future[HttpResponse] = postCertAsync(certaddr, content)
    val postResponse: HttpResponse = Await.result(future, timeout.duration)
    val t = (System.nanoTime -s) / 1000
    val length = content.length
    slangPerfCollector.addSetPostTime(t.toString, s"$certaddr $length") // collect set post time

    //Thread.sleep(1000)
    postResponse.status.intValue match {
      case c @ (204 | 200) =>
        logger.info(s"cert (${certaddr}) is posted (code: $c)")
        //checkPosted(certaddr, content) /* checking */
      case statuscode =>
        logger.info(s"postCert on ${certaddr} failed (code: ${statuscode}): ${content}")
        throw UnSafeException(s"postCert on ${certaddr} failed (code: ${statuscode}): ${content}")
    }
    certaddr.toString
  }

  def deleteCert(certaddr: CertAddr): String = {
    logger.info(s"Delete cert ${CertAddr}")
    val future: Future[HttpResponse] = deleteCertAsync(certaddr)
    val deleteResponse: HttpResponse = Await.result(future, timeout.duration)
    deleteResponse.status.intValue match {
      case c @ (204 | 200) =>
      case statuscode =>
        throw UnSafeException(s"deleteCert on ${certaddr} failed (code: ${statuscode})")
    }
    certaddr.toString
  }

  // Post async with callback 
  //private def postCertAsyncCallback(certaddr: CertAddr, content: String): String = {
  //  val future: Future[HttpResponse] = postCertAsync(certaddr, content)
  //  future.onComplete {
  //    case Success(response) if (response.status.intValue == 204 | response.status.intValue == 200) => 
  //      // 204 No content
  //      //println(s"[SprayStorageClient] postCertAsync completed")
  //    case response =>
  //      throw UnSafeException(s"postCertAsync on ${certaddr} failed: ${response}")
  //  }
  //  certaddr
  //}
}

object SprayStorageClient {
  def apply(s: ActorSystem): SprayStorageClient = new SprayStorageClient(s)
}
