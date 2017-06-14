package safe.programming

import safe.safelang.{Safelang, slangPerfCollector}
import safe.safelog.UnSafeException

import scala.util.Random
import scala.collection.mutable.{ListBuffer, Map => MutableMap, Set => MutableSet}
import scala.collection.mutable.{LinkedHashSet => OrderedSet}
import scala.collection.Set
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.LinkedBlockingQueue
import com.typesafe.scalalogging.LazyLogging

/** trait for operations in SAFE authorization */
trait SafeOperation extends BenchCommons with StateManagementHelper with LazyLogging {

  type ChainInfo = Tuple2[Int, String]
 
  /** State machine signature of an operation */
  type OperationFSMSignature = Tuple3[SlangCallDescription, Seq[Any], ChainInfo]
  
  val operators = Seq[String]()

  val opcountMap = Map[String, Int]()

  var allPrincipals = ListBuffer[PrincipalStub]()  

  val replayMan = new ReplayManager()  // Record and replay manager

  def getOpCount(calldesc: SlangCallDescription): Int = {
    val opname = calldesc.getOperationName
    if(opcountMap.contains(opname)) {
      opcountMap(opname)
    } else {
      throw UnSafeException("Unrecognized opname: ${opname} ${calldesc}")
    }
  } 

  /** Update uops with new generated items */
  def updateOperandPools(uops: Seq[AnyRef], items: Seq[Any]): Unit = {
    assert(uops.length == items.length, 
           s"uops and items must be of the same length: ${uops.length}  ${items.length}")
    for(i <- 0 to uops.length-1) {
      val pool = uops(i)
      assert(items(i).isInstanceOf[Seq[Any]], s"Seq is expected: ${items(i)}")
      val t = items(i).asInstanceOf[Seq[Any]] 
      t.length match {
        case 1 => addEntry(pool.asInstanceOf[OrderedSet[PrincipalStub]], t(0).asInstanceOf[PrincipalStub])
        case 3 => addEntry(pool.asInstanceOf[MutableMap[String, MutableMap[PrincipalStub, Int]]], 
                           t(0).asInstanceOf[String], t(1).asInstanceOf[PrincipalStub], t(2).asInstanceOf[Int])
        case _ => throw UnSafeException(s"Unexpected item to update: ${t}")
      } 
    }
  }

  /**
   * Perform an operation with randomly picked stateful operands
   * operate on stateful operands
   * @param sl     safelang inference engine 
   * @param op     operator name
   *
   * Three steps of each operation:
   *   - use the pre-processing function to generate a slang call desc and operand updates
   *   - invoke local slang according to the call desc
   *   - update operands if any 
   */
  def doOperation(sl: Safelang, op: String, torecord: Boolean = false): Boolean = {
    if(!opTable.contains(op)) {
      println(s"Unrecognized op: ${op} ${opTable}")
      return false
    }
    //println(s"do operation: ${op}")
    val opDesc = opTable(op)
    val rops = opDesc._1  // rops: ready operand pools 
    val uops = opDesc._2  // uops: operand pools to update 
    val preprocessing = opDesc._3 // preprocessing function
    val r = preprocessing(op, rops)
    //println(s"r: ${r}")
    if(r == null) return false
    val (callDesc, toupdate, chain) = r
    //println(s"callDesc: ${callDesc};  toupdate: ${toupdate}; chain: ${chain}")
    callDesc.invokeLocal(sl)  // Execute slang call
    if(torecord) {
      replayMan.recordSlangCall(callDesc)  // record 
    }
    updateOperandPools(uops, toupdate) // update operands
    val (delcount, info) = chain 
    slangPerfCollector.addDelegation(delcount, info) // log chain info of this op
    true
  } 

  /**
   * Table of operations
   */
  val opTable = Map[ String, Tuple3[ Seq[AnyRef], Seq[AnyRef], (String, Seq[AnyRef]) => OperationFSMSignature] ]()

  var opCDF: Seq[Double] = Seq[Double]()  //computeCDF(Seq(0, 0, 0, 0, 0, 0,
                                          //               1, 2, 1, 2, 0, 0, 0, 0, 0, 0)) // get the CDF of ops

  /**
   * Given a share distribution of ops, compute the CDF for roulette
   * Example op shares:   
   * val opShares = Seq(1, 2, 1, 2, 0, 0, 0, 0, 0, 0)
   */
  def computeCDF(opShares: Seq[Int]): Seq[Double] = {
    if(operators == null) println("operators is null")
    if(opShares == null)  println("opShares is null")
    assert(operators.length == opShares.length,
           s"operators and opShares must be of the same length: ${operators}   ${opShares}")
    val totalShare = opShares.sum.toDouble
    val normalizedShares = opShares.map(i => i.toDouble/totalShare)
    val _cdf = ListBuffer[Double]()
    var t = 0.0
    for(prob <- normalizedShares) {
      t = t + prob
      _cdf += t
    }
    logger.info(s"ops: ${operators}")
    logger.info(s"opshares: ${opShares}")
    logger.info(s"cdf: ${_cdf.toSeq}")
    _cdf.toSeq
  }

  def setOpCDF(opShares: Seq[Int]): Unit = {
    opCDF = computeCDF(opShares)
  }

  /** Pick up a random op using roulette */
  def getRandomOp(): String = {
    val p = Random.nextDouble()
    var i = 0
    while(p >= opCDF(i)) {
      i += 1
    }
    //logger.info(s"p=${p}   i=${i}    op=${opDist(i)}")
    operators(i)
  }
}
