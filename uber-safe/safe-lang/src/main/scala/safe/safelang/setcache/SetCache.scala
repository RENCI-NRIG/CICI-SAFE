package safe.safelang
package setcache

import safe.safelog.{UnSafeException, SetId}
import safe.safelang.model.Subject
import safe.safelang.{Token => Index}
import safe.safelang.safesets._

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import safe.cache.SafeTable

//import grizzled.slf4j.Logger

import scala.concurrent.{Await, Future, blocking}
import scala.concurrent.duration._

import scala.util.{Try, Success, Failure}
import scala.util.Random.nextInt

import com.google.common.cache._
import com.typesafe.scalalogging.LazyLogging

/**
 * This is a simple cache with async link crawling/prefetch using futures.
 * The cache is a Google Guava cache.
 *
 * We just plug in two application-specific methods:
 * - A CacheLoader.load method that knows enough about the objects to know what to prefetch.
 * - A fetch method that knows how to fetch the cacheable objects from a store and validate them.
 *
 * For SAFE, the objects are certs, and the cert fetch method must fetch another cert (an identity
 * cert) to validate a logic cert.  So we throw in an idcache for those too.
 */

class SetCache (localSetTable: SafeTable[SetId, SlogSet], safeSetsClient: SafeSetsClient) extends CRDTAPI with LazyLogging { 
  //val logger = Logger(classOf[SetCache])
  //val database = ci.initializeDB

  /*
    Make a Guava cache.  There's some mess here to get type conformity between
    Java and Scala, because Guava needs Java types: Any is not an Object.
   */
  //val spec = "maximumSize=20000,initialCapacity=20000,concurrencyLevel=10"
  val spec = "maximumSize=1000000,initialCapacity=1000000,concurrencyLevel=10"
  val loader = new SafePrefetchingCacheLoader()
  val cache: LoadingCache[Index, SlogSet] = CacheBuilder.from(spec)
    .build[Index, SlogSet](loader)

  val idloader = new CacheLoader[Index, IDSet] {
    def load(token: Index): IDSet = { fetchValidIDSet(token) }
  }
  val idcache: LoadingCache[Index, IDSet] = CacheBuilder.from(spec)
    .build[Index, IDSet](idloader)

  def refresh(token: Index): Option[SlogSet] = {
    logger.info(s"refreshing token: ${token.name}")
    loader.prebuffer.invalidate(token) // Invalidate the entry in the prebuffer as well
    cache.invalidate(token)
    get(token)
  }

  def get(token: Index): Option[SlogSet] = {
    logger.info(s"[safelang/setcache/SetCache.get()] get ${token}")
    Try(cache.get(token)) match {
      case Success(s) =>
        Some(s)
      case Failure(e) =>
        //logger.info(s"Fetch via set cache failed on token $token : " + e.printStackTrace);
        logger.info(s"Fetch via set cache failed on token $token : " + e.getMessage);
        //if(getLocal(token).isDefined) { // for debugging
        //  println("[" + Console.RED + s"Local set ${token} exists, but get failed (stack trace below)" +  Console.RESET + "] ")
        //  e.printStackTrace
        //}
        None
    }
  }

  /** @DeveloperAPI  for debugging */
  def getWithError(token: Index): Option[SlogSet] = {
    //Some(cache.get(token))
    Try(cache.get(token)) match {
      case Success(s) =>
        Some(s)
      case Failure(e) =>
        logger.info(s"Fetch via set cache failed on token $token : " + e.getMessage);
        println(s"[SetCache getWithError]" + Console.RED + "stack trace:" + Console.RESET)
        e.printStackTrace
        None
    }
  }
 
  /** Get slogset from local set table */ 
  def getLocal(token: Index): Option[SlogSet] = {
    localSetTable.get(new SetId(token.name))
  }

  /** Insert a new local SlogSet in the cache (the local set tabel) */
  def putLocal(token: Index, slogset: SlogSet): Unit = {
    localSetTable.put(new SetId(token.name), slogset) 
  }

  /** A wrapper of putIfAbsent for local set table */
  def putLocalIfAbsent(token: Index, slogset: SlogSet): Option[SlogSet] = {
    localSetTable.putIfAbsent(new SetId(token.name), slogset)
    //val local = localSetTable.putIfAbsent(new SetId(token.name), slogset)
    //val v = localSetTable.get(new SetId(token.name))  // for debugging
    //require(v.isDefined, s"set must be in the local set table. v=${v}  slogset=${slogset}")
    //local
  }
  
  /** 
   * Inform cache that this local set has been posted. 
   * The corresponding localSetTable entry can be removed
   */ 
  def postedLocal(token: Index): Unit = {
    localSetTable.remove(new SetId(token.name))
  }

  def fetchValidIDSet(token: Index): IDSet = {
    val certs: Seq[SlogSet] = fetch(token)
    require(certs.size > 0, s"fetch loader should throw out exception first: ${certs}")
    val slogset = certs(0)  // Just use the first Idset 
    val idset = if(slogset.isIDSet) {
      slogset.toIDSet
    } else {
      throw UnSafeException(s"ID set expected, but slog set found: ${slogset}")
    }
    /**
     * Check if the ID set is valid:
     * 1) Valid token hash
     * 2) Valid cert signature (Self-signed)
     * 3) It's not expired
     */
    val subject = idset.principalSubject
    if(token.name != subject.id.toString) {
      throw new UnSafeException(s"Invalid id set token: ${token.name} != ${subject.id.toString}")
    } 
    // We don't need this check because idset can be local, which doens't need verification
    //if(!idset.issuer.isDefined) {
    //  throw new UnSafeException(s"Certificate doesn't include an issuer: ${idset}")
    //}
    val validatedIdset: Boolean = idset.synchronized { idset.validated } 
    if(idset.issuer.isDefined && !validatedIdset) { // idset.issuer.isDefined
      if(!idset.verifyAndSetValidated(subject)) {
        throw new UnSafeException(s"Certificate signature doesn't check out: ${idset}")
        // Mark the ID set invalid
      }
    }
    if(idset.expired()) {
      throw new UnSafeException(s"ID set expired: ${idset}")
    }

    logger.info(s"fetched valid id set: " + token)
    idset
  }

  /** "fetch" loader */
  def fetch(token: Index): Seq[SlogSet] = {
    val cert: Option[SlogSet] = getLocal(token) //localSetTable.get(new SetId(token.name))  // Check localSetTable first 
    if(cert.isDefined) {
      Seq(cert.get)
    } else { // Fetch from safesets
      val fetched: Seq[SlogSet] = safeSetsClient.fetchSlogSet(token.name)
      if(fetched.size == 0) {
        throw UnSafeException(s"Set ${token} unavailable neither in local set table nor in safesets  getLocal=${getLocal(token)}")
      } else {
        fetched
      }
    }
  }

  /*
    This "validate" function is a stub and is not specific to this example.  Just spin a bit.
    It is called on each object after fetch.
    For SAFE, we need to get the full public key from a referenced ID cert before validating,
    so use the IDcache here.
  */
  def validate(slogset: SlogSet): Boolean = {
    //println(s"validating set: ${slogset}")
    //if(!slogset.issuer.isDefined) { // Local slogset
    val validated: Boolean = slogset.synchronized { slogset.validated }
    if(!slogset.issuer.isDefined || validated || !slogset.signature.isDefined || slogset.signature.get.isEmpty ) { // local slogset or verified set
      slogset.setValidated()
      true //throw UnSafeException(s"Validating a local slogset? Issuer is undefined: ${slogset.issuer}")
    } else { 
      val idtoken = Index(slogset.issuer.get) 
      val idset: IDSet = idcache.get(idtoken)
      val speakerSubject: Subject = idset.getPrincipalSubject()
      val res = slogset.verifyAndSetValidated(speakerSubject)
      if(res && idset.freshUntil.isDefined) { // If signature checks out, set issuerFreshUntil 
        slogset.setIssuerFreshUntil(idset.freshUntil.get)
      }
      res
    }
  }

  def getLocalSetTable(): SafeTable[SetId, SlogSet] = {
    localSetTable
  }


  /*
    This CacheLoader maintains a bounded L2 prefetch buffer.
    Loads are satisfied from the prefetch buffer, and then from the store on a miss.
    It uses futures to prefetch links, and pauses prefetching when the buffer is full.
    In SAFE, one purpose here is to limit the amount of work we do to prefetch a pointer
    structure that we don't really trust.  The module above must check the structures,
    and we don't want to prefetch too much of it before the check.  We keep the prefetch
    buffer and its pauseThreshold small, but not too small...
    FIFO replacement would be just fine here, and it might help to keep
    track of more state (e.g., to limit depth), but for now Guava is expedient.
   */
  class SafePrefetchingCacheLoader extends CacheLoader[Index, SlogSet] {
    var totalFetches = 0
    var entryCount: AtomicInteger = new AtomicInteger(0)
    var cacheMisses: AtomicInteger = new AtomicInteger(0)
    var pauseThreshold = 100
    var pauses = 0
    //val spec = "maximumSize=1000,initialCapacity=1000,concurrencyLevel=10"
    val spec = "maximumSize=10000,initialCapacity=10000,concurrencyLevel=10"

    val certloader = new CacheLoader[Index, SlogSet] {
      def load(token: Index): SlogSet = { loadCert(token) }
    }

    val listener = new RemovalListener[Index, SlogSet] {
      def onRemoval(notification: RemovalNotification[Index, SlogSet]): Unit = {
        entryCount.decrementAndGet()
        if (notification.getCause() == RemovalCause.EXPIRED) {
          logger.info("Key " + notification.getKey() + " has expired from prefetching cache");
        }
      }
    }
    val prebuffer: LoadingCache[Index, SlogSet] = CacheBuilder.from(spec)
      .expireAfterAccess(3000, TimeUnit.MILLISECONDS)
      .removalListener(listener)
      .build[Index, SlogSet](certloader)

    /*
       The load function is called for miss handling from the L1 cache.
     */
    def load(token: Index): SlogSet = {
      val result = prebuffer.get(token)
      //prebuffer.invalidate(token)  // The prebuffer might have to fetch it again when needed.
      result
    }

    import scala.concurrent.ExecutionContext.Implicits.global

    private def prefetch(token: Index) = {
      val e: Int = entryCount.get()
      if (e > pauseThreshold)
        pauses += 1        // unsynchronized so we may miss some
      else {
        val f = Future {
          blocking {
            logger.info(s"prebuffer $token")
            prebuffer.get(token)
          }
        }
      }
    }

    /*
       Application-specific loadCert function fetches from store and triggers prefetch.
       It must be in the prefetching loader so that it can invoke prefetch methods.
    */
    private def loadCert(token: Index): SlogSet = { //  throws AnyException
      val certs: Seq[SlogSet] = fetch(token)
      require(certs.size > 0, s"fetch loader should throw out exception first: ${certs}")
      for(c <- certs) {
        if(!validate(c)) {
          println(s"Invalid slogset: ${c}")
          throw UnSafeException(s"Invalid slogset: ${c}")
        }
      }
      val c: SlogSet = merge(certs)  // CRDT merge
      totalFetches += 1     // unsynchronized so we may miss some
      val e = entryCount.incrementAndGet()
      logger.info(s"$totalFetches fetched (prebuffer $e): " + token) //+ c)
      
      // Prefetch the links recursively.
      logger.info(s"links of cert (${token.name}): ${c.links}")
      for (i <- c.links) {
        if(c.issuer.isDefined && i != c.issuer.get)  // Only prefetch remote non-identity sets
          prefetch(Index(i))
      }
     
      val m = cacheMisses.incrementAndGet()
      if(m % 100 == 0) {
        slangPerfCollector.addSetcacheMiss(m, token.name) // Record the ordinal of the miss and the token
      }
      c
    }
  } /* member class SafePrefetchingCacheLoader */
}
