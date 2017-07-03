package prolog.fluents

//import java.io._
import scala.collection.mutable.{LinkedHashMap, Map => MutableMap}

import prolog.terms._
import prolog.interp.Prog
import prolog.io._
import com.typesafe.scalalogging.LazyLogging

//case class Key(f: String, n: Int)
//case class Key(index: String)

object IndexTypes {
  val PRIMARY_INDEX = 1
  val SECONDARY_INDEX = 2
}

class DataBase(fname: String) extends LinkedHashMap[String, Deque[List[Term]]] with LazyLogging {
  import IndexTypes._
  var numstmts = 0

  val allByPrimary = MutableMap[String, Deque[List[Term]]]() 
  val factsBySecondary = MutableMap[String, Deque[List[Term]]]()
  val rulesByPrimary = MutableMap[String, Deque[List[Term]]]() 

  def this() = this("")

  /**
   * Make a database using a set of Styla statements
   */
  def this(cs: List[List[Term]]) {
    this()
    addAll(cs)
  } 

  def this(name: String, cs: List[List[Term]]) {
    this()
    addAll(cs)
  }

  type CLAUSE = List[Term]

  val vars = new LinkedHashMap[String, Var]

  if (null != fname && !fname.isEmpty()) {
    fromFile(fname, true)
  } else
    initialize()

  private final def initialize() {
    clear()
    addAll(DataBase.lib)
    vars.clear()
    vars ++= DataBase.vars
  }

  final def fromFile(f0: String, doClear: Boolean): Int = {
    val f = IO.find_file(f0)
    if (f.eq(null)) 0
    else {
      if (doClear) initialize()
      val parser = new TermParser(vars)
      val cs = parser.file2clauses(f)
      addAll(cs)
      1
    }
  }

  def addAll(cs: List[CLAUSE]) {
    cs.foreach(add_or_exec)
    //cs.foreach(add)
    //for(ccs <- cs) {
    //  add(ccs)
    //}
  }

  def exec_cmd(body: CLAUSE) = {
    val q = new_prog(Const.cmd, body)
    q.toSink(new EmptySink())
  }

  def add_or_exec(c: CLAUSE): Boolean = {
    c match {
      case null => false
      case Nil => false
      case Const.cmd :: b :: bs => {
        b match {
          case xs: Cons =>
            if (bs.isEmpty)
              Cons.toList(xs).foreach {
                f => fromFile(f.asInstanceOf[Const].name, false)
              }
            else
              IO.warnmes("bad directive: " + c)

          case other => exec_cmd(b :: bs)
        }
        true
      }
      case other => {
        add(other)
        true
      }
    }
  }

  def new_prog(answer: Term, gs: List[Term]): Prog = {
    val q = new Prog(this)
    q.set_query(answer, gs)
    q
  }

  def key(c: CLAUSE, xindex: Int) = {
    //val x = c.head.ref.asInstanceOf[Const]
    //Key(x.sym, x.len)
    val h = c.head.ref
    val x: String = 
      if(h.isInstanceOf[Fun]) {
        val f = h.asInstanceOf[Fun]
        xindex match {
          case PRIMARY_INDEX => f.primaryIndex()
          case SECONDARY_INDEX => f.secondaryIndex()
          case _ => throw new RuntimeException(s"Unrecognized index type ${xindex}") 
        }
      }
      else { // We need to keey this branch to deal with some Styla stuff, such as \+ 
             // Example: tagAccessTest in safe-apps/safe-styla/defconWithList.slang
        //println(s"statement starts with a const: ${c}")
        val cc = h.asInstanceOf[Const]
        s"${cc.sym}${cc.len}"
      }
    x
  }

  def has_clauses(h: Term): Int = {
    val x = h.ref.asInstanceOf[Fun]
    val k = x.getIndex()
    //val x = h.ref.asInstanceOf[Const]
    //val k = Key(x.sym, x.len)
    val r = get(k)
    r match {
      case None => -1
      case Some(cs) => if (cs.isEmpty) 0 else 1
    }
  }

  def isFact(c: CLAUSE): Boolean = {
    if(c.length == 1) {
      c.head.ref match {
        case f: Fun => if(f.allParamsNonvar) true else false
        case _ => false
      }
    } else {
      false
    }
  }

  def add(c: CLAUSE) {
    val k = key(c, PRIMARY_INDEX)
    val cs = allByPrimary.getOrElseUpdate(k, new Deque[CLAUSE]())
    cs.add(c)
    if(isFact(c)) {  // fact
      val k2 = key(c, SECONDARY_INDEX)
      val cs2 = factsBySecondary.getOrElseUpdate(k2, new Deque[CLAUSE]())
      cs2.add(c)
    } else { // rule
      val cs1 = rulesByPrimary.getOrElseUpdate(k, new Deque[CLAUSE]())
      cs1.add(c)
    }
    numstmts += 1
  }

  def push(c: CLAUSE) {
    val k = key(c, PRIMARY_INDEX)
    val cs = allByPrimary.getOrElseUpdate(k, new Deque[CLAUSE]())
    cs.push(c)
    if(isFact(c)) {  // fact
      val k2 = key(c, SECONDARY_INDEX)
      val cs2 = factsBySecondary.getOrElseUpdate(k2, new Deque[CLAUSE]())
      cs2.push(c)
    } else { // rule
      val cs1 = rulesByPrimary.getOrElseUpdate(k, new Deque[CLAUSE]())
      cs1.push(c)
    }
    numstmts += 1
  }

  def del1(h: Term): List[Term] = {
    val c = List(h)
    val k1 = key(c, PRIMARY_INDEX)
    val all_r1 = del1(h, k1, allByPrimary)
    val rule_r1 = del1(h, k1, rulesByPrimary)
    val k2 = key(c, SECONDARY_INDEX)
    val fact_r2 = del1(h, k2, factsBySecondary)  
    if(all_r1 != null)  return all_r1
    if(fact_r2 != null)  return fact_r2
    if(rule_r1 != null)  return rule_r1
    null
  } 

  def del1(h: Term, k: String, table: MutableMap[String, Deque[List[Term]]]): List[Term] = {
    val xss = table.get(k)
    xss match {
      case None => null
      case Some(css) => {
        val trail = new Trail()
          def is_matching(cs: CLAUSE) = h.matches(cs.head, trail)
        val maybeCs = css.dequeueFirst(is_matching)
        maybeCs match {
          case None => null
          case Some(cs) => cs // .head
        }
      }
    }
  }

  def delAll(h: Term) {
    var deleted: List[Term] = del1(h)
    while (deleted != null) { 
      numstmts -= 1
      deleted = del1(h)
    }
  }

  def cleanUpKey(h: Term) {
    delAll(h)
    val c = List(h)
    val k1 = key(c, PRIMARY_INDEX)
    cleanUpKey(k1, allByPrimary)
    cleanUpKey(k1, rulesByPrimary)
    val k2 = key(c, SECONDARY_INDEX)
    cleanUpKey(k2, factsBySecondary)   
  }

  def cleanUpKey(k: String, table: MutableMap[String, Deque[List[Term]]]) {
    val cs = table.get(k)
    cs match {
      case None => {
        table -= k
      }
      case Some(x) => {
        if (x.isEmpty) table -= k
      }
      case _ => {}
    }
  }

  def getMatches(c: CLAUSE): Deque[CLAUSE] = {
    //getMatches(c, true)
    //logger.info(s"getMatches: ${c}")
    getMatches(c, false)
  }

  def getMatches(c: CLAUSE, verbose: Boolean): Deque[CLAUSE] = {
    val h = c.head.ref.asInstanceOf[Fun]
    var matches = new Deque[CLAUSE]()
    val k1 = h.primaryIndex()
    //logger.info(s"[DataBase getMatches] h:${h}  k1:${k1}  isFirstParamConstant:${h.isFirstParamConstant}")
    if(h.isFirstParamConstant()) {
      val k2 = h.secondaryIndex()
      val facts = factsBySecondary.get(k2) match {
        case None => new Deque[CLAUSE]()
        case Some(cs) => cs
      }  
      val rules = rulesByPrimary.get(k1) match {
        case None => new Deque[CLAUSE]()
        case Some(cs) => cs
      }
      //logger.info(s"[DataBase getMatches] k2:${k2}")
      //logger.info(s"[DataBase getMatches] facts:${facts}")
      //logger.info(s"[DataBase getMatches] rules:${rules}")
      if(!facts.isEmpty && rules.isEmpty) return facts
      if(facts.isEmpty && !rules.isEmpty) return rules
      matches.add(facts)
      matches.add(rules)
    } else {
      matches = allByPrimary.get(k1) match {
        case None => new Deque[CLAUSE]() 
        case Some(cs) => cs
      }
    }
    if(matches.isEmpty) {
      if (verbose) IO.warnmes("call to undefined predicate: " + k1)
      return null
    }
    return matches
  }

  def revVars() = {  // Reverse var map
    val I = vars.iterator
    val revMap = new LinkedHashMap[Var, String]
    while (I.hasNext) {
      val (s, v) = I.next
      val s_ = if (s.startsWith("__")) "_" else s
      revMap.put(v, s_)
    }
    revMap
  }

}

object DataBase {
  val parser = new TermParser()
  val vars = parser.vars
  // ensures this is only parsed once and then reused when creating a new db
  val lib = parser.parseProg(Lib.code)

  def key2fun(k: String) = {
    val f = """\d+""".r.findAllIn(k).toSeq(0)
    val n = """[a-zA-Z]""".r.findAllIn(k).toSeq(0)
    new Fun("/", Array(new Const(f), SmallInt(n.toInt)))
  }

  def fun2key(fn: Fun) = {
    fn.getIndex()
  }
}
