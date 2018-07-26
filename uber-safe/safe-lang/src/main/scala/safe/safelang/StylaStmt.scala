package safe.safelang

import safe.safelog.{Statement, Term, StrLit, Index, UnSafeException, Constant, Structure}
import safe.safelog.AnnotationTags._  // For queries
import prolog.terms.{Term => StyTerm, Fun => StyFun, FunBuiltin => StyFunBuiltin, Const => StyConstant, Var => StyVar, Query => StyQueryTerm, Copier => StyCopier, RetractionTerm => StyRetractionTerm, Num => StyNum, Real => StyReal, Cons => StyCons}
import prolog.{LogicEngine => StyLogicEngine}
import prolog.builtins.{interpolate => StyInterpolate, true_ => StyTrue,  fail_ => StyFail}
import scala.collection.mutable.{LinkedHashSet => OrderedSet, Map => MutableMap, LinkedHashMap}
import com.typesafe.scalalogging.LazyLogging

/* A wrapper for a Styla equivalent of a logic statement */
class StyStmt(val styterms: List[StyTerm], val vmap: LinkedHashMap[String, StyVar]) extends Statement with LazyLogging {
  val terms = Seq[Term]()  // skeleton
  
  def setStyVar(vname: String, value: StyTerm): Unit = {
    if(vmap.contains(vname)) {
      val styv = vmap(vname)
      styv.set_to(value)
    }
  }

  def canEqual(a: Any) = a.isInstanceOf[StyStmt]
 
  override def equals(that: Any): Boolean = that match {
    case t: StyStmt => this.hashCode == that.hashCode 
    case _ => false
  }

  override def hashCode: Int = { 
    //this.toStringWithSays().hashCode
    var result = 1
    val prime = 31 
    //val varnames = vmap.keySet
    //for(v <- varnames) {
    //  result = result * prime + v.hashCode
    //}
    for(i <- 0 to styterms.length-1) {
      result = result * prime + styterms(i).hashCode
    }
    result
  } 

  override def isFact(): Boolean = if(styterms.length == 1) true else false

  def buildStmtObject(sterms: List[StyTerm], vm: LinkedHashMap[String, StyVar]) = {
    StyStmt(sterms, vm)
  }

  val numPattern = """(-?(\d+)(\.\d+)?)""".r

  def safeCopyStyStmt(): StyStmt = {
    val copier = new StyCopier()
    val newterms = StyTerm.copyList(styterms, copier)
    //println(s"[Safelang StyStmt bind] copier=${copier};  newterms=${newterms}")
    val newvmap = LinkedHashMap[String, StyVar]()
    for( (s, v) <- vmap ) {
      //if(!copier.contains(v)) {
      //  throw UnSafeException(s"Original variable not copied after binding: ${s} -> ${v};   copier=${copier};   newterms=${newterms};  stystmt=${this}")
      //}
      if(copier.contains(v)) {  // Skip irrevelent variables 
        val newv = copier(v)
        newvmap(s) = newv
      }
    } 
    val newstmt = buildStmtObject(newterms, newvmap) 
    //println(s"[Safelang StyStmt bind] newstmt=${newstmt}")
    newstmt
  }

  override def bind(bindings: Map[StrLit, Term]): Statement = {  
    val newstmt = safeCopyStyStmt()

    // Effective bindings
    val ebindings = bindings.filter{ case (variable, value) => newstmt.vmap.contains(variable.name) }

    /**
     * The Styla data structure makes it easily to do binding
     * Ideally, we only need to do binding once for each set
     * This implementation is to be compatible with the legacy
     * slang/slog interface. 
     */
    for( (variable, value) <- ebindings) {
      value match {
        case Constant(v: StrLit, _, _, _) => 
          v.name match {
            case "true" => newstmt.setStyVar(variable.name, new StyTrue())
            case "fail" => newstmt.setStyVar(variable.name, new StyFail()) 
            case s if s(0)=='[' && s.last==']' =>   // list
              val l = s.substring(1, s.length - 1)
              val elements = l.split(",").toList
              val styvalue = prolog.io.TermParser.list2cons(elements.map(s => new StyConstant(s.trim)))
              //println(s"styvalue=${styvalue}  styvalue.getClass=${styvalue.getClass}")
              newstmt.setStyVar(variable.name, styvalue)
            case numPattern(_*) =>  newstmt.setStyVar(variable.name, new StyReal(v.name))
            case _ => newstmt.setStyVar(variable.name, new StyConstant(v.name)) 
          }          
        case iprange @ Structure(StrLit("_ipv4"), _, _, _, _) => 
          newstmt.setStyVar(variable.name, new StyConstant(iprange.toString))
        case _ =>
      }
    }

    //println(s"[Safelang StyStmt bind] after binding: newstmt=${newstmt}")
    newstmt

    // Bind() only doesn't binding
    // Adding a speaker to each statement should go to slogset instantiation
    //
    //val stmtWithSpeaker = if(bindings.contains(StrLit("$Self"))) {  // $Self is the speaker 
    //  if(bindings(StrLit("$Self")).isInstanceOf[Constant]) {
    //    newstmt.addSpeaker(bindings(StrLit("$Self")).asInstanceOf[Constant].id.name) 
    //  } else {
    //    newstmt
    //  }
    //} else {
    //  newstmt
    //}
    //stmtWithSpeaker
  }

  override def addSpeaker(speaker: String): StyStmt = {
    val spkop: String = ":"
    val termsWithSpeaker = styterms.map {
      t: StyTerm => 
        t match {
          case f: StyFun => 
            if(f.sym != spkop) {
              val speakerTerm = new StyConstant(speaker)
              new StyFun(spkop, Array[StyTerm](speakerTerm, t))  
            } else {
              t
            } 
          case _ => t 
        }  
    }
    //println(s"[StylaStmt addSpeaker] newterms=${termsWithSpeaker}")
    //StyStmt(termsWithSpeaker, vmap)
    buildStmtObject(termsWithSpeaker, vmap)
  } 

  def addSpeaker(bindings: Map[StrLit, Term]): StyStmt = {
    if(bindings.contains(StrLit("Self"))) {  // Add speaker if Self is defined
      addSpeaker(bindings(StrLit("Self")).id.name)
    } else {
      this
    }
  }

  override def primaryIndex(): StrLit = {
    val hterm: StyFun = getIndexableHeadTerm()
    val idxOfReserved: Option[String] = indexOfReservedPredicate(hterm)
    val primaryIdx: String = if(idxOfReserved.isDefined) idxOfReserved.get else hterm.primaryIndex()
    StrLit(primaryIdx)
  }

  override def secondaryIndex(): StrLit = { 
    val hterm: StyFun = getIndexableHeadTerm()
    val idxOfReserved: Option[String] = indexOfReservedPredicate(hterm)
    val secondaryIdx: String = if(idxOfReserved.isDefined) idxOfReserved.get else hterm.secondaryIndex()
    StrLit(secondaryIdx)
  }

  def getIndexableHeadTerm(): StyFun = {
    val h = styterms.head
    if(!h.isInstanceOf[StyFun]) {
      throw UnSafeException(s"Statement cannot be indexed: $this")
    } else {
      h.asInstanceOf[StyFun]
    }
  }

  def indexOfReservedPredicate(f: StyFun): Option[String] = {
    val reserved = safe.safelog.Config.config.reserved.keySet.collect {
      case r if r.name == f.predicateName => r.name
    }
    if(reserved.size > 0) {
      assert(reserved.size == 1, 
          s"Matching more than one reserved predicates: headterm=${f}  reserved=${reserved}")
      Some(s"_${reserved.head}") 
    } else {
      None
    }
  }

  /**
   * Get an argument from the head term 
   * @param pos  position of the target argument of the head styla term
   * @return     value of the attribute
   */
  def getHeadArgument(pos: Int): Option[String] = {
    if(styterms.length < 1) {
      throw UnSafeException(s"No head in styterms ${styterms}")
    }
    styterms.head match {
      case sf: StyFun if sf.sym == ":" =>  // with speaker
        assert(sf.args.length == 2 && sf.args(1).isInstanceOf[StyFun], s"Invalid atom with speaker: ${sf}")
        getFunArgument(sf.args(1).asInstanceOf[StyFun], pos) 
      case f: StyFun =>
        getFunArgument(f, pos) 
      case _ => None
    }
  } 

  /**
   * Get an argument from a styla function 
   * @param f    a styla function
   * @param pos  position of the target attribute in the styla fun
   * @return     value of the attribute
   */
  def getFunArgument(f: StyFun, pos: Int): Option[String] = {
    if(pos < f.args.length) {
      f.args(pos).ref match {
        case i: StyInterpolate => Some(i.eval)
        case c: StyConstant => Some(c.sym)
        case _ => None
      } 
    } else {
      logger.error(s"pos (${pos}) is out of bound (${f.args.length})")
      None 
    }
  }

  import StyStmtHelper._

  override def toStringWithSays() = toString("")
  override def toStringCompact(self: String): String = {
    //println(s"[StylaStmt toStringCompact] self=${self}")
    toString(".", self) 
  }

  def toString(endsWith: String, speaker: String = ""): String = {
    val sb = new StringBuilder 
    //println(s"\n[StylaStmt toString] ${this}")
    if(styterms.length > 0) {
      val head = styterms.head
      val last = styterms.last
      for(t <- styterms) {
        sb.append(styTermToString(t, vmap, speaker))
        if(t == last) {
          sb.append(endsWith)
        } else if(t == head) {
          sb.append(":-")
        } else {
          sb.append(",")
        }
      }
    }
    sb.toString()
  }

  override def toString(): String = {
    s"StyStmt(${styterms}, ${vmap})"
  }
}

object StyStmt {
  def apply(styterms: List[StyTerm]): StyStmt = {
    val vmap = LinkedHashMap[String, StyVar]()
    new StyStmt(styterms, vmap)
  }

  def apply(styterms: List[StyTerm], vmap: LinkedHashMap[String, StyVar]): StyStmt = {
    new StyStmt(styterms, vmap)
  }

  def apply(styterms: List[StyTerm], vmap: LinkedHashMap[String, StyVar], speaker: String): StyStmt = {
    val stmt = new StyStmt(styterms, vmap)
    stmt.addSpeaker(speaker)
  }
}

class StyRetraction(styterms: List[StyTerm], vmap: LinkedHashMap[String, StyVar]) extends StyStmt(styterms, vmap) {
  override def toStringCompact(self: String): String = {
    //println(s"[StyRetraction] called toStringCompact    self=${self}")
    val s = toString("~", self) 
    //println(s"[StyRetraction] called toStringCompact    s=${s}")
    s
  }

  override def toString(): String = {
    s"StyRetraction(${styterms}~, ${vmap})"
  }
 
  override def buildStmtObject(sterms: List[StyTerm], vm: LinkedHashMap[String, StyVar]) = {
    StyRetraction(sterms, vm)
  }
}

object StyRetraction {
  def apply(styterms: List[StyTerm], vmap: LinkedHashMap[String, StyVar]): StyStmt = {
    new StyRetraction(styterms, vmap)
  }

  def apply(styterms: List[StyTerm], vmap: LinkedHashMap[String, StyVar], speaker: String): StyStmt = {
    val stmt = new StyRetraction(styterms, vmap)
    stmt.addSpeaker(speaker)
  }
}

/**
 * Styla queries and annotated queries
 */
class StyQuery(styterms: List[StyTerm], vmap: LinkedHashMap[String, StyVar], val annotation: Int) extends StyStmt(styterms, vmap) {
  val endwith = if(annotation == UNCLASSIFIED) "?" else s"? =@= ${tagToString(annotation)}"
  override def toStringCompact(self: String): String = {
    //println(s"[StyRetraction] called toStringCompact    self=${self}")
    val s = toString(endwith, self) 
    //println(s"[StyRetraction] called toStringCompact    s=${s}")
    s
  }

  override def toString(): String = {
    s"StyQuery(${styterms}${endwith}, ${vmap})"
  }
 
  override def buildStmtObject(sterms: List[StyTerm], vm: LinkedHashMap[String, StyVar]) = {
    StyQuery(sterms, vm, annotation)
  }
}

object StyQuery {
  def apply(styterms: List[StyTerm], vmap: LinkedHashMap[String, StyVar]): StyStmt = {
    new StyQuery(styterms, vmap, UNCLASSIFIED)
  }

  def apply(styterms: List[StyTerm], vmap: LinkedHashMap[String, StyVar], annotation: Int): StyStmt = {
    new StyQuery(styterms, vmap, annotation)
  }

  def apply(styterms: List[StyTerm], vmap: LinkedHashMap[String, StyVar], annotation: Int, speaker: String): StyStmt = {
    val stmt = new StyQuery(styterms, vmap, annotation)
    stmt.addSpeaker(speaker)
  }
}

/* Helper for styla statements */
object StyStmtHelper {
  final val PLAIN = 0
  final val SINGLE_QUOTED = 1
  final val DOUBLE_QUOTED = 2

  /**
   * Get a string representation of a Styla term
   * Useful for posting certificates
   */
  def styTermToString(t: StyTerm, vmap: LinkedHashMap[String, StyVar], self: String): String = {
    val reverseVmap = LinkedHashMap[StyVar, String]()
    for( (vname, styvar) <- vmap ) {
      reverseVmap.put(styvar, vname)
    }
    val sb = new StringBuilder() 
    styTermToString(t, reverseVmap, sb, self)
    sb.toString 
  }

  def styTermToString(t: StyTerm, rvmap: LinkedHashMap[StyVar, String], sb: StringBuilder, self: String, strType: Int): Unit = {
    if(strType == PLAIN) {
      styTermToString(t, rvmap, sb, self)
    } else if(strType == SINGLE_QUOTED) {
      //sb.append("'")
      styTermToString(t, rvmap, sb, self)
      //sb.append("'")
    } else if(strType == DOUBLE_QUOTED) {
      //sb.append("\"")
      styTermToString(t, rvmap, sb, self)
      //sb.append("\"")
    } else {
       throw UnSafeException(s"Unrecognized string type: ${strType}") 
    }
  }

  val symPattern = """([a-z][a-zA-Z0-9]*)""".r
  val ipv4Pattern = """ipv4"(.*)""".r
  val portPattern = """port"(.*)""".r

  val styInfixMap: Map[String, String] = Map(":"->":", "isInRange"->"<:", "is_nonnum"->":=", "."->"|", "eq"->"=") 
  def styTermToString(t: StyTerm, rvmap: LinkedHashMap[StyVar, String], sb: StringBuilder, self: String): Unit = {
    //println(s"[StylaStmt styTermToString]   t=${t}")
    t.ref match {
      case cons: StyCons => 
        //println(s"cons.args(0).getClass=${cons.args(0).getClass}  cons.getArg(0)=${cons.getArg(0)}" +  
        //        s"cons.args(1).getClass=${cons.args(1).getClass}  cons.getArg(1)=${cons.getArg(1)}")
        sb.append(StyCons.to_string(cons, rvmap)) 
      //case f: StyFun if f.sym==":" || f.sym=="isInRange" || f.sym =="is_nonnum" || f.sym=="." || f.sym=="eq" => // infix operator 
      case f: StyFun if f.sym==":" || f.sym=="isInRange" || f.sym =="is_nonnum" || f.sym=="eq" => // infix operator 
        assert(f.args.length == 2, s"${f.sym} fun must have 2 args: $t   #args=${f.args.length}")
        //if(f.sym == ".") sb.append("[")
        if(f.sym != ":" || !f.args(0).isInstanceOf[StyConstant] || f.args(0).asInstanceOf[StyConstant].sym != self) { // omit speaker if it's self 
          styTermToString(f.args(0), rvmap, sb, self)
          // operator
          sb.append(styInfixMap(f.sym))
        }
        styTermToString(f.args(1), rvmap, sb, self)
        //if(f.sym == ".") sb.append("]")
      case f: StyInterpolate =>
        //println(s"[StylaStmt styTermToString StyInterpolate]   f=${f}")
        //println(s"[StylaStmt styTermToString StyInterpolate]   f.eval=${f.eval}")
        sb.append("'")
        sb.append(f.eval)
        sb.append("'")
      case f: StyFun => 
        //println(s"[StylaStmt styTermToString fun]   f.sym=${f.sym}")
        sb.append(f.sym)
        sb.append("(")
        val numargs = f.args.length
        val singlequoted = (f.sym == "link" || f.sym == "principal")  // Links are base64 encoded
        //println(s"[StylaStmt styTermToString fun]   singlequoted=${singlequoted}   numargs=${numargs}")
        for(i <- 0 to numargs-1) {
          if(singlequoted) {
            styTermToString(f.args(i), rvmap, sb, self, SINGLE_QUOTED)
          } else {
            //println(s"[StylaStmt styTermToString fun]   f.args(${i}) = ${f.args(i)};  ${f.args(i).getClass}")
            styTermToString(f.args(i), rvmap, sb, self)
          }
          if(i != numargs-1) {
            sb.append(",")
          }
        }
        sb.append(")")
      case v: StyVar if v.unbound =>
        if(rvmap.contains(v)) {
          val str = if(rvmap(v).startsWith("_")) "_" else rvmap(v) 
          //println(s"[StylaStmt styTermToString unbound]   v=${v}    rvmap(v)=${rvmap(v)}    str=${str}")
          sb.append(str) 
        } else {
          throw UnSafeException(s"cannot find the name for Styla variable $v     ${rvmap.keySet}") 
        }
      case v: StyVar =>
        //println(s"[StylaStmt styTermToString]   v.ref=${v.ref}    v.ref=${v.ref.getClass}")
        styTermToString(v.ref, rvmap, sb, self)
      case c: StyConstant =>
        //println(s"[StylaStmt styTermToString StyConstant]   c.sym=${c.sym}")
        c.sym match {
          case symPattern(t) => sb.append(c.sym) 
          case ipv4Pattern(t) => sb.append(c.sym)
          case portPattern(t) => sb.append(c.sym)
          case _ =>         // strings that need single quotes 
            sb.append("'") 
            sb.append(c.sym)  
            sb.append("'")
        }
      case c: StyNum => sb.append(c)
      case _ => sb.append("[NA]") 
    }
  }

  /* Make a set of indexed SytStmts out of a styla-parsed prolog program */
  def indexStyStmts(prolog: List[List[StyTerm]], vmap: LinkedHashMap[String, StyVar]): Map[Index, OrderedSet[Statement]] = {
    val speaker = ""
    indexStyStmts(prolog, vmap, speaker)
  }
 
  val qAnnotMap = Map("_query" -> UNCLASSIFIED, "_query_allow" -> ALLOW, "_query_require" -> REQUIRE, "_query_deny" -> DENY)
  /* Make a set of indexed SytStmts wth speaker out of a styla-parsed prolog program */
  def indexStyStmts(prolog: List[List[StyTerm]], vmap: LinkedHashMap[String, StyVar], speaker: String):
     Map[Index, OrderedSet[Statement]] = {
    val program = MutableMap[Index, OrderedSet[Statement]]()
    for(l <- prolog) {
      l match {
        case (query: StyQueryTerm) :: Nil => 
          val qtype = query.sym
          assert(qAnnotMap.contains(qtype), s"Invalid type of Styla query: ${qtype}")
          val annotation = qAnnotMap(qtype)
          val stystmt = if(speaker.isEmpty) StyQuery(query.qargs.toList, vmap, annotation) else StyQuery(query.qargs.toList, vmap, annotation, speaker)
          val idx = "_query" //query.sym
          //println(s"[StyStmt indexStyStmts] idx=${idx}  ==>  stystmt=${stystmt}")
          addStmt(StrLit(idx), stystmt, program)
        case (retraction: StyRetractionTerm) :: Nil =>
          val stystmt = if(speaker.isEmpty) StyRetraction(retraction.args.toList, vmap) else StyRetraction(retraction.args.toList, vmap, speaker)
          val idx = retraction.sym // "_retraction"
          addStmt(StrLit(idx), stystmt, program) 
	case _ =>  
          val stystmt = if(speaker.isEmpty) StyStmt(l, vmap) else StyStmt(l, vmap, speaker)
          val idx = stystmt.secondaryIndex
          //println(s"[StyStmt indexStyStmts] idx=${idx}  ==>  stystmt=${stystmt}")
          addStmt(idx, stystmt, program) 
      }
    } 
    //println(s"[StyStmt indexStyStmts] program.toMap=${program.toMap}")
    program.toMap
  }

  /* Add a statement into an index mutable map */ 
  def addStmt(idx: Index, stmt: Statement, m: MutableMap[Index, OrderedSet[Statement]]): 
      OrderedSet[Statement] = {
    val stmts: OrderedSet[Statement] = m.get(idx) match {
      case Some(v: OrderedSet[Statement]) => v += stmt
      case None =>
        val newset = OrderedSet[Statement]()
        newset += stmt
        m.put(idx,newset)
        newset
    }
    stmts
  }

}

