package prolog.builtins

object BuiltinHelpers {
  def getNameComponents(name: String, delimiter: String = "/"): Seq[String] = {
    val components: Seq[String] = name.split(delimiter)
    components
  }
}
