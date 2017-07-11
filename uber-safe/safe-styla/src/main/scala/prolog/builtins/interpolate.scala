package prolog.builtins
import prolog.terms._
import prolog.fluents._
import prolog.io._
import prolog.interp.Prog

final class interpolate() extends FunBuiltin("interpolate", 1) {

  override def exec(p: Prog) = {
    1
  }
 
  def eval(): String = {
    val x = getArg(0)
    assert(x.isInstanceOf[Fun], s"Argument of an interpolate fun must be a fun: ${x}") 
    val args = x.asInstanceOf[Fun].args
    val xfun = x.asInstanceOf[Fun]
    //println(s"[prolog.builtins.interpolate eval] x.sym=${xfun.sym}   x.args=${xfun.args}")
    val sb = new StringBuilder()
    for(comp <- args) {
      //println(s"[prolog.builtins.interpolate eval] comp.ref=${comp.ref}   comp.ref.getClass=${comp.ref.getClass}")
      val v = comp.ref
      if(v.isInstanceOf[Const] || v.isInstanceOf[Cons] || v.isInstanceOf[Real]) {
        //assert(v.isInstanceOf[Const], s"Component of string interpolation is not bound: ${v};  interpolation:${x}")
        //sb.append(v.asInstanceOf[Const].sym)
        sb.append(v)
      }
    }
    sb.toString
  }

  override def hashCode = {
    eval().hashCode
  }

  override def safeCopy() = {
    new interpolate()
  }


}
