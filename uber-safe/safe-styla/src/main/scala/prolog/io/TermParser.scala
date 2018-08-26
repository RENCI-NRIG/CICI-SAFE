package prolog.io

//import scala.language.postfixOps -- 2.10 only
import scala.util.parsing.combinator._
import scala.collection.mutable._
import java.io.File
import java.io.BufferedReader
import java.io.FileReader
//import scala.Console
import prolog.terms._
import prolog.builtins._
import scala.language.postfixOps

class TermParser(val vars: LinkedHashMap[String, Var])
  extends RegexParsers {
  def this() = this(new LinkedHashMap[String, Var])

  def mkVar(x0: String) = {
    val x = if ("_" == x0) x0 + x0 + vars.size else x0
    vars.getOrElseUpdate(x, new Var(x))
  }
  
  def mkEVar(x: String) = {
    //println(s"[TermParser mkEVar] x=$x")
    vars.getOrElseUpdate(x, new EVar(x))
  }

  def trimQuotes(q: String) = q.substring(1, q.length() - 1)

  protected override val whiteSpace = """(\s|//.*|%.*|(?m)/\*(\*(?!/)|[^*])*\*/|(?m)\(\*(\*(?!\))|[^*])*\*\))+""".r

  val anyVarToken = """((\$|\?)(?:\()([A-Z_]\w*)(?:\)))|((\$|\?)([A-Z_]\w*))""".r 
  val evarToken: Parser[String] = """\$[A-Z_]\w*""".r
  val varToken: Parser[String] = """\?[A-Z_]\w*|[A-Z_]\w*|_""".r
  val symToken: Parser[String] = """[a-z]\w*""".r
  val numToken: Parser[String] = """-?(\d+)(\.\d+)?""".r
  //val specToken: Parser[String] = """[!\+\-\*/>=<]|\\\+|\[\]""".r
  val specToken: Parser[String] = """[!]|\\\+|\[\]""".r
  val quotedToken: Parser[String] = """\"[^"]*\"|\'[^']*\'""".r
  val stringToken: Parser[String] = """[^"]*""".r
  val eocToken: Parser[String] = """\.[\n\r\f\t]*""".r
  val retractToken: Parser[String] = """~[\n\r\f\t]*""".r
  val ipToken: Parser[Term] =  """ipv4\"[0-9/.]+\"""".r ^^ {
    case ip => new Const(ip)
  }

  // These are not what we need
  //val portToken: Parser[Term] =  """port\"[0-9{,\\s+}-]+\"""".r ^^ {
  //val portToken: Parser[Term] =  """port\"[([0-9]+|[0-9]+-[0-9]+)(,\\s+)]+\"""".r ^^ {
  //val portToken: Parser[Term] =  """port\"[0-9,-([\n\r\f\t])]+\"""".r ^^ {
  //  case port => new Const(port)
  //}
  val portSubrange: Parser[String] = """[0-9-]+""".r
  def portRange: Parser[List[String]] = repsep(portSubrange, conjTok)   
  val portToken: Parser[Term] =  """port\"""".r ~ portRange ~ """\"""".r ^^ {
    case h ~ subranges ~ end =>
      //println(s"h: ${h}   subranges: ${subranges.mkString(",")}   end: ${end}") 
      new Const(s"${h}${subranges.mkString(",")}${end}")
  }

  val dcgTopToken: Parser[String] = """-->""".r
  val topToken = """:-""".r
  val clauseTok = topToken | dcgTopToken
  val disjTok = """;""".r
  val implTok = """->""".r
  val conjTok = """,""".r
  val isTok = """<:|is|==|\\==|=\\=|@=<|@>=|@<|@>|=>|<=|=\.\.|>=|=<|=:=|:=|=|>|<""".r
  // isInRange: "<:"
  // is: ":="
  val plusTok = """\+|\-""".r
  val timesTok = """\*|/\\|\\/|//|/|mod|div|:|<<|>>|\^""".r
  val queryTok = """\?""".r
  val qannot = """=@=""".r // query annotation delimiter
  val qtype = """(allow)|(require)|(deny)""".r

  def idToken: Parser[String] = (quotedToken ^^ { trimQuotes } |
    specToken | symToken) ^^ { x => x.replace("\\n", "\n") }

  def prog: Parser[List[List[Term]]] = (cmd | clause | retraction | query)*

  def cmd: Parser[List[Term]] = (topToken ~> conj <~ eocToken) ^^ mk_cmd

  def mk_cmd(bs: List[Term]) = {
    /*
    val f = new Fun(":-")
    f.args = new Array[Term](1)
    val c = TermParser.postProcessBody(Const.nil, bs)
    f.args(0) = Conj.fromList(c.tail)
    List(f)
    */
    TermParser.postProcessBody(Const.cmd, bs)
  }
 
  def retraction: Parser[List[Term]] = (head ~ opt(body) <~ retractToken) ^^ 
    {
      case h ~ None => List(new RetractionTerm(Array(h)))
      case h ~ Some(bs) => 
        val ts = TermParser.postProcessBody(h, bs)
        List(new RetractionTerm(ts.toArray))
    }

  def clause: Parser[List[Term]] = (head ~ opt(body) <~ eocToken) ^^
    {
      case h ~ None => List(h)
      case h ~ Some(bs) => TermParser.postProcessBody(h, bs)
    }

  def head: Parser[Term] = term
  def body: Parser[List[Term]] = topToken ~> conj |
    ((dcgTopToken ~> conj) ^^ { xs => TermParser.DCG_MARKER :: xs })

  def query: Parser[List[Term]] = (conj ~ queryTok ~ opt(qannot ~> qtype)) ^^ 
    {
      case c ~ qflag ~ Some(t) =>
        val q = new Query(s"_query_${t}", c.toArray)
        //println(s"[TermParser annotatedQuery] q=${q}")
        List(q)
      case c ~ qflag ~ None =>  
        val q = new Query("_query", c.toArray)
        //println(s"[TermParser annotatedQuery] q=${q}")
        List(q)
    }

  def mkTerm(t: Term ~ Option[String ~ Term]): Term = t match {
    case x ~ None => x
    case x ~ Some(op ~ y) => {
      //println(s"[TermParser mkTerm] op=$op    x=$x    y=$y")
      val operator = if(op == "<:") "isInRange" else op
      val xy = Array[Term](x, y)
      TermParser.toFunBuiltin(new Fun(operator, xy))
    }
  }

  def conj: Parser[List[Term]] = repsep(term, conjTok)

  def term: Parser[Term] = isTerm

  def clauseTerm: Parser[Term] =
    disjTerm ~ opt(clauseTok ~ disjTerm) ^^ mkTerm

  def disjTerm: Parser[Term] =
    repsep(implTerm, disjTok) ^^ (Disj.fromList)

  def implTerm: Parser[Term] =
    conjTerm ~ opt(implTok ~ conjTerm) ^^ mkTerm

  def conjTerm: Parser[Term] =
    repsep(isTerm, conjTok) ^^ (Conj.fromList)

  def isTerm: Parser[Term] =
    plusTerm ~ opt(isTok ~ plusTerm) ^^ mkTerm

  def plusTerm: Parser[Term] =
    timesTerm ~ opt(plusTok ~ timesTerm) ^^ mkTerm

  def timesTerm: Parser[Term] =
    parTerm ~ opt(timesTok ~ parTerm) ^^ mkTerm

  def parTerm: Parser[Term] = "(" ~> clauseTerm <~ ")" | dcg_escape | plainTerm

  def dcg_escape: Parser[Term] = "{" ~> disjTerm <~ "}" ^^
    { x =>
      val f = new Fun("{}")
      val xs = Array(x)
      f.args = xs
      f
    }

  def plainTerm: Parser[Term] = listTerm | funTerm | stringTerm // this order is important for "[]"

  def funTerm: Parser[Term] =
    varToken ^^ mkVar      |
    evarToken ^^ mkEVar    |
    ipToken                |
    portToken              |
    numToken ^^
      { x => new Real(x) } |
    (idToken ~ opt(args)) ^^
      {
        case x ~ None => {
          val interp = interpolate(x)
          if(interp.isDefined) {
            interp.get
          } else { // Simple string
            val b = TermParser.string2ConstBuiltin(x)
            if (!b.eq(null)) b
            else new Const(x)
          }
        }
        case x ~ Some(xs) => {
          val l = xs.length
          val array = new Array[Term](l)
          //println(s"[TermParser FunTerm]  x=$x      xs=$xs")
          xs.copyToArray(array)
          if (l == 2 && x == ".") {
            //println(s"[TermParser Cons]  l=$l      x=$x      xs=$xs")
            new Cons(xs(0), xs(1))
          }
          else
            TermParser.toFunBuiltin(new Fun(x, array))
        }
      }

  def interpolate(x: String): Option[Term] = {
    val embeddedVars = anyVarToken.findAllIn(x).matchData
    if(embeddedVars.isEmpty) {
      None
    } else {  // String interpolation
      var prev = 0
      val components = ListBuffer[Term]()
      for(m <- embeddedVars) {
        val const = new Const(x.substring(prev, m.start))
        components += const
        val vstr = if(m.group(1)==null) s"${m.group(0)}" else s"${m.group(2)}${m.group(3)}"
        val v = mkVar(vstr) 
        components += v
        prev = m.end
      }
      val const = new Const(x.substring(prev, x.length))
      components += const
      val argfun = new Fun("argsFun", components.toArray)
      val t = TermParser.toFunBuiltin(new Fun("interpolate", Array(argfun)))
      Some(t)
    }
  }

  def stringTerm: Parser[Term] = "\"" ~> stringToken <~ "\"" ^^ { x =>
    //println("here=" + x)
    Cons.fromList(x.toList.map { i => SmallInt(i) })
  }

  def listTerm: Parser[Term] =
    listArgs ^^
      {
        case (xs ~ None) =>
          TermParser.list2cons(xs.asInstanceOf[List[Term]], Const.nil)
        case (xs ~ Some(x)) =>
          TermParser.list2cons(
            xs.asInstanceOf[List[Term]],
            x.asInstanceOf[Term])
      }

  def listArgs: Parser[List[Term] ~ Option[Term]] =
    "[" ~> (repsep(term, ",") ~ opt("|" ~> term)) <~ "]"

  def args: Parser[List[Term]] = "(" ~> repsep(term, ",") <~ ")"

  def parse(s: String) = {
    val text = if (s.trim.endsWith(".")) s else s + ". "
    val termList = parseAll(clause, text) match {
      case Success(xss, _) => xss
      case other => {
        IO.warnmes("syntax error: " + other)
        Nil
      }
    }
    termList
  }

  def parseProg(text: String): List[List[Term]] = {
    //println(s"[TermParser  parseProg] parsing $text")
    val clauseList = parseAll(prog, text) match {
      case Success(xss, _) => xss
      case other => {
        IO.warnmes("syntax error: " + other)
        Nil
      }
    }
  
    // Check the parsing results
    //println("\nStyla parsing results: ")
    //for(c <- clauseList) {
    //  for(t <- c) {
    //    print(s"$t, ") 
    //  }
    //  println(".") // end of a clause
    //}

    clauseList
  }

  def file2clauses(fname: String): List[List[Term]] = {
    if ("stdio" == fname) {
      List(parse("true :- " + IO.readLine("> ")))
    } else parseProg(scala.io.Source.fromFile(fname, "utf-8").mkString)
  }

  def readGoal() = {
    val s = IO.readLine("?- ")
    if (s.eq(null)) null
    else {
      vars.clear()
      val t = parse("true :- " + s)
      (t, vars)
    }
  }
}

object TermParser {

  type VMAP = LinkedHashMap[Var, String]

  val builtinMap = new HashMap[String, Const]()  // Avoid re-loading builtin predicates
  builtinMap.put("true", true_())
  builtinMap.put("fail", fail_())
  builtinMap.put("=", new eq())
  builtinMap.put(":=", new is_nonnum())

  private def fun2special(t: Fun): Fun = {
    if (2 == t.args.length) t.name match {
      case "," => new Conj(t.getArg(0), t.getArg(1))
      case "." => new Cons(t.getArg(0), t.getArg(1))
      case ":-" => new Clause(t.getArg(0), t.getArg(1))
      case ";" => new Disj(t.getArg(0), t.getArg(1))

      case _: String => t
    }
    else if (1 == t.args.length) t.name match {
      case "return" => new Answer(t.getArg(0))
      case _: String => t
    }
    else
      t
  }

  private def string2builtin(s0: String): Const = {

    //println(s"[TermParser string2builtin] s0=${s0}") 
    //if(builtinMap.get(s0).isDefined && builtinMap.get(s0).get.sym == "eq") {
    //  println(s"[TermParser string2builtin] sym = ${builtinMap.get(s0).get.sym}")
    //  //println(s"builtinMap.get(s0).args.length=${builtinMap.get(s0).get.asInstanceOf[Fun].args.length}")
    //}
    //println(s"builtinMap.get(s0)=${builtinMap.get(s0)}")
   // println(s"[TermParser string2builtin] s0=${s0}        builtinMap.get(s0)=${builtinMap.get(s0)}     builtinMap.keySet=${builtinMap.keySet}")
    val res = builtinMap.get(s0) match {
      case None => {

        try {
          val s=if(s0.length>3 && s0.contains('.')) s0 
          else "prolog.builtins." + s0
          
          val bclass = Class.forName(s)
          //println(s"[TermParser string2builtin] s0=${s0}      s=${s}        bclass=${bclass}")

          try {

            val b = bclass.newInstance

            if (b.isInstanceOf[FunBuiltin] || b.isInstanceOf[ConstBuiltin]) {
              val c = b.asInstanceOf[Const]
              builtinMap.put(s, c)
              c
            } else {
              //println("unexpected prolog.builtins class =>" + s)
              null
            }
          } catch {
            case e: Error => {
              //println("unexpected builtin creation failure =>" + s + "=>" + e)
              null
            }
          }
        } catch { // thrown as as we try to see which are our built-ins
          case err: ClassNotFoundException => {
            //println("err=" + err)
            //println("expected builtin creation failure =>" + s0)
            null
          }
        }
      }
      case Some(b) => b
    }
    res
  }

  def toConstBuiltin(c: Const): Const = {
    val t = string2ConstBuiltin(c.name)
    if (t.eq(null)) c else t
  }

  def string2ConstBuiltin(s: String): ConstBuiltin =
    string2builtin(s: String) match {
      case null => null
      case b: ConstBuiltin => b
      case _ => null
    }

  def string2FunBuiltin(s: String): FunBuiltin = {
    string2builtin(s: String) match {
      case null => null
      case b: FunBuiltin => b
      case _ => null
    }
  }

  def toFunBuiltin(f: Fun): Fun = {
    //println(s"[Termparser toFunBuiltin] f=${f}")
    val proto: FunBuiltin = string2FunBuiltin(f.sym)

    val res =
      if (proto.eq(null)) fun2special(f)
      else if (f.args.length != proto.arity) f
      else {
        val b = proto.funClone
        b.args = f.args
        b
      }
    res
  }

  def toQuoted(s: String) = {
    val c =
      if (s.eq(null) || s.length == 0) ' '
      else s.charAt(0)
    if (c >= 'a' && c <= 'z') s   // no quotes for const
    else "'" + s + "'"
  }
  def printclause(rvars: VMAP, xs: List[Term]) =
    IO.println(clause2string(rvars, xs))

  def term2string(t: Term): String =
    term2string(new VMAP(), List(t), "")

  def clause2string(xs: List[Term]): String = {
    clause2string(new VMAP(), xs)
  }

  def clause2string(rvars: VMAP,
    xs: List[Term]): String =
    term2string(rvars, xs, ".")

  def term2string(rvars: VMAP,
    xs: List[Term], end: String): String = {
    val buf = new StringBuilder()

      def pprint(t: Term): Unit = {

        //println(s"[TermParser  term2string]  t.ref=${t.ref}   t.ref.getClass=${t.ref.getClass}")
        t.ref match {
          case v: Var => {
            val s_opt = rvars.get(v)
            s_opt match {
              case None => buf ++= v.toString
              case Some(s) => buf ++= s
            }
          }
          case _: cut => buf ++= "!"
          case _: neck => buf ++= "true"
          case q: eq => {
            pprint(q.getArg(0))
            buf ++= "="
            pprint(q.getArg(1))
          }
          case l: Cons =>
            buf ++= Cons.to_string({ x => term2string(rvars, List(x), "") }, l)
          case f: Fun => {
            val s = toQuoted(f.name)
            buf ++= s
            buf ++= "("
            var first = true
            if (f.args.eq(null)) buf ++= "...null..."
            else
              for (x <- f.args) {
                if (first) first = false else buf ++= ","
                pprint(x)
              }
            buf ++= ")"
          }
          case c: Const => {
            val s = toQuoted(c.name)
            buf ++= s
          }
          case other: Term => buf ++= other.toString
        }

      }

    pprint(xs.head)
    val bs = xs.tail
    if (!bs.isEmpty) {
      buf ++= ":-\n  "
      val ys = if (!bs.isEmpty && bs.head.ref.isInstanceOf[neck]) bs.tail else bs
      pprint(ys.head)
      ys.tail.foreach { x => { buf ++= ",\n  "; pprint(x) } }
    }
    buf ++= end
    buf.toString
  }

  def list2cons(xs: List[Term]): Term = list2cons(xs, Const.nil)

  def list2cons(xs: List[Term], z: Term): Term = xs match {
    case Nil => z
    case y :: ys => new Cons(y, list2cons(ys, z))
  }

  /*
   * transforms the body - cut, for now etc.
   */

  def postProcessBody(h: Term, xs: List[Term]): List[Term] = {
    var hasCut = false
    val V = Var()

      def fixSpecial(t: Term): Term = t match {
        case f: Fun => {
          val g = f.funClone
          val newargs: Array[Term] = f.args.map(fixSpecial)
          g.args = newargs
          g
        }
        case x: Const =>
          x.sym match {
            case "!" => {
              hasCut = true
              cut(V)
            }
            case other => x
          }
        case other => other
      }

    val ys = xs.map(fixSpecial)
    val (e, es) = ys match {
      case DCG_MARKER :: bs =>
        dcgExpand(h, bs)
      case _ => (h, ys)
    }
    if (hasCut) e :: neck(V) :: es else e :: es
  }

  val DCG_MARKER = new Const("$dcg_body")

  //def dcgExpand(a: Term, b: List[Term]) = (a, b)

  def dcgExpand(a: Term, bs: List[Term]): (Term, List[Term]) = {
    val V1 = Var()
    val V2 = Var()
    val hd = dcg_goal(a, V1, V2)
    val cs = dcg_body(bs, V1, V2)
    (hd, cs)
  }

  def dcg_goal(g0: Term, S1: Var, S2: Var): Term = g0 match {
    case c: Cons => { // assuming just [f] not [f,..]
      val f = new Cons(c.getHead, S2)
      val e = new eq()
      val args = new Array[Term](2)
      args(0) = S1
      args(1) = f
      e.args = args
      e
    }
    //case b: cut => b
    //case n: neck => n
    case b: ConstBuiltin => {
      S1.set_to(S2)
      b
    }
    case f: FunBuiltin => {
      S1.set_to(S2)
      f
    }
    case g: Const => {
      if (g.sym == "{}" && g.isInstanceOf[Fun]) {
        val x = g.asInstanceOf[Fun].args(0) // validate
        S1.set_to(S2)
        x
      } else {
        val f = new Fun("vs")
        val args = new Array[Term](2)
        args(0) = S1
        args(1) = S2
        f.args = args
        termcat.action(g, f)
      }
    }
  }

  def dcg_body(bs: List[Term], S1: Var, S2: Var) = {
    //println("here=" + bs)
    val r = dcg_conj(bs, S1, S2)
    //println("there=" + r)
    r
  }

  def dcg_conj(ts: List[Term], S1: Var, S2: Var): List[Term] = {
    ts match {
      case Nil => {
        S1.set_to(S2)
        Nil
      }
      case Const.nil :: Nil => {
        S1.set_to(S2)
        Nil
      }
      case x :: xs => {
        val V = Var()
        val y = dcg_goal(x, S1, V)
        val ys = dcg_conj(xs, V, S2)
        y :: ys
      }

    }
  }

  def string2goal(s: String, parser: TermParser): List[Term] =
    parser.parse("true :- " + s + ". ")

  def string2goal(s: String): List[Term] = string2goal(s, new TermParser())

}
