package safe.runtime

trait JVMInterpreter {
  val name: String = "scala" // name of the interpreted language

  def compile(code: String, args: Array[String]): Class[_] // compile code
  def eval(code: String, args: Array[String]): Any
  def eval(compiledCode: Class[_], args: Array[String]): Any
}


