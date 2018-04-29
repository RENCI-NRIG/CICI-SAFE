package safe.safelang
package safesets

import model.{Principal, Identity}
import safe.safelog.{UnSafeException, Validity}
import safe.safelang.{Token => PIDToken}
import safe.cache.SafeTable
import com.google.common.cache.LoadingCache

import scala.concurrent.{Await, Future}
import akka.pattern.ask
import akka.actor.{ActorRef, ActorSystem}
import spray.http.HttpResponse
import spray.http.{MultipartContent, BodyPart}
import spray.httpx.unmarshalling._
import spray.http.{MediaTypes, MediaType}
import scala.util.{Success, Failure, Try}
import com.typesafe.scalalogging.LazyLogging
import scala.collection.mutable.ListBuffer
import org.joda.time.DateTime
  
trait SafeSetsService { 
  // To be initialized with the class that extends this trait
  val safeSetsClient: SafeSetsClient 
}

/**
 * SafeSetsClient is a top-level abstraction of SafeSets
 * client. It uses one or more storage clients to read/
 * write certificates over http. The Apache storage client
 * also supports support TLS.
 * 
 * If Config.config.localSafeSets is set, reads/writes
 * go to local safesets: a local directory on disk 
 * containing certificates in individual files. The path 
 * of this local safeset is set by Config.config.safeSetsDir 
 *
 * It deals with these kinds of links (tokens) as described 
 * below:
 *
 * 0) Self certifying links: [PID]:[LabelHash]. Each principal
 * has its own shard comprised of a list of preferred set
 * stores. Links to identity sets only have the PID part.
 * Read/write requests through these links are served by a
 * well-known metastore.  
 *
 * 1) Flat links: [Hash of PID+Labelhash]. This happens when
 * set sharding (thus self certifying tokens) is turned off. The
 * metastore stores the sets and serves read/write requests.
 *
 * The code considers 1) and 2) under the same model: self
 * certifying tokens, with ID set tokens and flat tokens being
 * a non-trivial subset of self certifying tokens. All non-trivial
 * self certifying tokens point to sets stored on the metastore.
 *
 * 2) Web links: [Web url], e.g., 
 * http://152.3.145.253:8098/types/safesets/buckets/safe/keys/cert0 
 * Only read operations on these links at the moment.
 *
 * 3) LDAP links: [LDAP server addr]/[Search base]
 * For example:
 * ldap://registry-test.cilogon.org:389/ou=people,o=ImPACT,dc=cilogon,dc=org 
 * Only read operations at the moment. 
 */

class SafeSetsClient(val system: ActorSystem) extends LazyLogging {
  //import system.dispatcher   // execution context for futures
  import HttpMultipartContentHelper._
  
  val metastore = Config.config.metastore
  val localSafeSets: Boolean = Config.config.localSafeSets

  private var idcache: LoadingCache[PIDToken, IDSet] = null
  
  def attachIdSetCache(c: LoadingCache[PIDToken, IDSet]): Unit = {
    idcache = c
  }

  /**
   * SSLSession with a set store can be reused across multiple requests.
   * Server authentication as part of the SSL handshake is done when 
   * the first request is processed. Subsequent requests just use the  
   * established SSL session. 
   *
   * A TLS storage client (based on Apache) provides the server's public
   * key hash only in the context of the first request to each store. 
   * serverAuthNTable keeps track of the authentication status of the set 
   * stores that have been interacted so far. This cache helps the decision
   * making on server authentication for the requests that are not the
   * the first one sent to each store. 
   */
  val serverAuthNTable: SafeTable[String, AuthNStatus] = new SafeTable[String, AuthNStatus](
    1024*1024,
    0.75f,
    16
  )

  //val storageclient: StorageClient = if(localSafeSets) {
  //  DiskaccessStorageClient()
  //} else {  SprayStorageClient(system) }

  val storageclient: StorageClient = SprayStorageClient(system)                         // default client, via http
  val diskaccessclient: StorageClient = DiskaccessStorageClient()                       // Local disk
  val tlsstorageclient: StorageClient = ApacheStorageClient(Config.config.sslOn, this)  // tls client, via https 
  val cassandraclient: StorageClient = CassandraStorageClient()                         // cassandra client, via its native protocol
  val ldapclient: LDAPClient = new LDAPClient()                                      // ldap client, read only

  /**
   * A Self-certifying cert token is in the following format:
   * [PID]:[labelhash]
   *
   * Both PID and labelhash don't contain character ":".
   * When labelhash is empty, the cert token is "[PID]", referring
   * to the ID set of the principal.
   */
  def getPIDFromSelfCertifyingToken(sctoken: String): String = {
    val i = sctoken.lastIndexOf(":")
    //println(s"i=${i}    sctoken=${sctoken}")
    if(i < 0) {  // delimiter ":" not found 
    // ID set token
      return sctoken
    }
    sctoken.substring(0, i)
  }

  /**
   * Check if a token is an ID set token or a flat token. We start with
   * every token being a self certifying token and consider ID set tokens
   * and flat tokens are a special case of self certifying tokens.
   */
  def isIDSetTokenOrFlatToken(sctoken: String): Boolean = {
    val i = sctoken.lastIndexOf(":")
    if(i < 0) {
      logger.info(s"Non-trivial self certifying token (ID set token or flat token): ${sctoken}")
      true
    } else {
      logger.info(s"Trivial self certifying token: ${sctoken}")
      false
    }
  }

  /**
   * Take a self certifying token and return the desc of the
   * set store that holds the set. For tokens of id sets and
   * tokens that are generated without enabling self certifying
   * token (flat tokens), the sets are hosted on the metastore.
   */
  def getSetStoreDescForSelfCertifyingToken(sctoken: String): SetStoreDesc = {
    val pid = getPIDFromSelfCertifyingToken(sctoken)
    if(pid.isEmpty) {
      throw UnSafeException(s"${sctoken} refers to a local set missing from the local set table!")
    }
    //println(s"pid=${pid}")

    if(pid == sctoken) { // ID set 
      metastore
    }
    else {
      Try(idcache.get(PIDToken(pid))) match {
        case Success(s: IDSet) =>
          val setstores: Seq[SetStoreDesc] = s.getPreferredSetStores() 
          println(s"ID SET \n${s}")
          var ss = metastore       // Use the metastore the default set store
          if(setstores.size > 0) { // Issuer specified at lease one set stores
            //println(s"setstores.size=${setstores.size}")
            // Use the first set store
            ss = setstores(0)
          }
          ss
        case Failure(e) =>
          throw UnSafeException(s"ID set not found for principal: ${pid}")
      }
    }
  }

  /**
   * Compute certificate address of a self certifying token.
   * Note that flat tokens (tokens that are generated with self certifying
   * tokens disabled) and ID set tokens are a special case of self certifying
   * tokens, in the sense that these tokens only have the PID part and that all
   * go to the metastore.
   */
  def getCertAddrFromSelfCertifyingToken(sctoken: String): CertAddr = {
    val ss: SetStoreDesc = getSetStoreDescForSelfCertifyingToken(sctoken)
    var hashToken: String = sctoken
    if(!isIDSetTokenOrFlatToken(sctoken)) {
      hashToken = Identity.encode(Identity.hash(sctoken.getBytes(
                                                StringEncoding),"SHA-256"), "base64URLSafe")
    }
    val certaddr: CertAddr = CertAddr(ss, hashToken)
    certaddr
  }

  def postSlogSet(setToPost: SlogSet, speaker: Principal): String = {
    val token: String = setToPost.computeToken(speaker)   // compute slogset token 
    val encoding = Seq("slang")
    setToPost.synchronized { // Synchronizing on the slogset
      val certificateSeq = encoding map { format =>
                val (token, certificate) = setToPost.signAndEncode(speaker, format)
                //println(s"[LocalSafeSetsService postSlogSet] certificate: $certificate \ntoken: ${token}")
                certificate
              }
      val allCertificates = certificateSeq.mkString("\n")
   
      logger.info(s"Token $token:\n$allCertificates")

      val certaddr: CertAddr = getCertAddrFromSelfCertifyingToken(token)
      //println(s"Token $token:\n$allCertificates")
      if(localSafeSets) { diskaccessclient.postCert(certaddr, allCertificates) } 
      else { 
        //postValidation(token, allCertificates, speaker)
        postCert(certaddr, allCertificates)
      }
    }
    token
  }

  def postCert(certaddr: CertAddr, content: String): String = {
    val p: Int = certaddr.getStoreProtocol
    if(p == SetStoreDesc.HTTP) {
      storageclient.postCert(certaddr, content) 
    } else if(p == SetStoreDesc.CASSANDRA_NATIVE) {
      cassandraclient.postCert(certaddr, content)
    } else if(p == SetStoreDesc.HTTPS) {
      // Set up store authN entry before request
      val storeaddr: String = certaddr.getStoreAddr
      val storeId: String = certaddr.getStoreID
      logger.info(s"authN table: ${serverAuthNTable}   containsKey: ${serverAuthNTable.containsKey(storeaddr)}")
      if(!serverAuthNTable.containsKey(storeaddr)) {
        val authNEntry: AuthNStatus = AuthNStatus(storeId)
        logger.info(s"authN status: ${authNEntry}") 
        serverAuthNTable.put(storeaddr, authNEntry)      
      }
      tlsstorageclient.postCert(certaddr, content)
    } else {
      throw UnSafeException(s"Unrecognized protocol of set store ${p}")
    }
  }

  def fetchCert(certaddr: CertAddr): Option[String] = {
    val p: Int = certaddr.getStoreProtocol
    if(p == SetStoreDesc.HTTP) {
      storageclient.fetchCert(certaddr) 
    } else if (p == SetStoreDesc.CASSANDRA_NATIVE) {
      cassandraclient.fetchCert(certaddr) 
    } else if(p == SetStoreDesc.HTTPS) {
      // Set up store authN entry before request
      val storeaddr: String = certaddr.getStoreAddr
      val storeId: String = certaddr.getStoreID
      logger.info(s"authN table: ${serverAuthNTable}   containsKey: ${serverAuthNTable.containsKey(storeaddr)}")
      //scala.io.StdIn.readLine()
      if(!serverAuthNTable.containsKey(storeaddr)) {
        val authNEntry: AuthNStatus = AuthNStatus(storeId)
        logger.info(s"authN status: ${authNEntry}") 
        //scala.io.StdIn.readLine()
        serverAuthNTable.put(storeaddr, authNEntry)      
      }
      tlsstorageclient.fetchCert(certaddr)
    } else {
      throw UnSafeException(s"Unrecognized protocol of set store ${certaddr}   ${certaddr.getStoreProtocol}")
    }
  }

  def deleteCert(certaddr: CertAddr): String = {
    val p: Int = certaddr.getStoreProtocol
    if(p == SetStoreDesc.HTTP) {
      storageclient.deleteCert(certaddr) 
    } else if(p == SetStoreDesc.CASSANDRA_NATIVE) {
      cassandraclient.deleteCert(certaddr)
    } else if(p == SetStoreDesc.HTTPS) {
      // Set up store authN entry before request
      val storeaddr: String = certaddr.getStoreAddr
      val storeId: String = certaddr.getStoreID
      if(!serverAuthNTable.containsKey(storeaddr)) {
        val authNEntry: AuthNStatus = AuthNStatus(storeId)
        serverAuthNTable.put(storeaddr, authNEntry)      
      }
      tlsstorageclient.deleteCert(certaddr)
    } else {
      throw UnSafeException(s"Unrecognized protocol of set store ${p}")
    }
  }


  /**
   * Validate if a post to a token is valid 
   * can be a check performed by a storage 
   * server
   */
  private def postValidation(tkn: String, source: String, s: Principal): Unit = {

    val t0 = System.nanoTime()

    val endOfToken = source.indexOf("\n")
    val token: String = source.substring(0, endOfToken)
    val endOfSig = source.indexOf("\n", endOfToken+1)
    val sig: String = source.substring(endOfToken+1, endOfSig)
    val endOfSpeaker = source.indexOf("\n", endOfSig+1)
    val speaker: String = source.substring(endOfSig+1, endOfSpeaker)
    val endOfValidity = source.indexOf("\n", endOfSpeaker+1)
    val validity: String = source.substring(endOfSpeaker+1, endOfValidity)
    val endOfSigAlg = source.indexOf("\n", endOfValidity+1)
    val sigAlg = source.substring(endOfValidity+1, endOfSigAlg)
    val endOfLabel = source.indexOf("\n", endOfSigAlg+1)
    val label: String = source.substring(endOfSigAlg+1, endOfLabel)
    val startOfLogic = source.indexOf("\n", endOfLabel+1) + 1  // An empty line as delimiter

    val setData: String = source.substring(endOfSig+1, source.length)
    val slogSource: String = source.substring(startOfLogic, source.length)
    val vldParts: Array[String] = validity.split(",")
    assert(vldParts.size == 3, s"Invalid validity: ${validity}  ${vldParts}")
    val v: Validity = Validity(Some(vldParts(0).trim), Some(vldParts(1).trim), Some(vldParts(2).trim))

    // validate signature
    val sig_tool: java.security.Signature = java.security.Signature.getInstance(sigAlg)
    sig_tool.initVerify(s.getPublicKey)
    val h: Array[Byte] = Identity.hash(setData.getBytes())
    sig_tool.update(h)
    val res = sig_tool.verify(Identity.base64Decode(sig))
    if(!res) {
      throw UnSafeException(s"Signature on token ${token} doesn't checkout")
    }
  
    // check if token == hash(pid, label)
    val nameHash = Identity.encode(Identity.hash(label.getBytes(StringEncoding), "MD5"), "base64URLSafe")
    val namespace = s"${speaker}:${nameHash}"
    val setIdHash = Identity.hash(namespace.getBytes(StringEncoding), "SHA-256")
    val t = Identity.encode(setIdHash, "base64URLSafe")
    val res1 = (t == token)
    if(!res1) {
      throw UnSafeException(s"Token doesn't checkout   token:${token}  speaker:${speaker}   label:${label}  t:${t}")
    }

    // check if expiration time isn't passed 
    val now = new DateTime()
    val res2 = v.notAfter.isAfter(now)
    if(!res2) {
      throw UnSafeException(s"cert is expired: ${v.notAfter}  ${now}")
    }

    val tt = System.nanoTime()
    slangPerfCollector.addStarPerfStats((tt-t0)/1000, s"postvalidation_${token}")

  } 

  /**
   * Parse and convert a cert to a datalog SlogSet
   */
  def parseASlogSet(cert: String): SlogSet = {
    val slangParser = Parser()
    slangParser.parseCertificate(cert)
  }

  def fetchSlogSet(certaddr: CertAddr): Seq[SlogSet] = {
    val p: Int = certaddr.getStoreProtocol
    if(p == SetStoreDesc.LDAP) {  // using ldapclient to fetch slog set directly
      return ldapclient.fetchSlogSet(certaddr)
    }
    val rawCert: Option[String] = if(localSafeSets) {
          diskaccessclient.fetchCert(certaddr) } else { fetchCert(certaddr) }
    val start = System.nanoTime
    //logger.info(s"rawCert: $rawCert")
    val slogsets: Seq[SlogSet] = rawCert match {
      case Some(cert) => 
        logger.info(s"cert: \n$cert")
        if(localSafeSets || notMultipartEntity(cert)) {
          val s: SlogSet = parseASlogSet(cert) 
          logger.info(s"slogset: $s")

          // Collect set-parsing time
          val t = (System.nanoTime - start) / 1000
          val length = cert.length
          slangPerfCollector.addSetParsingTime(t.toString, s"${certaddr.getUrl} $length")

          Seq(s) 
        } else {
          val unmarshalled: Deserialized[MultipartContent] = unmarshalMultipartContent(cert)
          logger.info(s"unmarshalled: $unmarshalled")
          val fetched = ListBuffer[SlogSet]()
          unmarshalled match {
            case Right(mpc) =>
              var i = 0
              for(part <- mpc.parts) {
                logger.info(s"MultipartContent: part $i")
                logger.info(s"${part.headers.map(_.toString)}")
                logger.info(s"${part.entity.asString}")
                val s: SlogSet = parseASlogSet(part.entity.asString)
                fetched += s
                i += 1 
              }
            case Left(deserializationError) =>
              logger.error(s"$deserializationError") 
          }
          fetched.toSeq
        }
      case _ =>  
        logger.info(s"Invalid rawCert: ${rawCert}")
        Seq[SlogSet]()
    } 
    slogsets
  }


  def fetchSlogSet(token: String): Seq[SlogSet] = {
    /**
     * Web links and LDAP links serve as entry points to
     * external endorsements, and these links are ready only.
     * For the links, a token itself identifies a cert
     * address. 
     *
     * 1) A web link begins with "http://"
     *
     * 2) An LDAP link begins with "ldap://"
     *
     */
    val certaddr = token.substring(0, 7)  match {
      case "ldap://" => CertAddr(SetStoreDesc(token, SetStoreDesc.LDAP, ""), "") 
      case "http://" => CertAddr(SetStoreDesc(token, SetStoreDesc.HTTP, ""), "") 
      case _ => getCertAddrFromSelfCertifyingToken(token) 
    } 
    //val certaddr = getCertAddrFromSelfCertifyingToken(token)
    
    fetchSlogSet(certaddr)
  }

  def deleteSlogSet(token: String): String = {
    val certaddr = getCertAddrFromSelfCertifyingToken(token)
    val delResponse: String = if(localSafeSets) {
          diskaccessclient.deleteCert(certaddr) } else { deleteCert(certaddr) }
    delResponse
  }

}

object SafeSetsClient {
  def apply(system: ActorSystem): SafeSetsClient = new SafeSetsClient(system)
} 
