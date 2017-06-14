package safe.programming

import safe.safelang.model._
import util._
import safe.safelang._
import safe.safelog._

import java.security.{PublicKey, KeyPair}

/**
 * Local stub of a remote principal
 */
class PrincipalStub (
    protected val pid: String,                     // Principal id: hash of public key
    protected val canonicalName: String,           // Symbolic name of the principal
    protected val serverJvm: String,               // Address of the principal's JVM
    protected var subjectSetTokens: Seq[String],   // Tokens of his subject sets 
    protected val keyFile: String                  // pem filepath
  ) extends Serializable with SlangObjectHelper {
 
  def this(publicKey: PublicKey, cn: String, serverJvm: String, subjectSetTokens: Seq[String]) {
    this(PrincipalStub.pidFromPublicKey(publicKey), cn, serverJvm, subjectSetTokens, "")
  }

  def this(publicKey: PublicKey, cn: String, serverJvm: String) {
    this(publicKey, cn, serverJvm, Seq[String]())
  }
 
  def this(publicKey: PublicKey, serverJvm: String, subjectSetTokens: Seq[String]) {
    this(PrincipalStub.pidFromPublicKey(publicKey), "", serverJvm, subjectSetTokens, "")
  }

  def this(publicKey: PublicKey, serverJvm: String) {
    this(publicKey, "", serverJvm, Seq[String]())
  }
 
  def this(keyFile: String, cn: String, serverJvm: String, subjectSetTokens: Seq[String]) {
    this(PrincipalStub.pidFromFile(keyFile), cn, serverJvm, subjectSetTokens, keyFile)
  }
 
  def this(keyFile: String, cn: String, serverJvm: String) {
    this(keyFile, cn, serverJvm, Seq[String]())
  }

  def this(keyFile: String, serverJvm: String, subjectSetTokens: Seq[String]) {
    this(PrincipalStub.pidFromFile(keyFile), "", serverJvm, subjectSetTokens, keyFile)
  }
 
  def this(keyFile: String, serverJvm: String) {
    this(keyFile, "", serverJvm, Seq[String]())
  }

  /* DeveloperAPI */
  override def toString(): String = {
    s"""|${pid}
        |${canonicalName}
        |${serverJvm}
        |${subjectSetTokens}
        |${keyFile}""".stripMargin
  }

  def getPid(): String = pid
  def getCN(): String = canonicalName
  def getJvm(): String = serverJvm
  def getSubjectSetTokens: Seq[String] = subjectSetTokens
  def getKeyFile(): String = keyFile

  def queryLocalSlang(inference: Safelang, goal: String, args: Seq[Constant]): Seq[Seq[Statement]] = {
    val query = Query(Seq(Structure(goal, args)))
    val res: Seq[Seq[Statement]] = inference.solveSlang(Seq(query), false)
    res
  } 

  def queryAndExtractToken(inference: Safelang, goal: String, args: Seq[Constant]): String = {
    val res: Seq[Seq[Statement]] = queryLocalSlang(inference, goal, args)
    val token = firstTokenOfInferredResult(res)
    token
  } 

  /**
   * Make a simple remote call. The remote call is simple as it follows 
   * a simple convention:
   * entryPoint(?ServerJVM, ?ServerPrincipal, ?Envs, ... ?Arg ...)
   */
  def simpleRemoteCall(inference: Safelang, entryPoint: String, 
      env: String = emptyEnvs, args: Seq[String] = Seq()): String = {
    val queryArgs = (Seq(serverJvm, pid, env) ++ args).map(s => buildConstant(s))
    val token: String = queryAndExtractToken(inference, entryPoint, queryArgs)
    token
  }

  /**
   * Remote call to a specific server 
   * The caller must ensure that the principal is installed on the server
   */
  def remoteCallToServer(inference: Safelang, entryPoint: String, specifiedJvm: String, 
      env: String = emptyEnvs, args: Seq[String] = Seq()): String = {
    val queryArgs = (Seq(specifiedJvm, pid, env) ++ args).map(s => buildConstant(s))
    val token: String = queryAndExtractToken(inference, entryPoint, queryArgs)
    token
  }

  /**
   * Format of the postIdSet defcall in local Slang:
   * defcall postIdSet(?JVM, ?Principal, ?Envs, ?CN)
   */
  def postIdSet(inference: Safelang): String = {
    simpleRemoteCall(inference, "postIdSet", args=Seq(canonicalName)) 
  }

  /**
   * Format of the postSubjectSet defcall in local Slang: 
   * defcall postSubjectSet(?JVM, ?Principal, ?Envs)
   */
  def postSubjectSet(inference: Safelang): String = {
    simpleRemoteCall(inference, "postSubjectSet")
  }

  /**  
   * Post subject set and store the token into subjectSetTokens
   * We assume each principal has only one subject set. So each
   * principal only call this method once.
   */ 
  def postSubjectSetAndGetToken(inference: Safelang): Unit = {
    val token = postSubjectSet(inference)
    if(!subjectSetTokens.contains(token)) { 
    // Add the token into the subject token set if it hasn't been added yet
      subjectSetTokens = subjectSetTokens :+ token
    }
  }

  /**
   * Update subject set with a link 
   */
  def updateSubjectSet(inference: Safelang, link: String): String = {
    simpleRemoteCall(inference, "updateSubjectSet", args=Seq(link))
  } 

  def init(inference: Safelang): Unit = { }
}

object PrincipalStub {
  def pidFromFile(pemFile: String): String = {
    val publicKey = publicKeyFromFile(pemFile)
    pidFromPublicKey(publicKey)
  }

  def publicKeyFromFile(pemFile: String): PublicKey = {
    val keyPair: KeyPair = Principal.keyPairFromFile(pemFile)
    keyPair.getPublic()
  }

  def pidFromPublicKey(publicKey: PublicKey): String = {
    Identity.encode(Identity.hash(publicKey.getEncoded()))
  } 
}
