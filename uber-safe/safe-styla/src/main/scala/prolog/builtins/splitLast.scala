package prolog.builtins
import BuiltinHelpers._
import prolog.terms._
import prolog.io.IO
import prolog.interp.Prog

final class splitLast() extends FunBuiltin("splitLast", 3) {
  override def exec(p: Prog) = {
    val arg0 = getArg(0)
    assert(arg0.isInstanceOf[Const], s"First argument of splitLast must be a constant: ${arg0}")
    val components = getNameComponents(arg0.asInstanceOf[Const].sym)
    if(components.length < 2) { // not splittable
      0
    } else {
      val init = components.init.mkString("/")
      val last = components.last
      if( putArg(1, new Const(init), p)>0 && putArg(2, new Const(last), p)>0 ) 1 else 0   
    }
  }
  override def safeCopy() = {
    new splitLast()
  }

}
