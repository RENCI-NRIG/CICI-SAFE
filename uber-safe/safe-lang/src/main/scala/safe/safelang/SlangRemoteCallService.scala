package safe.safelang
import safe.safelog._
import prolog.terms.{Fun => StyFun, Const => StyConstant, Term => StyTerm}

import scala.concurrent.Future
import com.typesafe.scalalogging.LazyLogging

trait SlangRemoteCallService {
  val slangCallClient: SlangRemoteCallClient
}

import spray.json._

/**
 * Slangcall messages
 */
case class SlangCallParams(speaker: Option[String], subject: Option[String], objectId: Option[String],
                           bearerRef: Option[String], principal: Option[String], otherValues: Seq[String])

case class SlangCallResponse(message: String)

object SlangCallMessageFormat extends DefaultJsonProtocol {
  implicit val paramsFormat = jsonFormat6(SlangCallParams)
  implicit val repsFormat = jsonFormat1(SlangCallResponse)
}

/**
 * Helpers for the slangcall service 
 */
object SlangRemoteCallService extends LazyLogging {

  def stringOrNone(str: String): Option[String] = {
    if(str.isEmpty) None else Some(str)
  }

  // Structure example: method($Self, ?JVM, ?Principal, ?Envs, ?otherArg0, ?otherArg1, ...)  
  // Envs example: [speaker]:[subject]:[object]:[bearerRef]
  def slangCall(struct: Structure, slangCallClient: SlangRemoteCallClient): Future[SlangCallResponse] = {
    if(struct.terms.size < 4) throw UnSafeException(s"Not enough args for slang call ${struct}")
    val jvmAddr: String = struct.terms(1).id.name
    val principal: Option[String] = Some(struct.terms(2).id.name)
    val envStr = struct.terms(3).id.name
    val envs = parseSlangCallEnvs(envStr) 
    val speaker: Option[String] = stringOrNone(envs(0)) 
    val subject: Option[String] = stringOrNone(envs(1)) 
    val objectId: Option[String] = stringOrNone(envs(2))
    val bearerRef: Option[String] = stringOrNone(envs(3))
    val method: String = struct.id.name 
    val otherArgs: Seq[String] = struct.terms.drop(4).map{ t => t.id.name}
    logger.info(s"================================== slang call: struct=${struct};  jvmAddr=${jvmAddr};  principal=${principal};  speaker=${speaker};  subject=${subject};  objectId=${objectId};  bearerRef=${bearerRef};  method=${method};  otherArgs=${otherArgs} ===========================================")
    slangCallClient.sendSlangRequest(jvmAddr, method, speaker, subject, objectId, bearerRef, principal, otherArgs)
  }

  // StyFun format0: method(?JVM, ?Principal, ?Envs, ?otherArg0, ?otherArg1, ...)  
  //        format1: ?Principal: method(?JVM, ?Envs, ?otherArg0, ?otherArg1, ...) // for interative slang shell 
  // Envs example: [speaker]:[subject]:[object]:[bearerRef]
  def slangCall(sfun: StyFun, slangCallClient: SlangRemoteCallClient): Future[SlangCallResponse] = {
    val fun = if(sfun.sym == ":" && sfun.args.size == 2) convertToStdFun(sfun) else sfun 
    if(fun.args.size < 3) throw UnSafeException(s"Not enough args for slang call ${fun} ${fun.args.size}")
    val jvmAddr: String = fun.getArg(0).asInstanceOf[StyConstant].sym
    val principal: Option[String] = Some(fun.getArg(1).asInstanceOf[StyConstant].sym)
    val envStr = fun.getArg(2).asInstanceOf[StyConstant].sym
    val envs = parseSlangCallEnvs(envStr) 
    val speaker: Option[String] = stringOrNone(envs(0)) 
    val subject: Option[String] = stringOrNone(envs(1)) 
    val objectId: Option[String] = stringOrNone(envs(2))
    val bearerRef: Option[String] = stringOrNone(envs(3))
    val method: String = fun.sym 
    //logger.info(s"fun.args.length=${fun.args.length}  Seq.range(0, fun.args.length -1).drop(3)=${Seq.range(0, fun.args.length -1).drop(3)}")
    val otherArgs: Seq[String] = Seq.range(0, fun.args.length).drop(3).map{i => fun.getArg(i).toString} 
    logger.info(s"================================== slang call (style engine): fun=${fun};  jvmAddr=${jvmAddr};  principal=${principal};  speaker=${speaker};  subject=${subject};  objectId=${objectId};  bearerRef=${bearerRef};  method=${method};  otherArgs=${otherArgs} ===========================================")
    //println(s"================================== slang call (style engine): fun=${fun};  jvmAddr=${jvmAddr};  principal=${principal};  speaker=${speaker};  subject=${subject};  objectId=${objectId};  bearerRef=${bearerRef};  method=${method};  otherArgs=${otherArgs} ===========================================")
    slangCallClient.sendSlangRequest(jvmAddr, method, speaker, subject, objectId, bearerRef, principal, otherArgs)
  }

  /**
   * Convert a speak fun to a standard defcall fun
   */
  def convertToStdFun(f: StyFun): StyFun = {
    assert(f.sym == ":" && f.args.size == 2 && f.getArg(1).isInstanceOf[StyFun], s"Cannot convert ${f} to a standard defcall fun")
    val speaker: StyTerm = f.getArg(0)
    val fun = f.getArg(1).asInstanceOf[StyFun]
    val fargs = fun.args 
    val fname = fun.sym
    val stdargs = fargs(0) +: speaker +: fargs.slice(1, fargs.length)
    new StyFun(fname, stdargs)
  }

  // Helper for per-request envs
  def parseSlangCallEnvs(envStr: String): Seq[String] = {
    val envs = if(REQ_ENV_DELIMITER == "|") {  // Need to escape 
       envStr.split(s"\\${REQ_ENV_DELIMITER}", -1) // keep the leading and trailing empty entries
    } else {
       envStr.split(s"${REQ_ENV_DELIMITER}", -1) // keep the leading and trailing empty entries
    }
    if(envs.size != 4) { // we expect four envs: speaker, subject, object, bearerRef
      throw UnSafeException(s"Invalid slang call envs: ${envStr} ${envs.length} ${envs}")
    }
    envs
  }

  /**
   * Extract slogset tokens from a slangcall response message of 
   * slang inference result (objectStr).
   *
   * Format of a inference response with slogset tokens:
   * 'token0', 'token1', ..., 'tokenN'
   *
   * An example:
   * 'X8buNSIAef3BHZXBt5etZgFd-eiTrOCDTwTqOX-PLfY', 'jSvdyfIYzmWO-d1V_mJqPq1fPkIS8CPkE2TxFfsk3o'
   */
  def extractSlogsetTokens(objectStr: String): Seq[String] = {
    val tokenPattern = """'(.*)'""".r
    if(!objectStr.isEmpty) {
      val parts = objectStr.split(", ")
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

  /**
   * Two types of slangcall responses: 
   * 1) token seq replied by a defpost
   * 2) query result replied by a defguard
   *
   * Token response example:
   * ['X8buNSIAef3BHZXBt5etZgFd-eiTrOCDTwTqOX-PLfY', 'jSvdyfIYzmWO-d1V_mJqPq1fPkIS8CPkE2TxFfsk3ow']
   *
   * Query response example:
   *
   * { 'S1RLw6fjPg0Hii-UbRkUHkiVkG0Tya0EnrYLZm4zWl8': approveSliver('PaGprNcC8u_8lxUP9Cyg0Z9bldrgIxhTgzBj7IwKBzo', '-mzMI8X26qjB1J7xtsarkbcjqHjhp7R6_z08IJE5yng:slice1') } 
   */
  def parseSlangCallResponse(objectStr: String): Seq[Term] = {
    val tokenResponsePattern = """\[(.*)\]\s*$""".r
    val queryResultPattern = """\{(.*)\}\s*$""".r 
    val tokensOrQueryres: Seq[Term] = objectStr match {
      case tokenResponsePattern(tokensStr) => {
        extractSlogsetTokens(tokensStr).map{ str => 
          Constant(StrLit(str), StrLit("nil"), StrLit("StrLit"), Encoding.AttrBase64)
        }
      } 
      case queryResultPattern(resultStr) => {
        if(!resultStr.isEmpty) {
          Seq(Constant(StrLit(resultStr.trim), StrLit("nil"), StrLit("StrLit"), Encoding.Attr))
        }
        else {
          Seq[Term]()
        }
      }
      case "" => Seq[Term]() // Unsatisfied: empty res list
      case _ => Seq[Term]() //throw UnSafeException(s"Invalid defcall response message $objectStr")
    }
    tokensOrQueryres 
  }
}

