package safe.safelang
package parser

import scala.collection.mutable.{Set => MutableSet}
import scala.collection.mutable.{LinkedHashSet => OrderedSet}

import safe.safelog.{
  Constant, Variable, Encoding, MutableCache, StrLit,
  Assertion, Retraction, Query, QueryAll, Term, Structure, Statement, 
  termType, ParserException, Index, Validity, UnSafeException
}
import model.{SubjectStmt, SpeakerStmt, Subject, SlogSetTemplate}
    
trait ParserImpl
  extends safe.safelog.parser.ParserImpl
  with scala.util.parsing.combinator.JavaTokenParsers
  with scala.util.parsing.combinator.PackratParsers
  with com.typesafe.scalalogging.LazyLogging {
  slangParser: ParserService =>

  lazy val reservedFunctions: Set[StrLit] = Set(StrLit("definit"), StrLit("defenv"), StrLit("defcon"), StrLit("defun"), StrLit("defetch"), StrLit("defpost"), StrLit("defguard"), StrLit("defcall"))

  /*
  override lazy val endOfSource =

  """([^"\\]*(?:\\.[^"\\]*)*)""".r     // double quotes
  """([^'\\]*(?:\\.[^'\\]*)*)""".r     // single quotes
  """[^}\\]*(?:\\.[^}\\]*)*""".r       // set term
  """[^`\\]*(?:\\.[^`\\]*)*""".r       // fun term
  """(.*)([.?~!]|end)\s*(//.*)?$""".r  // standard eos
  */

  override lazy val statement: PackratParser[(Index, OrderedSet[Statement])] = (query | assertion | retraction) ^^ {
    case s @ Assertion(terms) => terms.head match {
      case Structure(StrLit("import"), trs +: Nil, _, _, _) =>
        val idx = s.primaryIndex
        //val idx = s.secondaryIndex
        //println(s"assertion=${s};  secondaryIndex=${idx}")
        val res = addStatement(idx, s)
        (idx, res)
        //val res = addStatement(StrLit("import"), s)
        //(StrLit("import"), res)
      case c @ Constant(name, _, _, _) if reservedFunctions.contains(name) => // def* case
        //println("[slangParser statement] assertion starting with a constant: " + s)
        buildIndexForDef(c.primaryIndex, s.terms.tail.head.primaryIndex, s.terms.tail)
      case x                  =>
        //println("[slangParser statement] primaryIndex: ${s.primaryIndex}      assertion: ${s}")
        val res = addStatement(s.primaryIndex, s)
        (s.primaryIndex, res)
    }
    case s @ Retraction(x)    =>
      val res = addStatement(StrLit("_retraction"), s)
      (StrLit("_retraction"), res)
    case s @ Query(x)         =>
      //println("[slangParser statement] query: " + s)
      val qIndex = s.terms.head.primaryIndex
      if(qIndex == StrLit("definit0")) {
        buildIndexForDef(qIndex, s.terms.tail.head.primaryIndex, s.terms.tail)
      } else {
        val res = addStatement(StrLit("_query"), s)
        (StrLit("_query"), res)
      }
    case s @ QueryAll(x)      =>
      val qIndex = s.terms.head.primaryIndex
      if(qIndex == StrLit("definit0")) {
        buildIndexForDef(qIndex, s.terms.tail.head.primaryIndex, s.terms.tail)
      } else {
        val res = addStatement(StrLit("_query"), s)
        (StrLit("_query"), res)          // this is on purpose; for indexing we only care whether the statement is a query
      }
    case other                => throw UnSafeException(s"Statement type not detected: $other")
  }
  
  private def buildIndexForDef(defIndex: StrLit, stmtIndex: StrLit, terms: Seq[Term]): Tuple2[Index, OrderedSet[Statement]] = {
    //println("[slangParser]: terms: " + terms)
    println("[slangParser]: build indices: " + defIndex.name + "  " + stmtIndex.name)
    val stmts = Assertion(terms)
    println("[slangParser] stmts  " + stmts)
    val resForDef = addStatement(defIndex, stmts)  // Inserted two statements with different indices
    val res = addStatement(stmtIndex, stmts)
    (stmtIndex, res)
  }
  
  // Note: not("def.*".r) is to avoid the case definit ?Var := fun()
  /*
  override lazy val queryAll: PackratParser[QueryAll] =
    (("queryAll" ~ not("def.*".r) ~> qliterals <~ "end") | (opt(logicalIf) ~ not("def.*".r) ~> qliterals <~ "??")) ^^ {case q => QueryAll(q)}

  override lazy val queryOne: PackratParser[Query] =
    (("query" ~ not("def.*".r) ~> qliterals <~ "end") | (opt(logicalIf) ~ not("def.*".r) ~> qliterals <~ "?")) ^^ {case q => Query(q)}
  */
  override lazy val queryAll: PackratParser[QueryAll] =
    (("queryAll" ~ not("defenv") ~> qliterals <~ "end") | (opt(logicalIf) ~ not("defenv") ~> qliterals <~ "??")) ^^ {case q => QueryAll(q)}

  override lazy val queryOne: PackratParser[Query] =
    (("query" ~ not("defenv") ~> qliterals <~ "end") | (opt(logicalIf) ~ not("defenv") ~> qliterals <~ "?")) ^^ {case q => Query(q)}
 
  lazy val qliterals: PackratParser[Seq[Term]] = initFunction | literals

  override lazy val clause: PackratParser[Seq[Term]]  = (function | rule | groundFact)
  override lazy val headLiteral: PackratParser[Term] = (infixTerm | structureTerm | funTerm | setTerm | atom)
  override lazy val literal: PackratParser[Term] = (headLiteral | nilAtom)

  override lazy val atom: PackratParser[Term] = // Note: opt(typeDelimiter) is a hack to make #type -> value work
    opt((singleQuotedString | symbol | "?" | "_") <~ typeDelimiter) ~ opt(opt(typeDelimiter) ~ (symbol | singleQuotedString) ~ (attrMapIndexDelimiter | attrMapDelimiter)) ~ (sequence | structureTerm | variable | constant) ^^ { 

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

    case None ~ Some(dlm ~ tpe ~ keyAttr)      ~ Variable(x, cattrName, ctpe, ckey)            =>
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

  override lazy val operatorTerm: PackratParser[Term] = functor ~ "(" ~ atoms <~ ")" ^^ {
    case funct ~ lParen ~ trs => Structure(funct, trs)
  }

  override lazy val symbolTerm: PackratParser[Term] = (constantString | singleQuotedString) ~ "(" ~ atoms <~ ")" ^^ {
    case funct ~ lParen ~ trs => Structure(funct.id, trs)
  }

  override lazy val structureTerm: PackratParser[Term] = opt((singleQuotedString | symbol | "?" | "_") <~ typeDelimiter) ~ opt(opt(typeDelimiter) ~ (symbol | singleQuotedString) <~ (attrMapIndexDelimiter | attrMapDelimiter)) ~ (operatorTerm | symbolTerm) ^^ {
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

  override lazy val expr: PackratParser[Term] = (sequence | structureTerm | variable | constant)

  /* Forms of list:
   * (1) [a, b, c, d] = [a | [b, c, d]] = [a, b | [c, d]] = [a, b, c | [d]] = [a, b, c, d | []].
   * (2) [a | [b]] = [a, b].
   * (3) [a, b, c, d] = [a | [b | X]] = [a, b|X].
   *     X = [c, d]
   * (4) [a, b, c, d] = [A, B|C] = [A | [B|C]].
   *     A = a,
   *     B = b,
   *     C = [c, d].
   * (5) [a, b, c, d] = [A|[B|C]].
   *     A = a,
   *     B = b,
   *     C = [c, d].
   */

  lazy val sequence: PackratParser[Term] = "[" ~> opt(literals) ~ opt("|" ~ (variable | sequence)) <~ "]" ^^ {
    case Some(head) ~ None              =>  head.foldRight(Structure(StrLit("nil"), Nil)) { (x,y) => Structure(StrLit("_seq"), Seq(x) ++: Seq(y)) }
    case Some(head) ~ Some(cons ~ body) =>  head.foldRight(body) { (x,y) => Structure(StrLit("_seq"), Seq(x) ++: Seq(y)) }
    case None       ~ None              =>  Constant(StrLit("nil"))
    case None       ~ Some(x)           =>  throw ParserException(s"Invalid sequence format")
  }

  //============== Functional forms ================================//
  lazy val function: PackratParser[Seq[Term]] = (setFunction | jvmFunction | guardFunction | postFunction | fetchFunction | envFunction | initFunction | callFunction)

  def conAndGuardTerm(funType: StrLit, head: Term, body: Seq[Term]): Seq[Term] = body.last match {
    case t @ SetTerm(id: StrLit, argRefs, args, certificate, _, _, _) => 
      Constant(funType) +: head +: body.init :+ Structure(funType, head +: t +: Nil)
    case _ => throw ParserException(s"$funType rule should end with a set term")
  }
  /*
  lazy val guardFunction: PackratParser[Seq[Term]] = "defguard" ~ structureTerm ~ logicalIf ~ literals ^^ {
    case predef ~ head ~ logicalIf ~ body => body.last match {
      case t @ SetTerm(id: StrLit, argRefs, args, certificate, _, _, _) => 
        Constant(StrLit("defguard")) +: head +: body.init :+ Structure(StrLit("defguard"), head +: t +: Nil)
      case Constant(StrLit("true"), _, _, _)     => conAndGuardTerm(StrLit("defguard"), head, body.init)
      case Structure(StrLit("spec"), _, _, _, _) => conAndGuardTerm(StrLit("defguard"), head, body.init)
      case other @ _ => throw ParserException(s"defguard rule should end with a set term but $other found")
    }
  }
  */

  //====defguard is relaxed to support any method after query====//
  lazy val guardFunction: PackratParser[Seq[Term]] = "defguard" ~ structureTerm ~ logicalIf ~ literals ^^ {
    case predef ~ head ~ logicalIf ~ body => Constant(StrLit("defguard")) +: head +: body.map {
      case t @ SetTerm(id: StrLit, argRefs, args, certificate, _, _, _) => 
        Structure(StrLit("defguard"), head +: t +: Nil)
      case other: Term => other
    }
  }

  def fetchAndPostTerm(funType: StrLit, head: Term, body: Seq[Term]): Seq[Term] = body.last match {
    case t @ Structure(StrLit("_seq"), terms, _, _, _) =>
      Constant(funType) +: head +: body.init :+ Structure(funType, head +: t +: Nil)
    case other @ _ => throw ParserException(s"$funType rule should end with a sequence but $other found")
  }
  lazy val postFunction: PackratParser[Seq[Term]] = "defpost" ~ structureTerm ~ logicalIf ~ literals ^^ {
    case predef ~ head ~ logicalIf ~ body => body.last match {
      case t @ Structure(StrLit("_seq"), terms, _, _, _) =>
        Constant(StrLit("defpost")) +: head +: body.init :+ Structure(StrLit("defpost"), head +: t +: Nil)
      case Constant(StrLit("true"), _, _, _)     => fetchAndPostTerm(StrLit("defpost"), head, body.init)
      case Structure(StrLit("spec"), _, _, _, _) => fetchAndPostTerm(StrLit("defpost"), head, body.init)
      case other @ _ => throw ParserException(s"defpost rule should end with a sequence but $other found")
    }
  }
  lazy val fetchFunction: PackratParser[Seq[Term]] = "defetch" ~ structureTerm ~ logicalIf ~ literals ^^ {
    case predef ~ head ~ logicalIf ~ body => body.last match {
      case t @ Structure(StrLit("_seq"), terms, _, _, _) =>
        Constant(StrLit("defetch")) +: head +: body.init :+ Structure(StrLit("defetch"), head +: t +: Nil)
      case Constant(StrLit("true"), _, _, _)     => fetchAndPostTerm(StrLit("defetch"), head, body.init)
      case Structure(StrLit("spec"), _, _, _, _) => fetchAndPostTerm(StrLit("defetch"), head, body.init)
      case other @ _ => throw ParserException(s"defetch rule should end with a sequence but $other found")
    }
  }
  lazy val setFunction: PackratParser[Seq[Term]] = "defcon" ~ structureTerm ~ logicalIf ~ literals ^^ {
    case predef ~ head ~ logicalIf ~ body => body.last match {  // Parse a defcon. Literals can eventually be reduced to setTerm (literals -> literal -> headLiteral -> setTerm)
      case t @ SetTerm(id: StrLit, argRefs, args, certificate, _, _, _) => 
        //println("\n[slangParser setFunction] setTerm " + t + "\n") 
        //println("\n[slangParser setFunction] body " + body + "\n")
        Constant(StrLit("defcon")) +: head +: body.init :+ Structure(StrLit("defcon"), head +: t +: Nil)
      case Constant(StrLit("true"), _, _, _)     => conAndGuardTerm(StrLit("defcon"), head, body.init)
      case Structure(StrLit("spec"), _, _, _, _) => conAndGuardTerm(StrLit("defcon"), head, body.init)
      case other @ _ => throw ParserException(s"defcon rule should end with a set term but $other found")
    }
  }

  lazy val callFunction: PackratParser[Seq[Term]] = "defcall" ~ structureTerm ~ logicalIf ~ literals ^^ {
    case predef ~ head ~ logicalIf ~ body => body.last match {  // Parse a defcon. Literals can eventually be reduced to setTerm (literals -> literal -> headLiteral -> setTerm)
      case t @ SetTerm(id: StrLit, argRefs, args, certificate, _, _, _) => 
        //println("\n[slangParser callFunction] setTerm " + t + "\n") 
        //println("\n[slangParser callFunction] body " + body + "\n")
        Constant(StrLit("defcall")) +: head +: body.init :+ Structure(StrLit("defcall"), head +: t +: Nil)
      case other @ _ => throw ParserException(s"defcon rule should end with a set term but $other found")
    }
  }

  def defunTerm(head: Term, body: Seq[Term]): Seq[Term] = body.last match {
    case t @ FunTerm(id: StrLit, code: String, terms: Seq[Term], compileRef: Class[_], _, _, _) => 
      Constant(StrLit("defun")) +: head +: body.init :+ Structure(StrLit("defun"), head +: t +: Nil)
    case other @ _ => throw ParserException(s"defun rule should end with a set term but $other found")
  }
  lazy val jvmFunction: PackratParser[Seq[Term]] = "defun" ~ structureTerm ~ logicalIf ~ literals ^^ {
    case predef ~ head ~ logicalIf ~ body => body.last match {
      case t @ FunTerm(id: StrLit, code: String, terms: Seq[Term], compileRef: Class[_], _, _, _) => 
        Constant(StrLit("defun")) +: head +: body.init :+ Structure(StrLit("defun"), head +: t +: Nil)
      case Constant(StrLit("true"), _, _, _)     => defunTerm(head, body.init)
      case Structure(StrLit("spec"), _, _, _, _) => defunTerm(head, body.init)
      case other @ _ => throw ParserException(s"defun rule should end with a functional term but $other found")
    }
  }
  lazy val initFunction: PackratParser[Seq[Term]] = "definit" ~ literals ^^ {
    case predef ~ body => Constant(StrLit("definit")) +: Constant(StrLit("query(_*)")) +: body // query(_*) can be anything and acts as a rule head
  }
  lazy val envFunction: PackratParser[Seq[Term]] = "defenv" ~ opt("?") ~> symbol ~ opt("(" ~ ")") ~ logicalIf ~ literals ^^ {
    case head ~ paren ~ logicalIf ~ body => body.last match {
      case Constant(StrLit("true"), _, _, _)     =>
        Constant(StrLit("defenv")) +: Variable(head) +: body.init.init :+ Structure(StrLit("defenv"), Variable(head) +: body.init.last +: Nil)
      case Structure(StrLit("spec"), _, _, _, _) =>
        Constant(StrLit("defenv")) +: Variable(head) +: body.init.init :+ Structure(StrLit("defenv"), Variable(head) +: body.init.last +: Nil)
      case _                            =>
        Constant(StrLit("defenv")) +: Variable(head) +: body.init :+ Structure(StrLit("defenv"), Variable(head) +: body.last +: Nil)
    }
  }

  //lazy val funTerm: PackratParser[Term] = opt("java" | "scala") ~ "`" ~ """[^`\\]*(?:\\.[^`\\]*)*""".r <~ "`" ^^ {
  lazy val funTerm: PackratParser[Term] = opt(symbolConstant) ~ "`" ~ """[^`\\]*(?:\\.[^`\\]*)*""".r <~ "`" ^^ {
    case None ~ lParen ~ body                 => funTermHelper(body)
    case Some(jvmInterpreter) ~ lParen ~ body if((jvmInterpreter == "java") | (jvmInterpreter == "scala")) => 
      funTermHelper(body, s"${jvmInterpreter}")
    case Some(jvmInterpreter) ~ lParen ~ body => throw ParserException(s"Unsupported JVM interpreter language: $jvmInterpreter")
  }

  import java.util.regex.Pattern.quote
  def stripMultiComments(str: String, s: String = "/*", e: String = "*/") = str.replaceAll("(?s)" + quote(s)+".*?" + quote(e), "")
  def stripSingleComments(str: String, markers: String = "//") = str.takeWhile(!markers.contains(_)).trim
  
  @inline
  def funTermHelper(bodyWithComments: String, lang: String = "scala"): FunTerm = {
    // remove duplicates
    // group(1) matches with the Variable without dollar
    val body = stripMultiComments(stripMultiComments(bodyWithComments), "//", "\n")
    val args: Seq[String] = globalVariablePattern.findAllIn(body).matchData.collect {
      case m if !m.group(1).isEmpty => m.group(1) // to handle cases where println(s"$$Self") inside ``.
    }.toSeq.distinct
    val argsWithDollar = args.map(arg => "$" + arg).toArray
    val argsWithQ      = args.map(arg => Variable("?" + arg))
    //val argsWithDollar = args.map(_.toString).toArray
    val compiledRef = jvmContext(lang).compile(body, argsWithDollar)
    FunTerm(StrLit(s"_${lang}"), body, argsWithQ, compiledRef)(jvmContext(lang))
  }

  //============== Set form ================================//
  lazy val setTerm: PackratParser[Term] = immutableSetTerm | mutableSetTerm

  lazy val immutableSetTerm: PackratParser[Term] = "{" ~> "{" ~ opt("""[^}\\]*(?:\\.[^}\\]*)*""".r) ~ "}" <~ "}" ^^ {
    case lparen2 ~ None ~ rparen1                   =>
      Constant(StrLit("nil"))
    case lparen2 ~ Some(slogSource) ~ rparen1       =>
      setTermHelper(slogSource, None, immutable = true)
  }

  lazy val mutableSetTerm: PackratParser[Term] = opt(symbolConstant | variable) ~ "{" ~ opt("""[^}\\]*(?:\\.[^}\\]*)*""".r) <~ "}" ^^ {
    case None ~ lparen ~ None                    =>
      Constant(StrLit("nil"))
    case None ~ lparen ~ Some(slogSource)        =>
      setTermHelper(slogSource, None)
    case Some(name) ~ lparen ~ None             =>
      Constant(StrLit("nil"))        // TODO: is this okay? Under what conditions, we do need to post an empty set?
    case Some(name) ~ lparen ~ Some(slogSource) =>
      setTermHelper(slogSource, Some(name))
  }

  private def setTermHelper(slogSourceWithComments: String, name: Option[Term], immutable: Boolean = false): SetTerm = {
    //val slogSource = stripMultiComments(stripMultiComments(slogSourceWithComments), "//", "\n")
    val slogSource = slogSourceWithComments

    val nameMayBe: Option[Term] = if(immutable) Some(Variable("%ImmutableHash")) else name
    // group(1) catches the variable name without dollar
    val args: Seq[String] = {globalVariablePattern.findAllIn(whiteSpace.replaceAllIn(slogSource, "")).matchData.collect {
      case m if !m.group(1).isEmpty => m.group(1)   // to handle cases where println(s"$$Self") inside ``.
    }.toSeq :+ "Self" :+ "SelfKey"}.distinct

    val argsWithQ: Seq[Term] = args.map{x => Variable("?" + x)}
    val argsMap: Map[StrLit, Term] = argsWithQ.map {term => (term.id, term)}.toMap

    //val unsignedUnBoundCredential: UnsignedUnBoundCredential = {
    //  SafelogParserContext("Self").parseUnsignedCredential(slogSource, argsMap, nameMayBe, immutable)
    //}

    val template: SlogSetTemplate = {
      if(Config.config.logicEngine == "styla") {
        StylaParserService.getParser().parseSlogSetTemplate(slogSource, nameMayBe)
      } else { // Use slog
        SafelogParserContext("Self").parseSlogSetTemplate(slogSource, nameMayBe)
      }
    }

    // setName is a hash of its contents -- helping for testing
    val setName = safe.safelang.model.Identity.base64EncodeURLSafe(
      safe.safelang.model.Identity.hash(slogSourceWithComments.getBytes("UTF-8"), "MD5")
    )
    //println("[slangParser setTerm] === get a setTerm === ")
    //println(SetTerm(setName, args.map(x => StrLit(x)), argsWithQ, unsignedUnBoundCredential)(safelogContext))
    //println("========================================== ")
    SetTerm(setName, args.map(x => StrLit("$"+x)), argsWithQ, template)(safelogContext)
  }

  lazy val symbolConstant: PackratParser[Term] = singleQuotedString | constantString | doubleQuotedString

  private[safe] def parseCertificate(source: String): SlogSet = { 
    //println("[slang ParserImpl] parseCertificate source =================")
    //println(source)
    //println("============================================================") 
    val endOfToken = source.indexOf("\n")
    val token: String = source.substring(0, endOfToken)
    val endOfSig = source.indexOf("\n", endOfToken+1)
    val sig: String = source.substring(endOfToken+1, endOfSig)
    val endOfSpeaker = source.indexOf("\n", endOfSig+1)
    val speaker: String = source.substring(endOfSig+1, endOfSpeaker)
    val endOfValidity = source.indexOf("\n", endOfSpeaker+1)
    val validity: String = source.substring(endOfSpeaker+1, endOfValidity) 
    val endOfSigAlg = source.indexOf("\n", endOfValidity+1)
    val endOfLabel = source.indexOf("\n", endOfSigAlg+1)
    val label: String = source.substring(endOfSigAlg+1, endOfLabel)
    val startOfLogic = source.indexOf("\n", endOfLabel+1) + 1  // An empty line as delimiter
  
    val setData: String = source.substring(endOfSig+1, source.length)
    val slogSource: String = source.substring(startOfLogic, source.length) 
    val vldParts: Array[String] = validity.split(",")
    assert(vldParts.size == 3, s"Invalid validity: ${validity}  ${vldParts}")
    val v: Validity = Validity(Some(vldParts(0).trim), Some(vldParts(1).trim), Some(vldParts(2).trim))
    //println(s"slogSource:${slogSource}")
    //println(s"setData:${setData}")

    StylaParserService.getParser().parseSlogSet(slogSource, label, setData, sig, speaker, v)
  }

  private[safe] def parseFileAsCertificate(fileName: String): SlogSet = {
    val source = scala.io.Source.fromFile(fileName).getLines.mkString("\n")
    val res = parseCertificate(source)
    res
  }

  private def parseAsSegmentsHelper(
    result: ParseResult[MutableCache[Index, OrderedSet[Statement]]]
  ): Tuple4[Map[Index, OrderedSet[Statement]], Seq[Statement], Seq[Statement], Seq[Statement]] = result match {
    case Success(_result, _) =>
      val importSeq      = _result.get(StrLit("_import")).getOrElse(Nil).toSeq
      // We don't need to remove special statements because _statementCache is a new instance on each parseAll call
      //_result           -= StrLit("_import")
      val querySeq       = _result.get(StrLit("_query")).getOrElse(Nil).toSeq
      //_result           -= StrLit("_query")
      val envSeq         = _result.get(StrLit("defenv0")).getOrElse(Nil).toSeq
      //_result           -= StrLit("defenv0")
      val initSeq        = _result.get(StrLit("definit0")).getOrElse(Nil).toSeq
      //_result           -= StrLit("definit0")
      val retractionSeq  = _result.get(StrLit("_retraction")).getOrElse(Nil).toSeq
      //_result           -= StrLit("_retraction")
      val allQueries     = envSeq.map {stmt => Query(stmt.terms.tail)} ++ initSeq.map {stmt => Query(stmt.terms.tail)} ++ querySeq
      Tuple4(_result.toMap, allQueries, importSeq, retractionSeq)
    case failure: NoSuccess => throw ParserException(s"${failure.msg}")
  }

  override def parseAsSegments(
    source: String
  ): Tuple4[Map[Index, OrderedSet[Statement]], Seq[Statement], Seq[Statement], Seq[Statement]] = {
    parseAsSegmentsHelper(parseAll(program, source))
  }

  override def parseAsSegments(
    source: java.io.Reader
  ): Tuple4[Map[Index, OrderedSet[Statement]], Seq[Statement], Seq[Statement], Seq[Statement]] = {
    parseAsSegmentsHelper(parseAll(program, source))
  }

  override def parseFileAsSegments(
    fileName: String
  ): Tuple4[Map[Index, OrderedSet[Statement]], Seq[Statement], Seq[Statement], Seq[Statement]] = {
    val source = new java.io.BufferedReader(new java.io.FileReader(fileName))
    val res = parseAsSegments(source)
    source.close()
    res
  }
}
