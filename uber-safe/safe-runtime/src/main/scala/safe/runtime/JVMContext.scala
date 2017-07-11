package safe.runtime

object JVMContext {
  val interpreters: Map[Lang, JVMInterpreter] = Config.config.jvmCompilerPath match {
    case "memory" => Map(
        "scala" -> new ScalaInterpreter(None)
      , "java"  -> new JavaInterpreter(None)
    )
    case  path    => Map(
        "scala" -> new ScalaInterpreter(Some(new java.io.File(path)))
      , "java"  -> new JavaInterpreter(Some(new java.io.File(path)))
    )
  }           
}
