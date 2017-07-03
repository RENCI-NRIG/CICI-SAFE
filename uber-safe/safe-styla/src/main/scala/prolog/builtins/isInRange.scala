package prolog.builtins
import prolog.terms._
import prolog.fluents._
import prolog.io._
import prolog.interp.Prog
import org.apache.commons.net.util.SubnetUtils
import com.google.common.net.InetAddresses

final class isInRange() extends FunBuiltin("isInRange", 2) {

  override def exec(p: Prog) = {
    //println(s"[bultins isInRange] exec...")
    val operand0 = getArg(0)
    val operand1 = getArg(1)
    if(!operand0.isInstanceOf[Const] || !operand1.isInstanceOf[Const]) {
      throw new RuntimeException(s"unbound isInRange operands: ${operand0}  ${operand1}")
    }
    val str0 = operand0.asInstanceOf[Const].sym  
    val str1 = operand1.asInstanceOf[Const].sym
    val irange0 = str0.substring(5, str0.length-1) // Internal format: ipv4"XXX.XXX.XXX..."
    val irange1 = str1.substring(5, str1.length-1)
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
