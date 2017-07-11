package prolog.builtins
import BuiltinHelpers._
import prolog.terms._
import prolog.io.IO
import prolog.interp.Prog

final class splitHead() extends FunBuiltin("splitHead", 3) {
  override def exec(p: Prog) = {
    val arg0 = getArg(0)
    assert(arg0.isInstanceOf[Const], s"First argument of splitHead must be a constant: ${arg0}")
    val components = getNameComponents(arg0.asInstanceOf[Const].sym)
    if(components.length < 2) {  // not splittable
      0
    } else {
      val head = components.head
      val tail = components.tail.mkString("/")
      if( putArg(1, new Const(head), p)>0 && putArg(2, new Const(tail), p)>0 ) 1 else 0   
    }
  }
  override def safeCopy() = {
    new splitHead()
  }

}
