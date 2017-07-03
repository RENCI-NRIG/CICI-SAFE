package prolog.terms

class Query(sym: String, var qargs: Array[Term]) extends Fun(sym, qargs) {
  def this(sym: String) = this(sym, null)

  override def safeCopy(): Fun = {
    new Query(sym, null)
  }

}
