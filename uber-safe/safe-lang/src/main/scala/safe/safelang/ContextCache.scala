package safe.safelang


import com.google.common.cache._
import org.joda.time.DateTime
import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable.{LinkedHashSet => OrderedSet, ListBuffer}
import scala.collection.mutable.Queue

import scala.util.{Try, Failure, Success}

import setcache.SetCache
import safe.safelog.{UnSafeException, Subcontext}
import safe.safelog._
import safe.safelang.{Token => Index}

/**
  * ContextCache wraps a Guava cache
  */
class ContextCache(setcache: SetCache) extends LazyLogging { 
  //val logger = Logger(classOf[ContextCache])
  val maxSubcontextSize = Config.config.maxSubcontextSize

  val contextLoader = new CacheLoader[Index, Subcontext] {
    def load(token: Index): Subcontext = { 
      //renderSubcontext(token) 
      renderSubcontextTwoSteps(token) 
    }
  } 

  //val spec = "maximumSize=20000,initialCapacity=20000,concurrencyLevel=10"
  val spec = "maximumSize=1000000,initialCapacity=1000000,concurrencyLevel=10"
  val cache: LoadingCache[Index, Subcontext] = CacheBuilder.from(spec)
    .build[Index, Subcontext](contextLoader)

  def renderSubcontext(token: Index): Subcontext = {
    val subcnt = Subcontext(token.name)
    val processedSetTokens = OrderedSet[Index]()
    val tokensToProcess = Queue[Index](token) 
    var count = 0
    val s = System.nanoTime
    while(!tokensToProcess.isEmpty) {    
      val _token = tokensToProcess.front
      setcache.get(_token) match {
        case Some(slogset: SlogSet) =>
          if(slogset.expired()) {
            println(s"slogset expired: $slogset")
            setcache.refresh(_token)              // Invalidate the set and get a fresh one next time
          } else if(validateSpeakers(slogset)) { 
            addSlogset(subcnt, slogset, _token) 
            processedSetTokens += _token
            val additionalTokens: Seq[Index] = slogset.links.map(Index(_))
              .filter(!processedSetTokens.contains(_)).filter(!tokensToProcess.contains(_)) 
            //println(s"[ContextCache renderSubcontext] additionalTokens: ${additionalTokens}   processedSetTokens: ${processedSetTokens}")
            tokensToProcess ++= additionalTokens   // Enqueue the additional tokens
            count += 1
            tokensToProcess.dequeue
          } 
          else {
            println(s"[Slang ContextCache] speakers validation failed on slogset ${slogset}")
          }
        case None =>
          println(s"[Slang ContextCache] skip set because it's not found: ${_token}")
          //throw UnSafeException(s"Subcontext rendering failed because of no set being found on token: ${_token}")
      }        
      if(count >= maxSubcontextSize) {
        logger.error(s"Context too large: token ${token} failed to validate because it links >=${count} sets")
        throw UnSafeException(s"Context too large: token ${token} failed to validate because it links >=${count} sets")
      } 
    }
    slangPerfCollector.addContextSize(count, token.name) // collect the context size
    val cntBuildingTime = (System.nanoTime - s) / 1000
    slangPerfCollector.addContextBuildingTime(cntBuildingTime.toString, token.name) // collect the context building time
    subcnt 
  }

  /**
   * @Developer API
   * Building a subcontext in two steps: 
   * - Fetch closure: fetching, verifying, and parsing sets 
   * - Render the context: checking sets 
   */
  def renderSubcontextTwoSteps(token: Index): Subcontext = {
    val processedSetTokens = OrderedSet[Index]()
    val tokensToProcess = Queue[Index](token) 
    val closure = ListBuffer[Tuple2[SlogSet, Index]]()
    var count = 0
    val s = System.nanoTime
      
    // Step 1: fetch closure
    while(!tokensToProcess.isEmpty) {    
      val _token = tokensToProcess.front
      setcache.get(_token) match {
        case Some(slogset: SlogSet) =>
          val t = (slogset, _token)
          closure += t
          processedSetTokens += _token
          val additionalTokens: Seq[Index] = slogset.links.map(Index(_))
            .filter(!processedSetTokens.contains(_)).filter(!tokensToProcess.contains(_)) 
          tokensToProcess ++= additionalTokens   // Enqueue the additional tokens
          count += 1
        case None => 
          //println(s"[ContextCache] Skip dangling token (${_token}) at context (${token}) rendering")
          //throw UnSafeException(s"Subcontext rendering failed on token: ${_token}")
      }
      tokensToProcess.dequeue
      if(count >= maxSubcontextSize) {
        logger.error(s"Context too large: token ${token} failed to validate because it links >=${count} sets")
        throw UnSafeException(s"Context too large: token ${token} failed to validate because it links >=${count} sets")
      } 
    }
    slangPerfCollector.addContextSize(count, token.name) // collect the context size
    val closureFetchTime = (System.nanoTime -s) / 1000
    slangPerfCollector.addClosureFetchTime(closureFetchTime.toString, token.name) // collect the closure-fetch time
     
    // Step 2: make a context
    val renders = System.nanoTime
    val subcnt = Subcontext(token.name)
    for( (slogset, _token) <- closure ) {
      if(!slogset.expired() && validateSpeakers(slogset)) {
        addSlogset(subcnt, slogset, _token)
      } else {
        println(s"[Slang ContextCache] speakers validation failed on slogset ${slogset}")
      }
    } 
    val cntRenderTime = (System.nanoTime - renders) / 1000
    slangPerfCollector.addContextRenderTime(cntRenderTime.toString, token.name) // collect the context-render time
    val cntBuildingTime = (System.nanoTime - s) / 1000
    slangPerfCollector.addContextBuildingTime(cntBuildingTime.toString, token.name) // collect the context-building time

    subcnt 
  }

 
  /**
   * Add a slogset into a Subcontext
   * This could be a method of Subcontext. We put it here 
   */
  def addSlogset(subcnt: Subcontext, slogset: SlogSet, settoken: Index): Unit= {
    slogset.synchronized {  // sync on slogset
      subcnt.addStatements(slogset.statements)
      subcnt.addQueries(slogset.queries)
      if(slogset.freshUntil.isDefined) {  // bump freshUntil
        subcnt.updateFreshUntil(slogset.freshUntil.get)
      }
    }
    subcnt.addSetToken(settoken.name) // update set token to the subcontext
    if(!slogset.isIDSet()) {
      slogset.addContainingContextToken(subcnt.id.name) // update containing context token to the slog set
    }
  }

  /**
   * Validate the speakers of the statements in a slogset
   * 
   */
  def validateSpeakers(slogset: SlogSet): Boolean = {
    if(slogset.issuer.isDefined && slogset.subject.isDefined && slogset.speaksForToken.isDefined) { // check speaksFor
      val methodName = "validateSpeaksFor"
      val args = Seq(slogset.issuer.get, slogset.subject.get, slogset.speaksForToken.get)
      val query = Query(Seq(Structure(methodName, args.map{
        case x: String  => Constant(StrLit(x), StrLit("nil"), StrLit("StrLit"), Encoding.AttrBase64)
        case a @ _ => throw UnSafeException(s"Invalid speaksFor check argument: ${a}")
      })))

      logger.info(s"[validateSpeakers] query: $query   \n slogset: \n ${slogset}")
      val res = SafelangManager.instance().solveSlangQuery(query).flatten.mkString("; ")
      println(s"speaksFor validation res: ${res}")
      //val strres = res.mkString("; ")
      //val desc = methodName + "___" + args.mkString("___") + "___" + requestedEnv("Principal") + "___" +  requestedEnv("Subject") + "___" + requestedEnv("BearerRef")
      val queryResultPattern = """\{(.*)\}\s*$""".r
      val valid: Boolean = res match {
        case queryResultPattern(result) => true
        case _ => false
      }
      println(s"speaksFor validity: ${valid} \n${res}")
      valid
    }
    else if (slogset.validatedSpeaker || slogset.checkMatchingSpeaker) { // check matching speaker
      true
    } else { // other
      false 
    } 
  }

  def invalidate(token: Index): Unit = {
    cache.invalidate(token)
  }

  def get(token: Index): Option[Subcontext] = {
    // logger.info(s"[safelang/ContextCache.get()] get ${token}")
    // scala.io.StdIn.readLine()
    Try(cache.get(token)) match {
      case Success(s) => 
        Some(s)
      case Failure(e) => 
        println(s"Context rendering failed on token ${token} : ${e.printStackTrace}") 
        None
    }
  }
 
  def put(token: Index, subcontext: Subcontext): Unit = {
    Try(cache.put(token, subcontext)) match {
      case Success(s) =>
      case Failure(e) => 
        println(s"Context put failed on token ${token} : ${e.getMessage}")
    }   
  }

  /** 
   * Refresh the slogsets of the subcontext that is indexed by the 
   * token. We cannot just simply refresh all the existing sets in 
   * the subcontext, because the subcontext may have been updated
   * with a newly added set and that we have a stale copy of
   * that set in the cache 
   */
  def refreshContainedSets(token: Index): Unit = {
    val processedSetTokens = OrderedSet[Index]()
    val tokensToProcess = Queue[Index](token)
    while(!tokensToProcess.isEmpty) {
      val _token = tokensToProcess.dequeue
      val refreshedset = setcache.refresh(_token) 
      processedSetTokens += _token
      refreshedset match { // If it contains links, refresh those links
        case Some(slogset: SlogSet) =>
          val additionalTokens: Seq[Index] = slogset.links.map(Index(_))
            .filter(!processedSetTokens.contains(_)).filter(!tokensToProcess.contains(_))
          tokensToProcess ++= additionalTokens   // Enqueue the additional tokens
        case _ =>
      }
    }
  }
 
  def refresh(token: Index): Option[Subcontext] = {  // Refresh sync
    val cached: Option[Subcontext] = get(token) 
    if(cached.isDefined) {
      val cnt = cached.get 
      if(cnt.refreshableUntil.isDefined) { // Check if the subcontext is refreshable now
        val now = new DateTime()
        val refreshEpoch = cnt.refreshableUntil.get
        val millisToWait: Long = refreshEpoch.getMillis() - now.getMillis() 
        if(millisToWait > 0) {
          logger.info("[" + Console.RED + "Wait on context refresh" + Console.RESET + s"]: ${token}")
          Thread.sleep(millisToWait) // Block until the subcontext becomes refreshable 
        }
      }
      //val slogsetTokens = cnt.slogsetTokens
      //slogsetTokens.foreach{ case t: String => setcache.refresh(Token(t)) } // Refresh the slogsets of this subcontext
      refreshContainedSets(token) // Refresh contained slogsets
      cache.invalidate(token)
    } 
    val fresh: Option[Subcontext] = get(token)  
    if(fresh.isDefined) {  // Set refreshableUntil for the fresh subcontext
      val now = new DateTime()
      val refreshableUntil: DateTime = now.plus(Config.config.minContextRefreshTime)
      val freshcnt: Subcontext = fresh.get 
      freshcnt.setRefreshableUntil(refreshableUntil)
    }
    fresh
  }
  
  def getValidContext(token: Index): Option[Subcontext] = {
    var cnt: Option[Subcontext] = get(token)
    if(cnt.isDefined && cnt.get.expired()) {
      cache.invalidate(token)
      cnt = get(token)
    }
    cnt
  }

}
