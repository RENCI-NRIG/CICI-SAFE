package prolog.builtins
import prolog.terms._
import prolog.fluents._
import prolog.io._
import prolog.interp.Prog

/**
 * Builtin "spec" for being compatible with slog
 */
final class spec() extends FunBuiltin("spec", 1) {
  override def exec(p: Prog) = {
    1
  }
  override def safeCopy() = {
    new spec()
  }

 
}
