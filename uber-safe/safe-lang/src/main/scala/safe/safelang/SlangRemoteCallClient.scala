package safe.safelang

import akka.actor.{Actor, ActorRef}
import akka.actor.ActorSystem
import scala.concurrent.Future

import spray.http._
import spray.client.pipelining._

import spray.json._
import spray.httpx.SprayJsonSupport._
import scala.util.{Failure, Success}

import java.net.InetAddress

import SlangCallMessageFormat._

class SlangRemoteCallClient(context: ActorSystem) {
  implicit val system = context
  import system.dispatcher

  val pipeline: HttpRequest => Future[SlangCallResponse] = (
    addHeader("app", "slang")
    //~> addHeader("Accept", "application/json")
    ~> sendReceive
    ~> unmarshal[SlangCallResponse]
  )

  def validJVMAddr(jvmAddr: String): Boolean = {
    val parts = jvmAddr.split(":")
    if(parts.size == 2) {
      val ip = parts(0)
      val port = parts(1)
      (InetAddress.getByName(ip).getHostAddress()==ip) && port.forall(Character.isDigit)
    }
    false
  }

  def getURL(jvmAddr: String, method: String): String = {
    val urlBody = jvmAddr + "/" + method
    val httpPattern = """(http|https)://(.*)""".r
    val url = urlBody match {
      case httpPattern(protocol, addr) => urlBody
      case _ => "http://" + urlBody
    }
    url
  }

  def sendSlangRequest(jvmAddr: String, method: String, speaker: Option[String], 
                       subject: Option[String], objectId: Option[String], 
                       bearerRef: Option[String], principal: Option[String],
                       otherArgs: Seq[String]): Future[SlangCallResponse] = {
    val url = getURL(jvmAddr, method)
    val content = SlangCallParams(speaker, subject, objectId, bearerRef, principal, otherArgs)
    //println(s"content as SlangCallParams: ${content}")
    val response: Future[SlangCallResponse] = pipeline(Post(url, content))
    response
  }

  //def forMessage(jvmAddr: String, principal: String, method: String,
  //               paramsInJSON: String): Future[HttpResponse]
  //def reqMessage(jvmAddr: String, subject: String, method: String,
  //               paramsJson: String): Future[HttpResponse] 


  // for request testing
  def checkMarshalling(jvmAddr: String, method: String, speaker: Option[String], 
                       subject: Option[String], objectId: Option[String], 
                       bearerRef: Option[String], principal: Option[String],
                       otherArgs: Seq[String]): Unit = {
    import spray.httpx.marshalling._

    val url = getURL(jvmAddr, method)
    val content = SlangCallParams(speaker, subject, objectId, bearerRef, principal, otherArgs)
    println(s"[checkMarshalling] marshal(content)=${marshal(content)}")

    // Spray test spec
    //import spray.routing.HttpService
    //val route = post {
    //  entity(as[SlangParams]) { params =>  
    //    println(s"[route] params=${params}")
    //    complete(params)
    //  }
    //}
    //val url = getURL(jvmAddr, method)
    //val content = SlangParams(bearerRef, subject, principal, otherArgs)
    //Post("/", content) ~> route ~> check {
    //  println(s"[response] ${responseAs[String]}")
    //} 
  }

}
