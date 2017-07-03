package safe.programming

import safe.safelang.Safelang
import safe.safelang.SlangCallParams
import safe.safelang.SlangCallResponse
import safe.safelog.UnSafeException

import scala.util.{Failure, Success}
import akka.pattern.ask
import scala.concurrent.Await
import scala.concurrent.Future

import scala.concurrent.ExecutionContext.Implicits.global

/**
 * DeveloperApi
 *
 * This object is created For the purpose of testing the slang call
 * client. It's not a necessary component for a deployment.
 */

object SlangCallTest {

  /**
   * Simple parsing to extract slogset tokens from
   * a remote inferenence response (objectStr).
   *
   * A inference response with slogset tokens is
   * in the following format:
   * [u'<token0>', u'<token1>', ..., u'<tokenN>']
   *
   * An example:
   * [u'X8buNSIAef3BHZXBt5etZgFd-eiTrOCDTwTqOX-PLfY', u'jSvdyfIYzmWO-d1V_mJqPq1fPkIS8CPkE2TxFfsk3ow']
   */

  def extractSlogsetTokens(objectStr: String): Seq[String] = {
    val tokenPattern = """u'(.*)'""".r
    if(!objectStr.isEmpty && objectStr.head == '[' && objectStr.last == ']') {
      val parts = objectStr.stripPrefix("[").stripSuffix("]").split(", ")
      val tokens = parts.map { p =>
        p match {
          case tokenPattern(token) => token
          case _ => throw UnSafeException(s"Invalid token ${p}")
        }
      }
      tokens.toSeq
    }
    else Seq[String]()
  }


  def checkRequest(inference: Safelang) {
    val jvmAddr = "152.3.136.26:7777"
    //val method = "postIdSet"
    //val bearerRef: Option[String] = None
    //val subject: Option[String] = None
    //val principal: Option[String] = Some("lsdHylffAfTwlsLyVnHJFg7K4_sFOW2JWCTfmxgZh6k")
    //val otherArgs = Seq("mp_user12.pem")

    val method = "tagAccess"
    val speaker: Option[String] = None
    val subject: Option[String] = None
    val objectId: Option[String] = None
    val bearerRef: Option[String] = None
    val principal: Option[String] = None
    val otherArgs = Seq[String]()

    inference.slangCallClient.checkMarshalling(jvmAddr, method, 
      speaker, subject, objectId, bearerRef, principal, otherArgs)
  }


  def send2ndPostReq(inference: Safelang) {
  // curl --data "?File=jSvdyfIYzmWO-d1V_mJqPq1fPkIS8CPkE2TxFfsk3ow:TestFile" 
  //      --data "?Tag=SakSG4JGhi7Cejx3D9rl06UOb4hk6jAQRJs68DP57RE:TestingTag2"  http://152.3.136.26:7777/postFileLabel
    val jvmAddr = "152.3.136.26:7777"
    val method = "postIdSet"
    val speaker: Option[String] = None
    val subject: Option[String] = None
    val objectId: Option[String] = None
    val bearerRef: Option[String] = None

    //val principal: Option[String] = None
    //val principal: Option[String] = Some("EF7tjWe31e35kM6HAqwwoZ02psnGzKiVhnNhEKUvFu8")
    //val otherArgs = Seq("mp_user3.pem")

    //val principal: Option[String] = Some("GHWvK4Q3eNryvtjpMDLX0gM9SfACgHFah4P4ZRpU3fM")
    //val otherArgs = Seq("mp_user2.pem")


    //val principal: Option[String] = Some("8oF7nS_rrnU3FvzJKt75qcoXgzoH3Dsv3jnzy4dbFXk")
    //val otherArgs = Seq("mp_user13.pem")

    val principal: Option[String] = Some("lsdHylffAfTwlsLyVnHJFg7K4_sFOW2JWCTfmxgZh6k")
    val otherArgs = Seq("mp_user12.pem")

    val resp = inference.slangCallClient.sendSlangRequest(jvmAddr, method, 
                 speaker, subject, objectId, bearerRef, principal, otherArgs)
    resp.onComplete {
      case Success(response: SlangCallResponse) =>
        println(s"SlangResponse: ${response.message}")
        val trimmedResp = response.message.replaceAll("""(?m)\s+$""", "")
        val tokens = extractSlogsetTokens(trimmedResp)
        println(s"tokens: ${tokens}")
      case Failure(e) => println(e)
    }
  }

  def sendOnePostReq(inference: Safelang) {
  // curl --data "?File=jSvdyfIYzmWO-d1V_mJqPq1fPkIS8CPkE2TxFfsk3ow:TestFile" 
  // --data "?Tag=SakSG4JGhi7Cejx3D9rl06UOb4hk6jAQRJs68DP57RE:TestingTag2"  http://152.3.136.26:7777/postFileLabel
    val jvmAddr = "152.3.136.26:7777"
    val method = "postFileLabel"
    val speaker: Option[String] = None
    val subject: Option[String] = None
    val objectId: Option[String] = None
    val bearerRef: Option[String] = None

    //val principal: Option[String] = None
    val principal: Option[String] = Some("dycfg32gYgr22dXShIbs0VnJVQwRKXq-LDKr3LaRtw0")

    val otherArgs = Seq("jSvdyfIYzmWO-d1V_mJqPq1fPkIS8CPkE2TxFfsk3ow:TestFile", "SakSG4JGhi7Cejx3D9rl06UOb4hk6jAQRJs68DP57RE:TestingTag2")
    val resp = inference.slangCallClient.sendSlangRequest(jvmAddr, method, 
                 speaker, subject, objectId, bearerRef, principal, otherArgs)
    resp.onComplete {
      case Success(response: SlangCallResponse) =>
        println(s"SlangResponse: ${response.message}")
        val trimmedResp = response.message.replaceAll("""(?m)\s+$""", "")
        val tokens = extractSlogsetTokens(trimmedResp)
        println(s"tokens: ${tokens}")
      case Failure(e) => println(e)
    }
  }

  def sendOneQuery(inference: Safelang) {
  //curl --data "?X=bob"  http://152.3.136.26:7777/mymethod
    val jvmAddr = "152.3.136.26:7777"
    val method = "mymethod"
    val speaker: Option[String] = None
    val subject: Option[String] = Some("aaallliiiccceee")
    val objectId: Option[String] = None
    val bearerRef: Option[String] = None
    val principal: Option[String] = None

    //val subject: Option[String] = None
    //val subject: Option[String] = Some("dycfg32gYgr22dXShIbs0VnJVQwRKXq-LDKr3LaRtw0")
    //val principal: Option[String] = Some("dycfg32gYgr22dXShIbs0VnJVQwRKXq-LDKr3LaRtw0")

    val otherArgs = Seq("bob")
    val res = inference.slangCallClient.sendSlangRequest(jvmAddr, method, 
                speaker, subject, objectId, bearerRef, principal, otherArgs)
    res.onComplete {
      case Success(response: SlangCallResponse) =>
        println(s"SlangResponse: ${response.message}")
      case Failure(e) => println(e)
    }
  }

  def sendAnotherQuery(inference: Safelang) {
  //curl --data "?X=bob"  http://152.3.136.26:7777/mymethod
    val jvmAddr = "152.3.136.26:7777"
    val method = "tagAccess"
    val speaker: Option[String] = None
    val subject: Option[String] = None 
    val objectId: Option[String] = None
    val bearerRef: Option[String] = None
    val principal: Option[String] = None

    val otherArgs = Seq[String]()
    val res = inference.slangCallClient.sendSlangRequest(jvmAddr, method, 
                speaker, subject, objectId, bearerRef, principal, otherArgs)
    res.onComplete {
      case Success(response: SlangCallResponse) =>
        println(s"SlangResponse: ${response.message}")
      case Failure(e) => println(e)
    }
  }

  def send3rdQuery(inference: Safelang) {
    // curl  -v -X POST http://152.3.136.26:7777/createSliver -H "Content-Type: application/json" -d "{ \"subject\": \"PaGprNcC8u_8lxUP9Cyg0Z9bldrgIxhTgzBj7IwKBzo\", \"bearerRef\": \"zIQ72qcY4tiwK_AZuPK46FnRWgx4KwqCWA_7wwMreTg\", \"otherValues\": [\"-mzMI8X26qjB1J7xtsarkbcjqHjhp7R6_z08IJE5yng:slice1\"] }"
    val jvmAddr = "152.3.136.26:7777"
    val method = "createSliver"
    val speaker: Option[String] = None
    val subject: Option[String] = Some("PaGprNcC8u_8lxUP9Cyg0Z9bldrgIxhTgzBj7IwKBzo") 
    val objectId: Option[String] = None
    val bearerRef: Option[String] = Some("zIQ72qcY4tiwK_AZuPK46FnRWgx4KwqCWA_7wwMreTg")
    val principal: Option[String] = None

    val otherArgs = Seq[String]("-mzMI8X26qjB1J7xtsarkbcjqHjhp7R6_z08IJE5yng:slice1")
    val res = inference.slangCallClient.sendSlangRequest(jvmAddr, method, 
                speaker, subject, objectId, bearerRef, principal, otherArgs)
    res.onComplete {
      case Success(response: SlangCallResponse) =>
        println(s"SlangResponse: ${response.message}")
      case Failure(e) => println(e)
    }
  }

}
