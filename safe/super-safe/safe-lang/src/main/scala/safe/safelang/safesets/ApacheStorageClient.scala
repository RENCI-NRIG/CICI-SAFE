package safe.safelang
package safesets

import safe.safelog.UnSafeException
import model.Identity

import scala.concurrent.Future
import scala.util.{Try, Success, Failure}

import com.google.common.cache.LoadingCache

// For Apache HttpClient
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.{HttpPost, HttpGet, HttpDelete}
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.HttpEntity
import org.apache.http.auth.AuthState
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.conn.socket.{PlainConnectionSocketFactory, ConnectionSocketFactory}
import org.apache.http.conn.ssl.{SSLConnectionSocketFactory, NoopHostnameVerifier}
import org.apache.http.{HttpConnection, HttpHost, HttpRequest}
import org.apache.http.conn.ManagedHttpClientConnection
import org.apache.http.conn.routing.RouteInfo
import org.apache.http.config.{Registry, RegistryBuilder}
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.client.CredentialsProvider
import org.apache.http.protocol.HttpContext
import java.nio.charset.StandardCharsets
import java.io.FileInputStream
import java.io.File
import java.net.Socket

import javax.net.ssl.{SSLContext, TrustManager, X509TrustManager, KeyManager, SSLSession, SSLSocket}
import javax.net.ssl.KeyManagerFactory
import java.security.KeyStore
import java.security.SecureRandom
import javax.security.cert.X509Certificate

// For Apache HttpAsyncClient
// TODO: impl for an asynchronous client 
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient
import org.apache.http.impl.nio.client.HttpAsyncClients


/**
 * ApacheStorageClient is based on Apache HttpClient.
 * It's intended to mainly deal with SSL. All client
 * operations, i.e., fetch, post, and delete, are
 * synchronous. 
 *
 * TODO: implement asynchronous operations using
 * Apache HttpAsyncClient.
 */

class ApacheStorageClient(val ssl: Boolean, val ssclient: SafeSetsClient)
    extends StorageClient { 
  import HttpMultipartContentHelper._

  val PEER_CERT_CHAIN: String = "PeerCertChain"
  val RIAK_MULT_HEADER_KEY: String = "Accept"
  val RIAK_MULT_HEADER_VALUE: String = "multipart/mixed"

  val apachehttpclient: CloseableHttpClient = buildHttpClient(ssl) 
  
  def buildHttpClient(ssl: Boolean): CloseableHttpClient = {
    if(ssl) {
      initHttpClientSSL()
    } else {
      initHttpClient()
    }
  }
    
  def initHttpClient(): CloseableHttpClient = {
    // Set up apacheHttpClient 
    val cm = new PoolingHttpClientConnectionManager() 
    cm.setMaxTotal(200) 
    cm.setDefaultMaxPerRoute(20) 
    val httpclient: CloseableHttpClient = HttpClients.custom().setConnectionManager(cm).build() 
    httpclient
  }

  def initHttpClientSSL(): CloseableHttpClient = {
    // Set up apache http client with support for ssl/tls 
    val plainsf: ConnectionSocketFactory = PlainConnectionSocketFactory.getSocketFactory()
    val keystore: KeyStore  = KeyStore.getInstance("PKCS12")
    val fis: FileInputStream = new FileInputStream(new File(Config.config.sslKeyStore))
    val passwd: Array[Char] = Config.config.keystorePasswd.toCharArray()
    keystore.load(fis, passwd)

    val kmfactory: KeyManagerFactory = KeyManagerFactory.getInstance(
        KeyManagerFactory.getDefaultAlgorithm())
    kmfactory.init(keystore, passwd)

    val keyManagers: Array[KeyManager] = kmfactory.getKeyManagers()
    val trustManager: TrustManager = new X509TrustManager() {
      def checkClientTrusted(chain: Array[java.security.cert.X509Certificate], 
            authType: String): Unit = {
        logger.info(s"[TrustManager.checkClientTrusted] chain: ${chain} authType: ${authType}")
      }
      def checkServerTrusted(chain: Array[java.security.cert.X509Certificate], 
            authType: String): Unit = {
        logger.info(s"[TrustManager.checkServerTrusted] chain: ${chain} authType: ${authType}")
        //for(c <- chain) {
        //  logger.info(s"IssuerDN: ${c.getIssuerDN}     \nIssuerUniqueID: ${c.getIssuerUniqueID}
        //              \nIssuerX500Principal: ${c.getIssuerX500Principal}     
        //              \nIssuerX500Principal.getName: ${c.getIssuerX500Principal.getName}
        //              \nIssuerX500Principal.getEncoded: ${c.getIssuerX500Principal.getEncoded}
        //              \nSubjectPublicKey: ${c.getPublicKey}      
        //              \nSubjectID: ${Identity.encode(Identity.hash(c.getPublicKey.getEncoded))}")
        //}
      }
      def getAcceptedIssuers(): Array[java.security.cert.X509Certificate] = {
        logger.info(s"[TrustManager.getAcceptedIssuers]")
        return null
      }
    }

    val secureRandom: SecureRandom = null
    //val sslcontext: SSLContext = SSLContext.getDefault() 
    val sslcontext: SSLContext = SSLContext.getInstance("TLSv1")
    sslcontext.init(keyManagers, Array(trustManager), secureRandom)
    val sslsf: SSLConnectionSocketFactory = new SSLConnectionSocketFactory(sslcontext,
          NoopHostnameVerifier.INSTANCE) { //SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER)
      @Override
      override def createLayeredSocket(
            socket: Socket,
            target: String,
            port: Int,
            context: HttpContext): Socket = {
        logger.info(s"[createLayeredSocket] context.hash: ${context.hashCode}")
        val layeredSocket: SSLSocket = super.createLayeredSocket(socket, target, port, context)
                                                          .asInstanceOf[javax.net.ssl.SSLSocket]
        val sslsession: SSLSession = layeredSocket.getSession()
        val peerCertChain: Array[X509Certificate] = sslsession.getPeerCertificateChain()
        // Attach peer cert chain to the context
        context.setAttribute(PEER_CERT_CHAIN, peerCertChain)
        layeredSocket
      }
    }

    // Register socket factories for http and https 
    //val r: Registry[ConnectionSocketFactory] = 
    //       RegistryBuilder.create[ConnectionSocketFactory]()
    //                      .register("http", plainsf).register("https", sslsf).build()
    val r: Registry[ConnectionSocketFactory] = 
         RegistryBuilder.create[ConnectionSocketFactory]()
                        .register("http", sslsf).register("https", sslsf).build()

    val cm = new PoolingHttpClientConnectionManager(r)
    cm.setMaxTotal(200)
    cm.setDefaultMaxPerRoute(20)
    val apacheHttpClient: CloseableHttpClient = HttpClients.custom().setConnectionManager(cm).build()
    //val apacheHttpClient:CloseableHttpClient=HttpClients.custom().setSSLSocketFactory(sslsf).build()
    apacheHttpClient
  }

  /** Post to remote safesets over TLS/SSL */
  def postCert(certaddr: CertAddr, content: String, authNServer: Boolean): String = {
    logger.info(s"Post ${certaddr}:\n  ${content}")
    val t = Try {
      val hpost = new HttpPost(certaddr.getUrl())
      //val e = new StringEntity(content, StandardCharsets.UTF_8)
      val e = new StringEntity(content)
      e.setContentType("text/plain")
      hpost.setEntity(e)
      val clientContext: HttpClientContext = HttpClientContext.create()
      //val conn0: ManagedHttpClientConnection = clientContext.getConnection(
      //                                                         classOf[ManagedHttpClientConnection])
      //logger.info(s"conn0: ${conn0}")  
      //// "conn0.isOpen(): ${conn0.isOpen} conn.isSecure(): ${conn.isSecure()}"
      //logger.info(s"clientContext.hashCode   ${clientContext.hashCode}")

      val response: CloseableHttpResponse =  apachehttpclient.execute(hpost, clientContext)

      authenticateStoreIfRequired(certaddr, clientContext, authNServer)

      val rentity: HttpEntity = response.getEntity
      if(rentity != null) {
        println(EntityUtils.toString(rentity))
      }
      EntityUtils.consume(rentity)
    }
    t match {
      case Success(s) =>
        println("done")
        //checkPosted(certaddr, content)
      case Failure(e) => throw UnSafeException(s"post on cert ${certaddr} failed ${e.printStackTrace}")
    }
    certaddr.toString
  }

  /** Fetch from safesets over TLS/SSL
   * @param certadd certificate address
   * @param mult boolean indicating whether to fetch multiple versions (for Riak-based store)
   * @param authNServer boolean whether to authenticate the store server via TLS
   */
  
  def fetchCert(certaddr: CertAddr, authNServer: Boolean, mult: Boolean = false): Option[String] = {
    logger.info(s"Fetch ${certaddr}")
    val t = Try {
      val hget = new HttpGet(certaddr.getUrl())
      if(mult) {
        hget.addHeader(RIAK_MULT_HEADER_KEY, RIAK_MULT_HEADER_KEY)
      }
      val clientContext: HttpClientContext = HttpClientContext.create()
      val response: CloseableHttpResponse =  apachehttpclient.execute(hget, clientContext)

      authenticateStoreIfRequired(certaddr, clientContext, authNServer)

      val status: Int = response.getStatusLine().getStatusCode()
      var content: String = "" 
      if(status >= 200 && status <=300) {
        val rentity: HttpEntity = response.getEntity
        if(rentity != null) {
          content = EntityUtils.toString(rentity)
          logger.info(content)
        }
        EntityUtils.consume(rentity)
      }
      content
    }
    t match {
      case Success(s) =>
        if(s.isEmpty) {
          None
        } else {
          if(isMultipleSiblings(s)) { // fetch all simblings in a jumbo string
            fetchCert(certaddr, authNServer, true)            
          }
          else { 
           logger.info("done")
           Some(s)
          }
        }
      case Failure(e) => 
        throw UnSafeException(s"Fetch cert ${certaddr} failed ${e.printStackTrace}")
    }
  }

  /** Delete from safesets over TLS/SSL */
  def deleteCert(certaddr: CertAddr, authNServer: Boolean): String = {
    logger.info(s"Delete ${certaddr}")
    val t = Try {
      val hdel = new HttpDelete(certaddr.getUrl())
      val clientContext: HttpClientContext = HttpClientContext.create()
      val response: CloseableHttpResponse =  apachehttpclient.execute(hdel, clientContext)

      authenticateStoreIfRequired(certaddr, clientContext, authNServer)

      val status: Int = response.getStatusLine().getStatusCode()
      if(status >= 200 && status <=300) {
        val rentity: HttpEntity = response.getEntity
        if(rentity != null) {
          logger.info(EntityUtils.toString(rentity))
        }
        EntityUtils.consume(rentity)
      } else {
        throw UnSafeException(s"Delete cert ${certaddr} failed (code: ${status})")
      }
    }
    t match {
      case Success(s) =>
        logger.info("done")
        certaddr.toString
      case Failure(e) => 
        throw UnSafeException(s"Delete cert ${certaddr} failed ${e.printStackTrace}")
    }
  }

  /**
   * Authenticate a store based on a request's target address
   * and its http context after execution. Note that only the
   * first request triggers the SSL handshake and results in 
   * a meaningful peer cert chain contained in the context. 
   * Any authentication failure leads to an exeception.
   */
  def authenticateStoreIfRequired(certaddr: CertAddr, clientContext: HttpClientContext, 
      authNRequired: Boolean): Unit = {
    if(!authNRequired) return  // No authN needed; return
    val peerCertChain: Array[X509Certificate] = clientContext.getAttribute(PEER_CERT_CHAIN)
                                                          .asInstanceOf[Array[X509Certificate]]
    logger.info(s"peer cert chain: ${peerCertChain}")

    val peerId = if(peerCertChain != null) {
      val c = peerCertChain(0) // Get the first cert from the chain
      Identity.encode(Identity.hash(c.getPublicKey.getEncoded))
    } else { null }
    logger.info(s"Peer Id from http client context: ${peerId}")

    // Another way to track peer cert chain, but it only works for open connection
    // Caveat: Connection got from the context can be closed when server proxy is used
    //val conn: ManagedHttpClientConnection = clientContext.getConnection(
    //                                                        classOf[ManagedHttpClientConnection])
    //logger.info(s"conn: ${conn}  conn.isOpen: ${conn.isOpen}  conn.isSecure: ${conn.isSecure}")
    //val t: Object = clientContext.getUserToken()
    //logger.info(s"user token: ${t}")    // t.getClass: ${t.getClass}")
    //val r: RouteInfo = clientContext.getHttpRoute()
    //logger.info(s"route: ${r}")
    //val cp: CredentialsProvider = clientContext.getCredentialsProvider()
    //logger.info(s"CredentialsProvider: ${cp}")
    //val targetAuthState: AuthState = clientContext.getTargetAuthState()
    //logger.info(s"targetAuthState: ${targetAuthState}")
    //val targetHost: HttpHost = clientContext.getTargetHost()
    //logger.info(s"targetHost: ${targetHost}")
    //val isRequestSent: Boolean = clientContext.isRequestSent()
    //logger.info(s"isRequestSent: ${isRequestSent}")
    //val req: HttpRequest = clientContext.getRequest()
    //logger.info(s"request: ${req}")
    //if(conn.isOpen() && conn.isInstanceOf[ManagedHttpClientConnection]) {
    //  val sslsession: SSLSession =conn.asInstanceOf[ManagedHttpClientConnection].getSSLSession()
    //  val peerCertChain: Array[X509Certificate] = sslsession.getPeerCertificateChain()
    //  logger.info(s"[SafeSetsService postRemoteSSL] peerCertChain: ${peerCertChain}")
    //}

    verifyIdentity(certaddr.getStoreAddr, peerId)
  }

  /**
   * verifyIdentity checks if the serverAddr matches 
   * the peerId harvested from SSL session. If the
   * verification fails, it throws an exception.
   */
  def verifyIdentity(serverAddr: String, peerId: String) {
    val serverAuthNTable = ssclient.serverAuthNTable
    val authNS: Option[AuthNStatus] = serverAuthNTable.get(serverAddr) 
    assert(authNS.isDefined, "Requested store address ${serverAddr} must be in the serverAuthNTable")
    val s: AuthNStatus = authNS.get
    logger.info(s"authN status: ${s}")
    if(peerId == null) {
      if(s.isAuthenticated()) { // pass 
      } else {
        throw UnSafeException(s"Subsequent requests to store ${serverAddr}; but peer Id hasn't been successfully authenticated ")
      }
    } else {
      if(peerId == s.getStoreID) { // pass && this should be the first request in the SSL session
        // Multiple connections can be established to the same server, but to different hashToken
        // In that case, we'll get a non-empty peerId from the request context
        // As long as peerId is equal to s.getStoreID, it's good
        //assert(!s.isAuthenticated(), s"First request to store ${serverAddr}   Peer Id ${peerId} must haven't been authenticated")
        if(!s.isAuthenticated()) {
          logger.info(s"new connection established to ${serverAddr}")
          s.setAuthenticated() // set this store authenticated
        }
      } else {
        throw UnSafeException(s"Authentication of store ${serverAddr} with Id ${peerId} failed. Expected server Id: ${s.getStoreID}")
      }
    }
  }

  def postCert(certaddr: CertAddr, content: String): String = {
    postCert(certaddr, content, ssl)
  }

  def fetchCert(certaddr: CertAddr): Option[String] = {
    fetchCert(certaddr, ssl)
  }

  def deleteCert(certaddr: CertAddr): String = {
    deleteCert(certaddr, ssl)
  }

  /** 
   * Convert a set to datalog statements 
   */
  def convertToSet(s: String): String = {
    s
  }
}

object ApacheStorageClient {
  def apply(sslOn: Boolean, c: SafeSetsClient): ApacheStorageClient = {
    new ApacheStorageClient(sslOn, c)
  }
  def apply(c: SafeSetsClient): ApacheStorageClient = {
    new ApacheStorageClient(true, c) // this storage client deals with ssl by default
  }
}
