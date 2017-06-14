package prolog.terms

import com.typesafe.scalalogging.LazyLogging

abstract class Nonvar extends Term with LazyLogging {
  def name: String

  override def bind_to(that: Term, trail: Trail) = {
    val res = getClass.eq(that.getClass)
    if(res == false) {
      logger.info(s"[Nonvar bind_to] Cannot bind: this=${this}; this.getClass=${getClass}; that=${that}; that.getClass=${that.getClass}")
    } 
    res
  }

  override def unify(other: Term, trail: Trail): Boolean = {
    val that = other.ref
    if (bind_to(that, trail)) true
    else that.bind_to(this, trail)
  }

  override def toString = name
  override def hashCode: Int = name.hashCode
}
