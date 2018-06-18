package safe.server
package frontend

import akka.actor.{Actor, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.Await
import scala.concurrent.duration._
import spray.http.StatusCodes
import spray.http.CacheDirectives.`max-age`
import spray.http.HttpHeaders.`Cache-Control`
import spray.http.StatusCodes._
import spray.httpx.SprayJsonSupport._
import spray.routing.HttpService

import spray.json._
import spray.routing._
import spray.routing.authentication._
import spray.http._ // BasicHttpCredentials
import MediaTypes._
import HttpMethods._
import spray.http.HttpHeaders._

import scala.concurrent.Future
import scala.util.{Success, Failure}

import java.util.UUID

import safe.safelang.{Safelang, SafelangManager, SetTerm, slangPerfCollector, GuardTable}
import safe.safelog._
import safe.safelang.SlangCallParams
import safe.safelang.SlangCallResponse
import safe.safelang.SlangCallMessageFormat._

import scala.collection.mutable.{LinkedHashSet => OrderedSet, Map => MutableMap}
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import com.typesafe.scalalogging.LazyLogging
import java.net.{NetworkInterface, InetAddress}
import scala.collection.JavaConversions._
import scala.util.parsing.json._


object RestfulService {
  def props(storeURI: String, role: Option[String], slangFile: String, fileArgs: Option[String], numWorkers: Int, requestTimeout: Timeout, keypairDir: String): Props =
    Props(classOf[RestfulService], storeURI, role, slangFile, fileArgs, numWorkers, requestTimeout, keypairDir: String)
  def name = "RestfulService"
}

class RestfulService(val storeURI: String, val role: Option[String], slangFile: String, fileArgs: Option[String], numWorkers: Int, val requestTimeout: Timeout, val keypairDir: String) extends Actor with RestfulHttpService {

  import scala.concurrent.ExecutionContext.Implicits.global

  def actorRefFactory = context

  inference = slangManager.createSafelang() // Daemon safelang

  guardTable = GuardTable(MutableMap[String, Seq[String]](), MutableMap[String, Int]())
  val guards = inference.compileAndGetGuards(slangFile, fileArgs)
  guardTable.addGuards(guards)

  // compile slang for import guard
  val importGuardSlangPath = safe.safelang.Config.config.importGuardSlangPath
  if(importGuardSlangPath != "") {
    val importGuards = inference.compileAndGetGuards(importGuardSlangPath)
    guardTable.addGuards(importGuards)
  }

  //def receive: Receive = runRoute(route(guardTable.allGuards.toSeq)) orElse importHandler
  def receive: Receive = runRoute(route(guardTable.allGuards.toSeq) ~ importSlangRoute())

  def runImportSlang(args: Seq[String], 
      requestTimeout: Timeout, isSlangSource: Boolean): Route = { reqContext: RequestContext => 

    import scala.language.postfixOps 
    import scala.concurrent.ExecutionContext.Implicits.global

    logger.info(s"""Import slang programs: ${args.mkString("; ")}      isSlangSource: ${isSlangSource}""")
    //println(s"""Import slang programs: ${args.mkString("; ")}""")
    val result = Future {
      for(arg <- args) {
        val slang = slangManager.createSafelang()
        val _guards = if(isSlangSource)
                        slang.compileAndGetGuardsWithSource(arg)
                      else slang.compileAndGetGuards(arg) 
        guardTable.addGuards(_guards)  // update guard table
      }
    } 
    result.onComplete {
      case Success(res) => 
        context become receive
        reqContext.complete(SlangCallResponse(s"Import completed"))
      case Failure(res) =>
        reqContext.complete(SlangCallResponse(s"Import failed: ${res}"))
    }
  }

  /**
   * Run a slang guard to check if an import request
   * could be approved based on the requester's IP 
   * address.
   */
  def approveImportBySourceIP(ip: String): Boolean = {
    import scala.language.postfixOps
    val importGuardFuture = Future {
      val envs = Map("Speaker" -> None, "Subject" -> None, "Object" -> None,
                     "BearerRef" -> None,  "Principal" -> None)
      val guardName = safe.safelang.Config.config.importGuardName
      val (r, d) = runGuard(guardName, Seq(ip))(envs, requestTimeout)
      r
    } 
   
    val res = Await.result(importGuardFuture, 5 seconds)
    val queryResultPattern = """\{(.*)\}\s*$""".r
    res match {
      case queryResultPattern(result) => true
      case _ => false
    }
  }

  def importSlangFromIP(ip: String, args: Seq[String],
       requestTimeout: Timeout, isSlangSource: Boolean): Route = {
    val approved = approveImportBySourceIP(ip)
    if(approved) {
      runImportSlang(args, requestTimeout, isSlangSource)
    } else {
      reqContext: RequestContext =>
        reqContext.complete(SlangCallResponse(s"Import request from an unauthorized IP: ${ip}"))
    } 
  }

  // Dynamic slang program loading
  def importSlangRoute(): Route = {
    path("import")  {
      postWithParameters { (params) =>
        clientIP { (remoteAddress) =>
          val ip = remoteAddress.toString 
          println(s"params.clientIP: ${ip}")
          importSlangFromIP(ip, params.otherValues, requestTimeout, false)
        }
      }
    } ~
    path("importSource") {
      postWithParameters { (params) =>
        //println(s"params: ${params}")
        clientIP { (remoteAddress) =>
          val ip = remoteAddress.toString
          println(s"params.clientIP: ${ip}")
          importSlangFromIP(ip, params.otherValues, requestTimeout, true)
        }
      }
    }
    // Testing
    //path("importSource") {
    //  clientIP { (ip) => 
    //    println(s"params.clientIP: ${ip}")
    //    reqContext: RequestContext =>
    //      reqContext.complete(SlangCallResponse(s"Import completed")) 
    //  }
    //}
  }

  def importHandler: Receive = {
    logger.info(s"Generating import route")
    println(s"Generating import route")
    runRoute(importSlangRoute())
  }
}

trait RestfulHttpService extends Actor with HttpService with DefaultJsonProtocol with LazyLogging {

  import scala.language.postfixOps // for 'q ? in parameter() below
  //implicit def ec = actorRefFactory.dispatcher
  import scala.concurrent.ExecutionContext.Implicits.global

  val storeURI: String
  val role: Option[String]
  val keypairDir: String     // Directory of key pairs of the server principals

  val numInference = 10
  var inference: Safelang = null
  var guardTable: GuardTable = null
  val requestTimeout: Timeout
  val timeout: scala.concurrent.duration.FiniteDuration = FiniteDuration(30, java.util.concurrent.TimeUnit.SECONDS)
 
  val numServedReqs = new AtomicInteger(0)  // For perf collection

  // safelang manager
  val slangManager: SafelangManager = new SafelangManager(keypairDir)

  //The responses should not be cached at the client site; so we explicitly disable them.
  val CacheHeader = (maxAge: Long) => `Cache-Control`(`max-age`(maxAge)) :: Nil

  //private def reqContext = context.system.settings.config
  //val cachedResults = reqContext.getBoolean("safelang.resultCache")
  val cachedResults: Boolean = false

  import safe.cache.SafeTable
  val resultCache: SafeTable[String, Any] = new SafeTable[String, Any](
      1024        // initialCapacity, i.e., 1024 statements in a context
    , 0.99f       // loadFactor; we do not expect to rehash often
    , 16          // concurrencyLevel; not many writers at the same time
  )

  import com.google.common.net.InetAddresses

  def guardHandlerRoute(
    methodName: String,
    postedArgs: String,
    guardArity: Int     // expected arity
  ) (
    requestTimeout: Timeout
  ): Route = { reqContext: RequestContext =>
    //logger.info(s"[guardHanlderRoute] postedArgs: ${postedArgs}")

    val result = Future {
      val s = System.nanoTime()
      val jsonAst = postedArgs.parseJson
      val params = jsonAst.convertTo[SlangCallParams]
      val envs = Map("Speaker" -> params.speaker, "Subject" -> params.subject, "Object" -> params.objectId,
                     "BearerRef" -> params.bearerRef,  "Principal" -> params.principal)
      //println(s"params as JSON object: ${params}")
      logger.info(s"[guardHandlerRoute] envs=${envs}     args=${params.otherValues}")
      var ret = postedArgs
      var desc = ""
      if(params.otherValues.size == guardArity) {
        val (r, d) = runGuard(methodName, params.otherValues)(envs, requestTimeout)
        ret = r
        desc = d
      } else {
        throw UnSafeException(s"Wrong params: ${guardArity} params expected; params.otherValues.size=${params.otherValues.size}")
      }
      val t = (System.nanoTime() - s)/1000
      slangPerfCollector.addRequestLatency(t, desc)
      ret
    }

    result.onComplete {
      case Success(res) =>
        //val responsetime = System.currentTimeMillis - s  // server-side response time of a query
        //slangPerfCollector.addLatency(responsetime, s"${methodName}_${args.mkString("_")}") 
        val nr = numServedReqs.incrementAndGet()
        //if(nr % 100 == 0) { // get cpu utilization
        //  slangPerfCollector.addCpuLoad(nr.toString)
        //  //System.gc
        //}
        if(nr % 1000 == 0) {
          slangPerfCollector.persist(s"slang-perf-part-${nr/1000}", allRecords=false)
        }
        reqContext.complete(SlangCallResponse(s"""${res}"""))
      case Failure(res) =>
        reqContext.complete(SlangCallResponse(s"Query failed with msg: $res"))
    }
  }


  def runGuard(
    methodName: String,
    args: Seq[String]
  )(
    requestedEnv: Map[String, Option[String]] = Map.empty,
    requestTimeout: Timeout): Tuple2[String, String] = {

    val query = Query(Seq(Structure(methodName, args.map{
      case x: String if(x.startsWith("?")) => Variable(x)
      case x: String if(x.startsWith("ipv4")) =>
        val ippart = x.substring(5, x.length-1)
        if(InetAddresses.isInetAddress(ippart)) {
          Structure(StrLit("_ipv4"), Seq(Constant(ippart)), termIndex, StrLit("address"))
        } else {
          Structure(StrLit("_ipv4"), Seq(Constant(ippart)), termIndex, StrLit("block"))
        }
      case x: String                       => Constant(StrLit(x), StrLit("nil"), StrLit("StrLit"), Encoding.AttrBase64) //Constant(StrLit(x), StrLit("nil"), StrLit("StrLit"), Encoding.AttrLiteral) //Constant(x)
      })))
    logger.info(s"[runGuard] query: $query \n requestedEnv: $requestedEnv")
    val res = slangManager.solveSlangQuery(query, requestedEnv, guardTable.getGuardType(methodName)).flatten
    val strres = res.mkString("; ")
    val desc = methodName + "___" + args.mkString("___") + "___" + requestedEnv("Principal") + "___" +  requestedEnv("Subject") + "___" + requestedEnv("BearerRef")

    //// Empty server
    //val strres = "{ " + Assertion(Seq(Structure(methodName, args.map{
    //  case x: String if(x.startsWith("?")) => Variable(x)
    //  case x: String if(x.startsWith("ipv4")) =>
    //    val ippart = x.substring(5, x.length-1)
    //    if(InetAddresses.isInetAddress(ippart)) {
    //      Structure(StrLit("_ipv4"), Seq(Constant(ippart)), termIndex, StrLit("address"))
    //    } else {
    //      Structure(StrLit("_ipv4"), Seq(Constant(ippart)), termIndex, StrLit("block"))
    //    }
    //  case x: String                       => Constant(StrLit(x), StrLit("nil"), StrLit("StrLit"), Encoding.AttrBase64) //Constant(StrLit(x), StrLit("nil"), StrLit("StrLit"), Encoding.AttrLiteral) //Constant(x)
    //}))).toString.init + " }"
    //val desc = methodName
    (strres, desc)
  }


  def runGuardRoute(
    methodName: String,
    args: Seq[String]
  )(
    requestedEnv: Map[String, Option[String]] = Map.empty,
    requestTimeout: Timeout
  ): Route = { reqContext: RequestContext =>
    logger.info(s"[runGuardRoute] methodName: $methodName     args: $args \n requestedEnv: $requestedEnv")

    val result = Future {
      val s = System.nanoTime()
      val query = Query(Seq(Structure(methodName, args.map{
        case x: String if(x.startsWith("?")) => Variable(x)
        case x: String if(x.startsWith("ipv4")) => 
          val ippart = x.substring(5, x.length-1)
          if(InetAddresses.isInetAddress(ippart)) {
            Structure(StrLit("_ipv4"), Seq(Constant(ippart)), termIndex, StrLit("address"))
          } else {
            Structure(StrLit("_ipv4"), Seq(Constant(ippart)), termIndex, StrLit("block"))
          }
        case x: String                       => Constant(StrLit(x), StrLit("nil"), StrLit("StrLit"), Encoding.AttrBase64) //Constant(StrLit(x), StrLit("nil"), StrLit("StrLit"), Encoding.AttrLiteral) //Constant(x)
      }))) 
      logger.info(s"[runGuardRoute future] query: $query \n requestedEnv: $requestedEnv")
      val res = slangManager.solveSlangQuery(query, requestedEnv, guardTable.getGuardType(methodName)).flatten
      val strres = res.mkString("; ")
      val desc = methodName + "___" + args.mkString("___") + "___" + requestedEnv("Principal") + "___" +  requestedEnv("Subject") + "___" + requestedEnv("BearerRef") 


      //// Empty server
      //val strres = "{ " + Assertion(Seq(Structure(methodName, args.map{
      //  case x: String if(x.startsWith("?")) => Variable(x)
      //  case x: String if(x.startsWith("ipv4")) => 
      //    val ippart = x.substring(5, x.length-1)
      //    if(InetAddresses.isInetAddress(ippart)) {
      //      Structure(StrLit("_ipv4"), Seq(Constant(ippart)), termIndex, StrLit("address"))
      //    } else {
      //      Structure(StrLit("_ipv4"), Seq(Constant(ippart)), termIndex, StrLit("block"))
      //    }
      //  case x: String                       => Constant(StrLit(x), StrLit("nil"), StrLit("StrLit"), Encoding.AttrBase64) //Constant(StrLit(x), StrLit("nil"), StrLit("StrLit"), Encoding.AttrLiteral) //Constant(x)
      //}))).toString.init + " }"
      //val desc = methodName


      val t = (System.nanoTime() - s)/1000
      slangPerfCollector.addRequestLatency(t, desc)
      strres
    }

    result.onComplete {
      case Success(res) =>  
        //val responsetime = System.currentTimeMillis - s  // server-side response time of a query
        //slangPerfCollector.addLatency(responsetime, s"${methodName}_${args.mkString("_")}") 
        val nr = numServedReqs.incrementAndGet()
        if(nr % 100 == 0) { // get cpu utilization
          slangPerfCollector.addCpuLoad(nr.toString) 
          //System.gc
        }
        if(nr % 1000 == 0) {
          slangPerfCollector.persist(s"slang-perf-part-${nr/1000}", allRecords=false) 
        }
        reqContext.complete(SlangCallResponse(s"""${res}"""))
      case Failure(res) => 
        reqContext.complete(SlangCallResponse(s"Query failed with msg: $res"))
    }
  }

  def prepareAndExecuteGuard(
      methodName: String
    , inference: Safelang
    , args: String*
  )(
      headers: Map[String, Option[String]] = Map.empty
    , requestTimeout: Timeout
  ): Route = { reqContext: RequestContext =>

    //reqContext.complete("pong\n")

    //perRequest(reqContext, safeService, ServiceMessageProtocol.QueryGuard(methodName, args.toIndexedSeq, headers), requestTimeout.duration)
    //val query = Query(Seq(Structure(methodName, args.toIndexedSeq.map{x: String => Constant(x)})))
    //println(s"compiledSlangProgram: $compiledSlangProgram; query: $query") 
    //val result = inference.solve(compiledSlangProgram, Seq(query), false)
    //reqContext.complete(s"Result is: $result")

    //println("prepareAndExecuteGuard: " + methodName + "    args: " + args)
    val result = Future {
      val query = Query(Seq(Structure(methodName, args.toSeq.map{
	case x: String if(x.startsWith("?")) => Variable(x)
	case x: String                       => Constant(StrLit(x), StrLit("nil"), StrLit("StrLit"), Encoding.AttrBase64) //Constant(StrLit(x), StrLit("nil"), StrLit("StrLit"), Encoding.AttrLiteral) //Constant(x)
      })))
      inference.solveSlang(Seq(query), false)
    }
    result.onComplete {
      case Success(res) => 
        reqContext.complete(s"""${res.mkString("; ")}\n""")
      case Failure(res) => 
        reqContext.complete(s"Query failed with msg: $res")
    }

  }

  private[this] val slogInferenceDirect: Safelog   = Safelog()

  import spray.http._
  import spray.client.pipelining._
  private[this] val pipeline: HttpRequest => Future[HttpResponse] = sendReceive
  private def postSet(id: String, content: String): Future[HttpResponse] = {
    pipeline(Post(s"${storeURI}/$id", content))
  }
  private def fetchSet(id: String): Route = { reqContext: RequestContext =>
    //println(s"URI: ${storeURI}/$id")
    val result: Future[HttpResponse] = pipeline(Get(s"${storeURI}/$id"))
    result.onComplete {
      case Success(res) => 
        reqContext.complete(res)
      case Failure(res) => 
        reqContext.complete(s"Query failed with msg: $res")
    }
  }

  private def computePi(numOfElements: String): Route = { reqContext: RequestContext =>

    val numElements: Int = numOfElements.toInt

    val fResult = Future {
      def calculatePiFor(nrOfElements: Int, start: Int = 1): Double = {
	var acc = 0.0
	for (i <- start until (start + nrOfElements)) {
	  acc += 4.0 * (1 - (i % 2) * 2) / (2 * i + 1)
	}
	acc
      }
      val pi: Double = {
	val piSeries = for {
          i <- 0 until 500
	  piValue = calculatePiFor(numElements, numElements * i)
	} yield (piValue)
        piSeries.sum
      }
      pi
    }
    fResult.onComplete {
      case Success(res) => 
        reqContext.complete(s"""Pi until $numElements is $res""")
      case Failure(res) => 
       reqContext.complete(s"Computing Pi failed with msg: $res")
    }
  }

  private val dataMapSize: Int = 10000000

  private val dataMap: SafeTable[String, Any] = new SafeTable[String, Any](
      dataMapSize  
    , 0.75f        
    , 16           
  )

  val postWithParameters =
    post &
    entity(as[SlangCallParams])

  def simpleRoute(guard: Tuple2[String, Seq[String]]): Route = {

    val methodName = guard._1
    val args       = guard._2

    println(s"MethodName: $methodName")

    path("pi") {
      post{
        args.size match {
          case 0 =>
            complete("ok")
          case 1 =>
            (
              formFields(args(0)) | 
              parameters(args(0))
            ) { arg0 => 
              computePi(arg0)
	    }
          case _ =>
            complete("ok")
        }
      }
    } ~
    path("directSlang") {
      post{
        args.size match {
          case 0 =>
            complete("ok")
          case 1 =>
            (
              formFields(args(0)) | 
              parameters(args(0))
            ) { arg0 => 
              //askQueryInSlang(inference, arg0)
              complete("ok")
	    }
          case _ =>
            complete("ok")
        }
      }
    } ~
    path("directSlog") {
      post{
        args.size match {
          case 0 =>
            //askQueryInSlog(inference)
            complete("ok")
          case 1 =>
            (
              formFields(args(0)) | 
              parameters(args(0))
            ) { arg0 => 
              //askQueryInSlog(inference, arg0)
              complete("ok")
	    }
          case _ =>
            complete("ok")
        }
      }
    } ~
    path(s"${methodName}_withReFetch") {
      post{
        args.size match {
          case 0 =>
            complete("ok")
            //prepareAndExecuteGuardWithReFetch(methodName, inference)
          case 1 =>
            (
              formFields(args(0)) | 
              parameters(args(0))
            ) { arg0 => 
              complete("ok")
              //prepareAndExecuteGuardWithReFetch(methodName, inference, arg0)
	    }
          case _ =>
            complete("ok")
        }
      }
    } ~
    path(Segment) { (id) =>
      get {
        if(role.get == "safesetsService") {
          fetchSet(id)
        } else {
          complete("Segment pong\n")
        }
      }
    } ~
    path(methodName / Segment) { (id) =>
      get {
        if(role.get == "safesetsService") {
	  //println(s"fetch id: $id")
          fetchSet(id)
        } else {
          complete(s"${methodName} Segment pong\n")
        }
      } ~
      post {
        entity(as[String]) { postContent =>
	  if(role.get == "safesetsService") {
	    //println(s"POST id: $id")
	    //println(s"POST content: $postContent")
            prepareAndExecuteGuard(methodName, inference, postContent)(Map.empty, requestTimeout)
	  } else {
	    complete(s"${methodName} Segment asString pong\n")
	  }
        }
      }
    } ~
    //path(methodName) {
    //  postWithParameters { (params) =>
    //    val envs = Map("Speaker" -> params.speaker, "Subject" -> params.subject, "Object" -> params.objectId,
    //                   "BearerRef" -> params.bearerRef,  "Principal" -> params.principal)
    //    //println(s"params as JSON object: ${params}")
    //    //println(s"rawmsg=${rawmsg}  rawmsg.getClass=${rawmsg.getClass}")
    //    logger.info(s"[RestfulService simpleRoute postWithParameters] envs=${envs}     args=${params.otherValues}")
    //    if(params.otherValues.size == args.size) {
    //      runGuardRoute(methodName, params.otherValues)(envs, requestTimeout)
    //    } else { 
    //      throw UnSafeException(s"Wrong # of params; params.otherValues.size=${params.otherValues.size}")
    //    }
    //  }
    //}
//
//    path(methodName) {  // post data as a string
//      post {
//        entity(as[String]) { (postedString) =>
//          //val jsonAst = postedString.parseJson
//          //val params = jsonAst.convertTo[SlangCallParams] 
//          val params = convertToSlangCallParams(postedString)
//          complete(s"called ${methodName}  params: ${postedString}   ok.")
//          //complete(s"ok.")
//        }
//      }
//    }
//
    path(methodName) {  // post data, including request env vars, as a string; push string parsing to the request handler
      post {
        entity(as[String]) { (postedArgs) =>
          guardHandlerRoute(methodName, postedArgs, args.size)(requestTimeout)
        }
      }
    }


  }

  def convertToSlangCallParams(s: String): Option[SlangCallParams] = {
    val json: Option[Any]  = JSON.parseFull(s)
    if(json.isDefined) {
      val m = json.get.asInstanceOf[Map[String, Any]]
      val speaker: Option[String] = m.get("speaker").asInstanceOf[Option[String]]
      val subject: Option[String] = m.get("subject").asInstanceOf[Option[String]] 
      val objectId: Option[String] = m.get("objectId").asInstanceOf[Option[String]]
      val bearerRef: Option[String] = m.get("bearerRef").asInstanceOf[Option[String]]
      val principal: Option[String] = m.get("principal").asInstanceOf[Option[String]]
      val ov: Option[Any] = m.get("otherValues")
      val otherValues: Seq[String] = if(ov.isDefined) ov.get.asInstanceOf[Seq[String]] else Seq[String]()
      Some(SlangCallParams(speaker, subject, objectId, bearerRef, principal, otherValues))
    } else {
      None 
    } 
  }

  def route(guards: Seq[Tuple2[String, Seq[String]]]): Route = guards match {
    case Nil =>
      path("null")  {
        get {
          complete("no defguard provided")
        }
      }
     case head +: tail =>
      simpleRoute(head) ~
      route(tail)
  }
}
