package safe
package safelog

case class StrLit(val name: String) extends AnyVal

/*
case class StrLit (val name: String) extends Serializable {
  // Converts this symbol to a string.
  //override def toString(): String = "'" + name

  @throws(classOf[java.io.ObjectStreamException])
  //private def readResolve(): Any = StrLit.apply(name)
  override def hashCode = name.hashCode()
  override def equals(other: Any) = this eq other.asInstanceOf[AnyRef]
}

object StrLit {
  //def apply(name: String): StrLit = new StrLit(name)
  //protected def valueFromKey(name: String): StrLit = new StrLit(name)
  //protected def keyFromValue(sym: StrLit): Option[String] = Some(sym.name)
}
*/
