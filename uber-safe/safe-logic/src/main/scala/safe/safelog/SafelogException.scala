package safe.safelog

import scala.util.control.NoStackTrace

sealed abstract class SafelogException(message: String) extends RuntimeException(message)

case class UnSafeException(message: String) extends SafelogException(message) with NoStackTrace
case class ParserException(message: String) extends SafelogException(message) with NoStackTrace
case class InfiniteLoopException(message: String) extends SafelogException(message) with NoStackTrace
case class NumericException(message: String) extends SafelogException(message) with NoStackTrace
case class NotImplementedException(message: String) extends SafelogException(message) with NoStackTrace
case class UnSupportedOperatorException(message: String) extends SafelogException(message) with NoStackTrace

object SafelogException {
  /* A helper function to pretty print the label info */
  def printLabel(resultType: Symbol) = resultType match {
    case 'success => println("[" + Console.GREEN + "satisfied" +  Console.RESET + "] ")
    case 'failure => println("[" + Console.RED + "unsatisfied" +  Console.RESET + "] ")
    case 'info => print("[" + Console.YELLOW + "info" + Console.RESET + "] ")
    case 'more => print("[" + Console.BLUE + "more?" + Console.RESET + "] ")
    case 'warn => print("[" + Console.RED+ "warn" + Console.RESET + "] ")
  }
}
