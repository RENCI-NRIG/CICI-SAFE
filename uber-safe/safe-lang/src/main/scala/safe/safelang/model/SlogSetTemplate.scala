package safe.safelang
package model

import safe.safelog.{Validity, UnSafeException, StrLit, Index, Structure, Term, Variable, Statement, EnvValue, Constant}
import safe.safelog.StatementHelper
import prolog.terms.{Var => StyVar}
import scala.collection.mutable.{Map => MutableMap}

import org.joda.time.DateTime
import scala.collection.mutable.{LinkedHashSet => OrderedSet, LinkedHashMap} 

case class SlogSetTemplate(
  statements: Map[Index, OrderedSet[Statement]],
  labelInDef: Option[Term]
) {
  import SlogSetHelper._

  def instantiate(bindings: Map[StrLit, Term], envContext: MutableMap[StrLit, EnvValue]): SlogSet = {
    val effectiveBindings: Map[StrLit, Term] = bindings.filter {
      case (varName: StrLit,  value: Variable) => false  // Filter out useless bindings 
      case _ => true
    }
    var bindedStmts: Map[Index, OrderedSet[Statement]] = statements
    var bindedLabelInDef: Option[Term] = labelInDef
    if(effectiveBindings.size > 0) {
      bindedStmts = getBindedStatements(effectiveBindings) 
      if(labelInDef.isDefined) {
        bindedLabelInDef = bindedLabelInDef.map(_.bind(effectiveBindings))
      }
    }
    val spkr: Option[String] = envContext.get(StrLit("Self")) match {
      case Some(s: Constant) => Some(s.id.name)
      case _ => None
    }
    //println(s"[SlogSetTemplate] spkr = ${spkr}   ${envContext.keySet}  ${envContext.values}")
    //println(s"[SlogSetTemplate] bindedStmts = ${bindedStmts}")
    val s = buildSlogSet(bindedStmts, bindedLabelInDef.map{term => term.id.name}, speaker=spkr)
    s.setStatementSpeaker()  // TODO: This might be part of the SlogSet constructor
    s
  }

  def getBindedStatements(bindings: Map[StrLit, Term]): Map[Index, OrderedSet[Statement]] = {
    //println(s"[SlogSetTemplate] ============== binded statement ============ ")
    //println(s"[SlogSetTemplate] bindings=${bindings}")
    statements.keySet.map {
      case idx: Index => 
        val resultStatements: OrderedSet[Statement] = {
          val s = statements.get(idx).getOrElse(OrderedSet.empty).map(stmt => stmt.bind(bindings))
          //println(s"[SlogSetTemplate] ${idx} =>  ${s}")
          s
        }
        idx -> resultStatements
    }.toMap
  } 
}

object SlogSetHelper {
  /**
   * Get an attribute from a statement at a specified position
   * @param stmt Option[Statement] 
   * @param pos  position of the target attribute in the term list of the stmt
   * @return     value of the attribute
   */
  def getAttribute(stmt: Option[Statement], pos: Int): Option[String] = {
    val attr: Option[String] = stmt match {
      case Some(s: StyStmt) => s.getHeadArgument(pos-1) 
      case Some(s: Statement) => StatementHelper.getAttribute(stmt, pos) // Slog statement
      case _ => None
    }
    attr
  }

  /** Given a sequence of statement of a specific predicate, finds the first unique statement */
  @inline
  def getUniqueStatement(stmts: Option[Iterable[Statement]]): Option[Statement] = stmts match {
    case None => None
    case Some(stmtset) => stmtset.size match {
      case 0        => None
      case 1        => Some(stmtset.head)
      case _        => throw UnSafeException(s"Unique ${stmts} expected but multiple found")
    }
  }

  /** 
   * Build a local slogset 
   */
  def buildSlogSet(stmts: Map[Index, OrderedSet[Statement]], labelPredef: Option[String] = None, 
      setData: Option[String] = None, signature: Option[String] = None, 
      speaker: Option[String] = None, validity: Option[Validity] = None): SlogSet = {
    
    var issuer: Option[String] = speaker
    var subject: Option[String] = speaker  // TODO: speaksFor
    var speaksForToken: Option[String] = None
    //if(!speaker.isDefined) {
    //
    // no use of the builtin speaker(., .) at the moment     
    //
    // val speakerStmt: Option[Statement] = getUniqueStatement(stmts.get(StrLit("_speaker"))) // self: speaker(speakerID, ref-to-speaksFor)
    // issuer = getAttribute(speakerStmt, 1) 
    // println(s"[SlogSetTemplate buildSlogSet] speakerStmt=${speakerStmt}    issuer=${issuer}")
    // speaksForToken = getAttribute(speakerStmt, 2)
    //
    // Builtin subject(.,.) is used to specify a speaker that is different from the issuer.
    // It also specifies a slogset reference that links to a proof of the speaksFor eligibility
    // for the issuer.
    //
    // Usage of subject(): self: subject(<subjectId> ,
    val subjectStmt: Option[Statement] = getUniqueStatement(stmts.get(StrLit("_subject"))) // self: subject(subject-id, publicKeyHash)
    println(s"[SlogSetTemplate buildSlogSet] subjectStmt=${subjectStmt}    subject=${subject}")
    subject = getAttribute(subjectStmt, 1) 
    //
    //}
    val freshUntil: Option[DateTime] = if(!validity.isDefined) { 
      val validityStmt: Option[Statement] = getUniqueStatement(stmts.get(StrLit("_validity"))) // self: validity(notBefore, notAfter) 
      println(s"[SlogSetTemplate buildSlogSet] validityStmt=${validityStmt}")
      getAttribute(validityStmt, 2) match {
        case Some(notAfter: String) => 
          Some(Validity.format.parseDateTime(notAfter))
        case _ => None
      }
    } else {
      Some(validity.get.notAfter)
    }
    val speakersFreshUntil: Option[DateTime] = None
    val issuerFreshUntil: Option[DateTime] = None
    var validatedSpeaker: Boolean = true 
    var validated: Boolean = true
    if(signature.isDefined) { // Remote slogset; have to be validated
      validatedSpeaker = false
      validated = false
    }
    val now = new DateTime()
    val resetTime: Option[DateTime] = Some(now.plus(Validity.defaultPeriod))  // 30 days by default
    val queries: Seq[Statement] = stmts.get(StrLit("_query")).getOrElse(Nil).toSeq
     
    val links: Seq[String] = stmts.get(StrLit("_link")).getOrElse(Nil).map { 
      case linkstmt => 
        val _link: Option[String] = getAttribute(Some(linkstmt), 1)  // self: link("str")
        if(_link.isDefined) { _link.get } else { throw UnSafeException(s"link expected, but not found: ${linkstmt}") } 
    }.toSeq

    val labelStmt: Option[Statement] = getUniqueStatement(stmts.get(StrLit("_label"))) // self: label("str") 
    val label: Option[String] =  getAttribute(labelStmt, 1) 
    //println(s"[SlogSetTemplate buildSlogSet] labelStmt=${labelStmt}    label=${label}")
    val slogsetLabel: String = if(label.isDefined) { label.get } 
                               else if(labelPredef.isDefined) { labelPredef.get }
                               else { "" } // No label is defined //{ throw UnSafeException("missing a label") }

    /** Get the effective stmts by excluding meta data */
    val metadataIdx = safe.safelog.Config.config.metadata.map { case id: StrLit => StrLit(s"_${id.name}") } 
    val effectiveStmts = stmts -- (metadataIdx + StrLit("_query"))
   
    //println(s"[SlogSetTemplate buildSlogSet] effectiveStmts = ${effectiveStmts}")
    SlogSet(issuer, subject, freshUntil, speakersFreshUntil, issuerFreshUntil, validatedSpeaker,
        validated, resetTime, queries, effectiveStmts, links, speaksForToken, slogsetLabel, signature, setData)
  }
}
