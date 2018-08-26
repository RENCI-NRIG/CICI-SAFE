package safe.safelang

import scala.collection.mutable.{Map => MutableMap}

import scala.concurrent.Future
import scala.util.{Success, Failure}
import java.util.concurrent.atomic.AtomicInteger

import safe.safelog.{Index, MutableCache, Statement, ParserException}
import safe.safelog._

import com.typesafe.scalalogging.LazyLogging
import com.google.common.net.InetAddresses

/**
 * SlangService wraps up the internal working of Safelang
 * and exposes well-defined APIs to the outside world as
 * a standard library does. Developers who would like to
 * use Safe as a library can start with SlangService. 
 *
 * Methods invoke and invokeAsync can be used to kick off
 * logic inference.
 */

class DefCallableDesc(val methodName: String, val args: Seq[String], val envs: Map[String, Option[String]])

class GuardTable(val allGuards: MutableMap[String, Seq[String]], guardType: MutableMap[String, Int]) {
  def addGuards(gset: Map[String, Tuple2[Int, Seq[String]]]): GuardTable = {
    for((guard, gprops) <- gset) {
      val gtype: Int = gprops._1
      val args: Seq[String] = gprops._2
      if(allGuards.contains(guard) && allGuards(guard).size == args.size) { // Duplicated guard
        safe.safelog.SafelogException.printLabel('warn)
        println(s"""Duplicated guard: ${guard}(${args.mkString(",")}); Ignored it""")
      } else {
        allGuards.put(guard, args)
        guardType.put(guard, gtype)
      }
    } 
    this
  } 

  def hasGuard(gname: String): Boolean = allGuards.contains(gname)

  def getGuardParameters(gname: String): Option[Seq[String]] = {
    if(hasGuard(gname)) {
      Some(allGuards(gname)) 
    } else {
      None
    }
  }

  def getGuardType(gname: String): Option[Int] = {
    if(hasGuard(gname)) {
      Some(guardType(gname)) 
    } else {
      None
    }
  }

} 

object GuardTable {
  def apply(allGuards: MutableMap[String, Seq[String]], guardType: MutableMap[String, Int]) = new GuardTable(allGuards, guardType)
}

class SlangService(keypairDir: String, slangFile: String, fileArgs: Option[String]) extends LazyLogging {
  import scala.concurrent.ExecutionContext.Implicits.global

  val slangManager: SafelangManager = SafelangManager.instance(keypairDir)
  val numServedReqs = new AtomicInteger(0)

  val slang = slangManager.createSafelang()
  val guardTable = GuardTable(MutableMap[String, Seq[String]](), MutableMap[String, Int]())
  val guards: Map[String, Tuple2[Int, Seq[String]]] = slang.compileAndGetGuards(slangFile, fileArgs)
  guardTable.addGuards(guards)

  def sanityCheck(defx: DefCallableDesc): Boolean = {
    val gargs = guardTable.getGuardParameters(defx.methodName)
    if(gargs.isDefined)  {
      if(gargs.get.size == defx.args.size) {
        return true
      }   
    }
    false  // signature doesn't match
  }
 
  private def executeHandler(methodName: String, args: Seq[String], requestedEnv: Map[String, Option[String]]): String = {  
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
    logger.info(s"[SlangService runSlangGuard] query: $query \n requestedEnv: $requestedEnv")
    val res = slangManager.solveSlangQuery(query, requestedEnv, guardTable.getGuardType(methodName)).flatten
    val strres = res.mkString("; ")
    val desc = methodName + "___" + args.mkString("___") + "___" + requestedEnv("Principal") + "___" +  requestedEnv("Subject") + "___" + requestedEnv("BearerRef")

    val t = (System.nanoTime() - s)/1000
    slangPerfCollector.addRequestLatency(t, desc)
    strres
  }

  def increaseCounter(): Unit = {
    val nr = numServedReqs.incrementAndGet()
    if(nr % 100 == 0) { // get cpu utilization
      slangPerfCollector.addCpuLoad(nr.toString)
    }
    if(nr % 1000 == 0) {
      slangPerfCollector.persist(s"slang-perf-part-${nr/1000}", allRecords=false)
    }
  }

  def invoke(defx: DefCallableDesc): String = {
    // Sanity check before call
    if(sanityCheck(defx)) return ""
    val r = executeHandler(defx.methodName, defx.args, defx.envs)
    increaseCounter() 
    r
  }

  def invokeAsync(defx: DefCallableDesc): Unit = {
    if(sanityCheck(defx)) return
    val resultFuture = Future {
      executeHandler(defx.methodName, defx.args, defx.envs)
    }

    resultFuture.onComplete {
      case Success(res) =>
        increaseCounter()
        res
      case Failure(res) =>
        s"Query failed with msg: ${res}"
    }
  }

}

object SlangService {
  def apply(kd: String, slangSourceFile: String, fileArgs: Option[String]=None) = new SlangService(kd, slangSourceFile, fileArgs)
}
