package safe.server

object ServiceMessageProtocol {
  //sealed trait ServiceMessage extends safesets.SafeSetsMessageProtocol.SafeSetsMessage
  trait ServiceMessage
  case class QueryGuard(methodName: String, args: IndexedSeq[String], headers: Map[String, Option[String]]) extends ServiceMessage
  case object GetDefGuards extends ServiceMessage
  case class DefGuardsAvailable(guards: Map[String, Seq[String]]) extends ServiceMessage
}
