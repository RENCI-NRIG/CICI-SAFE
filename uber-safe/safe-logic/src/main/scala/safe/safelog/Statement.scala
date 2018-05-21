package safe.safelog
import safe.safelog.AnnotationTags._

/** A statement is a sequence of terms. */
abstract class Statement {
  val terms: Seq[Term]

  def bind(f: StrLit => Term): Statement = this

  def isFact: Boolean = false

  // useful at slang level
  def bind(args: Map[StrLit, Term]): Statement = {
    var _statement = this
    args.foreach {
      case (varName, varValue) =>
        _statement = _statement.bind{v => if(v.name == varName.name) varValue else Variable(v)}
    }
    _statement
  }

  def toStringWithSays(): String = toString()
  def toStringCompact(speaker: String): String  = toString()

  lazy val arity: Int = terms.length // TODO: TMP due to serialization exception

  /** Index for efficient search
   *
   * This can be pushed to Statement level, but precomputing here is useful
   * for indexing query terms; otherwise the index should be built on-demand 
   * during inference
   */
  def primaryIndex(): StrLit = terms.head.primaryIndex() // index on principal functor and arity
  //def primaryIndex(): StrLit = StrLit(terms.head.id.name + terms.head.arity) // index on principal functor and arity
  //def primaryIndex(): StrLit = StrLit(terms.head.id.name + terms.head.arity) // index on principal functor and arity
  //def primaryIndex(): StrLit = if(terms.head.primaryIndex == StrLit("_Nil")) terms.head.secondaryIndex else terms.head.primaryIndex
  //def secondaryIndex(): StrLit = ""     // index on first argument
  //def ternaryIndex(): StrLit   = ""     // index on second argument
  //val (secondaryIndex: StrLit, ternaryIndex: StrLit) = (terms.head.secondaryIndex, terms.head.ternaryIndex)  
  //def secondaryIndex(): StrLit = terms.head.secondaryIndex
  def secondaryIndex(): StrLit = terms.head match {
    case s: Structure => s.secondaryIndex()
    case _ => terms.head.primaryIndex()
  }
}

case class Assertion(terms: Seq[Term]) extends Statement {
  override def toString() = Statement.toString(terms, ".")
  override def toStringWithSays() = Statement.toStringWithSays(terms, ".")
  override def toStringCompact(speaker: String)  = Statement.toStringCompact(speaker, terms, ".")
  override def bind(f: StrLit => Term): Statement = this.copy(terms.map(_.bind(f)))
  override def isFact(): Boolean = if(terms.length == 1) true else false
}
case class Result(terms: Seq[Term]) extends Statement {
  override def toString() = Statement.toString(terms, "")
  override def toStringWithSays() = Statement.toStringWithSays(terms, "")
  override def toStringCompact(speaker: String)  = Statement.toStringCompact(speaker, terms, "")
  override def bind(f: StrLit => Term): Statement = this.copy(terms.map(_.bind(f)))
}
case class Retraction(terms: Seq[Term]) extends Statement {
  override def toString() = Statement.toString(terms, "~")
  override def toStringWithSays() = Statement.toStringWithSays(terms, "~")
  override def toStringCompact(speaker: String)  = Statement.toStringCompact(speaker, terms, "~")
  override def bind(f: StrLit => Term): Statement = this.copy(terms.map(_.bind(f)))
}
case class AnnotatedQuery(terms: Seq[Term], queryTag: Int = ALLOW) extends Statement {
  //println("AnnotatedQuery: " + terms + "       queryTag: " + queryTag)
  //val query: Query = Query(terms)
  override def toString() = Statement.toString(terms, "? =@= "+tagToString(queryTag))
  override def toStringWithSays() = Statement.toStringWithSays(terms, "? =@= "+tagToString(queryTag))
  override def toStringCompact(speaker: String)  = Statement.toStringCompact(speaker, terms, "? =@= "+tagToString(queryTag))
  override def bind(f: StrLit => Term): Statement = this.copy(terms.map(_.bind(f)))
}
case class Query(terms: Seq[Term]) extends Statement {
  override def toString() = Statement.toString(terms, "?")
  override def toStringWithSays() = Statement.toStringWithSays(terms, "?")
  override def toStringCompact(speaker: String)  = Statement.toStringCompact(speaker, terms, "?")
  override def bind(f: StrLit => Term): Statement = this.copy(terms.map(_.bind(f)))
}
case class QueryAll(terms: Seq[Term]) extends Statement {
  override def toString() = Statement.toString(terms, "??")
  override def toStringWithSays() = Statement.toStringWithSays(terms, "??")
  override def toStringCompact(speaker: String)  = Statement.toStringCompact(speaker, terms, "??")
  override def bind(f: StrLit => Term): Statement = this.copy(terms.map(_.bind(f)))
}

object Statement {
  import java.nio.ByteBuffer
  import Term.{read, write}
  import scala.collection.mutable.ListBuffer

  //=============Function for pretty printing==================//
  def toString(terms: Seq[Term], endsWith: String): String = Term.normalizeTerms(terms) match {
    case head +: Nil => head + endsWith
    case head +: tail => head + " :- " + tail.mkString(", ") + "."
    case _ => ""
  }

  def toStringWithSays(terms: Seq[Term], endsWith: String): String = Term.normalizeTerms(terms) match {
    case head +: Nil  => head.toStringWithSays() + endsWith
    case head +: tail => head.toStringWithSays() + " :- " + tail.map(x => x.toStringWithSays()).mkString(", ") + "."
    case _ => ""
  }

  def toStringCompact(speaker: String, terms: Seq[Term], endsWith: String): String = Term.normalizeTerms(terms) match {
    case head +: Nil  => head.toStringCompact(speaker) + endsWith
    case head +: tail => 
      head.toStringCompact(speaker) + " :- " + tail.map(x => x.toStringCompact(speaker)).mkString(", ") + "."
    case _ => ""
  }

  private[safelog] def writeStatement(statement: Statement, byteBuffer: ByteBuffer): Unit = statement match {
    case Assertion(terms: Seq[Term]) =>
      byteBuffer.put(SAssertion)
      terms.foreach {term => write(term, byteBuffer)}
      byteBuffer.put(SNil)
    case Result(terms: Seq[Term]) =>
      byteBuffer.put(SResult)
      terms.foreach {term => write(term, byteBuffer)}
      byteBuffer.put(SNil)
    case Retraction(terms: Seq[Term]) =>
      byteBuffer.put(SRetraction)
      terms.foreach {term => write(term, byteBuffer)}
      byteBuffer.put(SNil)
    case Query(terms: Seq[Term]) =>
      byteBuffer.put(SQuery)
      terms.foreach {term => write(term, byteBuffer)}
      byteBuffer.put(SNil)
    case QueryAll(terms: Seq[Term]) =>
      byteBuffer.put(SQueryAll)
      terms.foreach {term => write(term, byteBuffer)}
      byteBuffer.put(SNil)
    case _ => throw new UnSafeException("Unknown typed passed")
  }

  @annotation.tailrec
  private def recurse(byteBuffer: ByteBuffer, terms: ListBuffer[Term] = ListBuffer()): ListBuffer[Term] = {
    read(byteBuffer) match {
      case Constant(StrLit("nil"), _, _, _) => terms
      case term => 
        terms += term
        recurse(byteBuffer, terms)
    }
  }
  
  private[safelog] def readStatement(byteBuffer: ByteBuffer): Statement = byteBuffer.get() match {
    case SNil         => Assertion(Seq(Constant(StrLit("Nil"))))
    case SAssertion   => Assertion(recurse(byteBuffer))
    case SResult      => Result(recurse(byteBuffer))
    case SRetraction  => Retraction(recurse(byteBuffer))
    case SQuery       => Query(recurse(byteBuffer))
    case SQueryAll    => QueryAll(recurse(byteBuffer))
    case x            => throw new UnSafeException(s"Unknown typed passed: $x")
  }
}

object StatementHelper {
  /**
   * Get an attribute from a statement at a specified position
   * @param stmt Option[Statement] 
   * @param pos  position of the target attribute in the term list of the stmt
   * @return     value of the attribute
   */
  def getAttribute(stmt: Option[Statement], pos: Int): Option[String] = {
    val attr: Option[String] = stmt match {
      case Some(s: Statement) => s.terms.head match {
        case Structure(pred, terms, _, _, _) =>
          if(pos < terms.length) {
            Some(terms(pos).id.name)
          } else {
            None
          }
        case _ => None
      }
      case _ => None
    }
    attr
  }
}
