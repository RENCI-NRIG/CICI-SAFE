package prolog.builtins
import prolog.terms._
import prolog.io.IO
import prolog.interp.Prog

/*
 * A non-numerical version of "is"
 */
final class is_nonnum() extends FunBuiltin("is_nonnum", 2) {
  def eval(expr: Term): Const = {
    expr match {
      case t: Fun =>
        {
          val l = t.args.length
          val rs = new Array[Const](l)
          var ok = true
          for (i <- 0 until l) {
            rs(i) = eval(t.getArg(i))
            if (rs(i).eq(null)) ok = false
          }
          if (!ok) null
          else if (l == 1) {
            val x: String = rs(0).sym
            t.sym match {
              case "rootPrincipal" => 
                //val array = x.split(":", -1)
                //assert(array.length>=2, s"scid must contain exactly two elements: ${array} (term: ${expr})")
                //new Const(array(0))
                val delimiterIndex = x.lastIndexOf(":")
                assert(delimiterIndex != -1, s"Invalid token: ${x}")
                new Const(x.substring(0, delimiterIndex))
              case "ipFromNetworkID" =>
                val array = x.split(":", -1)
                assert(array.length>=2, s"scid must contain exactly two elements: ${array} (term: ${expr})")
                new Const(s"""ipv4"${array(0)}"""")
              case "portFromNetworkID" =>
                val array = x.split(":", -1)
                assert(array.length>=2, s"scid must contain exactly two elements: ${array} (term: ${expr})")
                new Const(s"""port"${array(1)}"""")
            }
          }  else null
        }
      case c: Const => c
      case _: Term => null
    }
  }

  override def exec(p: Prog) = {
    val r: Const = eval(getArg(1))
    //println(s"[is_nonnum]  r: ${r}")
    if (null == r) IO.errmes("bad non-arithmetic operation", this, p)
    else putArg(0, r, p)
  }

  override def safeCopy() = {
    new is_nonnum()
  }


}
