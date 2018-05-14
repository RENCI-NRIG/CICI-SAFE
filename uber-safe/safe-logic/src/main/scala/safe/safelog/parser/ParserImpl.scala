package safe.safelog
package parser

import scala.collection.mutable.{ListBuffer, Queue, Set => MutableSet}
import safe.safelog.AnnotationTags._
import scala.collection.mutable.{LinkedHashSet => OrderedSet}
import java.nio.file.{Path, Paths}

trait ParserImpl
  extends scala.util.parsing.combinator.JavaTokenParsers 
  with scala.util.parsing.combinator.PackratParsers 
  with com.typesafe.scalalogging.LazyLogging {
  parserImpl: ParserService =>

  // override whiteSpace to support C-style comments or (* comments *)
                             // space | single-line-comment | multi-line-comments-c-style | multi-line-comments-with-braces
  //protected override val whiteSpace = """(\s|(?m)/\*(\*(?!/)|[^*])*\*/|(?m)\(\*(\*(?!\))|[^*])*\*\))+""".r // ?m for multiline mode
  protected override val whiteSpace = """(\s|//.*|(?m)/\*(\*(?!/)|[^*])*\*/|(?m)\(\*(\*(?!\))|[^*])*\*\))+""".r

  /*
  private var _saysOperator: Boolean = Config.config.saysOperator
  private var _self: String          = Config.config.self
  // _statementCache is accessed from Repl
  private[safelog] val _statementCache: MutableCache[Index, MutableSet[Statement]] = 
    new MutableCache[Index, MutableSet[Statement]]()
  */

  lazy val symbol = """[a-zA-Z][a-zA-Z_\d]*""".r
  lazy val identifier: PackratParser[String] = (
      "+"
    | "-"
    | "*"
    | "/"
    | "%"   // modulo operator
    | "!"
    | "not"
    | "<:<" // subset
    | "<="  // Note: order important here for parsing, i.e., <= and >= should come before < and > respectively
    | "<"
    | ">="  
    | ">"
    | "=:=" // compare opeartor; right side eval + left side eval + unify 
    | ":="  // is opeartor; right side eval + unify
    | "="   // unify opeartor
    | "compare"
    | "is"
    | "unify"
    | "max"
    | "min"
    | "range"
    | "subset"
    | "in"
    | "@"   // at operator
  )

  lazy val globalVariablePattern = """\$([a-zA-Z_\d]*)""".r
  lazy val variablePattern = """(^[\?_][a-zA-Z_\d]*)""".r
  lazy val anyVariablePattern = """((\$|\?)(?:\()([a-zA-Z_\d]+)(?:\)))|((\$|\?)([a-zA-Z_\d]+))""".r
  //lazy val anyVariablePattern = """([\$\?])(?:\()?([a-zA-Z_\d]*)(?:\))?""".r

  lazy val typeDelimiter: PackratParser[String]		   = ("#"   |  "type") // dom for domain
  lazy val logicalIf: PackratParser[String]		   = (":-"  |  "if")
  lazy val logicalAnd: PackratParser[String]		   = (","   |  "and")
  lazy val logicalOr: PackratParser[String]		   = (";"   |  "or")
  lazy val logicalEnd: PackratParser[String]		   = ("."   |  "end")
  lazy val logicalNegation: PackratParser[String]	   = ("!"   |  "not")
  lazy val attrMapDelimiter: PackratParser[String]	   = ("->"  |  "as") ^^ {case v => "as"}
  lazy val attrMapIndexDelimiter: PackratParser[String]    = ("->>" |  "keyAs") ^^ {case v => "keyAs"}

  def addStatement(index: Index, s: Statement): OrderedSet[Statement] = {
    val stmts: OrderedSet[Statement] = _statementCache.get(index) map {
      v => v +=s
    } getOrElse {
      val _newSet = OrderedSet.empty[Statement] 
      _newSet += s
    }
    _statementCache.put(index, stmts)
    stmts
  }
  
  def addStatementSeq(index: Index, seq: Seq[Statement]): OrderedSet[Statement] = {
    val stmts: OrderedSet[Statement] = _statementCache.get(index) map {
      v => v ++= seq
    } getOrElse {
      val _newSet = OrderedSet.empty[Statement]
      _newSet ++= seq
    }
    _statementCache.put(index, stmts)
    stmts
  }

  lazy val program: PackratParser[MutableCache[Index, OrderedSet[Statement]]]  = rep(multiRule | statement) ^^ {
    case multiStatements =>
      //multiStatements.foreach(stmt => println("[slog program] stmt: " + stmt))
      _statementCache
  }

  lazy val statement: PackratParser[(Index, OrderedSet[Statement])] = (query | assertion | retraction) ^^ {
    case s @ Assertion(terms) => terms.head match { //println("[slog parser] assertion  terms.head=" + terms.head); terms.head match {
      case Structure(id, trs, _, _, _) if Config.config.reserved.contains(id) => 
        //println("[slogParser statement] reserved structure: " + s)
        val expectedArity: Int = Config.config.reserved(id)
        val termsLength = if(saysOperator == true) {trs.length - 1} else {trs.length}
        //println(s"TERMS LENGTH: ${trs.length}; $terms; saysOp: ${saysOperator}")
        val res = if(termsLength != expectedArity) {
          //println(s"[slog parser] assertion For metadata, the expected arity of $id is $expectedArity but ${trs.length} found")
	  logger.warn(s"For metadata, the expected arity of $id is $expectedArity but ${trs.length} found: ${trs}")
          addStatement(StrLit(s"_${id.name}"), s)
        } else if(termsLength == expectedArity) addStatement(StrLit(s"_${id.name}"), s)
          else if(id.name == "name" && termsLength == 1) addStatement(StrLit(s"_${id.name}"), s)
          else addStatement(s.primaryIndex, s)
        (id, res)
      case _                  =>
        //println("[slogParser statement] assertion: " + s)
        val res = addStatement(s.primaryIndex, s)
        (s.primaryIndex, res)
    }
    case s @ Retraction(x)    => 
      val res = addStatement(StrLit("_retraction"), s)
      (StrLit("_retraction"), res)
    case s @ Query(x)         => 
      val res = addStatement(StrLit("_query"), s)
      (StrLit("_query"), res)
    case s @ QueryAll(x)      => 
      val res = addStatement(StrLit("_query"), s)
      (StrLit("_query"), res)          // this is on purpose; for indexing we only care whether the statement is a query
    case s @ AnnotatedQuery(x, t) =>
      //println("[safelog] insert an annotated query: " + x)
      val res = addStatement(StrLit("_query"), s)
      (StrLit("_query"), res)
    case _                    => throw new UnSafeException(s"Statement type not detected")
  }
  lazy val assertion: PackratParser[Statement] = 
    (("assert" ~> clause <~ "end") | (clause <~ logicalEnd)) ^^ {
    case trs => Assertion(trs)
  }
  lazy val retraction: PackratParser[Retraction] = 
    (("retract" ~> (predicateWithArity | clause) <~ logicalEnd) | ((predicateWithArity | clause) <~ "~")) ^^ {
    case trs => Retraction(trs)
  }

  lazy val predicateWithArity: PackratParser[Seq[Term]] = (constantString ~ "/" ~ integer) ^^ {
    case sym ~ slash ~ arity => Constant(StrLit("_withArity")) +: sym +: arity +: Nil
  }
  lazy val clause: PackratParser[Seq[Term]]  = (rule | groundFact)
  lazy val rule: PackratParser[Seq[Term]] = headLiteral ~ logicalIf ~ literals ^^ { // head :- body1; body2.
      // TODO
      // 1. check for safety: range restriction

      // 2. check for stratified logicalNegation and/or other valid rules (for e.g., guarded safelog)
    case head ~ lIf ~ body => 
      val (isSafe, unSafeVar) = rangeRestrictionCheck(head, body)
      if(!isSafe) {
        println(s"[slog parser] rule   head=${head};      body=${body}")
        throw ParserException(s"""Unsound rule dectected. Check range restriction failed for ${unSafeVar.mkString(",")}""")
      }
      //println("[slogParser] rule: " + (head +: body))
      head +: body
  }
  lazy val multiRule: PackratParser[(Index, OrderedSet[Statement])]  = (multiRuleAssertion | multiRuleRetraction) ^^ {
    case s @ Assertion(terms)  +: other    => terms.head match {
      case Structure(id, trs, _, _, _) if Config.config.reserved.contains(id) => 
        //println("[slogParser] multiRule assertion with reserved structure: " + s)
        val expectedArity: Int = Config.config.reserved(id)
        val termsLength = if(saysOperator == true) {trs.length - 1} else {trs.length}
        //println(s"TERMS LENGTH: ${trs.length}; $terms; saysOp: ${saysOperator}")
        val out = if(termsLength != expectedArity) {
	  logger.warn(s"For metadata, the expected arity of $id is $expectedArity but ${trs.length} found")
          val res: OrderedSet[Statement] = addStatementSeq(StrLit(s"_${id.name}"), s)
          (StrLit(s"_${id.name}"), res)
        } else if(termsLength == expectedArity) {
          val res: OrderedSet[Statement] = addStatementSeq(StrLit(s"_${id.name}"), s)
          (StrLit(s"_${id.name}"), res)
        } else if(id.name == "name" && termsLength == 1) {
          val res: OrderedSet[Statement] = addStatementSeq(StrLit(s"_${id.name}"), s)
          (StrLit(s"_${id.name}"), res)
        } else {
          val res: OrderedSet[Statement] = addStatementSeq(s.head.primaryIndex, s)
          (s.head.primaryIndex, res)
        }
        out
      case _                  =>
        //println("[slogParser] multiRule assertion: " + (s +: other))
        //println("[slogParser] multiRule assertion: " + s)
        //println("[slogParser] multiRule assertion:  s.size=" + s.size)
        //for(ss <- s) {
        //  println("[slogParser] multiRule assertion: ss.getClass=" + ss.getClass + "   ss=" + ss)
        //}
        val res = addStatementSeq(s.head.primaryIndex, s)
        (s.head.primaryIndex, res)
      }
    case s @ Retraction(x) +: other    => 
      val res: OrderedSet[Statement] = addStatementSeq(StrLit("_retraction"), s)
      (StrLit("_retraction"), res)
  }
  lazy val multiRuleAssertion: PackratParser[Seq[Statement]] = 
    (("assert" ~> headLiteral ~ logicalIf ~ repsep(literals, logicalOr) <~ "end") | (headLiteral ~ logicalIf ~ repsep(literals, logicalOr) <~ logicalEnd)) ^^ {
    case head ~ lIf ~ clauses => clauses.map{clause =>   // Qiang: clauses is a confusing name, as we have a combinator called clause. 
      val (isSafe, unSafeVar) = rangeRestrictionCheck(head, clause)
      if(!isSafe) {
        println(s"[slog parser] multiRule   head=${head};      clause=${clause}")
        throw ParserException(s"""Unsound rule dectected. Check range restriction failed for ${unSafeVar.mkString(",")}""")
      }
      Assertion(head +: clause)
    }
  }
  lazy val multiRuleRetraction: PackratParser[Seq[Statement]] = (("retract" ~> headLiteral ~ logicalIf ~ repsep(literals, logicalOr) <~ "end") | (headLiteral ~ logicalIf ~ repsep(literals, logicalOr) <~ "~")) ^^ {
    case head ~ lIf ~ clauses => clauses.map{clause => Retraction(head +: clause)}
  }

  lazy val groundFact: PackratParser[Seq[Term]] = headLiteral ^^ { 
    case head => 
      val (isSafe, unSafeVar) = rangeRestrictionCheck(head, Nil)
      //if(!isSafe) {
      //  println(s"[slog parser] groundFact   head=${head}")
      //  throw ParserException(s"""Unsound rule dectected. Check range restriction failed for ${unSafeVar.mkString(",")}""")
      //}
      Seq(head) 
  }

  lazy val query: PackratParser[Statement] = (queryAll | queryOne) 

  lazy val queryAll: PackratParser[QueryAll] = 
    (("queryAll" ~> literals <~ "end") | (opt(logicalIf) ~> literals <~ "??")) ^^ {case q => QueryAll(q)}

  lazy val annotationDelimiter = """=@=""".r
  lazy val annotationTags: PackratParser[String] = ("allow" | "require" | "deny") 

  lazy val queryOne: PackratParser[Statement] = (queryOneCanonical | queryOneSymbolic)
  lazy val queryOneCanonical: PackratParser[Statement] = 
    ("query" ~> literals ~ opt(annotationDelimiter ~> annotationTags) <~ "end") ^^ {
      case q ~ Some(tagName) => AnnotatedQuery(q, getTag(tagName))  
      case q ~ None => Query(q)
    }

  lazy val queryOneSymbolic: PackratParser[Statement] = (opt(logicalIf) ~> literals ~ "?" ~ opt(annotationDelimiter ~> annotationTags)) ^^ {
    case q ~ queryMark ~ Some(tagName) => println("[slogParser] an annotated query!"); AnnotatedQuery(q, getTag(tagName))
    case q ~ queryMark ~ None => Query(q)    
  }

  lazy val literals: PackratParser[Seq[Term]] = repsep(literal, logicalAnd)
  lazy val headLiteral: PackratParser[Term] = (infixTerm | negatedAtom | structureTerm | atom)
  lazy val literal: PackratParser[Term] = (headLiteral | nilAtom)
  lazy val atoms: PackratParser[Seq[Term]] = repsep(atom, logicalAnd)

  lazy val nilAtom: PackratParser[Term] = opt(constant) ^^ {
    case None                                => Constant(StrLit("true"))
    case Some(c: Constant) if(c.id == StrLit("end"))  => c // the constant match should never occur; it is only for pattern matching purposes
    case other                               => throw ParserException(s"Statement not terminated properly: $other")
  }

  lazy val atom: PackratParser[Term] =  // Note: opt(typeDelimiter) is a hack to make #type -> value work
    opt((singleQuotedString | symbol | "?" | "_") <~ typeDelimiter) ~ opt(opt(typeDelimiter) ~ (symbol | singleQuotedString) ~ (attrMapIndexDelimiter | attrMapDelimiter)) ~ (variable | constant) ^^ {

    case None ~ None                           ~ Constant(x, cattrName, ctpe, ckey)            => 
      val (cctpe: StrLit, indexAndEncode: Encoding) = typeWithEncoding(ctpe, ckey, Encoding.Attr)
      Constant(x, cattrName, cctpe, indexAndEncode)

    case None ~ None                           ~ Variable(x, cattrName, ctpe, ckey)            => 
      val (cctpe: StrLit, indexAndEncode: Encoding) = typeWithEncoding(ctpe, ckey, Encoding.Attr)
      Variable(x, cattrName, cctpe, indexAndEncode)

    case None ~ None                           ~ Structure(x, xterms, cattrName, ctpe, ckey)   => // interpolation case
      val (cctpe: StrLit, indexAndEncode: Encoding) = typeWithEncoding(ctpe, ckey, Encoding.Attr)
      Structure(x, xterms, cattrName, cctpe, indexAndEncode)

    case None ~ Some(None ~ attrName ~ keyAttr)     ~ Constant(x, cattrName, ctpe, ckey)       => 
      val (cctpe: StrLit, indexAndEncode: Encoding) = 
        if (keyAttr == "as") typeWithEncoding(ctpe, ckey, Encoding.Attr)
        else typeWithEncoding(ctpe, ckey, Encoding.IndexAttr)
      Constant(x, StrLit(attrName.toString), cctpe, indexAndEncode)

    case None ~ Some(None ~ attrName ~ keyAttr)     ~ Variable(x, cattrName, ctpe, ckey)       => 
      val (cctpe: StrLit, indexAndEncode: Encoding) =
        if (keyAttr == "as") typeWithEncoding(ctpe, ckey, Encoding.Attr)
        else typeWithEncoding(ctpe, ckey, Encoding.IndexAttr)
      Variable(x, StrLit(attrName.toString), cctpe, indexAndEncode)

    case None ~ Some(dlm ~ tpe ~ keyAttr)      ~ Constant(x, cattrName, ctpe, ckey)            => 
      val (cctpe: StrLit, indexAndEncode: Encoding) =
        if (keyAttr == "as") typeWithEncoding(ctpe, ckey, Encoding.Attr)
        else typeWithEncoding(ctpe, ckey, Encoding.IndexAttr)
      Constant(x, cattrName, StrLit(tpe.toString), indexAndEncode)

    case None ~ Some(dlm ~ tpe ~ keyAttr)      ~ Variable(x, cattrName, ctpe, ckey)       => 
      val (cctpe: StrLit, indexAndEncode: Encoding) =
        if (keyAttr == "as") typeWithEncoding(ctpe, ckey, Encoding.Attr)
        else typeWithEncoding(ctpe, ckey, Encoding.IndexAttr)
      Variable(x, cattrName, StrLit(tpe.toString), indexAndEncode)

    case Some(attrName) ~ None                      ~ Constant(x, cattrName, ctpe, ckey)       => 
      val (cctpe: StrLit, indexAndEncode: Encoding) = typeWithEncoding(ctpe, ckey, Encoding.Attr)
      Constant(x, StrLit(attrName.toString), cctpe, indexAndEncode)

    case Some(attrName) ~ None                      ~ Variable(x, cattrName, ctpe, ckey)       => 
      val (cctpe: StrLit, indexAndEncode: Encoding) = typeWithEncoding(ctpe, ckey, Encoding.Attr)
      Variable(x, StrLit(attrName.toString), cctpe, indexAndEncode)

    case Some(attrName) ~ Some(dlm ~ tpe ~ keyAttr) ~ Constant(x, cattrName, ctpe, ckey)       => 
      val (cctpe: StrLit, indexAndEncode: Encoding) =
        if (keyAttr == "as") typeWithEncoding(ctpe, ckey, Encoding.Attr)
        else typeWithEncoding(ctpe, ckey, Encoding.IndexAttr)
      Constant(x, StrLit(attrName.toString), StrLit(tpe.toString), indexAndEncode)

    case Some(attrName) ~ Some(dlm ~ tpe ~ keyAttr) ~ Variable(x, cattrName, ctpe, ckey)       => 
      val (cctpe: StrLit, indexAndEncode: Encoding) =
        if (keyAttr == "as") typeWithEncoding(ctpe, ckey, Encoding.Attr)
        else typeWithEncoding(ctpe, ckey, Encoding.IndexAttr)
      Variable(x, StrLit(attrName.toString), StrLit(tpe.toString), indexAndEncode)
  }

  protected def typeWithEncoding(tpe: StrLit, enc: Encoding, indexAndEncode: Encoding): (StrLit, Encoding) = {
    if(enc == Encoding.AttrLiteral) (StrLit("StrLit"), Encoding(indexAndEncode.id | 2))
    else if(enc == Encoding.AttrHex) (StrLit("StrLit"), Encoding(indexAndEncode.id | 4))
    else if(enc == Encoding.AttrBase64) (StrLit("StrLit"), Encoding(indexAndEncode.id | 6))
    else (tpe, indexAndEncode) // enc == StrLit
  }

  // A rule is safe (range restricted) iff:
  //
  // 1. Each distinguished variable,             // Unsafe: s(X) :- r(Y)
  // 2. Each variable in an arithmetic subgoal,  // Unsafe: s(X) :- r(Y), X < Y 
  //     or contains an equality or is goal where X = Y, where Y is safe
  // 3. Each variable in a negated subgoal,      // Unsafe: s(X) :- r(Y), NOT r(X)
  //
  // also appears in a nonnegated relational subgoal.

  //@annotation.tailrec
  private def rangeRestrictionCheck(head: Term, body: Seq[Term]): Tuple2[Boolean, Set[Term]] = {

    // filter arithmetic literals and negated literals from body
    def filterVariablesInNegatedArithemeticLiterals(
        body: Seq[Term]
      , stdLiteralVariables: Set[Term] = Set.empty
      , negatedArithmeticLiteralVariables: Set[Term] = Set.empty
    ): (Set[Term], Set[Term]) = body match {

      case NegatedTerm(id, term, _, _, _) +: other => 
        filterVariablesInNegatedArithemeticLiterals(
            other
          , stdLiteralVariables
          , negatedArithmeticLiteralVariables ++ term.unboundVariables
        )
      //case term @ Structure(equality, terms, _, _, _) +: other if Set(StrLit("_unify"), StrLit("_is")).contains(equality) => 
      //  val (isSubGoalSafe: Boolean, varSeq: Set[Term]) = terms.last match {
      //    case Constant(_, _, _, _)                                         => (true, Set.empty)
      //    case v @ Variable(_, _, _, _) if v.isEnvVariable()                => (true, Set.empty) // ignore env variables
      //    case Variable(_, _, _, _)                                         => rangeRestrictionCheck(head, terms)
      //    case Structure(_, xterms, _, _, _)                                => rangeRestrictionCheck(head, terms.head +: xterms)
      //  }
      //  if(isSubGoalSafe) {
      //    filterVariablesInNegatedArithemeticLiterals(
      //        other
      //      , stdLiteralVariables ++ term.head.unboundVariables
      //      , negatedArithmeticLiteralVariables
      //    )
      //  }
      //  else {
      //    filterVariablesInNegatedArithemeticLiterals(
      //        other
      //      , stdLiteralVariables
      //      , negatedArithmeticLiteralVariables ++ varSeq
      //    )
      //  }
      case term @ Structure(arithmetic, terms, _, _, _) +: other if arithmeticLiterals.contains(arithmetic) =>
        filterVariablesInNegatedArithemeticLiterals(
            other
          , stdLiteralVariables
          , negatedArithmeticLiteralVariables ++ term.head.unboundVariables
        )
      case stdLiteral +: other => 
        filterVariablesInNegatedArithemeticLiterals(
            other
          , stdLiteralVariables ++ stdLiteral.unboundVariables
          , negatedArithmeticLiteralVariables
        )
      case Nil => (stdLiteralVariables, negatedArithmeticLiteralVariables)
    }

    val (stdLiteralVariables, negatedArithmeticLiteralVariables) = filterVariablesInNegatedArithemeticLiterals(body)
    val headVariables = head.unboundVariables
    val variablesToCheck: Set[Term] = headVariables ++ negatedArithmeticLiteralVariables
    val diffVariables: Set[Term] = variablesToCheck.diff(stdLiteralVariables)
    if(diffVariables.isEmpty) (true, Set.empty) else (false, diffVariables)
  }

  lazy val negatedAtom: PackratParser[Term] = logicalNegation ~ opt("(") ~ (structureTerm | atom) ~ opt(")") ^^ {
    case neg ~ Some(_) ~ atm ~ Some(_) => NegatedTerm(StrLit("_not"), atm)
    case neg ~ None ~ atm ~ None => NegatedTerm(StrLit("_not"), atm)
  }

  lazy val infixTerm: PackratParser[Term] = (
      expr ~ "compare" ~ expr                     // eval both leftExpr and rightExpr and unify
    | expr ~ "notcompare" ~ expr                  // eval both leftExpr and rightExpr and unify
    | expr ~ "=:=" ~ expr                         // eval both leftExpr and rightExpr and unify
    | expr ~ "!=:=" ~ expr                        // eval both leftExpr and rightExpr and unify
    | expr ~ "is" ~ expr                          // eval rightExpr and unfiy
    | expr ~ ":=" ~ expr                          // eval rightExpr and unfiy
    | expr ~ "unify" ~ expr                       // unify 
    | expr ~ "notunify" ~ expr                    // unify 
    | expr ~ "=" ~ expr                           // unify 
    | expr ~ "!<:<" ~ expr                        // subset
    | expr ~ "<:<" ~ expr                         // subset
    | expr ~ "subset" ~ expr                      // subset
    | expr ~ "notsubset" ~ expr                   // subset
    | expr ~ "<:" ~ expr                          // in
    | expr ~ "in" ~ expr                          // in
    | expr ~ "!<:" ~ expr                         // in
    | expr ~ "notin" ~ expr                       // in
    | expr ~ "!=" ~ expr                          // not unify 
    | expr ~ "<=" ~ expr
    | expr ~ "!<=" ~ expr
    | expr ~ "<" ~ expr
    | expr ~ "!<" ~ expr
    | expr ~ ">=" ~ expr
    | expr ~ "!>=" ~ expr
    | expr ~ ">" ~ expr
    | expr ~ "!>" ~ expr
    | expr ~ "@" ~ expr                           // at operator
  ) ^^ {
    case leftTerm ~ operator ~ rightTerm => operator match {
      case "=:="  | "compare"     => Structure(StrLit("_compare"), Seq(leftTerm) ++: Seq(rightTerm))
      case "!=:=" | "notcompare"  => NegatedTerm(StrLit("_not"), Structure(StrLit("_compare"), Seq(leftTerm) ++: Seq(rightTerm)))
      case ":="   | "is"          => Structure(StrLit("_is"), Seq(leftTerm) ++: Seq(rightTerm))
      case "="    | "unify"       => Structure(StrLit("_unify"), Seq(leftTerm) ++: Seq(rightTerm))
      case "!="   | "notunify"    => NegatedTerm(StrLit("_not"), Structure(StrLit("_unify"), Seq(leftTerm) ++: Seq(rightTerm)))
      case "<:<"  | "subset"      => Structure(StrLit("_subset"), Seq(leftTerm) ++: Seq(rightTerm))
      case "!<:<" | "notsubset"   => NegatedTerm(StrLit("_not"), Structure(StrLit("_subset"), Seq(leftTerm) ++: Seq(rightTerm)))
      case "<:"   | "in"          => Structure(StrLit("_in"), Seq(leftTerm) ++: Seq(rightTerm))
      case "!<:<" | "notin"       => NegatedTerm(StrLit("_not"), Structure(StrLit("_in"), Seq(leftTerm) ++: Seq(rightTerm)))
      case "<"                    => Structure(StrLit("_lt"), Seq(leftTerm) ++: Seq(rightTerm))
      case "!<"                   => NegatedTerm(StrLit("_not"), Structure(StrLit("_lt"), Seq(leftTerm) ++: Seq(rightTerm)))
      case "<="                   => Structure(StrLit("_lteq"), Seq(leftTerm) ++: Seq(rightTerm))
      case "!<="                  => NegatedTerm(StrLit("_not"), Structure(StrLit("_lteq"), Seq(leftTerm) ++: Seq(rightTerm)))
      case ">"                    => Structure(StrLit("_gt"), Seq(leftTerm) ++: Seq(rightTerm))
      case "!>"                   => NegatedTerm(StrLit("_not"), Structure(StrLit("_gt"), Seq(leftTerm) ++: Seq(rightTerm)))
      case ">="                   => Structure(StrLit("_gteq"), Seq(leftTerm) ++: Seq(rightTerm))
      case "!>="                  => NegatedTerm(StrLit("_not"), Structure(StrLit("_gteq"), Seq(leftTerm) ++: Seq(rightTerm)))
      case "@"                    => Structure(StrLit("_at"), Seq(leftTerm) ++: Seq(rightTerm))
      case _                      => Structure(operator, Seq(leftTerm) ++: Seq(rightTerm))
    }
  }

  lazy val expr: PackratParser[Term] = (structureTerm | variable | constant)

  lazy val arithmeticLiterals: Set[StrLit] = Set(
      StrLit("_plus")
    , StrLit("_minus")
    , StrLit("_times")
    , StrLit("_div")
    , StrLit("_rem")
    , StrLit("_lteq")
    , StrLit("_lt")
    , StrLit("_gteq")
    , StrLit("_gt")
    , StrLit("_max")
    , StrLit("_min")
  )

  lazy val functor: PackratParser[StrLit] = (identifier) ^^ { // Qiang: we probably can merge this with the definition of identifier
    case "+"               => StrLit("_plus")
    case "-"               => StrLit("_minus")
    case "*"               => StrLit("_times")
    case "/"               => StrLit("_div")
    case "%"               => StrLit("_rem")     // modulo operator
    case "<:<" | "subset"  => StrLit("_subset")  // subset operator
    case "<:"  | "in"      => StrLit("_in")      // in operator
    case "<="              => StrLit("_lteq")
    case "<"               => StrLit("_lt")
    case ">="              => StrLit("_gteq")
    case ">"               => StrLit("_gt")
    case "=:=" | "compare" => StrLit("_compare") // compare opeartor; right side eval + left side eval + unify 
    case ":="  | "is"      => StrLit("_is")     // is opeartor; right side eval + unify
    case "="   | "unify"   => StrLit("_unify")
    case "!"   | "not"     => StrLit("_not")
    case "max"             => StrLit("_max")
    case "min"             => StrLit("_min")
    case "range"           => StrLit("_range")
    case "@"               => StrLit("_at")      // at operator
  }

  lazy val operatorTerm: PackratParser[Term] = opt(atom <~ ("says" | ":")) ~ functor ~ "(" ~ atoms <~ ")" ^^ {  
    case Some(subject) ~ funct ~ lParen ~ trs => Structure(funct, trs) // No signing needed for functional operators
    case None ~ funct ~ lParen ~ trs => Structure(funct, trs)
  }

  lazy val symbolTerm: PackratParser[Term] = opt(atom <~ ("says" | ":")) ~ (constantString | singleQuotedString) ~ "(" ~ atoms <~ ")" ^^ {
    case Some(speaker) ~ funct ~ lParen ~ trs => Structure(funct.id, speaker +: trs)
    case None ~ funct ~ lParen ~ trs =>
      if(saysOperator == true && self != "Self") Structure(funct.id, Constant(StrLit(self), StrLit("nil"), StrLit("StrLit"), Encoding.AttrBase64) +: trs)
      else if(saysOperator) Structure(funct.id, Variable("$" + s"${self}") +: trs)  // It's Variable("$Self")

      // Qiang: We could replace the above two lines with the following to  enable later binding of $Self
      //if(saysOperator) Structure(funct.id, Variable("$Self") +: trs)
      else Structure(funct.id, trs)
  }

  lazy val overrideOperatorTerm: PackratParser[Term] = opt(atom <~ ("says" | ":")) ~ identifier ~ "(" ~ atoms <~ ")" ^^ {
    case Some(subject) ~ funct ~ lParen ~ trs => Structure(funct, trs) // No signing needed for functional operators
    case None ~ funct ~ lParen ~ trs => Structure(funct, trs)
  }

  // override may be allowed indirectly from a higher layer (slang)
  lazy val overrideDefaultTerm = ".." ~> (overrideOperatorTerm | symbolTerm) ^^ {case x => x}

  lazy val structureTerm: PackratParser[Term] = opt((singleQuotedString | symbol | "?" | "_") <~ typeDelimiter) ~ opt(opt(typeDelimiter) ~ (symbol | singleQuotedString) <~ (attrMapIndexDelimiter | attrMapDelimiter)) ~ (overrideDefaultTerm | operatorTerm | symbolTerm) ^^ {
    // For Structure, attrMapIndexDelimiter does not matter since all predicates are indexed by default
    case None ~ None ~ Structure(funct, trs, _, _, _)                      => 
      Structure(funct, trs, StrLit("nil"), termType)
    case None ~ Some(None ~ attrName) ~ Structure(funct, trs, _, _, _)     => 
      Structure(funct, trs, StrLit(attrName.toString), termType)
    case None ~ Some(dlm ~ tpe) ~ Structure(funct, trs, _, _, _)           => 
      Structure(funct, trs, StrLit("nil"), StrLit(tpe.toString))
    case Some(attrName) ~ None ~ Structure(funct, trs, _, _, _)            => 
      Structure(funct, trs, StrLit(attrName.toString), termType)
    case Some(attrName) ~ Some(dlm ~ tpe) ~ Structure(funct, trs, _, _, _) => 
      Structure(funct, trs, StrLit(attrName.toString), StrLit(tpe.toString))
  }

  lazy val variable: PackratParser[Term] = (localVariable | globalVariable)

  lazy val localVariable: PackratParser[Term] = variablePattern ^^ {v => Variable(v)}
  lazy val globalVariable: PackratParser[Term] = "$" ~> symbol <~ opt("(" ~ ")") ^^ {v => Variable("$" + v)}

  lazy val constant: PackratParser[Term] = (doubleQuotedString | numeric | singleQuotedString | constantString) //^^ { case con => println(s"[slog parser] constant: ${con}"); con }
  lazy val constantString: PackratParser[Term] = not("""end$""".r) ~> symbol  ^^ {  // not would not consume characters
    case sym => Constant(sym) 
    //case sym => println("[slog parser] constantString: " + sym); Constant(sym)
  }
  lazy val doubleQuotedString: PackratParser[Term] = tripleDoubleQuotedString | doubleQuotedStringWithEscapeDelimitedMayBe

  private def parseDoubleQuotedString(str: String): Term = {
    //println(s"[Safelog Parser parseDoubleQuotedString] str=${str}")
    var _mutableStr = str
    val matchdata = anyVariablePattern.findAllIn(str).matchData.toSeq
    val allvars = matchdata.map { m =>
      val enclosedVarMayBe = if(m.group(1)==null) s"${m.group(0)}" else s"${m.group(2)}${m.group(3)}"
      if(m.group(1) != null) { 
        _mutableStr = _mutableStr.replace(s"${m.group(2)}(${m.group(3)})", enclosedVarMayBe) 
      }
      Variable(enclosedVarMayBe)
    } 
    //println(s"[Safelog Parser parseDoubleQuotedString] allvars=${allvars}; allvars.length=${allvars.length}")   
    if(allvars.length == 0) {
      Constant(StrLit(Term.stripQuotes(_mutableStr.toString)), StrLit("nil"), StrLit("StrLit"), Encoding.AttrLiteral)
    } else {
      //println(s"[Safelog Parser parseDoubleQuotedString] _mutableStr=${_mutableStr}")
      Structure(StrLit("_interpolate")
         , Constant(Term.stripQuotes(_mutableStr.toString)) 
        +: Constant(allvars.mkString(","))
        +: allvars
      )
    }
  }

  import com.google.common.net.InetAddresses
 
  lazy val doubleQuotedStringWithEscapeDelimitedMayBe: PackratParser[Term] = opt(symbol) ~ "\"" ~ """([^"\\]*(?:\\.[^"\\]*)*)""".r <~ "\"" ^^ {
    case Some(tpe) ~ lquote ~ str if (tpe == "r" | tpe == "regex") =>
      Variable(s"^$str")
    case Some(tpe) ~ lquote ~ str if (tpe == "ipv4") => 
      if(InetAddresses.isInetAddress(str)) { Structure(StrLit("_ipv4"), Seq(Constant(str)), termIndex, StrLit("address")) }
      else if(getSubnetUtils(str) != null) { Structure(StrLit("_ipv4"), Seq(Constant(str)), termIndex, StrLit("block")) }
      else { throw ParserException(s"Invalid IPv4: ${str}") }
    case Some(tpe) ~ lquote ~ str =>
      throw ParserException(s"Prefix type not recognized: $tpe")
    case None ~ lquote ~ str      => parseDoubleQuotedString(str)
  }
  lazy val tripleDoubleQuotedString: PackratParser[Term] = opt(symbol) ~ "\"\"\"" ~ """(((?s)(?!\"\"\").)*)""".r <~ "\"\"\"" ^^ {
    case Some(tpe) ~ lquote ~ str if (tpe == "r" | tpe == "regex") =>
      Variable(s"^$str")
    case Some(tpe) ~ lquote ~ str =>
      throw ParserException(s"Prefix type not recognized: $tpe")
    case None ~ lquote ~ str      => parseDoubleQuotedString(str)
  }

  lazy val singleQuotedString: PackratParser[Term] = tripleQuotedString | singleQuotedStringWithEscapeDelimitedMayBe // ^^ { case strTerm => println("[slog parser] singleQuotedString: " + strTerm); strTerm }

  // ((?!''')(.|\n))* --- negative lookup is very expensive resulting in stack overflow
  lazy val tripleQuotedString: PackratParser[Term] = opt(symbol) ~ "'''" ~ """(((?s)(?!''').)*)""".r <~ "'''" ^^ {
    case None ~ lquote ~ str      => Constant(StrLit(str.replaceAll("""\\'""", "'")), StrLit("nil"), StrLit("StrLit"), Encoding.AttrLiteral)
    case Some(tpe) ~ lquote ~ str => tpe match {
      case "u" => Constant(StrLit(str.replaceAll("""\\'""", "'")), StrLit("nil"), StrLit("StrLit"), Encoding.AttrBase64)
      case "h" =>
	val hex = try {java.lang.Long.parseLong(str, 16)} catch {
	  case ex: NumberFormatException => throw NumericException(s"Invalid input for hex: $str")
	}
        Constant(StrLit(hex.toString), StrLit("nil"), StrLit("StrLit"), Encoding.AttrHex)
      case _ => throw ParserException(s"Unknown encoding detected: $tpe")
    }
  }

  // ([^'\\]*(?:\\.[^'\\]*)*) match anything other than ' or \; followed by \anything and then not ' or \
  lazy val singleQuotedStringWithEscapeDelimitedMayBe: PackratParser[Term] = opt(symbol) ~ "'" ~ """([^'\\]*(?:\\.[^'\\]*)*)""".r <~ "'" ^^ {
    case None ~ lquote ~ str      => //println("[slog parser] singleQuotedString  str: " + str); 
      Constant(StrLit(str.replaceAll("""\\'""", "'")), StrLit("nil"), StrLit("StrLit"), Encoding.AttrLiteral) // """[^']""".r good enough?
    case Some(tpe) ~ lquote ~ str => tpe match {
      case "u" => Constant(StrLit(str.replaceAll("""\\'""", "'")), StrLit("nil"), StrLit("StrLit"), Encoding.AttrBase64)
      case "h" =>
	val hex = try {java.lang.Long.parseLong(str, 16)} catch {
	  case ex: NumberFormatException => throw NumericException(s"Invalid input for hex: $str")
	}
        Constant(StrLit(hex.toString), StrLit("nil"), StrLit("StrLit"), Encoding.AttrHex)
      case _ => throw ParserException(s"Unknown encoding detected: $tpe")
    }
  }

  lazy val numeric = (
      float              // 32 bit
    | double             // 64 bit
    | hexInteger         // 32 bit (same as long)
    | bigInteger         // string
    | long               // 64 bit
    | doubleInteger      // 64 bit
    | floatInteger       // 32 bit
    | short              // 16 bit (same as char)
    | byte               // 8 bit
    | integer            // 32 bit
  )

  lazy val float: PackratParser[Term] = """-?(\d+\.\d+)([eE][+-]?\d+)?""".r <~ """[fF]+""".r ^^ { c => Constant(c, StrLit("nil"), StrLit("Float")) }
  lazy val double: PackratParser[Term] = """-?(\d+\.\d+)""".r <~ opt("""[dD]?""".r) ^^ { c => Constant(c, StrLit("nil"), StrLit("Double")) } // restriction: .1 and 3. are not valid 
  lazy val hexInteger: PackratParser[Term] = """\-?0x[\da-fA-f]+""".r ^^ { c => 
    val hex = try {java.lang.Long.parseLong(c.substring(2), 16)} catch {
      case ex: NumberFormatException => throw NumericException(s"Invalid input for hex: $c")
    }
    Constant(StrLit(hex.toString), StrLit("nil"), StrLit("StrLit"), Encoding.AttrHex)
  }
  lazy val bigInteger: PackratParser[Term] = wholeNumber <~ """[zZ]""".r ^^ { c => Constant(c, StrLit("nil"), StrLit("BigInt")) }
  lazy val long: PackratParser[Term] = wholeNumber <~ """[lL]""".r ^^ { c => Constant(c, StrLit("nil"), StrLit("Long")) }
  lazy val doubleInteger: PackratParser[Term] = wholeNumber <~ """[dD]""".r ^^ { c => Constant(c, StrLit("nil"), StrLit("Double")) }
  lazy val floatInteger: PackratParser[Term] = wholeNumber <~ """[fF]""".r ^^ { c => Constant(c, StrLit("nil"), StrLit("Float")) }
  lazy val short: PackratParser[Term] = wholeNumber <~ """[sS]""".r ^^ { c => 
    try { c.toShort } catch {
      case ex: NumberFormatException => throw NumericException(s"Invalid input for short: $c: ")
    }
    Constant(c, StrLit("nil"), StrLit("Short")) 
  }
  lazy val byte: PackratParser[Term] = wholeNumber <~ """[bB]""".r ^^ { c => 
    try { c.toByte } catch {
      case ex: NumberFormatException => throw NumericException(s"Invalid input for byte: $c")
    }
    Constant(c, StrLit("nil"), StrLit("Byte"))
  }
  lazy val integer: PackratParser[Term] = wholeNumber ^^ {c => Constant(c, StrLit("nil"), StrLit("Int"))}

  override def parseAll[T](p: Parser[T], in: CharSequence): ParseResult[T] = {
    // Start the statement cache from fresh
    _statementCache = new MutableCache[Index, OrderedSet[Statement]]()
    //println(s"[parse CharSequence] adding a new _statementCache")
    //scala.io.StdIn.readLine()
    super.parseAll(p, in)
  }

  override def parseAll[T](p: Parser[T], in: java.io.Reader): ParseResult[T] = {
    // Start the statement cache from fresh
    _statementCache = new MutableCache[Index, OrderedSet[Statement]]()
    //println(s"[parse java.io.Reader] adding a new _statementCache")
    //scala.io.StdIn.readLine()
    super.parseAll(p, in)
  }

  /**
   * Parse slog code
   *   - source is provided by slang 
   */
  def parseSlog(source: String): ParseResult[MutableCache[Index, OrderedSet[Statement]]] = {
    //println(s"[slogParser parseSlog] saysOperator: ${saysOperator}       Config.confg.saysOperator: ${Config.config.saysOperator}")
    parseAll(program, source)
  }

  private[safe] def parseLiterals(source: String): Seq[Term] = {
    //println("[slog ParserImpl] parseLiterals source =================")
    //println(source)
    //println("============================================================")
    val res = parseAll(literals, source) match {
      case Success(result, _) =>  // result: Term
        result
      case failure: NoSuccess => throw ParserException(s"${failure.msg}")
    }
    res
  }

  override def parse(source: String): Map[Index, OrderedSet[Statement]] = {
    //println(s"[slogParser parse] saysOperator: ${saysOperator}")
    //println("===================== parse source =======================")
    //println(source)
    //println("==========================================================")
    //println(s"_statementCache=${_statementCache}")
    //scala.io.StdIn.readLine()
    val res: Map[Index, OrderedSet[Statement]] = parseAll(program, source) match {
      case Success(_result, _) =>
        //println(s"parsed _result.toMap=${_result.toMap}")
        //scala.io.StdIn.readLine()
        _result.toMap
      case failure: NoSuccess => println("parsing failed"); throw ParserException(s"${failure.msg}")
    }
    res
  }

  override def parse(source: java.io.Reader): Map[Index, OrderedSet[Statement]] = {
    val res: Map[Index, OrderedSet[Statement]] = parseAll(program, source) match {
      case Success(_result, _) => 
        _result.toMap
      case failure: NoSuccess => throw ParserException(s"${failure.msg}")
    }
    res
  }

  override def parseFile(fileName: String): Map[Index, OrderedSet[Statement]] = {
    val source = new java.io.BufferedReader(new java.io.FileReader(fileName))
    println("============parse file===========") 
    println(fileName)
    println("=================================")
    val res = parse(source)
    source.close()
    res
  }

  override def parseFileFresh(speaker: String, fileName: String): Map[Index, OrderedSet[Statement]] = {
    val source = new java.io.BufferedReader(new java.io.FileReader(fileName))
    val res = parse(source)
    source.close()
    res
  }

  def parseFileWithProgramArgs(fileName: String, fileArgs: Option[String] = None): SafeProgram = {
    val t0 = System.nanoTime
    //println(s"Calling parseFileWithProgramArgs")
    //scala.io.StdIn.readLine()

    val stmts: SafeProgram = fileArgs match {
      case Some(args: String) =>
        val argSeq = args.split(",").toSeq
        var _fileContents = scala.io.Source.fromFile(fileName).mkString
        argSeq.zipWithIndex.foreach{ case (arg, idx) =>
          _fileContents = _fileContents.replaceAll(s"\\$$${idx + 1}", s"'$arg'")
        }
      parse(_fileContents)
      case _ => parseFile(fileName)
    }
    val compiletime = (System.nanoTime - t0) / 1000

    logger.info(s"\n\n ====== Parsing DONE: $fileName ======\n")
    //println(s"\n\n ====== Parsing DONE: $fileName ======\n")
    //println(s"Time used for compiling $fileName: $compiletime ms")
    //scala.io.StdIn.readLine()

    stmts
  }

  /**
   * Make an executable by assembling compiled programs from linked code
   * Simple smashing: env vars are shared among all linked code
   * TODO: not deal with scope yet (slog and slang)
   *
   * @param programs  compiled programs from linked code
   */
  def linkPrograms(programs: Seq[SafeProgram]): SafeProgram = {
    val t0 = System.nanoTime
    val allIndices = OrderedSet[Index]()
    programs.foreach{ p => allIndices ++= p.keySet } 
    
    // Import statements are not needed in the combined program
    allIndices -= new Index("import1")

    val monolithic = allIndices.map {
      case i: Index =>
        val newStmtSet = OrderedSet[Statement]()
        programs.foreach { p => newStmtSet ++= p.get(i).getOrElse(OrderedSet.empty[Statement]) }
        i -> newStmtSet
    }.toMap

    val linktime = (System.nanoTime - t0) / 1000
    //println(s"/n/nMerged slang: $slang")
    //println(s"Time used for linking: $linktime ms")

    monolithic
  }


  /**
   * Compile program, and link imported code when it need to do so. 
   * It also keeps track of the imported code, so that the
   * processing is safe even in the face of importing loops. 
   */

  def compileAndLinkWithSource(slangSource: String, referencePath: Path = Paths.get(".")): SafeProgram = {
    import StatementHelper._

    val compiledSources = OrderedSet[String]()
    val sourcesToCompile = Queue[String]()
    var count = 0
    val t0 = System.nanoTime
    val allPrograms = ListBuffer[SafeProgram]()
    var stmts = parse(slangSource) 
    var rPath = referencePath
    var inputSource = true
    do {
      if(inputSource == false) {
        val s = sourcesToCompile.dequeue
        //stmts = parseFileWithProgramArgs(s, fileArgs)
        stmts = parseFileWithProgramArgs(s)
        rPath = Paths.get(s)
        compiledSources += s
      }
      allPrograms += stmts
      val importedFiles: Seq[String] = stmts.get(new Index("import1")) match {
        case Some(importStmts: OrderedSet[Statement]) =>
          //println(s"Import statments: $importStmts")
          importStmts.map(is => getAttribute(Some(is), 0)).filter(_.isDefined).map(_.get).toSeq
        case _ => Seq[String]()
      }

      //println(s"""Imported files: ${importedFiles.mkString("; ")}""")
      // Processing relative paths
      val additionalSources = importedFiles.map( f => rPath.resolve(f).toFile.getCanonicalPath )
      //println(s"""Additional sources: ${additionalSources.mkString("; ")}""")


      val uncompiledAdditional = additionalSources.filter(!sourcesToCompile.contains(_)).filter(!compiledSources.contains(_))
      //println(s"""Uncompiled additional: ${uncompiledAdditional.mkString("; ")}""")
      //scala.io.StdIn.readLine()

      sourcesToCompile ++= uncompiledAdditional
      count += 1
      if(inputSource) inputSource = false
    }  while(!sourcesToCompile.isEmpty)

    println(s"$count scripts in total are assembled into this code")
    compiledSources.foreach(println(_))
    println()
    val compileTime = (System.nanoTime - t0) / 1000
    println(s"Time used to compile all sources: $compileTime ms")
    //scala.io.StdIn.readLine()

    val monolithic: SafeProgram = linkPrograms(allPrograms)
    val compilePlusLinkTime = (System.nanoTime - t0) / 1000
    println(s"Time used to compile and assemble all code: $compilePlusLinkTime ms")
    //scala.io.StdIn.readLine()

    monolithic
  }


  def compileAndLink(fileName: String, fileArgs: Option[String] = None): SafeProgram = {
    val fileContent: String = substituteAndGetFileContent(fileName, fileArgs)
    val p: Path = Paths.get(fileName)
    compileAndLinkWithSource(fileContent, p)
  }


  def substituteAndGetFileContent(fileName: String, fileArgs: Option[String] = None): String = {
    var fileContent = scala.io.Source.fromFile(fileName).mkString
    fileArgs match {
      case Some(args: String) =>
        val argSeq = args.split(",").toSeq
        argSeq.zipWithIndex.foreach{ case (arg, idx) =>
          fileContent = fileContent.replaceAll(s"\\$$${idx + 1}", s"'$arg'")
        }
      case _ =>
    }
    fileContent
  } 

//  def compileAndLink(fileName: String, fileArgs: Option[String] = None): SafeProgram = {
//    import StatementHelper._
//
//    var referencePath: Path = Paths.get(fileName)
//    val compiledSources = OrderedSet[String]()
//    val sourcesToCompile = Queue[String](referencePath.toFile.getCanonicalPath)
//    var count = 0
//    val t0 = System.nanoTime
//    val allPrograms = ListBuffer[SafeProgram]()
//    while(!sourcesToCompile.isEmpty) {
//      val s = sourcesToCompile.dequeue
//      val stmts = parseFileWithProgramArgs(s, fileArgs)
//      allPrograms += stmts
//      val importedFiles: Seq[String] = stmts.get(new Index("import1")) match {
//        case Some(importStmts: OrderedSet[Statement]) =>
//          //println(s"Import statments: $importStmts")
//          importStmts.map(is => getAttribute(Some(is), 0)).filter(_.isDefined).map(_.get).toSeq
//        case _ => Seq[String]()
//      }
//
//      //println(s"""Imported files: ${importedFiles.mkString("; ")}""")
//      referencePath = Paths.get(s)
//      // Processing relative paths
//      val additionalSources = importedFiles.map( f => referencePath.resolve(f).toFile.getCanonicalPath )
//      //println(s"""Additional sources: ${additionalSources.mkString("; ")}""")
// 
//      compiledSources += s
//
//      val uncompiledAdditional = additionalSources.filter(!sourcesToCompile.contains(_)).filter(!compiledSources.contains(_))
//      //println(s"""Uncompiled additional: ${uncompiledAdditional.mkString("; ")}""")
//      //scala.io.StdIn.readLine()
//
//      sourcesToCompile ++= uncompiledAdditional
//      count += 1
//    }
//
//    println(s"$count scripts in total are assembled into this code")
//    compiledSources.foreach(println(_))
//    println()
//    val compileTime = (System.nanoTime - t0) / 1000
//    println(s"Time used to compile all sources: $compileTime ms")
//    //scala.io.StdIn.readLine()
//
//    val monolithic: SafeProgram = linkPrograms(allPrograms)
//    val compilePlusLinkTime = (System.nanoTime - t0) / 1000
//    println(s"Time used to compile and assemble all code: $compilePlusLinkTime ms")
//    //scala.io.StdIn.readLine()
//
//    monolithic
//  }

  private def parseAsSegmentsHelper(
    result: ParseResult[MutableCache[Index, OrderedSet[Statement]]]
  ): Tuple4[Map[Index, OrderedSet[Statement]], Seq[Statement], Seq[Statement], Seq[Statement]] = result match {

    case Success(_result, _) =>
      val importSeq      = _result.get(StrLit("_import")).getOrElse(Nil).toSeq
      // We don't need to remove special statements because _statementCache is a new instance on each parseAll call
      // _result           -= StrLit("_import")
      val querySeq       = _result.get(StrLit("_query")).getOrElse(Nil).toSeq
      // _result           -= StrLit("_query")
      val retractionSeq  = _result.get(StrLit("_retraction")).getOrElse(Nil).toSeq
      // _result           -= StrLit("_retraction")
      Tuple4(_result.toMap, querySeq, importSeq, retractionSeq)
    case failure: NoSuccess => throw ParserException(s"${failure.msg}")
  }

  override def parseAsSegments(source: String): Tuple4[Map[Index, OrderedSet[Statement]], Seq[Statement], Seq[Statement], Seq[Statement]] = {
    parseAsSegmentsHelper(parseAll(program, source))
  }

  override def parseAsSegments(source: java.io.Reader): Tuple4[Map[Index, OrderedSet[Statement]], Seq[Statement], Seq[Statement], Seq[Statement]] = {
    parseAsSegmentsHelper(parseAll(program, source))
  }

  override def parseFileAsSegments(fileName: String): Tuple4[Map[Index, OrderedSet[Statement]], Seq[Statement], Seq[Statement], Seq[Statement]] = {
    val source = new java.io.BufferedReader(new java.io.FileReader(fileName))
    val res = parseAsSegments(source)
    source.close()
    res
  }

  lazy val startsWithComment = """(\s*//.*)+$""".r  // line starts with a comment
  lazy val pasteMode         = """(\s*p(aste)?(\(\))?\.)\s*$""".r  // line starts with a comment
  lazy val endOfSource       = """(.*)([.?~]|end)\s*(//.*)?$""".r
  lazy val quit              = """(\s*q(uit)?\s*(\(\))?\s*[.?]+\s*$)""".r // q. | quit. | q(). | quit(). | q? | quit? | ..
  lazy val pasteQuit         = """(.*)(\.q(uit)?\s*(\(\))?\s*[.?]+\s*$)""".r // q. | quit. | q(). | quit(). | q? | quit? | ..
  var _isPasteMode           = false

  private def handleCmdLine(source: String): Tuple2[Option[MutableCache[Index, OrderedSet[Statement]]], Symbol] = {
    endOfSource.findFirstIn(source) match {
      case Some(_) => try {
	parseAll(program, source) match {
	  case Success(result, _) => 
	    (Some(result), 'success)
	  case Failure(msg, _)    => 
	    logger.error("Parse error: " + msg)
	    (None, 'failure)
	  case Error(msg, _)      => 
	    logger.error("Parse error: " + msg)
	    (None, 'error)
	}
      } catch {
	case ex: ParserException => 
	  logger.error("Parse error: " + ex)
	  (None, 'error)
	case ex: NumericException => 
	  logger.error("Parse error: " + ex)
	  (None, 'error)
      }
      case None => (None, 'continuation)
    }
  }

  override def parseCmdLine(source: String): Tuple2[Option[MutableCache[Index, OrderedSet[Statement]]], Symbol] = {
    source match {
      case startsWithComment(_) => (None, 'comment)
      case pasteMode(_, _, _)   => 
        _isPasteMode = true
        (None, 'paste)
      case quit(_, _, _) => (None, 'quit)
      case pasteQuit(_, _, _, _) if(_isPasteMode == false) => (None, 'quit)
      case pasteQuit(src, _, _, _) if(_isPasteMode == true)  =>
        _isPasteMode = false
        handleCmdLine(s"$src.")
      case _ if(_isPasteMode)     => (None, 'continuation)
      case _                      => handleCmdLine(source)
    }
  }
}
