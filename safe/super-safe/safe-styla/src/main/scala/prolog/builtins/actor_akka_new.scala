package prolog.builtins
import prolog.terms._
import prolog.fluents._
import prolog.interp.Prog
import prolog.acts.AkkaLogicActor

final class actor_akka_new()
  extends FunBuiltin("actor_akka_new", 2) {

  override def exec(p: Prog) = {
    val files = getArg(0)
    val db = Prog.make_db(files, p)
    val aName = files.asInstanceOf[Cons].getHead.toString
    val q = new AkkaLogicActor(aName, db, null)
    putArg(1, q, p)
  }

  override def safeCopy() = {
    new actor_akka_new()
  }


}
