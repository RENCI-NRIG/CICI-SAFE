package prolog.builtins
import prolog.terms._
import prolog.fluents._
import prolog.io._
import prolog.interp.Prog
import org.apache.commons.net.util.SubnetUtils
import com.google.common.net.InetAddresses
import com.typesafe.scalalogging.LazyLogging

final class isInRange() extends FunBuiltin("isInRange", 2) with LazyLogging {
  /**
   * isInRange() determines if operand0 is in range of operand1,
   * i.e., operand1 contains operand0.
   *     operand0 <: operand1
   */

  override def exec(p: Prog) = {
    logger.info(s"[bultins isInRange] exec...")
    val operand0 = getArg(0)
    val operand1 = getArg(1)
    if(!operand0.isInstanceOf[Const] || !operand1.isInstanceOf[Const]) {
      throw new RuntimeException(s"unbound isInRange operands: ${operand0}  ${operand1}")
    }
    val str0 = operand0.asInstanceOf[Const].sym  
    val str1 = operand1.asInstanceOf[Const].sym  

    /**
     * IPv4 range examples: 
     *            ipv4"152.3.136.0/24", ipv4"152.3.136.26"
     *
     * Port range example:
     *            port"10000, 10995-11000, 30000" 
     */   
  
    val range0 = str0.substring(5, str0.length-1)
    val range1 = str1.substring(5, str1.length-1)
 
    if(str0.startsWith("ipv4")) {
      logger.info(s"[isInRange] ipv4...")
      assert(str1.startsWith("ipv4"), s"Operand types do not match for range containment test ${str0} ${str1}")
      isInIPRange(range0, range1)
    } else if(str0.startsWith("port")) {
      logger.info(s"[isInRange] port...")
      assert(str1.startsWith("port"), s"Operand types do not match for range containment test ${str0} ${str1}")
      isInPortRange(range0, range1)
    } else {
      throw new RuntimeException(s"unknown operands for isInRange: ${operand0}  ${operand1}")
    }
  }

  /**
   * subroutine to handle IP ranges
   */
  def isInIPRange(irange0: String, irange1: String): Int = {
    if(InetAddresses.isInetAddress(irange1)) {
      if(irange0 == irange1) {
        return 1
      } else {
        return 0
      }
    }
    val subnet1 = getSubnetUtils(irange1) 
    val res = if(InetAddresses.isInetAddress(irange0)) {
      subnet1.getInfo.isInRange(irange0)
    } else {
      val subnet0 = getSubnetUtils(irange0)
      subnet1.getInfo.isInRange(subnet0.getInfo.getLowAddress()) &&
        subnet1.getInfo.isInRange(subnet0.getInfo.getHighAddress())
    }
    if(res) 1 else 0
  }

  /**
   * subrouting to handle port ranges
   */
  def isInPortRange(prange0: String, prange1: String): Int = {
    val subrangeSet0: Array[(Int, Int)] = getSortedPortSubrangesFromString(prange0)
    val subrangeSet1: Array[(Int, Int)] = getSortedPortSubrangesFromString(prange1) 
    var cur0: Int = 0
    var cur1: Int = 0
    var res = true 
    var break = false
    while(cur0 < subrangeSet0.length && cur1 < subrangeSet1.length && !break) {
      val subrangecmp = compareSubrangePair(subrangeSet0(cur0), subrangeSet1(cur1))
      if(subrangecmp == 0) { // desired containment
        logger.info(s"${subrangeSet0(cur0)} is in range ${subrangeSet1(cur1)}")
        cur0 = cur0 + 1
      }
      else if(subrangecmp == -1) { // subrangeSet0 is far behind
        logger.info(s"${subrangeSet0(cur0)} is behind ${subrangeSet1(cur1)}")
        cur0 = cur0 + 1
        res = false
        break = true
      } else if(subrangecmp == 1) { // subrangeSet1 is far behind
        logger.info(s"${subrangeSet0(cur0)} is ahead of ${subrangeSet1(cur1)}")
        cur1 = cur1 + 1
        if(cur1 == subrangeSet1.length) {
          res = false
        }
      } else if(subrangecmp == -1000) { // overlapping but no desired containment
        logger.info(s"${subrangeSet0(cur0)} is in overlap with ${subrangeSet1(cur1)}")
        res = false
        break = true
      }
    }  
    if(res == true) 1 else 0
  }

  def getSortedPortSubrangesFromString(str: String): Array[(Int, Int)] = {
    /**
     * Port range example: "10000, 10995-11000, 30000"
     * Subranges are disjoint.
     */
    val subrangeDelimiter = ","
    val boundaryConnector = "-"    

    val subranges: Array[(Int, Int)] = for(subrange <- str.split(subrangeDelimiter)) yield {
      val boundaryPair = subrange.trim.split(boundaryConnector)
      if(boundaryPair.length == 1) { // single-port subrange
        val p: Int = boundaryPair(0).toInt
        (p, p)
      } else if(boundaryPair.length == 2) {
        val p_low: Int = boundaryPair(0).toInt
        val p_high: Int = boundaryPair(1).toInt
        assert(p_low <= p_high, s"Invalid port subrange: port at the low boundary goes frist")
        (p_low, p_high)
      } else {
        throw new RuntimeException(s"Invalid port subrange: ${subrange}")
      }
    }

    subranges.sortWith(_._1 < _._1)
  }

  def compareSubrangePair(subr0: (Int, Int), subr1: (Int, Int)): Int = {
    if(subr0._2 < subr1._1) {
      return -1
    } else if(subr0._1 >= subr1._1 && subr0._2 <= subr1._2) {  // subr0 <: sub1
      return 0 
    } else if(subr0._1 > subr1._2) { 
      return 1
    } else { // overlapping subranges but no desired containment
      return -1000
    }
  }

  def getSubnetUtils(str: String): SubnetUtils = {
    var s: SubnetUtils = null
    try {
      s = new SubnetUtils(str)
      s.setInclusiveHostCount(true)
      s
    } catch {
      case e: Exception => println(e)
    }
    s
  }

  override def safeCopy() = {
    new isInRange()
  }


}
