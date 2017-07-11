package prolog.builtins
import BuiltinHelpers._
import prolog.terms._
import prolog.io.IO
import prolog.interp.Prog

final class singleComponent() extends FunBuiltin("singleComponent", 1) {
  override def exec(p: Prog) = {
    val arg0 = getArg(0)
    assert(arg0.isInstanceOf[Const], s"Argument of singleComponent must be a constant: ${arg0}")
    val components = getNameComponents(arg0.asInstanceOf[Const].sym)
    if(components.length >= 2) 0
    else 1
  }
  override def safeCopy() = {
    new singleComponent()
  }

}
