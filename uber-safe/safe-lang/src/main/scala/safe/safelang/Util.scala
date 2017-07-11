package safe.safelang

object Util {

  def readCmdLine(
      args: Seq[String]
    , requiredArgs: Seq[Symbol]
    , optionalArgs: Map[String, Symbol] = Map.empty
    , optionValues: Map[Symbol, String] = Map[Symbol, String]().withDefaultValue("")
  ) : Map[Symbol, String] = {
    
    val res = args match {
      case Nil                                                => optionValues
      case key +: value +: tail if optionalArgs.contains(key) => readCmdLine(tail, requiredArgs, optionalArgs, optionValues ++ Map(optionalArgs(key) -> value))
      case value +: tail if !requiredArgs.isEmpty             => readCmdLine(tail, requiredArgs.tail, optionalArgs, optionValues ++ Map(requiredArgs.head -> value))
      case Seq("--help") | Seq("-h")                          => optionValues ++ Map('help -> "true")
      case other +: tail                                      => sys.error(s"Unknown option [$other] specified")
    }
    res
  }
}
