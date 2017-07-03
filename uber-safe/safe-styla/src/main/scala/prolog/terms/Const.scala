package prolog.terms

import com.typesafe.scalalogging.LazyLogging

class Const(val sym: String) extends Nonvar with LazyLogging {
  override def name = sym
  def len: Int = 0

  override def bind_to(that: Term, trail: Trail) = {
    val res = super.bind_to(that, trail) &&
      sym == that.asInstanceOf[Const].sym
    if(res == false && super.bind_to(that, trail)) {
      logger.info(s"Cannot bind: sym=${sym}  that.sym=${that.asInstanceOf[Const].sym}")
    }
    res
  }
}

object Const {
  final val no = new Const("no")
  final val yes = new Const("yes")
  final val nil = new Const("[]")
  final val cmd = new Const("$cmd")
  final def the(X: Term): Const =
    if (X.eq(null)) Const.no
    else {
      val the = new Fun("the", Array[Term](X))
      the.copy.asInstanceOf[Const]
    }
}

