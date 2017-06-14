package safe.safelang

trait CRDT {
  def merge(sets: Seq[SlogSet]): SlogSet 
  def subsume(superset: String, set: String): Boolean 
}
  
trait CRDTAPI extends CRDT { 
  /** Merge multiple sets into one */
  def merge(slogsets: Seq[SlogSet]): SlogSet = {
    require(slogsets.size > 0, s"slogsets size must be larger than 0: ${slogsets.size}")
    if(slogsets.size == 1) { // done
      slogsets(0)
    } else { // merge sibling slogsets
      var mostDurable: SlogSet = slogsets(0)
      for(s <- slogsets) {
        require(s.freshUntil.isDefined, s"freshUntil must be defined: $s")
        if(mostDurable.freshUntil.get.isBefore(s.freshUntil.get)) {
          mostDurable = s
        }
      } 
      val union = mostDurable 
      for(s <- slogsets) {
        if(s != mostDurable) {
          union.mergeSlogset(s) 
        } 
      } 
      union
    }
  }

  /** Check if superset subsumes set */
  def subsume(superset: String, set: String): Boolean = {
    superset.contains(set)
  }
}
